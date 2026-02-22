package com.tuapp.tidal

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Creamos un diseño simple por código para no depender de archivos XML pesados
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val searchInput = EditText(this).apply {
            hint = "Buscar en Tidal Squid..."
        }

        val playButton = Button(this).apply {
            text = "Reproducir Prueba FLAC"
        }

        layout.addView(searchInput)
        layout.addView(playButton)
        setContentView(layout)

        // Inicializamos el motor de música
        player = ExoPlayer.Builder(this).build()

        playButton.setOnClickListener {
            // Este es un enlace de prueba. En el siguiente paso lo conectaremos a la API real.
            val testUrl = "https://clm-6.tidal.squid.wtf/api/download?id=65103444&quality=LOSSLESS"
            val mediaItem = MediaItem.fromUri(testUrl)
            
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.play()
            
            Toast.makeText(this, "Cargando música...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
