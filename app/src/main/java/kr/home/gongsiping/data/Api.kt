package kr.home.gongsiping.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(message: String) : Exception(message)

/**
 * 데이터 출처
 *  - 공시가격: 국토교통부 부동산공시가격 알리미(realtyprice.kr)의 공개 조회 경로. 인증키 없음.
 *      getApt.do        단지 검색 (이름 / 법정동)
 *      detail.do        단지의 동 목록 (HTML 의 sel_dong)
 *      getHo.do         동의 호 목록
 *      getPriceYear.do  한 세대의 연도별 공시가격
 *  - 실거래가(선택): 공공데이터포털 국토부 아파트 매매 실거래가. 이 기능만 인증키 필요.
 */
class Api(private val prefs: Prefs) {

    private val dataKey get() = prefs.dataKey
    val hasData get() = dataKey.isNotBlank()

    // ---------------------------------------------------------------- 공시가격 (키 없음)

    /** 단지 이름으로 검색 */
    suspend fun searchComplexes(name: String): List<Complex> = withContext(Dispatchers.IO) {
        val json = post(
            "getApt.do", SEARCH_PAGE,
            searchParams(gbn = "1", detail = "3", name = name)
        )
        toComplexes(listOf(json))
    }

    /** 같은 법정동의 모든 공동주택 단지 */
    suspend fun complexesInDong(regCode: String, eubCode: String): List<Complex> = withContext(Dispatchers.IO) {
        val json = post(
            "getApt.do", SEARCH_PAGE,
            searchParams(
                gbn = "2", detail = "2", name = "",
                sido = regCode.take(2), sigungu = regCode, dongri = regCode + eubCode,
                reg = regCode, eub = eubCode
            )
        )
        toComplexes(listOf(json))
    }

    /** 단지의 동 목록 */
    suspend fun dongs(aptCode: String, noticeDate: String): List<Choice> = withContext(Dispatchers.IO) {
        val html = postRaw(
            "detail.do", SEARCH_PAGE,
            mapOf("notice_date" to noticeDate, "apt_code" to aptCode, "tiles" to "false")
        )
        val select = Regex("""<select\b[^>]*id=["']sel_dong["'][^>]*>([\s\S]*?)</select>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?: throw ApiException("동 목록을 읽지 못했어요 (사이트 형식이 바뀌었을 수 있어요)")
        Regex("""<option\b[^>]*value=["']([^"']*)["'][^>]*>([\s\S]*?)</option>""", RegexOption.IGNORE_CASE)
            .findAll(select)
            .map { Choice(it.groupValues[1].trim(), it.groupValues[2].replace(Regex("<[^>]*>"), "").trim()) }
            .filter { it.code.isNotEmpty() && it.name.isNotEmpty() && !it.name.contains("선택") }
            .toList()
    }

    /** 동의 호 목록 */
    suspend fun hos(aptCode: String, noticeDate: String, dongCode: String): List<Choice> =
        withContext(Dispatchers.IO) {
            val json = post(
                "getHo.do", DETAIL_PAGE,
                mapOf("notice_date" to noticeDate, "apt_code" to aptCode, "dong_code" to dongCode)
            )
            listOf(json).flatMap { list(it) }.map {
                Choice(it.optString("CODE"), it.optString("NAME").trim())
            }.filter { it.code.isNotEmpty() && it.name.isNotEmpty() }
        }

    /** 한 세대의 연도별 공시가격 (최신 연도 먼저가 아닐 수 있음 → 호출 쪽에서 정렬) */
    suspend fun priceHistory(aptCode: String, noticeDate: String, dongCode: String, hoCode: String): List<PriceRow> =
        withContext(Dispatchers.IO) {
            val json = post(
                "getPriceYear.do", DETAIL_PAGE,
                mapOf(
                    "notice_date" to noticeDate, "apt_code" to aptCode,
                    "dong_code" to dongCode, "ho_code" to hoCode
                )
            )
            list(json).mapNotNull { r ->
                val year = Regex("""\d{4}""").find(
                    r.optString("NOTICE_DATE_NAME").ifBlank { r.optString("NOTICE_DATE") }
                )?.value?.toIntOrNull() ?: return@mapNotNull null
                val price = digits(r.optString("NOTICE_AMT"))
                if (price <= 0) return@mapNotNull null
                PriceRow(
                    year = year,
                    price = price,
                    area = r.optString("PRIV_AREA").toDoubleOrNull() ?: 0.0,
                    dong = r.optString("DONG_NAME").trim(),
                    ho = r.optString("HO_NAME").trim(),
                    complexName = r.optString("APT_NAME").trim(),
                    bjdName = r.optString("FULL_BJD_NAME").replace(Regex("\\s+"), " ").trim()
                )
            }.distinctBy { it.year }.sortedBy { it.year }
        }

    private fun searchParams(
        gbn: String, detail: String, name: String,
        sido: String = "", sigungu: String = "", dongri: String = "", reg: String = "", eub: String = ""
    ) = linkedMapOf(
        "search_gbn" to gbn, "search_detail_gbn" to detail,
        "notice_date" to "", "notice_date_nm" to "",
        "sido" to sido, "sido_nm" to "", "sigungu" to sigungu, "sigungu_nm" to "",
        "road_reg" to "", "road_initial" to "", "road_initial_nm" to "",
        "road_code" to "", "road_code_nm" to "",
        "dongri" to dongri, "dongri_nm" to "", "reg" to reg, "eub" to eub,
        "search_name" to name, "search_bun" to ""
    )

    private fun toComplexes(roots: List<JSONObject>): List<Complex> =
        roots.flatMap { list(it) }.mapNotNull { r ->
            val code = r.optString("APT_CODE").ifBlank { return@mapNotNull null }
            Complex(
                name = r.optString("MOD_APT_NAME").ifBlank { r.optString("APT_NAME") }.trim(),
                address = r.optString("FULL_BJD_NAME").replace(Regex("\\s+"), " ").trim(),
                road = r.optString("FULL_ROAD_NAME").trim(),
                aptCode = code,
                noticeDate = r.optString("NOTICE_DATE").trim(),
                regCode = r.optString("SREG").trim(),
                eubCode = r.optString("SEUB").trim(),
                bun = r.optString("SBUN1").toIntOrNull() ?: 0
            )
        }.distinctBy { it.aptCode }

    /** model.list 또는 modelMap.list */
    private fun list(root: JSONObject): List<JSONObject> {
        val arr = root.optJSONObject("model")?.optJSONArray("list")
            ?: root.optJSONObject("modelMap")?.optJSONArray("list")
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun post(path: String, referer: String, params: Map<String, String>): JSONObject {
        val text = postRaw(path, referer, params)
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ApiException("공시가격 사이트 응답을 읽지 못했어요. 잠시 뒤 다시 해주세요.")
        }
    }

    private fun postRaw(path: String, referer: String, params: Map<String, String>): String {
        val body = params.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val conn = URL("$BASE/m/town/$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conn.setRequestProperty("Referer", referer)
            conn.setRequestProperty("Accept", "application/json, text/html, */*")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException("공시가격 사이트가 응답하지 않아요 (HTTP $code)")
            return text
        } catch (e: IOException) {
            throw ApiException("인터넷 연결을 확인해주세요")
        } finally {
            conn.disconnect()
        }
    }

    // ---------------------------------------------------------------- 실거래가 (선택, 키 필요)

    /** 시군구(5자리) + 계약년월(YYYYMM)의 아파트 매매 실거래. Pair(법정동, 거래) */
    suspend fun trades(lawdCd: String, ym: String): List<Pair<String, Trade>> = withContext(Dispatchers.IO) {
        if (!hasData) throw ApiException("실거래는 설정에서 공공데이터포털 키를 넣으면 볼 수 있어요")
        val out = ArrayList<Pair<String, Trade>>()
        var page = 1
        while (page <= 5) {
            val key = if (dataKey.contains("%")) dataKey else URLEncoder.encode(dataKey, "UTF-8")
            val url = "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade" +
                "?serviceKey=$key&LAWD_CD=$lawdCd&DEAL_YMD=$ym&pageNo=$page&numOfRows=1000"
            val xml = httpGet(url)
            val (rows, total) = parseTradeXml(xml)
            out.addAll(rows)
            if (rows.size < 1000 || out.size >= total) break
            page++
        }
        out
    }

    private fun parseTradeXml(xml: String): Pair<List<Pair<String, Trade>>, Int> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val rows = ArrayList<Pair<String, Trade>>()
        var cur: HashMap<String, String>? = null
        var tag = ""
        var total = 0
        var resultCode = ""
        var resultMsg = ""
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "item") cur = HashMap()
                }
                XmlPullParser.TEXT -> {
                    val t = parser.text?.trim().orEmpty()
                    if (t.isNotEmpty()) {
                        val c = cur
                        if (c != null) c[tag] = t
                        else when (tag) {
                            "totalCount" -> total = t.toIntOrNull() ?: 0
                            "resultCode", "returnReasonCode" -> resultCode = t
                            "resultMsg", "returnAuthMsg", "errMsg" -> resultMsg = t
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        cur?.let { m ->
                            if (m["cdealType"].isNullOrBlank()) { // 해제된 거래는 뺀다
                                val t = Trade(
                                    aptName = m["aptNm"].orEmpty(),
                                    amountManwon = digits(m["dealAmount"].orEmpty()),
                                    year = m["dealYear"]?.toIntOrNull() ?: 0,
                                    month = m["dealMonth"]?.toIntOrNull() ?: 0,
                                    day = m["dealDay"]?.toIntOrNull() ?: 0,
                                    area = m["excluUseAr"]?.toDoubleOrNull() ?: 0.0,
                                    floor = m["floor"].orEmpty()
                                )
                                if (t.amountManwon > 0) rows.add(m["umdNm"].orEmpty().trim() to t)
                            }
                        }
                        cur = null
                    }
                    tag = ""
                }
            }
            ev = parser.next()
        }
        if (rows.isEmpty() && resultCode.isNotEmpty() && resultCode != "00" && resultCode != "000") {
            throw ApiException("실거래가: ${resultMsg.ifBlank { resultCode }}")
        }
        return rows to total
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/xml, text/xml, */*")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code ${body.take(120)}")
            return body
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://www.realtyprice.kr/notice"
        private const val SEARCH_PAGE = "$BASE/m/town/search.do"
        private const val DETAIL_PAGE = "$BASE/m/town/detail.do"

        fun digits(s: String): Long = s.filter { it.isDigit() }.toLongOrNull() ?: 0L

        /** "경기도 고양일산서구 일산동 1040" → "일산동" */
        fun umdOf(address: String): String {
            val re = Regex("^[가-힣0-9]*[가-힣](동|읍|면|가|리)$")
            return address.split(" ").lastOrNull { re.matches(it) } ?: ""
        }

        /** "경기도 고양일산서구 일산동 1040" → "일산동 1040" */
        fun shortAddr(address: String): String {
            val parts = address.split(" ").filter { it.isNotBlank() && it != "-" }
            val i = parts.indexOfLast { Regex("^[가-힣0-9]*[가-힣](동|읍|면|가|리)$").matches(it) }
            return if (i >= 0) parts.drop(i).take(2).joinToString(" ") else address
        }
    }
}
