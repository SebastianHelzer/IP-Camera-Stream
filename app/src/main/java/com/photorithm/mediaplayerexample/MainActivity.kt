package com.photorithm.mediaplayerexample

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    // Made from following this guide:
    // https://www.sitepoint.com/a-step-by-step-guide-to-building-an-android-audio-player-app/
    private var player: MediaPlayerService? = null
    var serviceBound = false
    var audioList: ArrayList<Audio>? = null

    @SuppressLint("Recycle")
    private fun loadAudio() {


        val contentResolver = contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        val cursor = contentResolver.query(uri, null, selection, null, sortOrder)

        if (cursor != null && cursor.count > 0) {
            audioList = ArrayList()
            while (cursor.moveToNext()) {
                val data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))

                // Save to audioList
                audioList!!.add(Audio(data, title, album, artist))
            }
        }
        cursor!!.close()
    }

    //Binding this Client to the AudioPlayer Service
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as MediaPlayerService.LocalBinder
            player = binder.service
            serviceBound = true

            Toast.makeText(this@MainActivity, "Service Bound", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
        }
    }

    private fun playAudio(audioIndex: Int) {
        //Check is service is active
        if (!serviceBound) {
            //Store Serializable audioList to SharedPreferences
            if(audioList != null) {
                val storage = StorageUtil(applicationContext)
                storage.storeAudio(audioList!!)
                storage.storeAudioIndex(audioIndex)
            }
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            //Store the new audioIndex to SharedPreferences
            val storage = StorageUtil(applicationContext)
            storage.storeAudioIndex(audioIndex)

            //Service is active
            //Send a broadcast to the service -> PLAY_NEW_AUDIO
            sendBroadcast(Intent(Broadcast_PLAY_NEW_AUDIO))
        }
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putBoolean("ServiceState", serviceBound)
        super.onSaveInstanceState(savedInstanceState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState.getBoolean("ServiceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            //service is active
            player?.stopSelf()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkIfCanLoadAudio()

        recycler_view.layoutManager = GridLayoutManager(this,1)
        recycler_view.adapter = object : RecyclerView.Adapter<MyViewHolder>(){
            override fun onCreateViewHolder(p0: ViewGroup, p1: Int): MyViewHolder {
                return MyViewHolder(layoutInflater.inflate(R.layout.my_view_holder,p0,false))
            }

            override fun getItemCount(): Int = if(audioList != null) audioList!!.size else 0

            override fun onBindViewHolder(p0: MyViewHolder, p1: Int) {
                p0.albumView.text = audioList!![p1].album
                p0.artistView.text = audioList!![p1].data
                p0.titleView.text = audioList!![p1].title
                p0.rootView.setOnClickListener { playAudio(p1) }
            }
        }
    }

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rootView = itemView
        val titleView = itemView.findViewById<TextView>(R.id.title_text)!!
        val albumView = itemView.findViewById<TextView>(R.id.album_title)!!
        val artistView = itemView.findViewById<TextView>(R.id.artist_text)!!
    }

    private fun checkIfCanLoadAudio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Should we show an explanation?
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // Explain to the user why we need to read the contacts
                }

                requestPermissions(
                    arrayOf( Manifest.permission.READ_EXTERNAL_STORAGE),
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
                )
            } else {
                loadAudio()
            }
        } else {
            loadAudio()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if(permissions.isNotEmpty()){
            if(requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE){
                loadAudio()
            }
        }
    }

    companion object {
        const val Broadcast_PLAY_NEW_AUDIO = "com.photorithm.mediaplayerexample.PlayNewAudio"
        private const val MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 50000
    }
}
