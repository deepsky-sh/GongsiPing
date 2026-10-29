package kr.home.gongsiping.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 단지 내 순위 계산을 화면과 상관없이 돌린다 (화면을 나가도 계속).
 * 한 번에 한 단지씩만 — 공시가격 알리미에 부담을 덜 주려고.
 */
object RankRunner {
    sealed interface State {
        data class Running(val done: Int, val total: Int) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val one = Mutex()
    private val _state = MutableStateFlow<Map<String, State>>(emptyMap())
    val state: StateFlow<Map<String, State>> = _state

    /** 한 번 모은 단지 표 (엑셀 내보내기에서 다시 씀). 앱이 켜져 있는 동안만 */
    val tables = HashMap<String, UnitTable>()

    private fun set(id: String, s: State?) {
        _state.value = if (s == null) _state.value - id else _state.value + (id to s)
    }

    /** 순위가 없거나 오래됐으면 계산. force = 사용자가 '다시' 를 눌렀을 때 */
    fun ensure(ctx: Context, apt: Apt, force: Boolean = false) {
        if (apt.demo || !apt.usable) return
        val cur = _state.value[apt.id]
        if (cur is State.Running) return
        if (!force && (cur is State.Failed || !apt.rankStale)) return // 실패한 건 자동으로 계속 다시 하지 않음
        set(apt.id, State.Running(0, 0))
        scope.launch {
            one.withLock {
                try {
                    val repo = Repo(ctx.applicationContext)
                    val fresh = Store.get(ctx, apt.id) ?: return@withLock set(apt.id, null)
                    val table = repo.refreshRank(fresh) { d, n -> set(apt.id, State.Running(d, n)) }
                    if (table != null) tables[apt.id] = table
                    val ok = Store.get(ctx, apt.id)?.rank != null
                    set(apt.id, if (ok) null else State.Failed("순위를 계산하지 못했어요"))
                } catch (e: Exception) {
                    set(apt.id, State.Failed(e.message ?: "세대 공시가격을 못 불러왔어요"))
                }
            }
        }
    }
}
