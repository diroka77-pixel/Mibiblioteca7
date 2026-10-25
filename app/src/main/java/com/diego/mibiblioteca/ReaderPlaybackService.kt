package com.diego.mibiblioteca

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle

/**
 * Keeps spoken reading alive while the screen is off and exposes transport controls to
 * Android, Bluetooth devices and the lock screen. TextToSpeech remains owned by ReaderScreen;
 * commands are returned to it as package-scoped broadcasts.
 */
class ReaderPlaybackService : Service() {
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var title = AppBrand.NAME
    private var author = "Lectura en voz alta"
    private var position = 0L
    private var speed = 1f
    private var playing = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> hasAudioFocus = true
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val focusWasHeld = hasAudioFocus
                hasAudioFocus = false
                if (playing && focusWasHeld) sendReaderCommand(ACTION_PAUSE)
            }
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) sendReaderCommand(ACTION_PAUSE)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()
        mediaSession = MediaSessionCompat(this, "MiBibliotecaReader").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = sendReaderCommand(ACTION_PLAY)
                override fun onPause() = sendReaderCommand(ACTION_PAUSE)
                override fun onStop() = sendReaderCommand(ACTION_STOP)
                override fun onSkipToNext() = sendReaderCommand(ACTION_FORWARD)
                override fun onSkipToPrevious() = sendReaderCommand(ACTION_REWIND)
            })
            isActive = true
        }
        ContextCompat.registerReceiver(
            this,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { AppBrand.NAME }
                author = intent.getStringExtra(EXTRA_AUTHOR).orEmpty().ifBlank { "Lectura en voz alta" }
                position = intent.getLongExtra(EXTRA_POSITION, position)
                speed = intent.getFloatExtra(EXTRA_SPEED, speed)
                playing = intent.getBooleanExtra(EXTRA_PLAYING, playing)
                if (playing) requestAudioFocus() else abandonAudioFocus()
                updateSession()
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_CLOSE -> {
                sendReaderCommand(ACTION_STOP)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PLAY, ACTION_PAUSE, ACTION_STOP, ACTION_FORWARD, ACTION_REWIND ->
                sendReaderCommand(intent.action!!)
        }
        return START_NOT_STICKY
    }

    private fun requestAudioFocus() {
        // Re-requesting focus while it is already held makes Android notify the previous
        // listener of a loss. That used to pause our own reader after resume, seek or speed.
        if (hasAudioFocus) return
        val result: Int
        if (Build.VERSION.SDK_INT >= 26) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                    .also { audioFocusRequest = it }
            result = audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            result = audioManager.requestAudioFocus(
                audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        // Clear first: some engines deliver the loss callback synchronously from abandon().
        hasAudioFocus = false
        if (Build.VERSION.SDK_INT >= 26) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    private fun updateSession() {
        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, author)
            .build())
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_PLAY_PAUSE)
            .setState(state, position, speed)
            .build())
    }

    private fun buildNotification(): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun command(action: String, requestCode: Int) = PendingIntent.getService(
            this, requestCode, Intent(this, ReaderPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleAction = if (playing) ACTION_PAUSE else ACTION_PLAY
        val toggleIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(author)
            .setContentIntent(openApp)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "Retroceder", command(ACTION_REWIND, 1))
            .addAction(toggleIcon, if (playing) "Pausar" else "Reproducir", command(toggleAction, 2))
            .addAction(android.R.drawable.ic_media_next, "Avanzar", command(ACTION_FORWARD, 3))
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun sendReaderCommand(action: String) {
        sendBroadcast(Intent(action).setPackage(packageName))
        if (action == ACTION_PAUSE || action == ACTION_STOP) {
            playing = false
            abandonAudioFocus()
            updateSession()
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Lectura en voz alta", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Controles de reproducción del lector"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(becomingNoisyReceiver)
        abandonAudioFocus()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_UPDATE = "com.diego.mibiblioteca.reader.UPDATE"
        const val ACTION_PLAY = "com.diego.mibiblioteca.reader.PLAY"
        const val ACTION_PAUSE = "com.diego.mibiblioteca.reader.PAUSE"
        const val ACTION_STOP = "com.diego.mibiblioteca.reader.STOP"
        const val ACTION_FORWARD = "com.diego.mibiblioteca.reader.FORWARD"
        const val ACTION_REWIND = "com.diego.mibiblioteca.reader.REWIND"
        const val ACTION_CLOSE = "com.diego.mibiblioteca.reader.CLOSE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_AUTHOR = "author"
        const val EXTRA_POSITION = "position"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_PLAYING = "playing"
        private const val CHANNEL_ID = "reader_playback"
        private const val NOTIFICATION_ID = 2401
    }
}
