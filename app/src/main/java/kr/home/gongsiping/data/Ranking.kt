package kr.home.gongsiping.data

import kotlin.math.abs

/** 단지 전체 세대 표에서 내 세대 순위를 계산한다 */
object Ranking {

    fun compute(apt: Apt, table: UnitTable): UnitRank? {
        // 기준 연도: 단지 표에 있는 가장 최근 공시 연도
        val year = table.years.maxOrNull() ?: return null
        val units = table.units.mapNotNull { u ->
            val p = u.prices[year] ?: return@mapNotNull null
            if (p <= 0) null else UnitPrice(u.dong, u.ho, p, u.area)
        }.toMutableList()
        if (units.isEmpty()) return null

        // 내 세대: 표에 있으면 그 값, (요청 실패로) 없으면 내 기록에서 같은 연도 값
        var me = units.firstOrNull { same(it.dong, apt.dong) && same(it.ho, apt.ho) }
        if (me == null) {
            val p = apt.prices.firstOrNull { it.year == year }?.price ?: return null
            me = UnitPrice(apt.dong, apt.ho, p, apt.area)
            units += me
        }

        val my = me.price
        val rank = 1 + units.count { it.price > my }
        val ties = units.count { it.price == my }
        val maxP = units.maxOf { it.price }
        val minP = units.minOf { it.price }
        val order = compareBy<UnitPrice, String>(Export.NATURAL) { it.dong }.thenBy(Export.NATURAL) { it.ho }
        val top = units.filter { it.price == maxP }.sortedWith(order)
        val bottom = units.filter { it.price == minP }.sortedWith(order)

        // 같은 전용면적(±0.5㎡) 안에서
        val area = if (me.area > 0) me.area else apt.area
        val sameArea = if (area > 0) units.filter { it.area > 0 && abs(it.area - area) <= 0.5 } else emptyList()
        val areaRank = if (sameArea.size >= 2) 1 + sameArea.count { it.price > my } else 0

        return UnitRank(
            year = year, rank = rank, ties = ties, total = units.size, myPrice = my,
            top = top.first(), topTies = top.size, bottom = bottom.first(), bottomTies = bottom.size,
            areaRank = areaRank, areaTotal = if (areaRank > 0) sameArea.size else 0,
            missing = table.failed.size, updated = System.currentTimeMillis()
        )
    }

    private fun same(a: String, b: String) = a.trim().trimStart('0') == b.trim().trimStart('0')
}
