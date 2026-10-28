package kr.home.gongsiping.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs

/** 화면·백그라운드 작업이 함께 쓰는 동작들 */
class Repo(private val ctx: Context) {
    val prefs = Prefs(ctx)
    val api = Api(prefs)

    /** 마지막 checkAll 중 하나라도 연결 실패가 있었는지 */
    var lastFailed = false
        private set

    /** 단지 + 동 + 호 를 골라 찜 목록에 추가 */
    suspend fun addApt(c: Complex, dong: Choice, ho: Choice): Apt {
        val rows = api.priceHistory(c.aptCode, c.noticeDate, dong.code, ho.code)
        if (rows.isEmpty()) throw ApiException("이 세대의 공시가격 기록이 없어요")
        val last = rows.last()
        val apt = Apt(
            id = UUID.randomUUID().toString(),
            complexName = c.name,
            nickname = "",
            address = c.address,
            aptCode = c.aptCode,
            noticeDate = c.noticeDate,
            regCode = c.regCode,
            eubCode = c.eubCode,
            bun = c.bun,
            umd = Api.umdOf(c.address).ifBlank { Api.umdOf(last.bjdName) },
            dong = dong.name,
            ho = ho.name,
            dongCode = dong.code,
            hoCode = ho.code,
            area = last.area,
            prices = rows.map { YearPrice(it.year, it.price) }
        )
        Store.upsert(ctx, apt)
        return apt
    }

    /** 주변(같은 법정동, 지번이 가까운 순) 단지들의 ㎡당 공시가격 */
    suspend fun refreshNearby(apt: Apt): Apt = coroutineScope {
        if (apt.demo || !apt.usable) return@coroutineScope apt
        val count = prefs.compareCount
        val all = api.complexesInDong(apt.regCode, apt.eubCode)
        val cands = all
            .filter { it.aptCode != apt.aptCode }
            .filterNot { Regex("빌라|연립|다세대|빌리지|주택").containsMatchIn(it.name) }
            .sortedBy { abs(it.bun - apt.bun) }
            .take(count + 4) // 몇 곳은 실패할 수 있으니 여유 있게
        val gate = Semaphore(3)
        val others = cands.map { c ->
            async {
                gate.withPermit {
                    try {
                        summarize(c)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull().take(count)
        val selfComplex = all.firstOrNull { it.aptCode == apt.aptCode }
            ?: Complex(apt.complexName, apt.address, "", apt.aptCode, apt.noticeDate, apt.regCode, apt.eubCode, apt.bun)
        val self = try {
            summarize(selfComplex)?.copy(isSelf = true, name = apt.complexName)
        } catch (e: Exception) {
            null
        }
        if (others.isEmpty() && self == null) throw ApiException("주변 단지 공시가격을 못 불러왔어요")
        val list = (listOfNotNull(self) + others).sortedByDescending { it.perSqm }
        Store.update(ctx, apt.id) { it.copy(nearby = list, nearbyUpdated = System.currentTimeMillis()) }
        Store.get(ctx, apt.id) ?: apt
    }

    /** 단지 하나를 대표하는 값: 가운데 동에서 세대 3곳(저·중·고층)을 골라 ㎡당 가격 중앙값 */
    private suspend fun summarize(c: Complex): Neighbor? {
        val dongs = api.dongs(c.aptCode, c.noticeDate)
        if (dongs.isEmpty()) return null
        val dong = dongs[dongs.size / 2]
        val hos = api.hos(c.aptCode, c.noticeDate, dong.code)
        if (hos.isEmpty()) return null
        val picks = listOf(hos.first(), hos[hos.size / 2], hos.last()).distinctBy { it.code }
        val latestPer = ArrayList<Double>()
        val prevPer = ArrayList<Double>()
        val prices = ArrayList<Double>()
        for (h in picks) {
            val rows = api.priceHistory(c.aptCode, c.noticeDate, dong.code, h.code).filter { it.area > 0 }
            val last = rows.lastOrNull() ?: continue
            latestPer += last.price / last.area
            prices += last.price.toDouble()
            rows.firstOrNull { it.year == last.year - 1 }?.let { prevPer += it.price / it.area }
        }
        if (latestPer.isEmpty()) return null
        return Neighbor(
            name = c.name,
            aptCode = c.aptCode,
            perSqm = median(latestPer).toLong(),
            medianPrice = median(prices).toLong(),
            units = latestPer.size,
            addr = Api.shortAddr(c.address),
            prevPerSqm = if (prevPer.isEmpty()) 0L else median(prevPer).toLong()
        )
    }

    /** 같은 법정동의 최근 2개월 실거래 (최신순) */
    suspend fun recentTrades(apt: Apt): List<Trade> {
        if (apt.demo) return Demo.trades()
        val out = ArrayList<Trade>()
        for (ym in recentMonths(2)) {
            out += api.trades(apt.lawdCode, ym).filter { matchesUmd(it.first, apt.umd) }.map { it.second }
        }
        return out.sortedWith(compareByDescending<Trade> { it.year * 10000 + it.month * 100 + it.day })
    }

    /** 최신 공시 일자 확인: 이름 검색 → 안 되면 법정동 목록에서 같은 단지 코드 */
    private suspend fun latestNotice(apt: Apt): String? {
        val byName = try {
            api.searchComplexes(apt.complexName.substringBefore("(").trim()).firstOrNull { it.aptCode == apt.aptCode }
        } catch (e: Exception) {
            null
        }
        if (byName != null) return byName.noticeDate
        return api.complexesInDong(apt.regCode, apt.eubCode).firstOrNull { it.aptCode == apt.aptCode }?.noticeDate
    }

    /** 새 공시 일자 기준으로 내 세대 기록 다시 받기 (동·호 코드가 바뀌었으면 이름으로 다시 찾음) */
    private suspend fun historyFor(apt: Apt, notice: String): Pair<List<PriceRow>, Pair<String, String>> {
        val keep = apt.dongCode to apt.hoCode
        val rows = api.priceHistory(apt.aptCode, notice, apt.dongCode, apt.hoCode)
        if (rows.isNotEmpty() && rows.last().dong == apt.dong && rows.last().ho == apt.ho) return rows to keep
        val dong = api.dongs(apt.aptCode, notice).firstOrNull { it.name == apt.dong } ?: return rows to keep
        val ho = api.hos(apt.aptCode, notice, dong.code).firstOrNull { it.name == apt.ho } ?: return rows to keep
        return api.priceHistory(apt.aptCode, notice, dong.code, ho.code) to (dong.code to ho.code)
    }

    private fun applyHistory(apt: Apt, notice: String, rows: List<PriceRow>, codes: Pair<String, String>) {
        if (rows.isEmpty()) return
        Store.update(ctx, apt.id) {
            it.copy(
                prices = mergePrices(it.prices, rows.map { r -> YearPrice(r.year, r.price) }),
                noticeDate = notice, dongCode = codes.first, hoCode = codes.second
            )
        }
    }

    /** 화면에서 '새로고침' — 새 공시가 있으면 반영 */
    suspend fun refreshHistory(apt: Apt): Apt {
        if (apt.demo || !apt.usable) return apt
        val notice = latestNotice(apt) ?: apt.noticeDate
        val (rows, codes) = historyFor(apt, notice)
        applyHistory(apt, notice, rows, codes)
        return Store.get(ctx, apt.id) ?: apt
    }

    /**
     * 백그라운드 확인. 새로 생긴 일들을 알림 문구로 돌려준다.
     *  1) 새 공시가격이 나왔는지 (인증키 없음)
     *  2) 같은 동네에 새 실거래 신고가 들어왔는지 (공공데이터포털 키가 있을 때만)
     */
    suspend fun checkAll(): List<Alert> {
        Store.reload(ctx)
        lastFailed = false
        val alerts = ArrayList<Alert>()
        val apts = Store.load(ctx).filter { !it.demo && it.usable }
        val tradeCache = HashMap<String, List<Pair<String, Trade>>>()

        for (apt in apts) {
            // 1) 공시가격
            if (prefs.priceAlert) try {
                val notice = latestNotice(apt)
                if (notice != null && notice > apt.noticeDate) {
                    val before = apt.latest?.year ?: 0
                    val (rows, codes) = historyFor(apt, notice)
                    applyHistory(apt, notice, rows, codes)
                    val updated = Store.get(ctx, apt.id) ?: apt
                    val newest = updated.latest
                    if (newest != null && newest.year > before) {
                        val rate = updated.changeRate
                        val diff = if (rate.isNaN()) "" else " · 작년보다 ${Fmt.rate(rate)}"
                        alerts += Alert(
                            apt.id,
                            "${apt.title} ${apt.dong}동 공시가격 나왔어요",
                            "${newest.year} 공시가 ${Fmt.won(newest.price)}$diff"
                        )
                    }
                }
            } catch (e: Exception) {
                lastFailed = true
            }
            // 2) 실거래 신고
            if (prefs.tradeAlert && api.hasData && apt.lawdCode.isNotEmpty()) try {
                val found = ArrayList<Trade>()
                for (ym in recentMonths(2)) {
                    val k = apt.lawdCode + ym
                    val list = tradeCache.getOrPut(k) { api.trades(apt.lawdCode, ym) }
                    found += list.filter { matchesUmd(it.first, apt.umd) }.map { it.second }
                }
                val cur = Store.get(ctx, apt.id) ?: apt
                val fresh = found.filter { it.key !in cur.seenTrades }
                val seen = (cur.seenTrades + found.map { it.key }).toList().takeLast(600).toSet()
                Store.update(ctx, apt.id) { it.copy(seenTrades = seen, tradesSeeded = true) }
                if (cur.tradesSeeded && fresh.isNotEmpty()) {
                    val top = fresh.maxBy { it.amountManwon }
                    val title = if (fresh.size == 1) "${apt.umd}에 실거래 신고 1건"
                    else "${apt.umd}에 새 실거래 ${fresh.size}건"
                    alerts += Alert(
                        apt.id,
                        title,
                        "${top.aptName} ${Fmt.area(top.area)} ${Fmt.manwon(top.amountManwon)}" +
                            if (fresh.size > 1) " 외" else ""
                    )
                }
            } catch (e: Exception) {
                lastFailed = true
            }
        }
        return alerts
    }

    private fun matchesUmd(tradeUmd: String, aptUmd: String): Boolean =
        aptUmd.isNotBlank() && (tradeUmd == aptUmd || tradeUmd.endsWith(" $aptUmd") || tradeUmd.startsWith(aptUmd))

    companion object {
        fun mergePrices(old: List<YearPrice>, new: List<YearPrice>): List<YearPrice> =
            (old.associateBy { it.year } + new.associateBy { it.year }).values.sortedBy { it.year }

        fun median(xs: List<Double>): Double {
            if (xs.isEmpty()) return 0.0
            val s = xs.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }

        fun recentMonths(n: Int): List<String> {
            val c = Calendar.getInstance()
            return (0 until n).map {
                val s = "%04d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
                c.add(Calendar.MONTH, -1)
                s
            }
        }
    }
}

data class Alert(val aptId: String, val title: String, val body: String)
