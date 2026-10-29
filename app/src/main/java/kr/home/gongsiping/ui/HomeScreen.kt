package kr.home.gongsiping.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.home.gongsiping.ads.BannerAd
import kr.home.gongsiping.data.Apt
import kr.home.gongsiping.data.Fmt
import kr.home.gongsiping.data.RankRunner
import kr.home.gongsiping.work.CheckWorker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    apts: List<Apt>,
    lastCheck: Long,
    lastCheckOk: Boolean,
    onOpen: (Apt) -> Unit,
    onAdd: () -> Unit,
    onDemo: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val rankStates by RankRunner.state.collectAsState()
    var rankId by rememberSaveable { mutableStateOf<String?>(null) }
    // 순위가 없거나 새 공시가가 나온 세대는 알아서 센다 (한 번에 한 단지씩)
    LaunchedEffect(apts) { apts.filter { it.rankStale }.forEach { RankRunner.ensure(ctx, it) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // 상단 줄
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            T("공시핑", Type.bodyBold, Modifier.weight(1f))
            if (apts.any { !it.demo }) {
                LinkText("엑셀", onExport)
                Spacer(Modifier.width(18.dp))
            }
            LinkText("설정", onSettings)
        }

        if (apts.isEmpty()) {
            Empty(onAdd, onDemo)
            return@Column
        }

        LazyColumn(Modifier.weight(1f)) {
            item {
                RankSection(
                    apts = apts,
                    selectedId = rankId,
                    onSelect = { rankId = it },
                    states = rankStates,
                    onRetry = { RankRunner.ensure(ctx, it, force = true) },
                    onOpen = onOpen
                )
            }
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    T("찜한 집 ", Type.big)
                    Marker("${apts.size}곳", Type.big)
                }
                Spacer(Modifier.height(10.dp))
                T(seasonLine(), Type.small, color = p.sub)
                T(checkLine(lastCheck, lastCheckOk), Type.small, color = p.sub)
                Spacer(Modifier.height(18.dp))
                // 추가 버튼은 위쪽에 둔다 (하단 광고 바로 옆에 버튼을 두지 않기 — AdMob 실수 클릭 정책)
                InkButton("+ 아파트 추가", onAdd)
                Spacer(Modifier.height(22.dp))
                Hairline(strong = true)
            }
            itemsIndexed(apts, key = { _, a -> a.id }) { i, a ->
                AptRow(i + 1, a) { onOpen(a) }
                Hairline()
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        BannerAd()
    }
}

@Composable
private fun AptRow(no: Int, a: Apt, onClick: () -> Unit) {
    val p = LocalPalette.current
    val latest = a.latest
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 18.dp),
        verticalAlignment = Alignment.Top
    ) {
        T("%02d".format(no), Type.label, Modifier.width(30.dp).padding(top = 4.dp), color = p.sub)
        Column(Modifier.weight(1f)) {
            T(a.title, Type.title, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            T("${a.complexName.takeIf { a.nickname.isNotBlank() }?.let { "$it · " } ?: ""}${a.dong}동 ${a.ho}호",
                Type.small, color = p.sub, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            if (latest != null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(Fmt.won(latest.price), Type.num)
                    Box(Modifier.padding(bottom = 4.dp)) { RateTag(a.changeRate) }
                }
                T("${latest.year} 공시가", Type.label, color = p.sub)
            } else {
                T("공시가격을 아직 못 불러왔어요", Type.small, color = p.sub)
            }
        }
    }
}

@Composable
private fun Empty(onAdd: () -> Unit, onDemo: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(64.dp))
        T("아직", Type.mega)
        Row { Marker("찜한 집", Type.mega) }
        T("이 없어요.", Type.mega)
        Spacer(Modifier.height(20.dp))
        T("아파트 하나 골라두면\n새 공시가격이 뜨는 날 바로 알려줄게요.\n동네에 실거래 신고 들어와도요.", Type.body, color = p.sub)
        Spacer(Modifier.height(32.dp))
        InkButton("아파트 찾기", onAdd)
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            LinkText("키 없이 예시로 둘러보기", onDemo, color = p.sub)
        }
        // 버튼과 광고 사이는 빈 공간으로 띄운다
        Spacer(Modifier.weight(1f))
        BannerAd()
    }
}

private fun seasonLine(): String =
    if (CheckWorker.inSeason()) "지금 공시가격 발표 시즌이에요. 하루 두 번 확인하고 있어요."
    else "공시가격은 보통 4월 말에 나와요. 그 전까진 동네 실거래를 챙겨볼게요."

private fun checkLine(t: Long, ok: Boolean): String {
    if (t == 0L) return "아직 확인 전"
    val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val fmt = if (c.get(Calendar.DAY_OF_YEAR) == today) "오늘 HH:mm" else "M/d HH:mm"
    return "마지막 확인 " + SimpleDateFormat(fmt, Locale.KOREA).format(Date(t)) + if (ok) "" else " (연결 실패, 저장된 값 표시 중)"
}
