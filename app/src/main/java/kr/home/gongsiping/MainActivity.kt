package kr.home.gongsiping

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kr.home.gongsiping.data.Demo
import kr.home.gongsiping.data.Repo
import kr.home.gongsiping.data.Store
import kr.home.gongsiping.ui.AppTheme
import kr.home.gongsiping.ui.DetailScreen
import kr.home.gongsiping.ui.HomeScreen
import kr.home.gongsiping.ui.LocalPalette
import kr.home.gongsiping.ui.SearchScreen
import kr.home.gongsiping.ui.SettingsScreen
import kr.home.gongsiping.work.CheckWorker

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Detail(val id: String) : Screen
}

class MainActivity : ComponentActivity() {

    /** 알림을 눌러서 들어온 경우 열 아파트 id */
    private var openFromNotification by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo(applicationContext)
        Store.load(applicationContext)
        CheckWorker.createChannels(this)
        CheckWorker.schedule(this)
        openFromNotification = intent?.getStringExtra(CheckWorker.EXTRA_APT)

        setContent {
            var themeMode by remember { mutableIntStateOf(repo.prefs.themeMode) }
            val dark = when (themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                val t = Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                )
            }

            val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            AppTheme(themeMode) {
                val p = LocalPalette.current
                val apts by Store.apts.collectAsState()
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }

                LaunchedEffect(openFromNotification) {
                    openFromNotification?.let { id ->
                        if (apts.any { it.id == id }) screen = Screen.Detail(id)
                        openFromNotification = null
                    }
                }

                Box(Modifier.fillMaxSize().background(p.bg).safeDrawingPadding()) {
                    when (val s = screen) {
                        Screen.Home -> HomeScreen(
                            apts = apts,
                            lastCheck = repo.prefs.lastCheck,
                            lastCheckOk = repo.prefs.lastCheckOk,
                            onOpen = { screen = Screen.Detail(it.id) },
                            onAdd = { screen = Screen.Search },
                            onDemo = { Store.upsert(applicationContext, Demo.apt()) },
                            onSettings = { screen = Screen.Settings }
                        )

                        Screen.Search -> SearchScreen(
                            repo = repo,
                            onBack = { screen = Screen.Home },
                            onPicked = { c, dong, ho ->
                                val apt = repo.addApt(c, dong, ho)
                                CheckWorker.runNow(applicationContext) // 실거래 기준점 잡기
                                screen = Screen.Detail(apt.id)
                            }
                        )

                        Screen.Settings -> SettingsScreen(
                            prefs = repo.prefs,
                            onBack = { screen = Screen.Home },
                            onTheme = { themeMode = it }
                        )

                        is Screen.Detail -> {
                            val apt = apts.firstOrNull { it.id == s.id }
                            if (apt == null) {
                                LaunchedEffect(s.id) { screen = Screen.Home }
                            } else {
                                DetailScreen(
                                    repo = repo,
                                    apt = apt,
                                    onBack = { screen = Screen.Home },
                                    onDeleted = { screen = Screen.Home }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(CheckWorker.EXTRA_APT)?.let { openFromNotification = it }
    }
}
