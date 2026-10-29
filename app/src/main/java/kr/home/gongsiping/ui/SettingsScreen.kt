package kr.home.gongsiping.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kr.home.gongsiping.BuildConfig
import kr.home.gongsiping.ads.Ads
import kr.home.gongsiping.data.Prefs
import kr.home.gongsiping.work.CheckWorker

@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit, onTheme: (Int) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    BackHandler { onBack() }

    var dKey by remember { mutableStateOf(prefs.dataKey) }
    var priceAlert by remember { mutableStateOf(prefs.priceAlert) }
    var tradeAlert by remember { mutableStateOf(prefs.tradeAlert) }
    var count by remember { mutableStateOf(prefs.compareCount) }
    var theme by remember { mutableStateOf(prefs.themeMode) }
    var checked by remember { mutableStateOf(false) }

    fun saveKeys() {
        prefs.dataKey = dKey
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) {
            LinkText("← 저장하고 나가기", { saveKeys(); onBack() })
        }
        Spacer(Modifier.height(20.dp))
        T("설정", Type.big)
        Spacer(Modifier.height(32.dp))

        SectionHead("01", "데이터")
        T("공시가격은 국토부 공시가격 알리미에서 바로 가져와요. 키가 필요 없어요.", Type.body)
        Spacer(Modifier.height(18.dp))
        T("(선택) 공공데이터포털 인증키 — 동네 실거래 알림을 받고 싶을 때만", Type.small, color = p.sub)
        UnderlineField(dKey, { dKey = it }, "data.go.kr 일반 인증키(Decoding)", style = Type.body)
        Spacer(Modifier.height(36.dp))

        SectionHead("02", "알림")
        ToggleRow("새 공시가격 나오면", "보통 4월 말 발표", priceAlert) { priceAlert = it; prefs.priceAlert = it }
        Hairline()
        ToggleRow("우리 동네 실거래 신고", "같은 법정동 · 위 키를 넣었을 때만", tradeAlert) { tradeAlert = it; prefs.tradeAlert = it }
        Hairline()
        Spacer(Modifier.height(14.dp))
        InkButton(if (checked) "확인 요청했어요 · 잠시 후 알림" else "지금 바로 확인하기", {
            saveKeys(); CheckWorker.runNow(ctx); checked = true
        })
        Spacer(Modifier.height(36.dp))

        SectionHead("03", "주변 비교 단지 수")
        val counts = listOf(5, 8, 12)
        Segmented(listOf("5곳", "8곳", "12곳"), counts.indexOf(count).coerceAtLeast(0), {
            count = counts[it]; prefs.compareCount = counts[it]
        })
        T("바꾸면 각 아파트 화면에서 '새로고침'을 눌러주세요.", Type.small, Modifier.padding(top = 8.dp), color = p.sub)
        Spacer(Modifier.height(36.dp))

        SectionHead("04", "화면")
        Segmented(listOf("시스템", "밝게", "어둡게"), theme, {
            theme = it; prefs.themeMode = it; onTheme(it)
        })
        if (Ads.privacyOptionsRequired.value) {
            Spacer(Modifier.height(24.dp))
            LinkText("광고 개인정보 설정", { (ctx as? android.app.Activity)?.let { Ads.showPrivacyOptions(it) } }, color = p.sub)
        }
        Spacer(Modifier.height(48.dp))
        T("공시핑 ${BuildConfig.VERSION_NAME} · 데이터는 폰 안에만 저장돼요. 광고로 무료 운영해요.", Type.small, color = p.sub)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(title, Type.bodyBold)
            T(sub, Type.small, color = p.sub)
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onLime,
                checkedTrackColor = p.lime,
                checkedBorderColor = p.ink,
                uncheckedThumbColor = p.sub,
                uncheckedTrackColor = p.bg,
                uncheckedBorderColor = p.sub
            )
        )
    }
}
