package kr.home.gongsiping.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kr.home.gongsiping.MainActivity
import kr.home.gongsiping.R
import kr.home.gongsiping.data.Alert
import kr.home.gongsiping.data.Repo
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 하루 2번(12시간마다) 돌면서
 *  새 공시가격 발표를 확인하고 알림을 띄운다. 서버 없이 폰 혼자 동작한다.
 */
class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = Repo(applicationContext)
        return try {
            val alerts = repo.checkAll()
            alerts.forEach { notify(applicationContext, it) }
            repo.prefs.lastCheck = System.currentTimeMillis()
            repo.prefs.lastCheckOk = !repo.lastFailed
            Result.success()
        } catch (e: Exception) {
            repo.prefs.lastCheckOk = false
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "gongsiping-check"
        private const val NOW = "gongsiping-check-now"
        const val CH_PRICE = "price"
        const val EXTRA_APT = "aptId"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<CheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<CheckWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        fun createChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_PRICE, "공시가격 발표", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "찜한 아파트의 새 공시가격이 나오면 알려줘요" }
            )
            nm.deleteNotificationChannel("trade") // 예전 버전의 '동네 실거래' 알림 채널 정리
        }

        @SuppressLint("MissingPermission")
        fun notify(ctx: Context, a: Alert) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            createChannels(ctx)
            val channel = CH_PRICE
            val intent = Intent(ctx, MainActivity::class.java).apply {
                putExtra(EXTRA_APT, a.aptId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val reqCode = (a.aptId + a.title).hashCode()
            val pi = PendingIntent.getActivity(
                ctx, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF141414.toInt())
                .setContentTitle(a.title)
                .setContentText(a.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(a.body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(reqCode, n)
            } catch (e: SecurityException) {
                // 권한이 꺼져 있으면 조용히 넘어간다
            }
        }

        /** 발표 시즌(3~5월)인지 — 화면 안내용 */
        fun inSeason(): Boolean = Calendar.getInstance().get(Calendar.MONTH) in 2..4
    }
}
