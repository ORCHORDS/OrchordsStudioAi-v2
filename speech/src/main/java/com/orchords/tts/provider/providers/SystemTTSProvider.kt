package com.orchords.tts.provider.providers

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import com.orchords.common.android.appTempFolder
import com.orchords.tts.model.AudioChunk
import com.orchords.tts.model.TTSRequest
import com.orchords.tts.provider.TTSProvider
import com.orchords.tts.provider.TTSProviderSetting
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SystemTTSProvider"

class SystemTTSProvider : TTSProvider<TTSProviderSetting.SystemTTS> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val audioData = suspendCancellableCoroutine<ByteArray> { continuation ->
            // Allocate the holder up-front so the OnInitListener can observe the assigned
            // TextToSpeech even when Android invokes the listener synchronously (which it
            // does on emulators / engines that have already been bound).
            val holder = arrayOfNulls<TextToSpeech>(1)
            val listener = TextToSpeech.OnInitListener { status ->
                val ttsInstance = holder[0]
                if (status == TextToSpeech.SUCCESS && ttsInstance != null) {

                // Set language
                val locale = Locale.getDefault()
                val langResult = ttsInstance.setLanguage(locale)

                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "generateSpeech: Language $locale not supported")
                }

                // Set speech parameters
                ttsInstance.setSpeechRate(providerSetting.speechRate)
                ttsInstance.setPitch(providerSetting.pitch)

                // Create temporary file for audio output using temp directory like OrchordsAIApp
                val tempDir = context.appTempFolder
                val audioFile = File(tempDir, "tts_${System.currentTimeMillis()}.wav")

                val utteranceId = UUID.randomUUID().toString()

                ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.i(TAG, "onStart: TTS engine started!")
                    }

                    override fun onDone(utteranceId: String?) {
                        try {
                            if (audioFile.exists()) {
                                val audioData = audioFile.readBytes()
                                audioFile.delete()

                                if (continuation.isActive) continuation.resume(audioData)
                            } else {
                                if (continuation.isActive) continuation.resumeWithException(
                                    Exception("Failed to generate audio file")
                                )
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        } finally {
                            ttsInstance.shutdown()
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "onError: TTS synthesis failed!")
                        audioFile.delete()
                        if (continuation.isActive) continuation.resumeWithException(
                            Exception("TTS synthesis failed")
                        )
                        ttsInstance.shutdown()
                    }
                })

                val result = ttsInstance.synthesizeToFile(
                    request.text,
                    null,
                    audioFile,
                    utteranceId
                )

                if (result != TextToSpeech.SUCCESS) {
                    if (continuation.isActive) continuation.resumeWithException(
                        Exception("Failed to start TTS synthesis")
                    )
                    ttsInstance.shutdown()
                }

            } else {
                if (continuation.isActive) continuation.resumeWithException(
                    Exception("Failed to initialize TextToSpeech engine")
                )
                ttsInstance?.shutdown()
            }
        }
        // Construct TextToSpeech with the listener and assign the instance into the holder
        // before this statement completes. The Android contract allows the constructor to
        // call the listener synchronously, so the holder must already be observable inside
        // the listener body when init completes.
        holder[0] = TextToSpeech(context, listener)

        continuation.invokeOnCancellation {
            holder[0]?.shutdown()
        }
    }

        emit(
            AudioChunk(
                data = audioData,
                format = com.orchords.tts.model.AudioFormat.WAV,
                isLast = true,
                metadata = mapOf(
                    "provider" to "system",
                    "speechRate" to providerSetting.speechRate.toString(),
                    "pitch" to providerSetting.pitch.toString()
                )
            )
        )
    }
}
