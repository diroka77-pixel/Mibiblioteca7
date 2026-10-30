package com.diego.mibiblioteca

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Mantiene viva la sincronización de la carpeta cuando la app pasa a segundo plano.
 *
 * Android congela las apps que no están en pantalla, y la lectura de una carpeta grande en
 * la nube (cientos de libros) se quedaba a medias. Mientras dura la sincronización, este
 * servicio muestra una notificación con el progreso y Android deja que termine. El trabajo
 * lo sigue haciendo LibraryViewModel; el servicio solo se encarga de la notificación.
 */
class LibrarySyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this, lastCount), type)
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Android 15 limita el tiempo de este tipo de servicio; al agotarse, se cierra sin error. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "library_sync"
        private const val NOTIFICATION_ID = 2402
        @Volatile private var lastCount = 0
        @Volatile private var lastShown = 0L

        fun start(context: Context) {
            lastCount = 0
            try {
                ContextCompat.startForegroundService(context, Intent(context, LibrarySyncService::class.java))
            } catch (_: Exception) {
                // Si Android no deja iniciarlo (por ejemplo, la app ya estaba en segundo plano),
                // la sincronización sigue igual que antes, solo sin notificación.
            }
        }

        /** Actualiza el número de archivos revisados (como mucho una vez por segundo). */
        fun progress(context: Context, count: Int) {
            lastCount = count
            val now = System.currentTimeMillis()
            if (now - lastShown < 1000L) return
            lastShown = now
            val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!allowed) return
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification(context, count))
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try { context.stopService(Intent(context, LibrarySyncService::class.java)) } catch (_: Exception) {}
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, tr("Sincronización de la biblioteca"),
                NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun notification(context: Context, count: Int) =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(tr("Sincronizando tu biblioteca"))
                .setContentText(if (count > 0) trf("{0} archivos revisados", count)
                    else tr("Leyendo la carpeta de libros…"))
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .build()
    }
}
