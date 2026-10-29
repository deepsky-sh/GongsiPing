package kr.home.gongsiping.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 찜한 아파트 목록을 앱 내부 파일(JSON)에 저장한다. 서버 없음. */
object Store {
    private const val FILE = "apts.json"
    private val lock = Any()

    private val _apts = MutableStateFlow<List<Apt>>(emptyList())
    val apts: StateFlow<List<Apt>> = _apts
    private var loaded = false

    fun load(ctx: Context): List<Apt> = synchronized(lock) {
        if (!loaded) {
            _apts.value = readFile(ctx)
            loaded = true
        }
        _apts.value
    }

    /** 다른 프로세스(백그라운드 작업)가 바꾼 내용을 다시 읽는다 */
    fun reload(ctx: Context) = synchronized(lock) {
        _apts.value = readFile(ctx)
        loaded = true
    }

    fun get(ctx: Context, id: String): Apt? = load(ctx).firstOrNull { it.id == id }

    fun upsert(ctx: Context, apt: Apt) = synchronized(lock) {
        val list = load(ctx).toMutableList()
        val i = list.indexOfFirst { it.id == apt.id }
        if (i >= 0) list[i] = apt else list.add(apt)
        save(ctx, list)
    }

    fun update(ctx: Context, id: String, change: (Apt) -> Apt) = synchronized(lock) {
        val list = load(ctx).map { if (it.id == id) change(it) else it }
        save(ctx, list)
    }

    fun remove(ctx: Context, id: String) = synchronized(lock) {
        save(ctx, load(ctx).filterNot { it.id == id })
    }

    private fun save(ctx: Context, list: List<Apt>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        val f = File(ctx.filesDir, FILE)
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
        _apts.value = list
    }

    private fun readFile(ctx: Context): List<Apt> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun toJson(a: Apt): JSONObject = JSONObject().apply {
        put("id", a.id); put("complexName", a.complexName); put("nickname", a.nickname)
        put("address", a.address); put("aptCode", a.aptCode); put("noticeDate", a.noticeDate)
        put("regCode", a.regCode); put("eubCode", a.eubCode); put("bun", a.bun)
        put("umd", a.umd); put("dong", a.dong); put("ho", a.ho)
        put("dongCode", a.dongCode); put("hoCode", a.hoCode); put("area", a.area)
        put("prices", JSONArray().apply {
            a.prices.forEach { put(JSONObject().put("y", it.year).put("p", it.price)) }
        })
        put("nearby", JSONArray().apply {
            a.nearby.forEach {
                put(JSONObject().put("name", it.name).put("aptCode", it.aptCode).put("perSqm", it.perSqm)
                    .put("median", it.medianPrice).put("units", it.units).put("addr", it.addr)
                    .put("self", it.isSelf).put("prev", it.prevPerSqm))
            }
        })
        put("nearbyUpdated", a.nearbyUpdated)
        put("demo", a.demo)
    }

    private fun fromJson(o: JSONObject): Apt {
        val prices = o.optJSONArray("prices")?.let { arr ->
            (0 until arr.length()).map { val p = arr.getJSONObject(it); YearPrice(p.getInt("y"), p.getLong("p")) }
        } ?: emptyList()
        val nearby = o.optJSONArray("nearby")?.let { arr ->
            (0 until arr.length()).map {
                val n = arr.getJSONObject(it)
                Neighbor(
                    n.optString("name"), n.optString("aptCode"), n.optLong("perSqm"), n.optLong("median"),
                    n.optInt("units"), n.optString("addr"), n.optBoolean("self"), n.optLong("prev")
                )
            }
        } ?: emptyList()
        return Apt(
            id = o.getString("id"),
            complexName = o.optString("complexName"),
            nickname = o.optString("nickname"),
            address = o.optString("address"),
            aptCode = o.optString("aptCode"),
            noticeDate = o.optString("noticeDate"),
            regCode = o.optString("regCode"),
            eubCode = o.optString("eubCode"),
            bun = o.optInt("bun"),
            umd = o.optString("umd"),
            dong = o.optString("dong"),
            ho = o.optString("ho"),
            dongCode = o.optString("dongCode"),
            hoCode = o.optString("hoCode"),
            area = o.optDouble("area", 0.0),
            prices = prices,
            nearby = nearby,
            nearbyUpdated = o.optLong("nearbyUpdated"),
            demo = o.optBoolean("demo")
        )
    }
}

/** 사용자 설정 (알림, 비교 단지 수, 테마) */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var priceAlert: Boolean
        get() = sp.getBoolean("priceAlert", true)
        set(v) = sp.edit().putBoolean("priceAlert", v).apply()
    /** 주변 비교에 쓸 단지 수 */
    var compareCount: Int
        get() = sp.getInt("compareCount", 8)
        set(v) = sp.edit().putInt("compareCount", v).apply()
    /** 0 = 시스템, 1 = 라이트, 2 = 다크 */
    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()
    var lastCheck: Long
        get() = sp.getLong("lastCheck", 0L)
        set(v) = sp.edit().putLong("lastCheck", v).apply()
    var lastCheckOk: Boolean
        get() = sp.getBoolean("lastCheckOk", true)
        set(v) = sp.edit().putBoolean("lastCheckOk", v).apply()
}
