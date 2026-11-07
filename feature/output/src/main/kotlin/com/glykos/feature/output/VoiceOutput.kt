package com.glykos.feature.output

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.format.VoiceMessageFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Speaks announcements through the headphones in es-ES, ducking the music meanwhile.
 *
 * Audio focus: AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK with navigation-guidance attributes, so music
 * apps lower their volume instead of pausing, and focus is released as soon as speech ends.
 * Works with the screen locked as long as the caller runs in a foreground service.
 */
@Singleton
class VoiceOutput @Inject constructor(
    @ApplicationContext private val context: Context,
) : GlucoseOutput {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()

    private val mutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var tts: TextToSpeech? = null

    override suspend fun deliver(announcement: Announcement) {
        speak(VoiceMessageFormatter.format(announcement))
    }

    /** Speaks [text] and suspends until it has been spoken. Serialized: never talks over itself. */
    suspend fun speak(text: String) = mutex.withLock {
        val engine = engine()
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>().also { pending[id] = it }
        // A denied request (e.g. during a phone call) means we must not talk now.
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            pending.remove(id)
            throw OutputUnavailableException("Audio focus denied")
        }
        try {
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                throw OutputUnavailableException("TTS rejected the utterance")
            }
            val ok = withTimeout(SPEAK_TIMEOUT) { done.await() }
            if (!ok) throw OutputUnavailableException("TTS failed while speaking")
        } catch (e: TimeoutCancellationException) {
            engine.stop()
            throw OutputUnavailableException("TTS timed out", e)
        } finally {
            pending.remove(id)
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private suspend fun engine(): TextToSpeech {
        tts?.let { return it }
        val ready = CompletableDeferred<Int>()
        val engine = TextToSpeech(context) { status -> ready.complete(status) }
        val status = try {
            withTimeout(INIT_TIMEOUT) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            engine.shutdown()
            throw OutputUnavailableException("TTS engine did not start", e)
        }
        if (status != TextToSpeech.SUCCESS) {
            engine.shutdown()
            throw OutputUnavailableException("TTS engine failed to start ($status)")
        }
        val lang = engine.setLanguage(SPANISH)
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.shutdown()
            throw OutputUnavailableException("No es-ES voice installed")
        }
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                pending[utteranceId]?.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                pending[utteranceId]?.complete(false)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                pending[utteranceId]?.complete(false)
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                pending[utteranceId]?.complete(false)
            }
        })
        tts = engine
        return engine
    }

    private companion object {
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")
        val INIT_TIMEOUT = 10.seconds
        val SPEAK_TIMEOUT = 30.seconds
    }
}
