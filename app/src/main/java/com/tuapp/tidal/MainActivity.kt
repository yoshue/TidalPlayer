package com.tuapp.tidal

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var statusText: TextView
    private lateinit var songInfo: TextView
    private lateinit var albumArt: ImageView
    private lateinit var loader: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DISEÑO OLED PREMIUM
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(60, 80, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val inputField = EditText(this).apply {
            hint = "Canción o Artista..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(30, 30, 30, 30)
        }

        val btnSearch = Button(this).apply {
            text = "GO"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
        }

        inputLayout.addView(inputField)
        inputLayout.addView(btnSearch)

        // Espacio para la carátula
        albumArt = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(800, 800).apply {
                setMargins(0, 100, 0, 50)
            }
            visibility = View.GONE // Oculto hasta que haya una canción
        }

        songInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 10)
        }

        statusText = TextView(this).apply {
            text = "320kbps Mode Active"
            setTextColor(Color.parseColor("#3DDC84")) // Verde
            textSize = 12f
        }

        loader = ProgressBar(this).apply { visibility = View.GONE }

        root.addView(inputLayout)
        root.addView(albumArt)
        root.addView(loader)
        root.addView(songInfo)
        root.addView(statusText)

        setContentView(root)

        player = ExoPlayer.Builder(this).build()

        btnSearch.setOnClickListener {
            val q = inputField.text.toString()
            if (q.isNotEmpty()) startHQSearch(q)
        }
    }

    private fun startHQSearch(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "Buscando en servidor HQ..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://saavn.me/search/songs?query=$encoded&limit=1"
                
                val connection = URL(searchUrl).openConnection() as HttpURLConnection
                val response = connection.inputStream.bufferedReader().readText()
                
                val json = JSONObject(response)
                val data = json.getJSONObject("data").getJSONArray("results")

                if (data.length() > 0) {
                    val track = data.getJSONObject(0)
                    val name = track.getString("name")
                    val artist = track.getJSONObject("primaryArtists").getString("name")
                    
                    // Imágenes: Saavn da varias calidades, la última es 500x500
                    val images = track.getJSONArray("image")
                    val coverUrl = images.getJSONObject(images.length() - 1).getString("link")

                    // Audio: 320kbps es el último link del array
                    val downloadUrls = track.getJSONArray("downloadUrl")
                    val hqUrl = downloadUrls.getJSONObject(downloadUrls.length() - 1).getString("link")

                    withContext(Dispatchers.Main) {
                        loader.visibility = View.GONE
                        albumArt.visibility = View.VISIBLE
                        albumArt.load(coverUrl) {
                            crossfade(true)
                            transformations(RoundedCornersTransformation(30f))
                        }
                        songInfo.text = "$name\n$artist"
                        statusText.text = "Reproduciendo 320kbps real"
                        playAudio(hqUrl)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "Error al conectar con el servidor."
                }
            }
        }
    }

    private fun playAudio(url: String) {
        player?.setMediaItem(MediaItem.fromUri(url))
        player?.prepare()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}ayer?.release()
    }
}
