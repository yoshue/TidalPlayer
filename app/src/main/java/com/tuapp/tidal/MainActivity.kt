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
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import kotlin.math.abs

data class Song(val id: String, val title: String, val artist: String, val artistId: String, val albumId: String, val cover: String, val preview: String, val duration: Int = 0)
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
    private var currentIndex: Int = -1
    
    private var fullSeekBar: SeekBar? = null
    private var timeElapsedTxt: TextView? = null
    private var timeTotalTxt: TextView? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 40)
        }
        val titleMain = TextView(this).apply { text = "Explorar"; setTextColor(Color.WHITE); textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
        
        val searchBox = RelativeLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#1A1A1A")); cornerRadius = 25f }
            setPadding(40, 10, 40, 10)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 30 }
        }
        val input = EditText(this).apply {
            id = View.generateViewId(); hint = "Buscar..."; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH; inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, 99) }
        }
        val searchIcon = ImageView(this).apply {
            id = 99; setImageResource(android.R.drawable.ic_menu_search); setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(60, 60).apply { addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL) }
            setOnClickListener { searchEverything(input.text.toString()) }
        }
        searchBox.addView(input); searchBox.addView(searchIcon); header.addView(titleMain); header.addView(searchBox)

        val scroll = NestedScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, header.id) }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 20, 50, 400) }
        
        content.addView(createLabel("Álbumes"))
        val rvAlbums = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false) }
        albumAdapter = AlbumAdapter(albumList) { openAlbumDetail(it) }
        rvAlbums.adapter = albumAdapter
        content.addView(rvAlbums)

        content.addView(createLabel("Canciones"))
        val rvSongs = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity); isNestedScrollingEnabled = false }
        songAdapter = SongAdapter(songList) { s -> 
            currentIndex = songList.indexOf(s)
            playSong(s) 
        }
        rvSongs.adapter = songAdapter
        content.addView(rvSongs)

        scroll.addView(content)
        root.addView(header); root.addView(scroll); root.addView(createMiniPlayer())
        setContentView(root)

        setupPlayer()
        searchEverything("Megadeth")
    }

    private fun createLabel(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 20f; setPadding(0, 40, 0, 20); typeface = Typeface.DEFAULT_BOLD
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val dur = player?.duration?.toInt() ?: 0
                    miniSeekBar.max = dur
                    fullSeekBar?.max = dur
                    timeTotalTxt?.text = formatTime(dur.toLong() / 1000L)
                    if (dynamicsProcessing == null) initAudioEngine(player!!.audioSessionId)
                    updateProgressTask()
                }
            }
        })
    }

    private fun initAudioEngine(sessionId: Int) {
        try {
            val config = DynamicsProcessing.Config.Builder(0, 1, true, 10, false, 0, false, 0, true).build()
            dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply { enabled = true }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateProgressTask() {
        lifecycleScope.launch {
            while (isActive) {
                player?.let {
                    val p = it.currentPosition.toInt()
                    miniSeekBar.progress = p
                    fullSeekBar?.progress = p
                    timeElapsedTxt?.text = formatTime(p.toLong() / 1000L)
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

    private fun skipNext() {
        if (currentIndex < songList.size - 1) {
            currentIndex++
            playSong(songList[currentIndex])
        }
    }

    private fun skipPrev() {
        if (currentIndex > 0) {
            currentIndex--
            playSong(songList[currentIndex])
        }
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
            radius = 30f; setCardBackgroundColor(Color.parseColor("#121212")); visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); setMargins(25, 0, 25, 30) }
            setOnClickListener { openFullPlayer() }
        }
        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        miniSeekBar = SeekBar(this).apply { setPadding(0,0,0,0); thumb = null; progressDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(30, 15, 30, 15) }
        val img = ImageView(this).apply { id = 777; layoutParams = LinearLayout.LayoutParams(100, 100) }
        miniTitle = TextView(this).apply { setTextColor(Color.WHITE); setPadding(25, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); maxLines = 1 }
        btnPlayMini = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0); setColorFilter(Color.WHITE); setOnClickListener { if(player?.isPlaying == true) player?.pause() else player?.play() } }
        row.addView(img); row.addView(miniTitle); row.addView(btnPlayMini); lay.addView(miniSeekBar); lay.addView(row); miniPlayer.addView(lay)
        return miniPlayer
    }

    private fun openFullPlayer() {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val v = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }
        
        val bg = ImageView(this).apply { layoutParams = RelativeLayout.LayoutParams(-1,-1); alpha = 0.4f; scaleType = ImageView.ScaleType.CENTER_CROP; load(currentSong?.cover) }
        v.addView(bg)

        val btnClose = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_close_clear_cancel); setBackgroundColor(0); setColorFilter(Color.WHITE); setOnClickListener { d.dismiss() } }
        val btnMenu = ImageButton(this).apply { 
            setImageResource(android.R.drawable.ic_menu_preferences); setBackgroundColor(0); setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(120, 120).apply { addRule(RelativeLayout.ALIGN_PARENT_END); setMargins(20, 20, 20, 0) }
            setOnClickListener { showPopMenu(it) }
        }
        v.addView(btnClose); v.addView(btnMenu)
        
        val flipContainer = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(850, 850).apply { addRule(RelativeLayout.CENTER_IN_PARENT); bottomMargin = 280 }
        }
        val front = CardView(this).apply { radius = 40f; addView(ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; load(currentSong?.cover) }) }
        val back = CardView(this).apply { radius = 40f; visibility = View.GONE; setCardBackgroundColor(Color.parseColor("#121212")) }
        
        flipContainer.addView(back); flipContainer.addView(front)

        // --- GESTOS DE SLIDE (SWIPE) ---
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val diffX = e2.x - (e1?.x ?: 0f)
                if (abs(diffX) > 100) {
                    if (diffX > 0) skipPrev() else skipNext()
                    // Actualizar UI del diálogo
                    front.findViewById<ImageView>(0)?.load(currentSong?.cover)
                    return true
                }
                return false
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val (outV, inV) = if (front.visibility == View.VISIBLE) front to back else back to front
                outV.animate().rotationY(90f).setDuration(200).withEndAction { outV.visibility = View.GONE; inV.rotationY = -90f; inV.visibility = View.VISIBLE; inV.animate().rotationY(0f).setDuration(200).start() }.start()
                return true
            }
        })
        flipContainer.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        val info = LinearLayout(this).apply { 
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL; layoutParams = RelativeLayout.LayoutParams(-1,-2).apply { addRule(RelativeLayout.BELOW, flipContainer.id); topMargin = 40; setMargins(70,0,70,0) }
            addView(TextView(context).apply { id = 101; text = currentSong?.title; setTextColor(Color.WHITE); textSize = 24f; typeface = Typeface.DEFAULT_BOLD; maxLines = 1 })
            addView(TextView(context).apply { id = 102; text = currentSong?.artist; setTextColor(Color.CYAN); textSize = 17f })
        }

        // --- CONTROLES CON BORNES (ATRÁS/ADELANTE) ---
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bottomMargin = 80; setMargins(70,0,70,0) }
        }

        val btnPrev = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_previous); setBackgroundColor(0); setColorFilter(Color.WHITE); setPadding(30,30,30,30); setOnClickListener { skipPrev() } }
        val btnPlay = ImageButton(this).apply { 
            setImageResource(if(player?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            layoutParams = LinearLayout.LayoutParams(180, 180).apply { setMargins(40,0,40,0) }
            setBackgroundColor(0); setColorFilter(Color.WHITE); scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { if(player?.isPlaying == true) { player?.pause(); setImageResource(android.R.drawable.ic_media_play) } else { player?.play(); setImageResource(android.R.drawable.ic_media_pause) } }
        }
        val btnNext = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_next); setBackgroundColor(0); setColorFilter(Color.WHITE); setPadding(30,30,30,30); setOnClickListener { skipNext() } }

        controls.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        controls.addView(btnPrev); controls.addView(btnPlay); controls.addView(btnNext)
        controls.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

        val seekLayout = RelativeLayout(this).apply { layoutParams = RelativeLayout.LayoutParams(-1,-2).apply { addRule(RelativeLayout.ABOVE, controls.id); bottomMargin = 40; setMargins(70,0,70,0) } }
        fullSeekBar = SeekBar(this).apply { id = View.generateViewId(); progressDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN) }
        timeElapsedTxt = TextView(this).apply { text = "0:00"; setTextColor(Color.WHITE); layoutParams = RelativeLayout.LayoutParams(-2,-2).apply { addRule(RelativeLayout.BELOW, fullSeekBar!!.id) } }
        timeTotalTxt = TextView(this).apply { text = "0:00"; setTextColor(Color.WHITE); layoutParams = RelativeLayout.LayoutParams(-2,-2).apply { addRule(RelativeLayout.BELOW, fullSeekBar!!.id); addRule(RelativeLayout.ALIGN_PARENT_END) } }
        seekLayout.addView(fullSeekBar); seekLayout.addView(timeElapsedTxt); seekLayout.addView(timeTotalTxt)
        
        v.addView(flipContainer); v.addView(info); v.addView(seekLayout); v.addView(controls)
        d.setContentView(v); d.show()

        // Listener para actualizar info cuando cambia la canción (por gestos o botones)
        player?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                v.findViewById<TextView>(101)?.text = currentSong?.title
                v.findViewById<TextView>(102)?.text = currentSong?.artist
                front.findViewById<ImageView>(0)?.load(currentSong?.cover)
                bg.load(currentSong?.cover)
            }
        })
    }

    private fun showPopMenu(v: View) {
        val p = PopupMenu(this, v)
        p.menu.add("Ecualizador").setOnMenuItemClickListener { true }
        p.menu.add("Letra").setOnMenuItemClickListener { true }
        p.show()
    }

    private fun openAlbumDetail(a: Album) {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(50, 100, 50, 50) }
        v.addView(TextView(this).apply { text = a.title; setTextColor(Color.WHITE); textSize = 24f; setPadding(0,0,0,40) })
        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity) }
        val list = mutableListOf<Song>()
        val adapter = SongAdapter(list) { s -> 
            currentIndex = songList.indexOf(s)
            playSong(s)
            d.dismiss() 
        }
        rv.adapter = adapter
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = URL("https://api.deezer.com/album/${a.id}/tracks").readText()
                val arr = JSONObject(data).getJSONArray("data")
                for(i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(Song(o.getString("id"), o.getString("title"), a.artist, "", a.id, a.cover, o.getString("preview"), o.getInt("duration")))
                }
                withContext(Dispatchers.Main) { adapter.notifyDataSetChanged() }
            } catch(e: Exception) { }
        }
        v.addView(rv); d.setContentView(v); d.show()
    }

    inner class SongAdapter(val list: List<Song>, val onClick: (Song) -> Unit) : RecyclerView.Adapter<SongAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val root = RelativeLayout(p.context).apply { setPadding(0, 20, 0, 20) }
            val img = ImageView(p.context).apply { id = 10; layoutParams = RelativeLayout.LayoutParams(110, 110) }
            val txts = LinearLayout(p.context).apply { 
                orientation = LinearLayout.VERTICAL; setPadding(25, 0, 0, 0)
                layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.RIGHT_OF, 10) }
                addView(TextView(p.context).apply { id = 11; setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; maxLines = 1 })
                addView(TextView(p.context).apply { id = 12; setTextColor(Color.GRAY); textSize = 13f })
            }
            root.addView(img); root.addView(txts)
            return VH(root)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]
            h.itemView.findViewById<ImageView>(10).load(s.cover) { transformations(RoundedCornersTransformation(12f)) }
            h.itemView.findViewById<TextView>(11).text = s.title
            h.itemView.findViewById<TextView>(12).text = s.artist
            h.itemView.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }

    inner class AlbumAdapter(val list: List<Album>,
