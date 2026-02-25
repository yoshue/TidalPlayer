package com.tuapp.tidal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Modelo de datos para la música
data class Song(val id: String, val title: String, val artist: String, val cover: String)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MusicAdapter
    private val songList = mutableListOf<Song>()
    
    // UI del Mini Player Inferior
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniCover: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniBtn: ImageButton
    private lateinit var loader: ProgressBar

    private val apiEndpoints = arrayOf(
        "https://pipedapi.lunar.icu",
        "https://api.piped.victr.me",
        "https://pipedapi.ducks.party"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")

        // Layout Principal
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#050505")) }

        // 1. Cabecera y Buscador
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 20)
            layoutParams = RelativeLayout.LayoutParams(-1, -2)
        }

        val titlePage = TextView(this).apply {
            text = "Explorar"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setPadding(0, 0, 0, 30)
        }

        val searchCard = CardView(this).apply {
            radius = 20f
            setCardBackgroundColor(Color.parseColor("#1A1A1A"))
            elevation = 0f
        }
        
        val searchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(30, 10, 30, 10)
            gravity = Gravity.CENTER_VERTICAL
        }

        val input = EditText(this).apply {
            hint = "Artistas, canciones o álbumes"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val searchBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
        }

        searchLayout.addView(input); searchLayout.addView(searchBtn)
        searchCard.addView(searchLayout)
        header.addView(titlePage); header.addView(searchCard)

        // 2. Lista de Resultados (Grid de 2 columnas)
        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            setPadding(20, 20, 20, 200) // Espacio para el miniplayer
            clipToPadding = false
            val lp = RelativeLayout.LayoutParams(-1, -1)
            lp.addRule(RelativeLayout.BELOW, header.id)
            layoutParams = lp
        }
        adapter = MusicAdapter(songList) { song -> playSong(song) }
        recyclerView.adapter = adapter

        // 3. Mini Player Inferior (Estilo Spotify)
        miniPlayer = LinearLayout(this).apply {
            visibility = View.GONE
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#121212"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 15, 40, 15)
            val lp = RelativeLayout.LayoutParams(-1, -2)
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            layoutParams = lp
            elevation = 20f
        }

        miniCover = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(120, 120) }
        
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        miniTitle = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; maxLines = 1
            typeface = Typeface.DEFAULT_BOLD
        }
        val miniSub = TextView(this).apply {
            text = "Reproduciendo ahora"; setTextColor(Color.GRAY); textSize = 11f
        }
        textContainer.addView(miniTitle); textContainer.addView(miniSub)

        miniBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE)
            scaleX = 1.3f; scaleY = 1.3f
        }

        miniPlayer.addView(miniCover); miniPlayer.addView(textContainer); miniPlayer.addView(miniBtn)

        loader = ProgressBar(this).apply {
            visibility = View.GONE
            val lp = RelativeLayout.LayoutParams(-2, -2)
            lp.addRule(RelativeLayout.CENTER_IN_PARENT)
            layoutParams = lp
        }

        root.addView(header); root.addView(recyclerView); root.addView(miniPlayer); root.addView(loader)
        setContentView(root)
        setupPlayer()

        searchBtn.setOnClickListener {
            val q = input.text.toString()
            if (q.isNotEmpty()) searchMusic(q)
        }
        
        miniBtn.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    miniBtn.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                }
            })
        }
    }

    private fun searchMusic(query: String) {
        loader.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            for (base in apiEndpoints) {
                try {
                    val q = URLEncoder.encode(query, "UTF-8")
                    val conn = URL("$base/search?q=$q&filter=music_songs").openConnection() as HttpURLConnection
                    if (conn.responseCode == 200) {
                        val json = JSONObject(conn.inputStream.bufferedReader().readText())
                        val items = json.getJSONArray("items")
                        
                        val tempResults = mutableListOf<Song>()
                        for (i in 0 until items.length()) {
                            val obj = items.getJSONObject(i)
                            tempResults.add(Song(
                                obj.getString("url").split("v=")[1],
                                obj.getString("title"),
                                obj.getString("uploaderName"),
                                obj.getString("thumbnail")
                            ))
                        }
                        
                        withContext(Dispatchers.Main) {
                            songList.clear()
                            songList.addAll(tempResults)
                            adapter.notifyDataSetChanged()
                            loader.visibility = View.GONE
                        }
                        return@launch
                    }
                } catch (e: Exception) { continue }
            }
        }
    }

    private fun playSong(song: Song) {
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = song.title
        miniCover.load(song.cover) { transformations(RoundedCornersTransformation(10f)) }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sUrl = URL("${apiEndpoints[0]}/streams/${song.id}")
                val sJson = JSONObject(sUrl.readText())
                val audio = sJson.getJSONArray("audioStreams").getJSONObject(0).getString("url")
                
                withContext(Dispatchers.Main) {
                    player?.setMediaItem(MediaItem.fromUri(audio))
                    player?.prepare(); player?.play()
                }
            } catch (e: Exception) { }
        }
    }

    // --- ADAPTADOR PARA LA CUADRÍCULA ---
    inner class MusicAdapter(private val list: List<Song>, val onClick: (Song) -> Unit) : 
        RecyclerView.Adapter<MusicAdapter.VH>() {
        
        inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
            val img = view.findViewById<ImageView>(1)
            val txt = view.findViewById<TextView>(2)
            val art = view.findViewById<TextView>(3)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = CardView(parent.context).apply {
                radius = 15f; setCardBackgroundColor(Color.TRANSPARENT); elevation = 0f
                layoutParams = GridLayoutManager.LayoutParams(-1, -2).apply { setMargins(15, 15, 15, 15) }
            }
            val lay = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val iv = ImageView(parent.context).apply { 
                id = 1; layoutParams = LinearLayout.LayoutParams(-1, 400)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            val tv = TextView(parent.context).apply { 
                id = 2; setTextColor(Color.WHITE); textSize = 13f; maxLines = 1; setPadding(5, 15, 5, 0) 
                typeface = Typeface.DEFAULT_BOLD
            }
            val av = TextView(parent.context).apply { 
                id = 3; setTextColor(Color.GRAY); textSize = 11f; setPadding(5, 0, 5, 10) 
            }
            lay.addView(iv); lay.addView(tv); lay.addView(av); card.addView(lay)
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = list[position]
            holder.img.load(s.cover) { transformations(RoundedCornersTransformation(20f)) }
            holder.txt.text = s.title
            holder.art.text = s.artist
            holder.view.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
