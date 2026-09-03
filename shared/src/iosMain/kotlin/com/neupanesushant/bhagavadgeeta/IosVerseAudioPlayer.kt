package com.neupanesushant.bhagavadgeeta

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.Foundation.NSData
import platform.Foundation.create
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosVerseAudioPlayer : VerseAudioPlayer {
    private var player: AVAudioPlayer? = null
    private var delegate: Delegate? = null

    private class Delegate(private val onFinished: () -> Unit) : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            CoroutineScope(Dispatchers.Main).launch {
                onFinished()
            }
        }
    }

    override fun play(id: String, bytes: ByteArray, onFinished: () -> Unit) {
        stop()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        delegate = Delegate(onFinished)
        player = AVAudioPlayer(data = data, error = null).also {
            it.delegate = delegate
            it.prepareToPlay()
            it.play()
        }
    }

    override fun stop() {
        player?.stop()
        player = null
        delegate = null
    }

    override fun release() = stop()
}
