package com.tuapp.tidal

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

class MusicService : Service() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Aquí es donde el servicio se prepara para sonar en segundo plano
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mediaSession?.token?.let { null } // Por ahora simple para que compile
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
