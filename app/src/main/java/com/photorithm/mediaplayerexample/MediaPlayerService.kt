package com.photorithm.mediaplayerexample

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import veg.mediaplayer.sdk.MediaPlayerConfig
import java.nio.ByteBuffer
import android.support.v4.media.app.NotificationCompat as MediaNotificationCompat


/*
 * Created by Sebastian Helzer on 12/17/2018.
 */
class MediaPlayerService : Service(), AudioManager.OnAudioFocusChangeListener {

    private var audioManager: AudioManager? = null
    private var mediaPlayer: veg.mediaplayer.sdk.MediaPlayer? = null
    private val rtspURL = "rtsp://admin:" + "admin@192.168.0.104/live0.264"
    //Handle incoming phone calls
    private var ongoingCall = false
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null
    //MediaSession
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSession? = null
    private var transportControls: MediaController.TransportControls? = null

    private fun initMediaPlayer() {
        mediaPlayer = veg.mediaplayer.sdk.MediaPlayer(this, false)
        playMedia()
    }

    private fun getConfig(rtspURL: String): MediaPlayerConfig{
        val config = MediaPlayerConfig()
        config.connectionUrl = rtspURL
        config.connectionNetworkProtocol = 1
        config.connectionDetectionTime = 500
        config.connectionBufferingSize = 200 //200
        config.connectionBufferingTime = 100 //100
        config.connectionBufferingType = 1 //1
        config.dataReceiveTimeout = 5000
        config.decodingType = 1
        config.rendererType = 1
        config.synchroEnable = 1 //1
        config.synchroNeedDropVideoFrames = 1
        config.enableColorVideo = 1
        config.aspectRatioMode = 1
        config.numberOfCPUCores = 0
        config.enableAudio = 1
        config.useNotchFilter = 0 //0
        config.volumeBoost = 1
        config.fadeOnChangeFFSpeed = 0
        config.setMode(veg.mediaplayer.sdk.MediaPlayer.PlayerModes.PP_MODE_AUDIO)
        return config
    }

    private fun playMedia() {
        if(mediaPlayer != null) {
            if (mediaPlayer!!.state != veg.mediaplayer.sdk.MediaPlayer.PlayerState.Opened) {
                mediaPlayer!!.Open(getConfig(rtspURL),
                    object : veg.mediaplayer.sdk.MediaPlayer.MediaPlayerCallback {
                        override fun OnReceiveData(p0: ByteBuffer?, p1: Int, p2: Long): Int {
                            Log.d("MainActivity", "OnReceiveData: $p0 $p1 $p2")
                            return 0
                        }

                        override fun Status(p0: Int): Int {
                            Log.d("MainActivity", "Status: $p0")
                            return 0
                        }
                    })
                buildNotification(PlaybackStatus.PLAYING)
            }
        }
    }

    private fun stopMedia() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.state == veg.mediaplayer.sdk.MediaPlayer.PlayerState.Opened) {
                mediaPlayer!!.Close()
            }
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.state != veg.mediaplayer.sdk.MediaPlayer.PlayerState.Paused) {
                mediaPlayer!!.Pause()
            }
        }
    }

    private fun resumeMedia() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.state == veg.mediaplayer.sdk.MediaPlayer.PlayerState.Paused) {
                mediaPlayer!!.Play()
            }
        }
    }

    // Binder given to clients
    private val iBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        return iBinder
    }

    private fun veg.mediaplayer.sdk.MediaPlayer.isPlaying(): Boolean{
        return this.state == veg.mediaplayer.sdk.MediaPlayer.PlayerState.Started
    }

    override fun onAudioFocusChange(focusState: Int) {
        //Invoked when the audio focus of the system is updated.
        when (focusState) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // resume playback
                if (mediaPlayer == null)
                    initMediaPlayer()
                else if (!mediaPlayer!!.isPlaying()) mediaPlayer!!.Play()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if(mediaPlayer != null) {
                    if (mediaPlayer!!.isPlaying()) mediaPlayer!!.Close()
                }
                mediaPlayer = null
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mediaPlayer != null && mediaPlayer!!.isPlaying()) mediaPlayer!!.Pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer != null && mediaPlayer!!.isPlaying()) mediaPlayer!!.setVolumeBoost(0)
        }
    }

    private var mFocusRequest: AudioFocusRequest? = null

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mPlaybackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mPlaybackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            val result = audioManager?.requestAudioFocus(mFocusRequest!!)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager?.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        //Could not gain focus
    }

    @Suppress("DEPRECATION")
    private fun removeAudioFocus(): Boolean =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mFocusRequest != null) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager?.abandonAudioFocusRequest(mFocusRequest!!)
        } else {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager?.abandonAudioFocus(this)
        }

    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener()
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver()
        //Listen for new Audio to play -- BroadcastReceiver
        registerPlayNewAudio()
    }

    //The system calls this method when an activity, requests the service be started
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        //Request audio focus
        if (!requestAudioFocus()) {
            //Could not gain focus
            stopSelf()
        }

        if (mediaSessionManager == null) {
            try {
                initMediaSession()
                initMediaPlayer()
            } catch (e: RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            buildNotification(PlaybackStatus.PLAYING)
        }

        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            stopMedia()
            mediaPlayer?.Close()
        }
        removeAudioFocus()
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }

        removeNotification()

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver)
        unregisterReceiver(playNewAudio)

        //clear cached playlist
        StorageUtil(applicationContext).clearCachedAudioPlaylist()
    }
    //Becoming noisy
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia()
            buildNotification(PlaybackStatus.PAUSED)
        }
    }

    private fun registerBecomingNoisyReceiver() {
        //register after getting audio focus
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, intentFilter)
    }

    //Handle incoming phone calls
    private fun callStateListener() {
        // Get the telephony manager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        //Starting listening for PhoneState changes
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String) {
                when (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> if (mediaPlayer != null) {
                        pauseMedia()
                        ongoingCall = true
                    }
                    TelephonyManager.CALL_STATE_IDLE ->
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false
                                resumeMedia()
                            }
                        }
                }
            }
        }
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager?.listen(
            phoneStateListener,
            PhoneStateListener.LISTEN_CALL_STATE
        )
    }

    private val playNewAudio = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio
            stopMedia()
            initMediaPlayer()
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private fun registerPlayNewAudio() {
        //Register playNewMedia receiver
        val filter = IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO)
        registerReceiver(playNewAudio, filter)
    }

    @Throws(RemoteException::class)
    private fun initMediaSession() {
        if (mediaSessionManager != null) return  //mediaSessionManager exists

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        // Create a new MediaSession
        mediaSession = MediaSession(applicationContext, "AudioPlayer")
        //Get MediaSessions transport controls
        transportControls = mediaSession!!.controller.transportControls
        //set MediaSession -> ready to receive media commands
        mediaSession!!.isActive = true
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            mediaSession!!.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        }

        //Set mediaSession's MetaData
        updateMetaData()

        // Attach Callback to receive MediaSession updates
        mediaSession!!.setCallback(object : MediaSession.Callback() {
            // Implement callbacks
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
                buildNotification(PlaybackStatus.PAUSED)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                skipToNext()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                skipToPrevious()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onStop() {
                super.onStop()
                removeNotification()
                //Stop the service
                stopSelf()
            }

        })
    }

    private fun updateMetaData() {
        val albumArt = BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_launcher_foreground
        ) //replace with medias albumArt
        // Update the current metadata
        mediaSession!!.setMetadata(
            MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                .build()
        )
    }

    private fun skipToNext() {
        stopMedia()
        //reset mediaPlayer
        initMediaPlayer()
    }

    private fun skipToPrevious() {
        stopMedia()
        //reset mediaPlayer
        initMediaPlayer()
    }

    enum class PlaybackStatus {
        PLAYING,
        PAUSED
    }

    private fun buildNotification(playbackStatus: PlaybackStatus) {

        var notificationAction = android.R.drawable.ic_media_pause//needs to be initialized
        var playPauseAction: PendingIntent? = null

        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus === PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause
            //create the pause action
            playPauseAction = playbackAction(1)
        } else if (playbackStatus === PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play
            //create the play action
            playPauseAction = playbackAction(0)
        }

        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_launcher_foreground
        ) //replace with your own image
        val channelID = "Default Channel ID"

        // Create a new Notification
        val notificationBuilder = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this,channelID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }

        val prevAction = getNotificationAction(android.R.drawable.ic_media_previous,"previous",playbackAction(3))
        val playPauseActionBuilder = getNotificationAction(notificationAction,"pause",playPauseAction)
        val nextAction = getNotificationAction(android.R.drawable.ic_media_next,"next",playbackAction(2))

        notificationBuilder
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // Set the Notification style
            .setStyle(Notification.MediaStyle()
                    // Attach our MediaSession token
                    .setMediaSession(mediaSession!!.sessionToken)
                    // Show our playback controls in the compact notification view.
                    .setShowActionsInCompactView(0, 1, 2)
            )
            // Set the large and small icons
            .setLargeIcon(largeIcon)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            // Set Notification content information
            // Add playback actions
            .addAction(prevAction.build())
            .addAction(playPauseActionBuilder.build())
            .addAction(nextAction.build()) as Notification.Builder

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationBuilder.setColor(resources.getColor(R.color.colorAccent,null))
        } else {
            @Suppress("DEPRECATION")
            notificationBuilder.setColor(resources.getColor(R.color.colorAccent))
        }


        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel(channelID,
                "Default",NotificationManager.IMPORTANCE_LOW))
            notificationBuilder.setChannelId(channelID)
        }

        notificationManager.notify(NOTIFICATION_ID,notificationBuilder.build())


    }

    private fun getNotificationAction(drawable: Int, title: CharSequence, action: PendingIntent?): Notification.Action.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Notification.Action.Builder(Icon.createWithResource(this,drawable),title,action)
        } else {
            @Suppress("DEPRECATION")
            Notification.Action.Builder(drawable,title,action)
        }
    }

    private fun removeNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun playbackAction(actionNumber: Int) : PendingIntent? {
        val playbackAction = Intent(this, MediaPlayerService::class.java)
        playbackAction.action = when (actionNumber) {
            0 -> ACTION_PLAY
            1 -> ACTION_PAUSE
            2 -> ACTION_NEXT
            3 -> ACTION_PREVIOUS
            else -> ACTION_STOP
        }
        return PendingIntent.getService(this, actionNumber, playbackAction, 0)
    }

    private fun handleIncomingActions(playbackAction: Intent?) {
        if (playbackAction?.action == null) return
        when (playbackAction.action!!) {
            ACTION_PLAY -> transportControls!!.play()
            ACTION_PAUSE -> transportControls!!.pause()
            ACTION_NEXT -> transportControls!!.skipToNext()
            ACTION_PREVIOUS -> transportControls!!.skipToPrevious()
            ACTION_STOP -> transportControls!!.stop()
        }
    }

    companion object {
        // Media session controls
        private const val ACTION_PLAY = "com.photorithm.mediaplayerexample.ACTION_PLAY"
        private const val ACTION_PAUSE = "com.photorithm.mediaplayerexample.ACTION_PAUSE"
        private const val ACTION_PREVIOUS = "com.photorithm.mediaplayerexample.ACTION_PREVIOUS"
        private const val ACTION_NEXT = "com.photorithm.mediaplayerexample.ACTION_NEXT"
        private const val ACTION_STOP = "com.photorithm.mediaplayerexample.ACTION_STOP"
        //AudioPlayer notification ID
        private const val NOTIFICATION_ID = 101
    }

}
