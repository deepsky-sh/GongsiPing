package kr.home.gongsiping.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** 한 세대의 연도별 공시가격 */
data class UnitRow(val dong: String, val ho: String, val area: Double, val prices: Map<Int, Long>)

/** 단지 전체 세대 표 */
data class UnitTable(
    val apt: Apt,
    val units: List<UnitRow>,
    val failed: List<String>, // 못 받아온 "동-호"
    val collectedAt: Long
) {
    val years: List<Int> get() = units.flatMap { it.prices.keys }.distinct().sorted()
}

/** 찜한 아파트의 모든 동·호 공시가격 모으기 + 엑셀 시트 만들기 */
object Export {

    /**
     * 동 목록 → 동마다 호 목록 → 호마다 연도별 공시가격.
     * 공시가격 알리미에 부담이 덜 가도록 동시에 4건까지만 요청한다.
     */
    suspend fun collect(
        api: Api,
        apt: Apt,
        onProgress: (done: Int, total: Int, phase: String) -> Unit
    ): UnitTable = coroutineScope {
        val gate = Semaphore(4)
        onProgress(0, 0, "동 목록 확인 중")
        val dongs = retry { api.dongs(apt.aptCode, apt.noticeDate) }
        val dongDone = AtomicInteger(0)
        val units = dongs.map { d ->
            async {
                gate.withPermit {
                    val hos = try {
                        retry { api.hos(apt.aptCode, apt.noticeDate, d.code) }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    onProgress(dongDone.incrementAndGet(), dongs.size, "동·호 목록 확인 중")
                    hos.map { h -> d to h }
                }
            }
        }.awaitAll().flatten()

        val total = units.size
        if (total == 0) throw ApiException("세대 목록을 받지 못했어요. 잠시 뒤 다시 해주세요.")
        val done = AtomicInteger(0)
        val failed = java.util.Collections.synchronizedList(ArrayList<String>())
        onProgress(0, total, "세대별 공시가격 모으는 중")
        val rows = units.map { (d, h) ->
            async {
                gate.withPermit {
                    val r = try {
                        val hist = retry { api.priceHistory(apt.aptCode, apt.noticeDate, d.code, h.code) }
                        if (hist.isEmpty()) null
                        else UnitRow(
                            dong = d.name,
                            ho = h.name,
                            area = hist.last().area,
                            prices = hist.associate { it.year to it.price }
                        )
                    } catch (e: Exception) {
                        null
                    }
                    if (r == null) failed += "${d.name}-${h.name}"
                    onProgress(done.incrementAndGet(), total, "세대별 공시가격 모으는 중")
                    r
                }
            }
        }.awaitAll().filterNotNull()

        val byNatural = compareBy<UnitRow, String>(NATURAL) { it.dong }.thenBy(NATURAL) { it.ho }
        UnitTable(apt, rows.sortedWith(byNatural), failed.toList(), System.currentTimeMillis())
    }

    private suspend fun <T> retry(block: suspend () -> T): T {
        var last: Exception? = null
        repeat(3) { i ->
            try {
                return block()
            } catch (e: Exception) {
                last = e
                delay(600L * (i + 1))
            }
        }
        throw last ?: ApiException("요청 실패")
    }

    /** year == null 이면 전체 연도 */
    fun sheets(table: UnitTable, year: Int?): List<Xlsx.Sheet> {
        val order = compareBy<UnitRow, String>(NATURAL) { it.dong }.thenBy(NATURAL) { it.ho }
        val t = table.copy(units = table.units.sortedWith(order))
        val years = t.years
        val main = if (year == null) allYears(t, years) else oneYear(t, year)
        return listOf(main, summary(t, year), info(t, year))
    }

    private fun H(s: String) = Xlsx.Txt(s, Xlsx.Style.HEADER)
    private fun won(v: Long?) = if (v == null || v <= 0) Xlsx.Empty else Xlsx.Num(v.toDouble(), Xlsx.Style.WON)
    private fun pct(cur: Long?, prev: Long?): Xlsx.Cell =
        if (cur == null || prev == null || prev <= 0) Xlsx.Empty
        else Xlsx.Num(Math.round((cur - prev) * 1000.0 / prev) / 10.0, Xlsx.Style.PCT)

    private fun allYears(t: UnitTable, years: List<Int>): Xlsx.Sheet {
        val head: List<Xlsx.Cell> = listOf(H("동"), H("호"), H("전용면적(㎡)")) + years.map { H("${it}년 (원)") } +
            listOf(H("최근 전년대비(%)"), H("첫해 대비(%)"))
        val rows = t.units.map { u ->
            val last = years.lastOrNull()
            val first = years.firstOrNull { u.prices[it] != null }
            listOf<Xlsx.Cell>(
                Xlsx.Txt(u.dong), Xlsx.Txt(u.ho), Xlsx.Num(u.area, Xlsx.Style.AREA)
            ) + years.map { won(u.prices[it]) } + listOf(
                if (last == null) Xlsx.Empty else pct(u.prices[last], u.prices[last - 1]),
                if (last == null || first == null || first == last) Xlsx.Empty else pct(u.prices[last], u.prices[first])
            )
        }
        return Xlsx.Sheet(
            name = "전체 연도",
            widths = listOf(8.0, 8.0, 12.0) + years.map { 15.0 } + listOf(15.0, 13.0),
            rows = listOf(head) + rows,
            freezeRows = 1, freezeCols = 2, filterRow = 0
        )
    }

    private fun oneYear(t: UnitTable, y: Int): Xlsx.Sheet {
        val head: List<Xlsx.Cell> = listOf(
            H("동"), H("호"), H("전용면적(㎡)"), H("${y}년 공시가격(원)"), H("㎡당(원)"),
            H("${y - 1}년 공시가격(원)"), H("전년대비(원)"), H("전년대비(%)")
        )
        val rows = t.units.filter { it.prices[y] != null }.map { u ->
            val cur = u.prices[y]
            val prev = u.prices[y - 1]
            listOf(
                Xlsx.Txt(u.dong), Xlsx.Txt(u.ho), Xlsx.Num(u.area, Xlsx.Style.AREA),
                won(cur),
                if (cur != null && u.area > 0) Xlsx.Num(Math.round(cur / u.area).toDouble()) else Xlsx.Empty,
                won(prev),
                if (cur != null && prev != null) Xlsx.Num((cur - prev).toDouble()) else Xlsx.Empty,
                pct(cur, prev)
            )
        }
        return Xlsx.Sheet(
            name = "${y}년",
            widths = listOf(8.0, 8.0, 12.0, 17.0, 13.0, 17.0, 14.0, 12.0),
            rows = listOf(head) + rows,
            freezeRows = 1, freezeCols = 2, filterRow = 0
        )
    }

    /** 연도별 요약: 세대 수, 평균, 중앙값, 최저, 최고, ㎡당 중앙값, 전년대비 */
    private fun summary(t: UnitTable, only: Int?): Xlsx.Sheet {
        val ys = if (only == null) t.years else listOf(only)
        val head: List<Xlsx.Cell> = listOf(
            H("연도"), H("세대 수"), H("평균(원)"), H("중앙값(원)"), H("최저(원)"), H("최고(원)"),
            H("㎡당 중앙값(원)"), H("중앙값 전년대비(%)")
        )
        var prevMedian: Long? = if (only != null) medianOf(t, only - 1) else null
        val rows = ys.map { y ->
            val ps = t.units.mapNotNull { it.prices[y] }
            val per = t.units.mapNotNull { u -> u.prices[y]?.takeIf { u.area > 0 }?.let { it / u.area } }
            val med = if (ps.isEmpty()) null else Repo.median(ps.map { it.toDouble() }).toLong()
            val row = listOf(
                Xlsx.Txt("${y}년"),
                Xlsx.Num(ps.size.toDouble(), Xlsx.Style.WON),
                if (ps.isEmpty()) Xlsx.Empty else Xlsx.Num(Math.round(ps.average()).toDouble()),
                won(med),
                won(ps.minOrNull()),
                won(ps.maxOrNull()),
                if (per.isEmpty()) Xlsx.Empty else Xlsx.Num(Math.round(Repo.median(per)).toDouble()),
                pct(med, prevMedian)
            )
            prevMedian = med
            row
        }
        return Xlsx.Sheet(
            name = "요약",
            widths = listOf(9.0, 9.0, 16.0, 16.0, 16.0, 16.0, 15.0, 17.0),
            rows = listOf(head) + rows,
            freezeRows = 1
        )
    }

    private fun medianOf(t: UnitTable, y: Int): Long? {
        val ps = t.units.mapNotNull { it.prices[y] }
        return if (ps.isEmpty()) null else Repo.median(ps.map { it.toDouble() }).toLong()
    }

    private fun info(t: UnitTable, year: Int?): Xlsx.Sheet {
        val f = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
        val notice = t.apt.noticeDate.let {
            if (it.length == 8) "${it.substring(0, 4)}.${it.substring(4, 6)}.${it.substring(6)}" else it
        }
        val lines: List<List<Xlsx.Cell>> = listOf(
            listOf(Xlsx.Txt(t.apt.complexName, Xlsx.Style.TITLE)),
            emptyList<Xlsx.Cell>(),
            listOf(H("항목"), H("내용")),
            listOf(Xlsx.Txt("주소"), Xlsx.Txt(t.apt.address)),
            listOf(Xlsx.Txt("내 세대"), Xlsx.Txt("${t.apt.dong}동 ${t.apt.ho}호" + if (t.apt.nickname.isNotBlank()) " (${t.apt.nickname})" else "")),
            listOf(Xlsx.Txt("내보낸 연도"), Xlsx.Txt(year?.let { "${it}년" } ?: "전체 연도 (${t.years.firstOrNull()}~${t.years.lastOrNull()})")),
            listOf(Xlsx.Txt("세대 수"), Xlsx.Num(t.units.size.toDouble(), Xlsx.Style.WON)),
            listOf(Xlsx.Txt("못 받은 세대"), Xlsx.Txt(if (t.failed.isEmpty()) "없음" else "${t.failed.size}세대: " + t.failed.take(50).joinToString(", ") + if (t.failed.size > 50) " …" else "")),
            listOf(Xlsx.Txt("최근 공시일"), Xlsx.Txt(notice)),
            listOf(Xlsx.Txt("만든 시각"), Xlsx.Txt(f.format(Date(t.collectedAt)))),
            listOf(Xlsx.Txt("출처"), Xlsx.Txt("국토교통부 부동산공시가격 알리미 (www.realtyprice.kr)")),
            listOf(Xlsx.Txt("참고"), Xlsx.Txt("공시가격은 세금 기준 가격으로 실제 시세와 다릅니다. 각 연도 1월 1일 기준.")),
            listOf(Xlsx.Txt("만든 앱"), Xlsx.Txt("공시핑"))
        )
        return Xlsx.Sheet(name = "정보", widths = listOf(14.0, 70.0), rows = lines)
    }

    fun fileName(t: UnitTable, year: Int?): String {
        val name = t.apt.complexName.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().take(40)
        return "공시가격_${name}_${year?.let { "${it}년" } ?: "전체연도"}.xlsx"
    }

    /** "101" < "102" < "1001", 숫자가 아닌 건 뒤로 */
    val NATURAL: Comparator<String> = Comparator { a, b ->
        val na = a.takeWhile { it.isDigit() }.toIntOrNull()
        val nb = b.takeWhile { it.isDigit() }.toIntOrNull()
        when {
            na != null && nb != null && na != nb -> na.compareTo(nb)
            na != null && nb == null -> -1
            na == null && nb != null -> 1
            else -> a.compareTo(b)
        }
    }
}
