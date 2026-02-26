package com.tuapp.tidal

import android.graphics.*
import android.media.audiofx.Equalizer
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

data class Song(val id: String, val title: String, val artist: String, val cover: String, val preview: String)
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

        // BUSCADOR CON LUPA
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 20)
        }
        val searchBox = RelativeLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#1A1A1A")); cornerRadius = 25f }
            setPadding(30, 10, 30, 10)
        }
        val input = EditText(this).apply {
            id = View.generateViewId()
            hint = "Buscar en Tidal..."; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH; inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, 99) }
        }
        val icon = ImageView(this).apply {
            id = 99; setImageResource(android.R.drawable.ic_menu_search); setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(80, 80).apply { addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL) }
            setOnClickListener { searchEverything(input.text.toString()) }
        }
        input.setOnEditorActionListener { v, actionId, _ -> 
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchEverything(v.text.toString()); true } else false 
        }
        searchBox.addView(input); searchBox.addView(icon); header.addView(searchBox)

        // CONTENIDO
        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, header.id) }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 0, 40, 350) }

        content.addView(createLabel("Álbumes Destacados"))
        val rvAlbums = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false) }
        albumAdapter = AlbumAdapter(albumList) { openAlbum(it) }
        rvAlbums.adapter = albumAdapter
        content.addView(rvAlbums)

        content.addView(createLabel("Canciones"))
        val rvSongs = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity); isNestedScrollingEnabled = false }
        songAdapter = SongAdapter(songList) { playSong(it) }
        rvSongs.adapter = songAdapter
        content.addView(rvSongs)

        scroll.addView(content)
        val miniP = createMiniPlayer()
        root.addView(header); root.addView(scroll); root.addView(miniP)
        setContentView(root)

        setupPlayer(); searchEverything("Top Global")
    }

    private fun searchEverything(q: String) {
        if (q.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val songs = JSONObject(URL("https://api.deezer.com/search?q=$q").readText()).getJSONArray("data")
                val albums = JSONObject(URL("https://api.deezer.com/search/album?q=$q").readText()).getJSONArray("data")
                withContext(Dispatchers.Main) {
                    songList.clear(); albumList.clear()
                    for (i in 0 until songs.length()) {
                        val o = songs.getJSONObject(i)
                        songList.add(Song(o.getString("id"), o.getString("title"), o.getJSONObject("artist").getString("name"), o.getJSONObject("album").getString("cover_medium"), o.getString("preview")))
                    }
                    for (i in 0 until albums.length()) {
                        val o = albums.getJSONObject(i)
                        albumList.add(Album(o.getString("id"), o.getString("title"), o.getString("cover_medium"), o.getJSONObject("artist").getString("name")))
                    }
                    songAdapter.notifyDataSetChanged(); albumAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {}
        }
    }

    private fun openAlbum(a: Album) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = JSONObject(URL("https://api.deezer.com/album/${a.id}/tracks").readText()).getJSONArray("data")
                withContext(Dispatchers.Main) {
                    songList.clear()
                    for (i in 0 until res.length()) {
                        val o = res.getJSONObject(i)
                        songList.add(Song(o.getString("id"), o.getString("title"), a.artist, a.cover, o.getString("preview")))
                    }
                    songAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {}
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_READY) {
                    val duration = player?.duration?.toInt() ?: 0
                    miniSeekBar.max = duration
                    fullSeekBar?.max = duration
                    updateProgress()
                }
            }
        })
    }

    private fun updateProgress() {
        lifecycleScope.launch {
            while (isActive) {
                player?.let {
                    val pos = it.currentPosition.toInt()
                    miniSeekBar.progress = pos
                    fullSeekBar?.progress = pos
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
        val view = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#050505")); setPadding(60, 60, 60, 60) }

        val btnClose = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_close_clear_cancel); setBackgroundColor(0); setColorFilter(Color.WHITE); setOnClickListener { dialog.dismiss() } }
        val btnMenu = ImageButton(this).apply { 
            setImageResource(android.R.drawable.ic_menu_manage); setBackgroundColor(0); setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(100, 100).apply { addRule(RelativeLayout.ALIGN_PARENT_END) }
            setOnClickListener { showMenu(it) }
        }

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.CENTER_IN_PARENT) } }
        val cover = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(850, 850); load(currentSong?.cover) { transformations(RoundedCornersTransformation(40f)) } }
        val title = TextView(this).apply { text = currentSong?.title; setTextColor(Color.WHITE); textSize = 24f; setPadding(0, 50, 0, 10); typeface = Typeface.DEFAULT_BOLD }
        val artist = TextView(this).apply { text = currentSong?.artist; setTextColor(Color.CYAN); textSize = 18f }
        info.addView(cover); info.addView(title); info.addView(artist)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bottomMargin = 50 } }
        fullSeekBar = SeekBar(this).apply { progressDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN); setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { if(f) player?.seekTo(p.toLong()) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })}
        val btns = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, 40, 0, 0) }
        val bPlay = ImageButton(this).apply { 
            setImageResource(if(player?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            setBackgroundColor(0); setColorFilter(Color.WHITE); scaleX = 2f; scaleY = 2f
            setOnClickListener { if(player?.isPlaying == true) { player?.pause(); setImageResource(android.R.drawable.ic_media_play) } else { player?.play(); setImageResource(android.R.drawable.ic_media_pause) } }
        }
        btns.addView(bPlay); controls.addView(fullSeekBar); controls.addView(btns)

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
        p.menu.add("Ir al Artista"); p.menu.add("Ir al Álbum"); p.menu.add("Info de canción")
        p.show()
    }

    private fun createMiniPlayer(): View {
        miniPlayer = CardView(this).apply { 
            radius = 30f; setCardBackgroundColor(Color.parseColor("#111111")); visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); setMargins(20, 0, 20, 30) }
            setOnClickListener { openFullPlayer() }
        }
        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        miniSeekBar = SeekBar(this).apply { setPadding(0,0,0,0); thumb = null; progressDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(30, 20, 30, 20) }
        val img = ImageView(this).apply { id = 777; layoutParams = LinearLayout.LayoutParams(110, 110) }
        miniTitle = TextView(this).apply { setTextColor(Color.WHITE); setPadding(30, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); maxLines = 1 }
        btnPlayMini = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0); setColorFilter(Color.WHITE)
            setOnClickListener { if(player?.isPlaying == true) player?.pause() else player?.play() }
        }
        row.addView(img); row.addView(miniTitle); row.addView(btnPlayMini); lay.addView(miniSeekBar); lay.addView(row)
        miniPlayer.addView(lay); return miniPlayer
    }

    private fun createLabel(t: String) = TextView(this).apply { text = t; setTextColor(Color.WHITE); textSize = 20f; setPadding(0, 40, 0, 20); typeface = Typeface.DEFAULT_BOLD }

    inner class SongAdapter(val list: List<Song>, val onClick: (Song) -> Unit) : RecyclerView.Adapter<SongAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LinearLayout(p.context).apply { setPadding(0, 20, 0, 20); gravity = Gravity.CENTER_VERTICAL }.apply { 
            addView(ImageView(p.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(120, 120) })
            addView(TextView(p.context).apply { id = 2; setTextColor(Color.WHITE); setPadding(30,0,0,0) })
        })
        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]; h.itemView.findViewById<ImageView>(1).load(s.cover) { transformations(RoundedCornersTransformation(15f)) }
            h.itemView.findViewById<TextView>(2).text = s.title; h.itemView.setOnClickListener { onClick(s) }
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
