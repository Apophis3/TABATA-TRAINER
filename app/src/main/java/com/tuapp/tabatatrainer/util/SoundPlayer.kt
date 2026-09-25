package com.tuapp.tabatatrainer.util

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.tuapp.tabatatrainer.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class Sound {
    BEEP,              // beep.mp3 - ritmo durante WORK
    COUNTDOWN_BEEP,    // countdown.mp3 - 3,2,1 segundos
    PISTOL,            // pistol.mp3 - inicio WORK 1 (disparo de salida)
    LETS_GO,           // lets_go.mp3 - inicio WORK 2+ (motivación)
    STOP_REST,         // stop_rest.mp3 - inicio REST
    START_WARMUP,      // start_warmup.mp3 - inicio calentamiento
    FINISH             // finish_session.wav - fin de sesión
}

@Singleton
class SoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SoundPlayer"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    
    fun playSound(sound: Sound) {
        try {
            // Liberar el MediaPlayer anterior si existe
            mediaPlayer?.release()
            
            val resourceId = when (sound) {
                Sound.BEEP -> R.raw.beep
                Sound.COUNTDOWN_BEEP -> R.raw.countdown
                Sound.PISTOL -> R.raw.pistol
                Sound.LETS_GO -> R.raw.lets_go
                Sound.STOP_REST -> R.raw.stop_rest
                Sound.START_WARMUP -> R.raw.start_warmup
                Sound.FINISH -> R.raw.finish_session
            }
            
            mediaPlayer = MediaPlayer.create(context, resourceId)?.apply {
                setOnCompletionListener { mp ->
                    mp.release()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "Error reproduciendo sonido: what=$what, extra=$extra")
                    mp.release()
                    true
                }
                start()
            }
            
            Log.d(TAG, "Reproduciendo sonido: $sound")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir sonido $sound: ${e.message}")
        }
    }
    
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
