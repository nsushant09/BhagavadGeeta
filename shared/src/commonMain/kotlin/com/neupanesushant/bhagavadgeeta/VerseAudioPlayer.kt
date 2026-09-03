package com.neupanesushant.bhagavadgeeta

interface VerseAudioPlayer {
    fun play(id: String, bytes: ByteArray, onFinished: () -> Unit)
    fun stop()
    fun release()
}

class NoOpVerseAudioPlayer : VerseAudioPlayer {
    override fun play(id: String, bytes: ByteArray, onFinished: () -> Unit) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}
