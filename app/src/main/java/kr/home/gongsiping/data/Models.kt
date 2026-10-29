package kr.home.gongsiping.data

/** 연도별 공시가격 (원) */
data class YearPrice(val year: Int, val price: Long)

/** 주변 단지 비교용 요약 */
data class Neighbor(
    val name: String,
    val aptCode: String,
    val perSqm: Long,       // ㎡당 공시가격 중앙값 (원)
    val medianPrice: Long,  // 표본 세대 공시가격 중앙값 (원)
    val units: Int,         // 표본 세대 수
    val addr: String,       // 짧은 지번 (예: 일산동 1040)
    val isSelf: Boolean = false,
    val prevPerSqm: Long = 0L // 전년 ㎡당 (상승률 계산용, 없으면 0)
) {
    val changeRate: Double
        get() = if (prevPerSqm > 0) (perSqm - prevPerSqm) * 100.0 / prevPerSqm else Double.NaN
}

/** 내가 찜한 아파트 한 세대 */
data class Apt(
    val id: String,
    val complexName: String,
    val nickname: String,
    val address: String,      // 지번 주소
    val aptCode: String,      // 공시가격알리미 단지 코드
    val noticeDate: String,   // 마지막으로 확인한 공시 일자 (YYYYMMDD)
    val regCode: String,      // 시군구 코드 5자리
    val eubCode: String,      // 읍면동 코드 5자리
    val bun: Int,             // 지번 본번 (주변 단지 정렬용)
    val umd: String,          // 법정동 이름 (예: 일산동)
    val dong: String,
    val ho: String,
    val dongCode: String,
    val hoCode: String,
    val area: Double,         // 전용면적 ㎡
    val prices: List<YearPrice> = emptyList(),
    val nearby: List<Neighbor> = emptyList(),
    val nearbyUpdated: Long = 0L,
    val demo: Boolean = false
) {
    val title: String get() = nickname.ifBlank { complexName }
    val latest: YearPrice? get() = prices.maxByOrNull { it.year }
    val previous: YearPrice? get() {
        val l = latest ?: return null
        return prices.filter { it.year < l.year }.maxByOrNull { it.year }
    }
    val changeRate: Double get() {
        val l = latest ?: return Double.NaN
        val p = previous ?: return Double.NaN
        if (p.price <= 0) return Double.NaN
        return (l.price - p.price) * 100.0 / p.price
    }
    val usable: Boolean get() = aptCode.isNotBlank() && noticeDate.isNotBlank()
}

/** 검색 결과 단지 */
data class Complex(
    val name: String,
    val address: String,   // 지번
    val road: String,      // 도로명
    val aptCode: String,
    val noticeDate: String,
    val regCode: String,
    val eubCode: String,
    val bun: Int
)

/** 동·호 선택지 (코드 + 이름) */
data class Choice(val code: String, val name: String)

/** 한 세대의 연도별 공시 원자료 한 행 */
data class PriceRow(
    val year: Int,
    val price: Long,
    val area: Double,
    val dong: String,
    val ho: String,
    val complexName: String,
    val bjdName: String
)
