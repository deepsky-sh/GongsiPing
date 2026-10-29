package kr.home.gongsiping.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.home.gongsiping.ads.BannerAd
import kr.home.gongsiping.data.Apt
import kr.home.gongsiping.data.Fmt
import kr.home.gongsiping.data.Repo
import kr.home.gongsiping.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailScreen(repo: Repo, apt: Apt, onBack: () -> Unit, onDeleted: () -> Unit) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }

    var nearbyBusy by remember { mutableStateOf(false) }
    var nearbyErr by remember { mutableStateOf<String?>(null) }
    var sortByRate by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun loadNearby() {
        scope.launch {
            nearbyBusy = true; nearbyErr = null
            try {
                repo.refreshNearby(apt)
            } catch (e: Exception) {
                nearbyErr = e.message ?: "주변 단지를 못 불러왔어요"
            }
            nearbyBusy = false
        }
    }

    LaunchedEffect(apt.id) {
        // 처음 열 때: 주변 비교가 없거나 7일 지났으면 새로
        if (!apt.demo && (apt.nearby.isEmpty() || System.currentTimeMillis() - apt.nearbyUpdated > 7 * 86_400_000L)) {
            loadNearby()
        }
        // 새 공시가 나왔는지 조용히 확인 (키 필요 없음)
        try {
            repo.refreshHistory(apt)
        } catch (e: Exception) {
        }
    }

    val latest = apt.latest
    val prev = apt.previous

    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LinkText("← 목록", onBack)
                Spacer(Modifier.weight(1f))
                LinkText("별명", { showRename = true }, color = p.sub)
                Spacer(Modifier.width(16.dp))
                LinkText("삭제", { showDelete = true }, color = p.sub)
            }
            Spacer(Modifier.height(20.dp))
            T(apt.title, Type.big)
            Spacer(Modifier.height(4.dp))
            T(
                (if (apt.nickname.isNotBlank()) apt.complexName + " · " else "") +
                    "${apt.dong}동 ${apt.ho}호 · ${Fmt.area(apt.area)}",
                Type.small, color = p.sub
            )
            Spacer(Modifier.height(36.dp))

            // 01 올해 공시가격
            SectionHead("01", "${latest?.year ?: ""} 공시가격")
            if (latest != null) {
                val (a, b) = Fmt.wonParts(latest.price)
                Row(verticalAlignment = Alignment.Bottom) {
                    T(a, Type.mega)
                    if (b.isNotEmpty()) T(" $b", Type.big, Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RateTag(apt.changeRate)
                    if (prev != null) T("작년보다 ${Fmt.diffWon(latest.price - prev.price)}", Type.body, color = p.sub)
                }
                Spacer(Modifier.height(10.dp))
                T(moodLine(apt.changeRate), Type.body)
            }
            Spacer(Modifier.height(40.dp))

            // 02 10년 흐름
            SectionHead("02", "${apt.prices.size}년 흐름")
            LineChart(apt.prices)
            Spacer(Modifier.height(18.dp))
        }
        items(apt.prices.reversed()) { yp ->
            val before = apt.prices.filter { it.year < yp.year }.maxByOrNull { it.year }
            val r = if (before != null && before.price > 0) (yp.price - before.price) * 100.0 / before.price else Double.NaN
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                T("${yp.year}", Type.label, Modifier.width(56.dp), color = p.sub)
                T(Fmt.won(yp.price), Type.body, Modifier.weight(1f))
                if (!r.isNaN()) RateTag(r)
            }
            Hairline()
        }

        // 03 동네 순위
        item {
            Spacer(Modifier.height(40.dp))
            SectionHead("03", "동네에서 몇 위?") {
                LinkText(if (nearbyBusy) "불러오는 중" else "새로고침", { if (!nearbyBusy) loadNearby() }, color = p.sub)
            }
            val list = apt.nearby
            val self = list.indexOfFirst { it.isSelf }
            if (list.isNotEmpty() && self >= 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    T("${list.size}곳 중 ", Type.title)
                    Marker("${self + 1}위", Type.big)
                }
                T("같은 ${apt.umd.ifBlank { "법정동" }}, 지번이 가까운 단지 · ㎡당 공시가격 기준", Type.small, color = p.sub)
                Spacer(Modifier.height(14.dp))
                Segmented(listOf("㎡당 가격순", "상승률순"), if (sortByRate) 1 else 0, { sortByRate = it == 1 })
                Spacer(Modifier.height(8.dp))
            } else if (!nearbyBusy) {
                T(nearbyErr ?: "주변 단지 정보가 아직 없어요.", Type.body, color = p.sub)
            }
            if (nearbyBusy && list.isEmpty()) {
                Row(Modifier.padding(vertical = 8.dp)) { Marker("근처 단지들 공시가격 모으는 중…", Type.bodyBold) }
            }
            if (nearbyErr != null && list.isNotEmpty()) T(nearbyErr!!, Type.small, color = p.sub)
        }
        val sorted = if (sortByRate) apt.nearby.sortedByDescending { if (it.changeRate.isNaN()) -999.0 else it.changeRate }
        else apt.nearby.sortedByDescending { it.perSqm }
        val maxPer = (apt.nearby.maxOfOrNull { it.perSqm } ?: 1L).coerceAtLeast(1L)
        items(sorted.size) { i ->
            val n = sorted[i]
            BarRow(
                rank = i + 1,
                name = n.name,
                value = Fmt.won(n.perSqm),
                ratio = n.perSqm.toFloat() / maxPer,
                highlight = n.isSelf,
                sub = n.addr + " · 세대 공시가 약 ${Fmt.won(n.medianPrice)}",
                rate = n.changeRate
            )
            Hairline()
        }

        item {
            Spacer(Modifier.height(48.dp))
            Box(Modifier.fillMaxWidth()) {
                Column {
                    T("출처", Type.label, color = p.sub)
                    Spacer(Modifier.height(4.dp))
                    T(
                        "국토교통부 부동산공시가격 알리미 (realtyprice.kr)\n" +
                            "공시가격은 세금 기준 가격이라 실제 시세와 달라요.",
                        Type.small, color = p.sub
                    )
                    if (apt.nearbyUpdated > 0) {
                        T(
                            "주변 비교 갱신 " + SimpleDateFormat("yyyy.M.d HH:mm", Locale.KOREA).format(Date(apt.nearbyUpdated)),
                            Type.small, color = p.sub
                        )
                    }
                    if (apt.demo) T("※ 예시 데이터예요. 실제 단지가 아닙니다.", Type.small, color = p.sub)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    BannerAd()
    }

    if (showRename) {
        var name by remember { mutableStateOf(apt.nickname) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = p.bg,
            shape = androidx.compose.ui.graphics.RectangleShape,
            title = { T("별명 붙이기", Type.title) },
            text = { UnderlineField(name, { name = it.take(20) }, "우리집, 꿈의 집…", style = Type.body) },
            confirmButton = {
                TextButton(onClick = {
                    Store.update(ctx, apt.id) { it.copy(nickname = name.trim()) }
                    showRename = false
                }) { T("저장", Type.bodyBold) }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { T("취소", Type.body, color = p.sub) } }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = p.bg,
            shape = androidx.compose.ui.graphics.RectangleShape,
            title = { T("찜 목록에서 뺄까요?", Type.title) },
            text = { T("${apt.title} 알림도 같이 멈춰요.", Type.body, color = p.sub) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    Store.remove(ctx, apt.id)
                    onDeleted()
                }) { T("빼기", Type.bodyBold) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { T("그대로", Type.body, color = p.sub) } }
        )
    }
}

private fun moodLine(r: Double): String = when {
    r.isNaN() -> "비교할 작년 자료가 없어요."
    r >= 10 -> "꽤 많이 올랐어요. 보유세도 같이 오를 수 있어요."
    r >= 3 -> "올해는 좀 올랐네요."
    r > 0 -> "거의 그대로, 살짝 올랐어요."
    r == 0.0 -> "작년이랑 똑같아요."
    r > -3 -> "살짝 내렸어요."
    else -> "올해는 꽤 내렸어요. 세금 부담은 줄겠네요."
}
