package kr.home.gongsiping.data

import java.text.DecimalFormat
import kotlin.math.abs

/** 숫자 표시 규칙 */
object Fmt {
    private val comma = DecimalFormat("#,###")

    /** 523,000,000원 → "5억 2,300만" / 87,000,000 → "8,700만" */
    fun won(v: Long): String = manwon(v / 10_000)

    /** 만원 단위 → "9억 2,500" 식 */
    fun manwon(m: Long): String {
        val eok = m / 10_000
        val rest = m % 10_000
        return when {
            eok > 0 && rest > 0 -> "${eok}억 ${comma.format(rest)}만"
            eok > 0 -> "${eok}억"
            else -> "${comma.format(rest)}만"
        }
    }

    /** 큰 숫자용: 억 부분과 나머지를 나눠서 돌려준다 ("5억", "2,300만") */
    fun wonParts(v: Long): Pair<String, String> {
        val m = v / 10_000
        val eok = m / 10_000
        val rest = m % 10_000
        return if (eok > 0) "${eok}억" to (if (rest > 0) "${comma.format(rest)}만" else "")
        else "${comma.format(rest)}만" to ""
    }

    fun rate(r: Double): String =
        if (r.isNaN()) "—" else (if (r > 0) "+" else if (r < 0) "−" else "±") + "%.1f%%".format(abs(r))

    fun perSqm(v: Long): String = "㎡당 " + won(v)

    fun area(a: Double): String {
        val pyeong = a / 3.3058
        return "%.0f㎡(%.0f평)".format(a, pyeong)
    }

    fun distance(m: Int): String = if (m < 1000) "${m}m" else "%.1fkm".format(m / 1000.0)

    fun diffWon(d: Long): String = (if (d >= 0) "+" else "−") + won(abs(d))
}
