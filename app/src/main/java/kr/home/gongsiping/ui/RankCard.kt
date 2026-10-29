package kr.home.gongsiping.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kr.home.gongsiping.data.Apt
import kr.home.gongsiping.data.Demo
import kr.home.gongsiping.data.Fmt
import kr.home.gongsiping.data.RankRunner
import kr.home.gongsiping.data.UnitPrice
import kr.home.gongsiping.data.UnitRank

/**
 * 홈 맨 위: 찜한 세대가 단지 전체 동·호 중 몇 등인지 + 최고가/최저가 세대.
 * 찜한 집이 여러 곳이면 위에서 골라 본다.
 */
@Composable
fun RankSection(
    apts: List<Apt>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    states: Map<String, RankRunner.State>,
    onRetry: (Apt) -> Unit,
    onOpen: (Apt) -> Unit
) {
    val p = LocalPalette.current
    val apt = apts.firstOrNull { it.id == selectedId } ?: apts.firstOrNull() ?: return
    // 업데이트 전에 저장된 예시 세대는 순위가 비어 있으니 예시 숫자로 채운다
    val rank = apt.rank ?: if (apt.demo) Demo.apt().rank else null
    val state = states[apt.id]

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(14.dp))
        SectionHead("01", "단지 안 우리 집 순위", right = {
            if (rank != null) T("${rank.year} 공시가", Type.label, color = p.sub)
        })

        if (apts.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                apts.forEach { a ->
                    val on = a.id == apt.id
                    Box(
                        Modifier
                            .border(1.dp, if (on) p.ink else p.line)
                            .background(if (on) p.ink else Color.Transparent)
                            .clickable { onSelect(a.id) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        T("${a.title} ${a.dong}-${a.ho}", Type.small, color = if (on) p.bg else p.ink, maxLines = 1)
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().clickable { onOpen(apt) }) {
            T(
                (if (apts.size > 1) "" else "${apt.title} · ") + "${apt.dong}동 ${apt.ho}호" +
                    (if (apt.demo) " · 예시 숫자" else ""),
                Type.small, color = p.sub, maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            when {
                rank != null -> RankBody(rank, "${apt.dong}동 ${apt.ho}호")
                state is RankRunner.State.Failed -> Failed(state.message) { onRetry(apt) }
                !apt.usable -> T("이 세대는 순위를 계산할 수 없어요", Type.body, color = p.sub)
                else -> Loading(state as? RankRunner.State.Running, first = true)
            }
        }

        // 순위는 있지만 새 연도로 다시 계산 중이거나 실패했을 때
        if (rank != null) {
            when (state) {
                is RankRunner.State.Running -> { Spacer(Modifier.height(12.dp)); Loading(state, first = false) }
                is RankRunner.State.Failed -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("새로 계산하지 못했어요 ", Type.small, color = p.sub)
                        LinkText("다시", { onRetry(apt) }, color = p.sub)
                    }
                }
                null -> if (!apt.demo) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(
                            if (rank.missing > 0) "${rank.missing}세대는 못 불러와 빼고 셌어요 · " else "",
                            Type.small, color = p.sub
                        )
                        LinkText("다시 세기", { onRetry(apt) }, color = p.sub)
                    }
                }
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun RankBody(r: UnitRank, mine: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Bottom) {
        T("${"%,d".format(r.total)}세대 중 ", Type.title, Modifier.padding(bottom = 6.dp))
        Marker("${"%,d".format(r.rank)}등", Type.mega)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.background(p.ink).padding(horizontal = 6.dp, vertical = 2.dp)) {
            T("상위 ${r.topPercent}%", Type.label, color = p.bg)
        }
        Spacer(Modifier.width(10.dp))
        T(
            listOfNotNull(
                if (r.ties > 1) "같은 가격 ${r.ties}세대 공동" else null,
                if (r.areaRank > 0) "같은 면적 ${r.areaTotal}세대 중 ${r.areaRank}등" else null
            ).joinToString(" · "),
            Type.small, color = p.sub, maxLines = 1
        )
    }
    Spacer(Modifier.height(22.dp))
    RankStrip(r.position)
    Spacer(Modifier.height(18.dp))
    UnitLine("최고", r.top, r.topTies)
    Hairline()
    UnitLine("최저", r.bottom, r.bottomTies)
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        T("우리", Type.label, Modifier.width(44.dp), color = p.sub)
        T(mine, Type.bodyBold, Modifier.weight(1f))
        T(Fmt.won(r.myPrice), Type.bodyBold)
    }
}

@Composable
private fun UnitLine(tag: String, u: UnitPrice, ties: Int) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        T(tag, Type.label, Modifier.width(44.dp), color = p.sub)
        Column(Modifier.weight(1f)) {
            T(u.label + if (ties > 1) " 외 ${ties - 1}세대" else "", Type.body, maxLines = 1)
            if (u.area > 0) T(Fmt.area(u.area), Type.small, color = p.sub)
        }
        T(Fmt.won(u.price), Type.bodyBold)
    }
}

/** 최저 ─────●──── 최고 : 단지 가격 범위 안에서 내 위치 */
@Composable
private fun RankStrip(position: Float) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val r = 7.dp.toPx()
            val y = size.height / 2
            val x0 = r
            val x1 = size.width - r
            drawLine(p.line, Offset(x0, y), Offset(x1, y), 3.dp.toPx())
            val x = x0 + (x1 - x0) * position
            drawLine(p.ink, Offset(x, y), Offset(x1, y), 3.dp.toPx()) // 나보다 비싼 쪽
            drawLine(p.ink, Offset(x0, y - 5.dp.toPx()), Offset(x0, y + 5.dp.toPx()), 1.dp.toPx())
            drawLine(p.ink, Offset(x1, y - 5.dp.toPx()), Offset(x1, y + 5.dp.toPx()), 1.dp.toPx())
            drawCircle(p.lime, r, Offset(x, y))
            drawCircle(p.ink, r, Offset(x, y), style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            T("최저", Type.label, color = p.sub)
            T("최고", Type.label, color = p.sub)
        }
    }
}

@Composable
private fun Loading(s: RankRunner.State.Running?, first: Boolean) {
    val p = LocalPalette.current
    val done = s?.done ?: 0
    val total = s?.total ?: 0
    if (first) {
        T("단지 전체 세대를 세는 중", Type.title)
        Spacer(Modifier.height(4.dp))
        T("보통 몇 초면 끝나요. 큰 단지는 조금 더 걸려요.", Type.small, color = p.sub)
        Spacer(Modifier.height(14.dp))
    }
    Box(Modifier.fillMaxWidth().height(6.dp).background(p.line)) {
        if (total > 0) {
            Box(Modifier.fillMaxWidth((done.toFloat() / total).coerceIn(0.02f, 1f)).height(6.dp).background(p.ink))
        }
    }
    Spacer(Modifier.height(6.dp))
    T(
        if (total > 0) "${"%,d".format(done)} / ${"%,d".format(total)}세대" + (if (first) "" else " · 새 공시가로 다시 세는 중")
        else "동·호 목록 확인 중",
        Type.label, color = p.sub
    )
}

@Composable
private fun Failed(msg: String, onRetry: () -> Unit) {
    val p = LocalPalette.current
    T("순위를 못 셌어요", Type.title)
    Spacer(Modifier.height(4.dp))
    T(msg, Type.small, color = p.sub)
    LinkText("다시 시도", onRetry)
}
