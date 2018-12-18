package com.photorithm.mediaplayerexample

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/*
 * Created by Sebastian Helzer on 12/18/2018.
 */
class StorageUtil(private val context: Context) {

    private var preferences: SharedPreferences? = null

    fun storeAudio(arrayList: ArrayList<Audio>) {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        preferences!!.edit().putString("audioArrayList", Gson().toJson(arrayList)).apply()
    }

    fun loadAudio(): ArrayList<Audio> {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val json = preferences!!.getString("audioArrayList", null)
        val type = object : TypeToken<ArrayList<Audio>>() { }.type
        return Gson().fromJson(json, type)
    }

    fun storeAudioIndex(index: Int) {
        Log.d("StorageUtil", "Store index: $index")
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        preferences!!.edit().putInt("audioIndex", index).apply()
    }

    fun loadAudioIndex(): Int {
        preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        Log.d("StorageUtil", "Load index: ${preferences!!.getInt("audioIndex", -1)}")
        return preferences!!.getInt("audioIndex", -1)//return -1 if no data found
    }

    fun clearCachedAudioPlaylist() {
        context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    companion object {
        private const val STORAGE = "com.photorithm.mediaplayerexample.STORAGE"
    }
}