package kr.home.gongsiping.ads

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kr.home.gongsiping.BuildConfig
import kr.home.gongsiping.ui.Hairline
import kr.home.gongsiping.ui.LocalPalette
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob 광고
 *  - 배너: 홈·아파트 화면 맨 아래 (적응형 고정 배너)
 * 광고 단위 ID 는 app/build.gradle 맨 위 ADMOB 블록에서 바꾼다.
 * 유럽 등 동의가 필요한 지역은 Google UMP 로 동의를 먼저 받는다.
 */
object Ads {
    /** 광고를 요청해도 되는 상태인지 (동의 확인 + SDK 초기화 끝) */
    val ready = mutableStateOf(false)
    /** 설정 화면에 '광고 개인정보 설정' 을 보여줘야 하는지 */
    val privacyOptionsRequired = mutableStateOf(false)

    private val started = AtomicBoolean(false)
    private var consent: ConsentInformation? = null

    fun start(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consent = info
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    privacyOptionsRequired.value = info.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    if (info.canRequestAds()) initSdk(activity)
                }
            },
            { if (info.canRequestAds()) initSdk(activity) } // 동의 서버에 못 닿아도 이전 동의가 있으면 진행
        )
        // 이전 실행에서 이미 동의했다면 기다리지 않고 바로
        if (info.canRequestAds()) initSdk(activity)
    }

    private fun initSdk(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        Thread {
            MobileAds.initialize(activity.applicationContext) {
                activity.runOnUiThread { ready.value = true }
            }
        }.start()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }
}

/** 화면 맨 아래 적응형 배너. 광고 SDK 준비 전에는 자리를 차지하지 않는다. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val ready by Ads.ready
    if (!ready) return
    val p = LocalPalette.current
    var loaded by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth.value.toInt().coerceAtLeast(320)
        val ctx = LocalContext.current
        val size = remember(width) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, width) }
        val view = remember(width) {
            AdView(ctx).apply {
                setAdSize(size)
                adUnitId = BuildConfig.ADMOB_BANNER
                adListener = object : AdListener() {
                    override fun onAdLoaded() { loaded = true }
                    override fun onAdFailedToLoad(error: LoadAdError) { loaded = false }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
        DisposableEffect(view) {
            view.resume()
            onDispose { view.destroy() }
        }
        // AdMob 배치 기준
        //  - 배너 높이는 처음부터 잡아둔다 (광고가 늦게 와서 화면이 밀리며 잘못 눌리는 일 방지)
        //  - 위쪽 앱 콘텐츠와는 클릭 안 되는 여백 + 구분선으로 떼어 놓는다
        //  - 아래쪽 시스템 내비게이션(제스처 바)과도 조금 띄운다
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth()
                    .height(size.height.dp)
                    .background(p.bg),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

