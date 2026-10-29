package kr.home.gongsiping.data

import java.util.Calendar

/** 인증키 없이 화면을 둘러보기 위한 예시 데이터 (가상의 단지) */
object Demo {
    fun apt(): Apt {
        val y = Calendar.getInstance().get(Calendar.YEAR)
        val base = listOf(312, 318, 331, 356, 402, 497, 401, 388, 405, 421) // 백만원
        val prices = base.mapIndexed { i, p -> YearPrice(y - 9 + i, p * 1_000_000L) }
        return Apt(
            id = "demo",
            complexName = "예시마을 숲속아파트",
            nickname = "우리집 (예시)",
            address = "예시시 예시구 하늘동 100",
            aptCode = "", noticeDate = "", regCode = "", eubCode = "", bun = 100,
            umd = "하늘동",
            dong = "103", ho = "1204", dongCode = "", hoCode = "",
            area = 84.97,
            prices = prices,
            nearby = listOf(
                Neighbor("하늘센트럴파크", "1", 7_120_000, 612_000_000, 840, "하늘동 120", prevPerSqm = 6_850_000),
                Neighbor("예시마을 숲속아파트", "0", 4_950_000, 421_000_000, 512, "하늘동 100", isSelf = true, prevPerSqm = 4_760_000),
                Neighbor("하늘동 한빛2단지", "2", 5_310_000, 455_000_000, 390, "하늘동 131", prevPerSqm = 5_180_000),
                Neighbor("강변뷰 리버하임", "3", 6_040_000, 503_000_000, 288, "하늘동 88", prevPerSqm = 5_720_000),
                Neighbor("새솔마을 5단지", "4", 4_180_000, 352_000_000, 620, "하늘동 140", prevPerSqm = 4_090_000),
                Neighbor("하늘역 스테이", "5", 4_620_000, 298_000_000, 210, "하늘동 97", prevPerSqm = 4_640_000),
                Neighbor("푸른들 1차", "6", 3_870_000, 331_000_000, 450, "하늘동 152", prevPerSqm = 3_800_000)
            ).sortedByDescending { it.perSqm },
            nearbyUpdated = System.currentTimeMillis(),
            tradesSeeded = true,
            demo = true
        )
    }

    fun trades(): List<Trade> {
        val c = Calendar.getInstance()
        val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1
        return listOf(
            Trade("하늘센트럴파크", 92_500, y, m, 21, 84.9, "17"),
            Trade("예시마을 숲속아파트", 61_000, y, m, 18, 84.97, "9"),
            Trade("하늘동 한빛2단지", 58_300, y, m, 9, 74.5, "3"),
            Trade("강변뷰 리버하임", 77_000, y, m, 2, 84.0, "21"),
            Trade("새솔마을 5단지", 44_800, if (m == 1) y - 1 else y, if (m == 1) 12 else m - 1, 27, 59.9, "12")
        )
    }
}
