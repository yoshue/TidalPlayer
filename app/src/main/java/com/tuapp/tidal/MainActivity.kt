package com.tuapp.tidal

import android.app.Dialog
import android.graphics.*
import android.media.audiofx.DynamicsProcessing
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import coil.transform.BlurTransformation
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

// --- MODELOS DE DATOS ---
data class Song(val id: String, val title: String, val artist: String, val artistId: String, val albumId: String, val cover: String, val preview: String, val duration: Int = 0)
data class Album(val id: String, val title: String, val cover: String, val artist: String)
data class LyricLine(val timeMs: Long, val text: String)

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
    private var timeElapsedTxt: TextView? = null
    private var timeTotalTxt: TextView? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // HEADER
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 40)
        }
        val titleMain = TextView(this).apply { text = "Explorar v3"; setTextColor(Color.WHITE); textSize = 34f; typeface = Typeface.DEFAULT_BOLD }
        
        val searchBox = RelativeLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#121212")); cornerRadius = 30f }
            setPadding(40, 15, 40, 15)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40 }
        }
        val input = EditText(this).apply {
            id = View.generateViewId(); hint = "Megadeth..."; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH; inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, 99) }
        }
        val searchIcon = ImageView(this).apply {
            id = 99; setImageResource(android.R.drawable.ic_menu_search); setColorFilter(Color.GRAY)
            layoutParams = RelativeLayout.LayoutParams(70, 70).apply { addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL) }
            setOnClickListener { searchEverything(input.text.toString()) }
        }
        searchBox.addView(input); searchBox.addView(searchIcon); header.addView(titleMain); header.addView(searchBox)

        // CONTENT
        val scroll = NestedScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, header.id) }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 0, 50, 400) }
        
        content.addView(createLabel("Álbumes Destacados"))
        val rvAlbums = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false) }
        albumAdapter = AlbumAdapter(albumList) { openAlbumDetail(it) }
        rvAlbums.adapter = albumAdapter
        content.addView(rvAlbums)

        content.addView(createLabel("Canciones"))
        val rvSongs = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity); isNestedScrollingEnabled = false }
        songAdapter = SongAdapter(songList) { playSong(it) }
        rvSongs.adapter = songAdapter
        content.addView(rvSongs)

        scroll.addView(content)
        root.addView(header); root.addView(scroll); root.addView(createMiniPlayer())
        setContentView(root)

        setupPlayer()
        searchEverything("Megadeth")
    }

    private fun createLabel(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 22f; setPadding(0, 50, 0, 20); typeface = Typeface.DEFAULT_BOLD
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val dur = player?.duration?.toInt() ?: 0
                    miniSeekBar.max = dur
                    fullSeekBar?.max = dur
                    timeTotalTxt?.text = formatTime(dur / 1000L)
                    if (dynamicsProcessing == null) initAudioEngine(player!!.audioSessionId)
                    updateProgressTask()
                }
            }
        })
    }

    private fun initAudioEngine(id: Int) {
        try {
            val config = DynamicsProcessing.Config.Builder(0, 1, true, DynamicsProcessing.Eq(true, true, 10), false, null, false, null, true, DynamicsProcessing.Limiter(true, true, 0, 1f, 2f, 1f, 10f, 0f)).build()
            dynamicsProcessing = DynamicsProcessing(0, id, config).apply { enabled = true }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateProgressTask() {
        lifecycleScope.launch {
            while (isActive) {
                player?.let {
                    val p = it.currentPosition.toInt()
                    miniSeekBar.progress = p
                    fullSeekBar?.progress = p
                    timeElapsedTxt?.text = formatTime(p / 1000L)
                }
                delay(1000)
            }
        }
    }

    private fun formatTime(sec: Long) = "${sec / 60}:${String.format("%02d", sec % 60)}"

    private fun playSong(s: Song) {
        currentSong = s
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = s.title
        findViewById<ImageView>(777).load(s.cover)
        player?.setMediaItem(MediaItem.fromUri(s.preview))
        player?.prepare()
        player?.play()
        btnPlayMini.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun searchEverything(q: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sData = URL("https://api.deezer.com/search?q=$q").readText()
                val aData = URL("https://api.deezer.com/search/album?q=$q").readText()
                val sObj = JSONObject(sData).getJSONArray("data")
                val aObj = JSONObject(aData).getJSONArray("data")
                
                withContext(Dispatchers.Main) {
                    songList.clear(); albumList.clear()
                    for(i in 0 until sObj.length()) {
                        val o = sObj.getJSONObject(i)
                        songList.add(Song(o.getString("id"), o.getString("title"), o.getJSONObject("artist").getString("name"), o.getJSONObject("artist").getString("id"), o.getJSONObject("album").getString("id"), o.getJSONObject("album").getString("cover_xl"), o.getString("preview"), o.getInt("duration")))
                    }
                    for(i in 0 until aObj.length()) {
                        val o = aObj.getJSONObject(i)
                        albumList.add(Album(o.getString("id"), o.getString("title"), o.getString("cover_xl"), o.getJSONObject("artist").getString("name")))
                    }
                    songAdapter.notifyDataSetChanged()
                    albumAdapter.notifyDataSetChanged()
                }
            } catch(e: Exception) { e.printStackTrace() }
        }
    }

    private fun createMiniPlayer(): View {
        miniPlayer = CardView(this).apply {
            radius = 40f; setCardBackgroundColor(Color.parseColor("#151515")); visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); setMargins(30, 0, 30, 40) }
            setOnClickListener { openFullPlayer() }
        }
        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        miniSeekBar = SeekBar(this).apply { setPadding(0,0,0,0); thumb = null; progressDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(40, 20, 40, 20) }
        val img = ImageView(this).apply { id = 777; layoutParams = LinearLayout.LayoutParams(110, 110) }
        miniTitle = TextView(this).apply { setTextColor(Color.WHITE); setPadding(30, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); maxLines = 1 }
        btnPlayMini = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0); setColorFilter(Color.WHITE); setOnClickListener { if(player?.isPlaying == true) player?.pause() else player?.play() } }
        row.addView(img); row.addView(miniTitle); row.addView(btnPlayMini); lay.addView(miniSeekBar); lay.addView(row); miniPlayer.addView(lay)
        return miniPlayer
    }

    private fun openFullPlayer() {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val v = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }
        
        val bg = ImageView(this).apply { layoutParams = RelativeLayout.LayoutParams(-1,-1); alpha = 0.3f; scaleType = ImageView.ScaleType.CENTER_CROP; load(currentSong?.cover) { transformations(BlurTransformation(this@MainActivity, 25f)) } }
        v.addView(bg)

        val btnClose = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_close_clear_cancel); setBackgroundColor(0); setColorFilter(Color.WHITE); setOnClickListener { d.dismiss() } }
        val btnMenu = ImageButton(this).apply { 
            setImageResource(android.R.drawable.ic_menu_preferences); setBackgroundColor(0); setColorFilter(Color.GRAY)
            layoutParams = RelativeLayout.LayoutParams(120, 120).apply { addRule(RelativeLayout.ALIGN_PARENT_END) }
            setOnClickListener { showPopMenu(it) }
        }
        
        val flipContainer = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(850, 850).apply { addRule(RelativeLayout.CENTER_IN_PARENT); bottomMargin = 300 }
        }
        val front = CardView(this).apply { radius = 50f; addView(ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; load(currentSong?.cover) }) }
        val back = CardView(this).apply { 
            radius = 50f; visibility = View.GONE; setCardBackgroundColor(Color.BLACK)
            val txt = TextView(context).apply { text = "TRACKLIST\n\n1. ${currentSong?.title}\n..."; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
            addView(txt)
        }
        flipContainer.addView(back); flipContainer.addView(front)
        flipContainer.setOnClickListener {
            val (outV, inV) = if (front.visibility == View.VISIBLE) front to back else back to front
            outV.animate().rotationY(90f).setDuration(300).withEndAction { outV.visibility = View.GONE; inV.rotationY = -90f; inV.visibility = View.VISIBLE; inV.animate().rotationY(0f).setDuration(300).start() }.start()
        }

        val info = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL; layoutParams = RelativeLayout.LayoutParams(-1,-2).apply { addRule(RelativeLayout.BELOW, flipContainer.id); topMargin = 50; setMargins(60,0,60,0) }
            val t = TextView(context).apply { text = currentSong?.title; setTextColor(Color.WHITE); textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
            val a = TextView(context).apply { text = currentSong?.artist; setTextColor(Color.CYAN); textSize = 18f }
            addView(t); addView(a)
        }

        val controls = RelativeLayout(this).apply { layoutParams = RelativeLayout.LayoutParams(-1,-2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bottomMargin = 100; setMargins(60,0,60,0) } }
        fullSeekBar = SeekBar(this).apply { id = View.generateViewId(); progressDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN) }
        timeElapsedTxt = TextView(this).apply { text = "0:00"; setTextColor(Color.WHITE); layoutParams = RelativeLayout.LayoutParams(-2,-2).apply { addRule(RelativeLayout.BELOW, fullSeekBar!!.id) } }
        timeTotalTxt = TextView(this).apply { text = "0:00"; setTextColor(Color.WHITE); layoutParams = RelativeLayout.LayoutParams(-2,-2).apply { addRule(RelativeLayout.BELOW, fullSeekBar!!.id); addRule(RelativeLayout.ALIGN_PARENT_END) } }
        val btnPlay = ImageButton(this).apply { 
            setImageResource(android.R.drawable.ic_media_play); layoutParams = RelativeLayout.LayoutParams(200, 200).apply { addRule(RelativeLayout.BELOW, timeElapsedTxt!!.id); addRule(RelativeLayout.CENTER_HORIZONTAL); topMargin = 50 }
            setOnClickListener { if(player?.isPlaying == true) player?.pause() else player?.play() }
        }
        controls.addView(fullSeekBar); controls.addView(timeElapsedTxt); controls.addView(timeTotalTxt); controls.addView(btnPlay)
        
        v.addView(btnClose); v.addView(btnMenu); v.addView(flipContainer); v.addView(info); v.addView(controls)
        d.setContentView(v); d.show()
    }

    private fun showPopMenu(v: View) {
        val p = PopupMenu(this, v)
        p.menu.add("Ecualizador").setOnMenuItemClickListener { true }
        p.menu.add("Ir al Artista").setOnMenuItemClickListener { true }
        p.show()
    }

    private fun openAlbumDetail(a: Album) {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(50, 100, 50, 50) }
        v.addView(TextView(this).apply { text = a.title; setTextColor(Color.WHITE); textSize = 24f })
        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity) }
        val list = mutableListOf<Song>()
        val adapter = SongAdapter(list) { playSong(it); d.dismiss() }
        rv.adapter = adapter
        lifecycleScope.launch(Dispatchers.IO) {
            val data = URL("https://api.deezer.com/album/${a.id}/tracks").readText()
            val arr = JSONObject(data).getJSONArray("data")
            for(i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Song(o.getString("id"), o.getString("title"), a.artist, "", a.id, a.cover, o.getString("preview"), o.getInt("duration")))
            }
            withContext(Dispatchers.Main) { adapter.notifyDataSetChanged() }
        }
        v.addView(rv); d.setContentView(v); d.show()
    }

    // --- ADAPTADORES ---
    inner class SongAdapter(val list: List<Song>, val onClick: (Song) -> Unit) : RecyclerView.Adapter<SongAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val root = RelativeLayout(p.context).apply { setPadding(0, 20, 0, 20) }
            val img = ImageView(p.context).apply { id = 10; layoutParams = RelativeLayout.LayoutParams(120, 120) }
            val txts = LinearLayout(p.context).apply { 
                orientation = LinearLayout.VERTICAL; setPadding(30, 0, 0, 0)
                layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.RIGHT_OF, 10) }
                addView(TextView(p.context).apply { id = 11; setTextColor(Color.WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
                addView(TextView(p.context).apply { id = 12; setTextColor(Color.GRAY); textSize = 14f })
            }
            root.addView(img); root.addView(txts)
            return VH(root)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]
            h.itemView.findViewById<ImageView>(10).load(s.cover) { transformations(RoundedCornersTransformation(15f)) }
            h.itemView.findViewById<TextView>(11).text = s.title
            h.itemView.findViewById<TextView>(12).text = s.artist
            h.itemView.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }

    inner class AlbumAdapter(val list: List<Album>, val onClick: (Album) -> Unit) : RecyclerView.Adapter<AlbumAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val root = LinearLayout(p.context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 40, 0) }
            val img = ImageView(p.context).apply { id = 20; layoutParams = LinearLayout.LayoutParams(400, 400) }
            val txt = TextView(p.context).apply { id = 21; setTextColor(Color.WHITE); setPadding(0, 10, 0, 0); maxLines = 1; layoutParams = LinearLayout.LayoutParams(400, -2) }
            root.addView(img); root.addView(txt)
            return VH(root)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val a = list[p]
            h.itemView.findViewById<ImageView>(20).load(a.cover) { transformations(RoundedCornersTransformation(25f)) }
            h.itemView.findViewById<TextView>(21).text = a.title
            h.itemView.setOnClickListener { onClick
