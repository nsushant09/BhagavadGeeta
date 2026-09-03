package com.neupanesushant.bhagavadgeeta

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosVerseAudioPlayer : VerseAudioPlayer {
    private var player: AVAudioPlayer? = null

    override fun play(id: String, bytes: ByteArray, onFinished: () -> Unit) {
        stop()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        player = AVAudioPlayer(data = data, error = null).also {
            it.prepareToPlay()
            it.play()
        }
    }

    override fun stop() { player?.stop(); player = null }
    override fun release() = stop()
}
