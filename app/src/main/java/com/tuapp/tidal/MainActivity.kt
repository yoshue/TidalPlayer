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
import java.util.Scanner

data class Song(val id: String, val title: String, val artist: String, val cover: String, val previewUrl: String)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MusicAdapter
    private val songList = mutableListOf<Song>()
    
    private lateinit var miniPlayer: CardView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniCover: ImageView
    private lateinit var btnPlay: ImageButton
    private lateinit var loader: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#0A0A0A")) }

        // Header
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(60, 130, 60, 30)
            layoutParams = RelativeLayout.LayoutParams(-1, -2)
        }

        val titleText = TextView(this).apply {
            text = "Explorar"
            setTextColor(Color.WHITE); textSize = 32f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setPadding(0, 0, 0, 40)
        }
        header.addView(titleText)

        // Buscador
        val searchCard = CardView(this).apply {
            radius = 15f; setCardBackgroundColor(Color.parseColor("#1C1C1C")); elevation = 0f
        }
        val searchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(35, 10, 20, 10)
        }
        val searchInput = EditText(this).apply {
            hint = "Artistas o canciones"; setHintTextColor(Color.parseColor("#555555"))
            setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setSingleLine()
        }
        val searchBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search); setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT); setPadding(20, 20, 20, 20)
        }
        searchLayout.addView(searchInput); searchLayout.addView(searchBtn)
        searchCard.addView(searchLayout); header.addView(searchCard)

        // Grid
        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            setPadding(30, 30, 30, 300)
            clipToPadding = false
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, header.id) }
        }
        adapter = MusicAdapter(songList) { playSong(it) }
        recyclerView.adapter = adapter

        // Mini Reproductor
        miniPlayer = CardView(this).apply {
            visibility = View.GONE; radius = 25f; setCardBackgroundColor(Color.parseColor("#222222"))
            elevation = 20f; layoutParams = RelativeLayout.LayoutParams(-1, 170).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); setMargins(40, 0, 40, 60)
            }
        }
        val miniLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(30, 0, 40, 0) }
        miniCover = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(110, 110) }
        val miniTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        miniTitle = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; maxLines = 1; typeface = Typeface.DEFAULT_BOLD }
        miniArtist = TextView(this).apply { setTextColor(Color.parseColor("#AAAAAA")); textSize = 11f; maxLines = 1 }
        miniTexts.addView(miniTitle); miniTexts.addView(miniArtist)
        btnPlay = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_pause); setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE); scaleX = 1.4f; scaleY = 1.4f }
        miniLayout.addView(miniCover); miniLayout.addView(miniTexts); miniLayout.addView(btnPlay); miniPlayer.addView(miniLayout)

        loader = ProgressBar(this).apply { visibility = View.GONE; layoutParams = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.CENTER_IN_PARENT) } }

        root.addView(header); root.addView(recyclerView); root.addView(miniPlayer); root.addView(loader)
        setContentView(root)

        searchBtn.setOnClickListener {
            val q = searchInput.text.toString()
            if (q.isNotEmpty()) performDeezerSearch(q)
        }

        setupPlayer()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        btnPlay.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            btnPlay.setImageResource(if (player?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }
    }

    private fun performDeezerSearch(query: String) {
        loader.visibility = View.VISIBLE
        songList.clear()
        adapter.notifyDataSetChanged()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // API Pública de Deezer
                val url = URL("https://api.deezer.com/search?q=${query.replace(" ", "%20")}")
                val conn = url.openConnection() as HttpURLConnection
                val response = Scanner(conn.inputStream).useDelimiter("\\A").next()
                val data = JSONObject(response).getJSONArray("data")

                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val artist = item.getJSONObject("artist").getString("name")
                    val album = item.getJSONObject("album")
                    songList.add(Song(
                        item.getString("id"),
                        item.getString("title"),
                        artist,
                        album.getString("cover_medium"),
                        item.getString("preview") // URL directa del audio (.mp3)
                    ))
                }

                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun playSong(song: Song) {
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = song.title
        miniArtist.text = song.artist
        miniCover.load(song.cover) { transformations(RoundedCornersTransformation(15f)) }
        
        player?.setMediaItem(MediaItem.fromUri(song.previewUrl))
        player?.prepare()
        player?.play()
        btnPlay.setImageResource(android.R.drawable.ic_media_pause)
    }

    inner class MusicAdapter(val list: List<Song>, val onClick: (Song) -> Unit) : RecyclerView.Adapter<MusicAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img = v.findViewById<ImageView>(1); val t1 = v.findViewById<TextView>(2); val t2 = v.findViewById<TextView>(3)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = CardView(parent.context).apply {
                radius = 20f; setCardBackgroundColor(Color.TRANSPARENT); elevation = 0f
                layoutParams = GridLayoutManager.LayoutParams(-1, -2).apply { setMargins(15, 15, 15, 15) }
            }
            val lay = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val iv = ImageView(parent.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(-1, 450); scaleType = ImageView.ScaleType.CENTER_CROP }
            val tv1 = TextView(parent.context).apply { id = 2; setTextColor(Color.WHITE); textSize = 14f; setPadding(10, 20, 10, 0); maxLines = 1; typeface = Typeface.DEFAULT_BOLD }
            val tv2 = TextView(parent.context).apply { id = 3; setTextColor(Color.parseColor("#777777")); textSize = 12f; setPadding(10, 5, 10, 30); maxLines = 1 }
            lay.addView(iv); lay.addView(tv1); lay.addView(tv2); card.addView(lay)
            return VH(card)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]
            h.img.load(s.cover) { transformations(RoundedCornersTransformation(25f)) }
            h.t1.text = s.title; h.t2.text = s.artist
            h.itemView.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }
}
