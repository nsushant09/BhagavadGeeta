package com.neupanesushant.bhagavadgeeta

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class AndroidVerseAudioPlayer(context: Context) : VerseAudioPlayer {
    private val audioFile = File(context.cacheDir, "current_verse_recitation.mp3")
    private var player: MediaPlayer? = null

    override fun play(id: String, bytes: ByteArray, onFinished: () -> Unit) {
        stop()
        audioFile.writeBytes(bytes)
        player = MediaPlayer().apply {
            setDataSource(audioFile.absolutePath)
            setOnCompletionListener { onFinished(); stop() }
            setOnErrorListener { _, _, _ -> onFinished(); stop(); true }
            prepare()
            start()
        }
    }

    override fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    override fun release() = stop()
}
