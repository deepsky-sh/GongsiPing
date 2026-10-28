package kr.home.gongsiping.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.home.gongsiping.data.Choice
import kr.home.gongsiping.data.Complex
import kr.home.gongsiping.data.Repo

@Composable
fun SearchScreen(
    repo: Repo,
    onBack: () -> Unit,
    onPicked: suspend (Complex, Choice, Choice) -> Unit
) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Complex>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    var step by remember { mutableStateOf(0) } // 0 단지, 1 동, 2 호
    var complex by remember { mutableStateOf<Complex?>(null) }
    var dongs by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var dong by remember { mutableStateOf<Choice?>(null) }
    var hos by remember { mutableStateOf<List<Choice>>(emptyList()) }

    fun back() {
        error = null
        if (busy != null) return
        if (step > 0) step-- else onBack()
    }
    BackHandler { back() }

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            busy = "찾는 중…"; error = null
            try {
                results = repo.api.searchComplexes(query.trim())
                searched = true
            } catch (e: Exception) {
                error = e.message ?: "검색에 실패했어요"
            }
            busy = null
        }
    }

    fun pickComplex(c: Complex) {
        scope.launch {
            busy = "${c.name} 동 목록 불러오는 중…"; error = null
            try {
                val list = repo.api.dongs(c.aptCode, c.noticeDate).sortedWith(compareBy(naturalOrder) { it.name })
                if (list.isEmpty()) error = "동 정보가 없는 단지예요. 다른 결과를 골라보세요."
                else { complex = c; dongs = list; step = 1 }
            } catch (e: Exception) {
                error = e.message ?: "불러오지 못했어요"
            }
            busy = null
        }
    }

    fun pickDong(d: Choice) {
        val c = complex ?: return
        scope.launch {
            busy = "${d.name}동 호 목록 불러오는 중…"; error = null
            try {
                val list = repo.api.hos(c.aptCode, c.noticeDate, d.code).sortedWith(compareBy(naturalOrder) { it.name })
                if (list.isEmpty()) error = "호 정보가 없어요."
                else { dong = d; hos = list; step = 2 }
            } catch (e: Exception) {
                error = e.message ?: "불러오지 못했어요"
            }
            busy = null
        }
    }

    fun pickHo(h: Choice) {
        val c = complex ?: return
        val d = dong ?: return
        scope.launch {
            busy = "공시가격 기록 모으는 중…"; error = null
            try {
                onPicked(c, d, h)
            } catch (e: Exception) {
                error = e.message ?: "추가하지 못했어요"
            }
            busy = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LinkText("← 뒤로", { back() })
            Spacer(Modifier.weight(1f))
            T(listOf("1 단지", "2 동", "3 호")[step], Type.label, color = p.sub)
        }

        when (step) {
            0 -> {
                Spacer(Modifier.height(24.dp))
                T("어느 아파트예요?", Type.big)
                Spacer(Modifier.height(18.dp))
                UnderlineField(query, { query = it }, "단지 이름 (예: 후곡마을)", onDone = { search() })
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Spacer(Modifier.weight(1f))
                    LinkText("검색", { search() })
                }
                Status(busy, error)
                if (searched && results.isEmpty() && busy == null && error == null) {
                    T("결과가 없어요. 이름을 짧게 넣어보세요. (예: '호수마을 5단지' → '호수마을')", Type.small, color = p.sub)
                }
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(results, key = { _, c -> c.aptCode }) { i, c ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = busy == null) { pickComplex(c) }
                                .padding(vertical = 14.dp)
                        ) {
                            T("%02d".format(i + 1), Type.label, Modifier.width(30.dp).padding(top = 3.dp), color = p.sub)
                            Column(Modifier.weight(1f)) {
                                T(c.name, Type.bodyBold)
                                T(c.road.ifBlank { c.address }, Type.small, color = p.sub)
                                if (c.road.isNotBlank()) T(c.address, Type.small, color = p.sub)
                            }
                        }
                        Hairline()
                    }
                }
            }

            1 -> {
                Spacer(Modifier.height(24.dp))
                T(complex?.name.orEmpty(), Type.small, color = p.sub)
                T("몇 동이에요?", Type.big)
                Spacer(Modifier.height(18.dp))
                Status(busy, error)
                Grid(dongs.map { it.code to "${it.name}동" }) { code ->
                    dongs.firstOrNull { it.code == code }?.let { pickDong(it) }
                }
            }

            2 -> {
                Spacer(Modifier.height(24.dp))
                T("${complex?.name.orEmpty()} · ${dong?.name.orEmpty()}동", Type.small, color = p.sub)
                T("몇 호예요?", Type.big)
                Spacer(Modifier.height(18.dp))
                Status(busy, error)
                Grid(hos.map { it.code to "${it.name}호" }) { code ->
                    hos.firstOrNull { it.code == code }?.let { pickHo(it) }
                }
            }
        }
    }
}

@Composable
private fun Status(busy: String?, error: String?) {
    val p = LocalPalette.current
    if (busy != null) {
        Row(Modifier.padding(vertical = 10.dp)) {
            Marker(busy, Type.bodyBold)
        }
    }
    if (error != null) {
        T(error, Type.body, Modifier.padding(vertical = 10.dp), color = p.ink)
    }
}

@Composable
private fun Grid(items: List<Pair<String, String>>, onClick: (String) -> Unit) {
    val p = LocalPalette.current
    LazyVerticalGrid(GridCells.Adaptive(92.dp), Modifier.fillMaxSize()) {
        items(items) { (key, label) ->
            Box(
                Modifier
                    .padding(4.dp)
                    .border(1.dp, p.line)
                    .clickable { onClick(key) }
                    .padding(vertical = 14.dp, horizontal = 10.dp)
            ) {
                T(label, Type.bodyBold, maxLines = 1)
            }
        }
    }
}

/** "101" < "102" < "1001", 숫자가 아닌 건 뒤로 */
val naturalOrder: Comparator<String> = Comparator { a, b ->
    val na = a.takeWhile { it.isDigit() }.toIntOrNull()
    val nb = b.takeWhile { it.isDigit() }.toIntOrNull()
    when {
        na != null && nb != null && na != nb -> na.compareTo(nb)
        na != null && nb == null -> -1
        na == null && nb != null -> 1
        else -> a.compareTo(b)
    }
}
