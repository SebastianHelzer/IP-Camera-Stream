package com.photorithm.mediaplayerexample

import android.app.Service
import android.media.MediaPlayer
import android.content.Intent
import android.os.IBinder
import android.media.AudioManager
import android.os.Binder
import android.util.Log
import java.io.IOException


/*
 * Created by Sebastian Helzer on 12/17/2018.
 */
class MediaPlayerService : Service(), MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener,
    MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
    MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null
    //path to the audio file
    private val mediaFile: String? = null

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        //Set up MediaPlayer event listeners
        mediaPlayer?.setOnCompletionListener(this)
        mediaPlayer?.setOnErrorListener(this)
        mediaPlayer?.setOnPreparedListener(this)
        mediaPlayer?.setOnBufferingUpdateListener(this)
        mediaPlayer?.setOnSeekCompleteListener(this)
        mediaPlayer?.setOnInfoListener(this)
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer?.reset()

        mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
        try {
            // Set the data source to the mediaFile location
            mediaPlayer?.setDataSource(mediaFile)
        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }

        mediaPlayer?.prepareAsync()
    }

    //Used to pause/resume MediaPlayer
    private var resumePosition: Int = 0

    private fun playMedia() {
        if(mediaPlayer != null) {
            if (!mediaPlayer!!.isPlaying) {
                mediaPlayer!!.start()
            }
        }
    }

    private fun stopMedia() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.stop()
            }
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.pause()
                resumePosition = mediaPlayer!!.getCurrentPosition()
            }
        }
    }

    private fun resumeMedia() {
        if (mediaPlayer != null) {
            if (!mediaPlayer!!.isPlaying) {
                mediaPlayer!!.seekTo(resumePosition)
                mediaPlayer!!.start()
            }
        }
    }

    // Binder given to clients
    private val iBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        return iBinder
    }

    override fun onBufferingUpdate(mp: MediaPlayer, percent: Int) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    override fun onCompletion(mp: MediaPlayer) {
        //Invoked when playback of a media source has completed.
        stopMedia()
        //stop the service
        stopSelf()
    }

    //Handle errors
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        //Invoked when there has been an error during an asynchronous operation
        when (what) {
            MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> Log.d(
                "MediaPlayer Error",
                "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $extra"
            )
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED $extra")
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN $extra")
        }
        return false
    }

    override fun onInfo(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        //Invoked to communicate some info.
        return false
    }

    override fun onPrepared(mp: MediaPlayer) {
        //Invoked when the media source is ready for playback.
        playMedia()
    }

    override fun onSeekComplete(mp: MediaPlayer) {
        //Invoked indicating the completion of a seek operation.
    }

    override fun onAudioFocusChange(focusChange: Int) {
        //Invoked when the audio focus of the system is updated.
    }

    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

}
