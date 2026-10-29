package kr.home.gongsiping.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.home.gongsiping.data.Apt
import kr.home.gongsiping.data.Export
import kr.home.gongsiping.data.RankRunner
import kr.home.gongsiping.data.Repo
import kr.home.gongsiping.data.UnitTable
import kr.home.gongsiping.data.Xlsx
import java.text.DecimalFormat

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(repo: Repo, apts: List<Apt>, initialId: String?, onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val comma = remember { DecimalFormat("#,###") }
    val usable = apts.filter { !it.demo && it.usable }

    var aptId by remember { mutableStateOf(initialId?.takeIf { id -> usable.any { it.id == id } } ?: usable.firstOrNull()?.id) }
    var year by remember { mutableStateOf<Int?>(null) } // null = 전체 연도
    // 한 번 모은 건 다시 안 받음 (단지 내 순위 계산 때 모아둔 것도 그대로 씀)
    val tables = remember { mutableStateMapOf<String, UnitTable>().apply { putAll(RankRunner.tables) } }
    var job by remember { mutableStateOf<Job?>(null) }
    var phase by remember { mutableStateOf("") }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var savedName by remember { mutableStateOf("") }

    val apt = usable.firstOrNull { it.id == aptId }
    val table = aptId?.let { tables[it] }
    val years = (table?.years ?: apt?.prices?.map { it.year } ?: emptyList()).sortedDescending()
    val busy = job?.isActive == true

    fun cancel() {
        job?.cancel(); job = null; phase = ""
    }
    BackHandler { if (busy) cancel() else onBack() }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
        val t = aptId?.let { tables[it] }
        if (uri == null || t == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "w")?.use { Xlsx.write(it, Export.sheets(t, year)) }
                        ?: throw IllegalStateException("파일을 열 수 없어요")
                }
                savedUri = uri
                savedName = Export.fileName(t, year)
            } catch (e: Exception) {
                error = "저장하지 못했어요: ${e.message}"
            }
        }
    }

    fun start() {
        val a = apt ?: return
        error = null; savedUri = null
        val ready = tables[a.id]
        if (ready != null) {
            saver.launch(Export.fileName(ready, year))
            return
        }
        job = scope.launch {
            try {
                val t = Export.collect(repo.api, a) { d, n, ph -> done = d; total = n; phase = ph }
                tables[a.id] = t
                RankRunner.tables[a.id] = t
                repo.applyRank(a.id, t) // 모은 김에 단지 내 순위도 갱신
                phase = ""
                saver.launch(Export.fileName(t, year))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "세대 정보를 모으지 못했어요"
                phase = ""
            } finally {
                job = null
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) {
            LinkText(if (busy) "← 그만하기" else "← 뒤로", { if (busy) cancel() else onBack() })
        }
        Spacer(Modifier.height(20.dp))
        T("엑셀로 내보내기", Type.big)
        Spacer(Modifier.height(6.dp))
        T("찜한 단지의 모든 동·호 공시가격을 한 파일로.", Type.body, color = p.sub)
        Spacer(Modifier.height(32.dp))

        if (usable.isEmpty()) {
            T("내보낼 아파트가 없어요. 먼저 아파트를 찜해주세요. (예시 데이터는 내보낼 수 없어요)", Type.body)
            return@Column
        }

        // 01 단지
        SectionHead("01", "단지")
        usable.forEachIndexed { i, a ->
            val on = a.id == aptId
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = !busy) { aptId = a.id; error = null; savedUri = null }
                    .padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.width(18.dp).height(18.dp)
                        .border(1.5.dp, p.ink)
                        .background(if (on) p.lime else Color.Transparent)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(a.complexName, if (on) Type.bodyBold else Type.body, maxLines = 1)
                    T(
                        a.address + (tables[a.id]?.let { " · ${comma.format(it.units.size)}세대 모아둠" } ?: ""),
                        Type.small, color = p.sub, maxLines = 1
                    )
                }
            }
            if (i < usable.size - 1) Hairline()
        }
        Spacer(Modifier.height(36.dp))

        // 02 연도
        SectionHead("02", "연도")
        FlowRow(Modifier.fillMaxWidth()) {
            YearChip("전체 연도", year == null, !busy) { year = null; savedUri = null }
            years.forEach { y -> YearChip("${y}년", year == y, !busy) { year = y; savedUri = null } }
        }
        Spacer(Modifier.height(10.dp))
        T(
            if (year == null) "동·호마다 연도별 공시가격이 가로로 쭉 — 시트: 전체 연도 / 요약 / 정보"
            else "${year}년 공시가격 · ㎡당 · 전년 대비 — 시트: ${year}년 / 요약 / 정보",
            Type.small, color = p.sub
        )
        Spacer(Modifier.height(36.dp))

        // 진행 상황
        if (busy) {
            Row { Marker(phase.ifBlank { "준비 중…" }, Type.bodyBold) }
            Spacer(Modifier.height(12.dp))
            val ratio = if (total > 0) done.toFloat() / total else 0f
            Box(Modifier.fillMaxWidth().height(10.dp).border(1.dp, p.ink)) {
                Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(10.dp).background(p.lime))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                T(if (total > 0) "${comma.format(done)} / ${comma.format(total)}" else "", Type.label, Modifier.weight(1f))
                T("세대가 많으면 몇 분 걸려요", Type.label, color = p.sub)
            }
            Spacer(Modifier.height(20.dp))
            LinkText("그만하기", { cancel() }, color = p.sub)
        } else {
            InkButton(
                if (table != null) "엑셀 파일 저장" else "세대 정보 모아서 엑셀 만들기",
                { start() }
            )
            if (table == null) {
                T(
                    "처음 한 번은 단지의 모든 세대를 공시가격 알리미에서 하나씩 받아와요. 받은 뒤엔 연도를 바꿔도 바로 저장돼요.",
                    Type.small, Modifier.padding(top = 10.dp), color = p.sub
                )
            } else if (table.failed.isNotEmpty()) {
                T("${table.failed.size}세대는 받지 못했어요 (정보 시트에 적어둠).", Type.small, Modifier.padding(top = 10.dp), color = p.sub)
            }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            T(it, Type.body)
        }

        savedUri?.let { uri ->
            Spacer(Modifier.height(28.dp))
            Hairline(strong = true)
            Spacer(Modifier.height(14.dp))
            Row { Marker("저장했어요", Type.title) }
            Spacer(Modifier.height(6.dp))
            T(savedName, Type.small, color = p.sub)
            Spacer(Modifier.height(16.dp))
            InkButton("공유하기 (카톡·메일·드라이브)", {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = XLSX
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, savedName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(send, "엑셀 파일 보내기"))
            })
            Spacer(Modifier.height(10.dp))
            LinkText("바로 열기", {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, XLSX)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (e: Exception) {
                    error = "엑셀을 열 앱이 없어요. 공유하기로 보내보세요."
                }
            }, color = p.sub)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun YearChip(label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .border(1.dp, if (on) p.ink else p.line)
            .background(if (on) p.ink else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        T(label, Type.bodyBold, color = if (on) p.bg else p.ink)
    }
}
