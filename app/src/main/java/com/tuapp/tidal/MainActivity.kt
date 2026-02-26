package com.tuapp.tidal

import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.*
import coil.load
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

data class Song(val id: String, val title: String, val artist: String, val cover: String, val preview: String, val duration: Int = 0)
data class Album(val id: String, val title: String, val cover: String, val artist: String)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var songAdapter: SongAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private val songList = mutableListOf<Song>()
    private val albumList = mutableListOf<Album>()
    
    private lateinit var miniSeekBar: SeekBar
    private lateinit var btnPlayMini: ImageButton
    private lateinit var miniPlayer: CardView
    private lateinit var miniTitle: TextView
    private var currentSong: Song? = null
    private var fullSeekBar: SeekBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // --- CABECERA ---
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 20)
        }
        val titleMain = TextView(this).apply { text = "Música"; setTextColor(Color.WHITE); textSize = 32f; typeface = Typeface.DEFAULT_BOLD; setPadding(0,0,0,30) }
        val searchBox = RelativeLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#1A1A1A")); cornerRadius = 25f }
            setPadding(30, 10, 30, 10)
        }
        val input = EditText(this).apply {
            id = View.generateViewId()
            hint = "Buscar..."; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH; inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, 99) }
        }
        val icon = ImageView(this).apply {
            id = 99; setImageResource(android.R.drawable.ic_menu_search); setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(80, 80).apply { addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL) }
            setOnClickListener { searchEverything(input.text.toString()) }
        }
        input.setOnEditorActionListener { v, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { searchEverything(v.text.toString()); true } else false }
        
        searchBox.addView(input); searchBox.addView(icon)
        header.addView(titleMain); header.addView(searchBox)

        // --- LISTAS PRINCIPALES ---
        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, header.id) }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 0, 40, 350) }

        content.addView(createLabel("Álbumes Destacados"))
        val rvAlbums = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false) }
        albumAdapter = AlbumAdapter(albumList) { openAlbumDetail(it) }
        rvAlbums.adapter = albumAdapter
        content.addView(rvAlbums)

        content.addView(createLabel("Sugerencias"))
        val rvSongs = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity); isNestedScrollingEnabled = false }
        songAdapter = SongAdapter(songList) { playSong(it) }
        rvSongs.adapter = songAdapter
        content.addView(rvSongs)

        scroll.addView(content)
        val miniP = createMiniPlayer()
        root.addView(header); root.addView(scroll); root.addView(miniP)
        setContentView(root)

        setupPlayer(); searchEverything("Megadeth")
    }

    // --- VENTANA NUEVA: DETALLE DEL ÁLBUM ---
    private fun openAlbumDetail(album: Album) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }
        
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 80, 0, 40)
        }

        val btnBack = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert); setBackgroundColor(0); setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(100, 100).apply { gravity = Gravity.START; marginStart = 40 }
            setOnClickListener { dialog.dismiss() }
        }
        
        val cover = ImageView(this).apply { 
            layoutParams = LinearLayout.LayoutParams(600, 600)
            load(album.cover) { transformations(RoundedCornersTransformation(30f)) }
        }
        val title = TextView(this).apply { text = album.title; setTextColor(Color.WHITE); textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setPadding(40, 30, 40, 0); gravity = Gravity.CENTER }
        val artist = TextView(this).apply { text = album.artist; setTextColor(Color.CYAN); textSize = 16f; setPadding(0, 10, 0, 40) }

        header.addView(btnBack); header.addView(cover); header.addView(title); header.addView(artist)

        val albumSongs = mutableListOf<Song>()
        val rv = RecyclerView(this).apply { 
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, header.id) }
        }
        val adapter = SongAdapter(albumSongs, true) { playSong(it); dialog.dismiss() }
        rv.adapter = adapter

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = JSONObject(URL("https://api.deezer.com/album/${album.id}/tracks").readText()).getJSONArray("data")
                for (i in 0 until res.length()) {
                    val o = res.getJSONObject(i)
                    albumSongs.add(Song(o.getString("id"), o.getString("title"), album.artist, album.cover, o.getString("preview"), o.getInt("duration")))
                }
                withContext(Dispatchers.Main) { adapter.notifyDataSetChanged() }
            } catch (e: Exception) {}
        }

        root.addView(header); root.addView(rv)
        dialog.setContentView(root); dialog.show()
    }

    // --- REPRODUCTOR Y LOGICA ---
    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_READY) {
                    miniSeekBar.max = player?.duration?.toInt() ?: 0
                    fullSeekBar?.max = player?.duration?.toInt() ?: 0
                    updateProgress()
                }
            }
        })
    }

    private fun updateProgress() {
        lifecycleScope.launch {
            while (isActive) {
                player?.let {
                    miniSeekBar.progress = it.currentPosition.toInt()
                    fullSeekBar?.progress = it.currentPosition.toInt()
                }
                delay(1000)
            }
        }
    }

    private fun playSong(s: Song) {
        currentSong = s
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = s.title
        findViewById<ImageView>(777).load(s.cover)
        player?.setMediaItem(MediaItem.fromUri(s.preview))
        player?.prepare(); player?.play()
        btnPlayMini.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun openFullPlayer() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#080808")); setPadding(60, 60, 60, 60) }

        val btnClose = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_close_clear_cancel); setBackgroundColor(0); setColorFilter(Color.WHITE); setOnClickListener { dialog.dismiss() } }
        val btnMenu = ImageButton(this).apply { 
            setImageResource(android.R.drawable.ic_menu_manage); setBackgroundColor(0); setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(100, 100).apply { addRule(RelativeLayout.ALIGN_PARENT_END) }
            setOnClickListener { showMenu(it) }
        }

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.CENTER_IN_PARENT) } }
        val cover = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(800, 800); load(currentSong?.cover) { transformations(RoundedCornersTransformation(40f)) } }
        val title = TextView(this).apply { text = currentSong?.title; setTextColor(Color.WHITE); textSize = 24f; setPadding(0, 50, 0, 10); typeface = Typeface.DEFAULT_BOLD }
        val artist = TextView(this).apply { text = currentSong?.artist; setTextColor(Color.CYAN); textSize = 18f }
        info.addView(cover); info.addView(title); info.addView(artist)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bottomMargin = 50 } }
        fullSeekBar = SeekBar(this).apply { progressDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN) }
        val bPlay = ImageButton(this).apply { 
            setImageResource(if(player?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            setBackgroundColor(0); setColorFilter(Color.WHITE); scaleX = 2f; scaleY = 2f
            setOnClickListener { if(player?.isPlaying == true) { player?.pause(); setImageResource(android.R.drawable.ic_media_play) } else { player?.play(); setImageResource(android.R.drawable.ic_media_pause) } }
        }
        controls.addView(fullSeekBar); controls.addView(bPlay)

        view.addView(btnClose); view.addView(btnMenu); view.addView(info); view.addView(controls)
        dialog.setContentView(view); dialog.show()
    }

    private fun showMenu(v: View) {
        val p = PopupMenu(this, v)
        p.menu.add("Ecualizador").setOnMenuItemClickListener {
            val i = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            i.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, player?.audioSessionId)
            i.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            startActivityForResult(i, 1); true
        }
        p.menu.add("Ir al Artista").setOnMenuItemClickListener { searchEverything(currentSong?.artist ?: ""); true }
        p.menu.add("Información").setOnMenuItemClickListener { Toast.makeText(this, "ID: ${currentSong?.id}", Toast.LENGTH_SHORT).show(); true }
        p.show()
    }

    private fun searchEverything(q: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sRes = JSONObject(URL("https://api.deezer.com/search?q=$q").readText()).getJSONArray("data")
                val aRes = JSONObject(URL("https://api.deezer.com/search/album?q=$q").readText()).getJSONArray("data")
                withContext(Dispatchers.Main) {
                    songList.clear(); albumList.clear()
                    for(i in 0 until sRes.length()){ val o = sRes.getJSONObject(i); songList.add(Song(o.getString("id"), o.getString("title"), o.getJSONObject("artist").getString("name"), o.getJSONObject("album").getString("cover_medium"), o.getString("preview"))) }
                    for(i in 0 until aRes.length()){ val o = aRes.getJSONObject(i); albumList.add(Album(o.getString("id"), o.getString("title"), o.getString("cover_medium"), o.getJSONObject("artist").getString("name"))) }
                    songAdapter.notifyDataSetChanged(); albumAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {}
        }
    }

    private fun createMiniPlayer(): View {
        miniPlayer = CardView(this).apply { radius = 30f; setCardBackgroundColor(Color.parseColor("#111111")); visibility = View.GONE; setOnClickListener { openFullPlayer() }
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); setMargins(20, 0, 20, 30) }
        }
        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        miniSeekBar = SeekBar(this).apply { setPadding(0,0,0,0); thumb = null; progressDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(30, 20, 30, 20) }
        val img = ImageView(this).apply { id = 777; layoutParams = LinearLayout.LayoutParams(110, 110) }
        miniTitle = TextView(this).apply { setTextColor(Color.WHITE); setPadding(30, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); maxLines = 1 }
        btnPlayMini = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0); setColorFilter(Color.WHITE); setOnClickListener { if(player?.isPlaying == true) player?.pause() else player?.play() } }
        row.addView(img); row.addView(miniTitle); row.addView(btnPlayMini); lay.addView(miniSeekBar); lay.addView(row)
        miniPlayer.addView(lay); return miniPlayer
    }

    private fun createLabel(t: String) = TextView(this).apply { text = t; setTextColor(Color.WHITE); textSize = 20f; setPadding(0, 40, 0, 20); typeface = Typeface.DEFAULT_BOLD }

    // --- ADAPTERS ---
    inner class SongAdapter(val list: List<Song>, val showDuration: Boolean = false, val onClick: (Song) -> Unit) : RecyclerView.Adapter<SongAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(RelativeLayout(p.context).apply { setPadding(20, 20, 20, 20) }.apply { 
            val img = ImageView(p.context).apply { id = 1; layoutParams = RelativeLayout.LayoutParams(120, 120) }
            val txts = LinearLayout(p.context).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 0, 0, 0)
                layoutParams = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.RIGHT_OF, 1) }
                addView(TextView(p.context).apply { id = 2; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
                addView(TextView(p.context).apply { id = 3; setTextColor(Color.GRAY); textSize = 12f })
            }
            val dur = TextView(p.context).apply { id = 4; setTextColor(Color.GRAY); textSize = 12f
                layoutParams = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_RIGHT); addRule(RelativeLayout.CENTER_VERTICAL) }
            }
            addView(img); addView(txts); addView(dur)
        })
        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]
            h.itemView.findViewById<ImageView>(1).load(s.cover) { transformations(RoundedCornersTransformation(15f)) }
            h.itemView.findViewById<TextView>(2).text = s.title
            h.itemView.findViewById<TextView>(3).text = s.artist
            val dTxt = h.itemView.findViewById<TextView>(4)
            if(showDuration && s.duration > 0) dTxt.text = "${s.duration/60}:${String.format("%02d", s.duration%60)}" else dTxt.text = ""
            h.itemView.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }

    inner class AlbumAdapter(val list: List<Album>, val onClick: (Album) -> Unit) : RecyclerView.Adapter<AlbumAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LinearLayout(p.context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 40, 0) }.apply { 
            addView(ImageView(p.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(400, 400) })
            addView(TextView(p.context).apply { id = 2; setTextColor(Color.WHITE); setPadding(0, 10, 0, 0); maxLines = 1; layoutParams = LinearLayout.LayoutParams(400, -2) })
        })
        override fun onBindViewHolder(h: VH, p: Int) {
            val a = list[p]; h.itemView.findViewById<ImageView>(1).load(a.cover) { transformations(RoundedCornersTransformation(20f)) }
            h.itemView.findViewById<TextView>(2).text = a.title; h.itemView.setOnClickListener { onClick(a) }
        }
        override fun getItemCount() = list.size
    }
}
