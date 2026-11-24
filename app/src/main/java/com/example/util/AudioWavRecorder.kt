package com.example.util

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.abs

class AudioWavRecorder {
    private var recordingJob: Job? = null
    private var pcmBuffer = ByteArrayOutputStream()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, onError: (String) -> Unit) {
        stopInternal()
        pcmBuffer = ByteArrayOutputStream()
        _amplitude.value = 0f
        _elapsedSeconds.value = 0

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) {
            onError("የመሳሪያው ማይክሮፎን (AudioRecord) ማስጀመር አልተቻለም።")
            return
        }

        val bufferSize = maxOf(minBufSize * 2, 4096)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (se: SecurityException) {
            onError("የማይክሮፎን ፍቃድ (RECORD_AUDIO) አልተሰጠም።")
            return
        } catch (e: Exception) {
            onError("ማይክሮፎን መክፈት አልተቻለም: ${e.localizedMessage ?: ""}")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError("የማይክሮፎን ሃርድዌር ዝግጁ አይደለም።")
            return
        }

        recordingJob = scope.launch(Dispatchers.IO) {
            val readBuf = ByteArray(bufferSize)
            val startedMs = System.currentTimeMillis()
            var lastMeterEmitMs = 0L
            try {
                recorder.startRecording()
                while (isActive) {
                    val read = recorder.read(readBuf, 0, readBuf.size)
                    if (read > 0) {
                        pcmBuffer.write(readBuf, 0, read)
                        val now = System.currentTimeMillis()
                        if (now - lastMeterEmitMs >= 65L) {
                            lastMeterEmitMs = now
                            _amplitude.value = computeNormalizedAmplitude(readBuf, read)
                            _elapsedSeconds.value = ((now - startedMs) / 1000L).toInt()
                        }
                    } else {
                        delay(15)
                    }
                }
            } catch (e: Exception) {
                onError("በድምጽ ቅጂ ወቅት ስህተት ተፈጥሯል: ${e.localizedMessage ?: ""}")
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                _amplitude.value = 0f
            }
        }
    }

    suspend fun stopAndBuildWav(): ByteArray {
        val job = recordingJob
        recordingJob = null
        job?.cancel()
        job?.join()
        val pcm = pcmBuffer.toByteArray()
        pcmBuffer.reset()
        return if (pcm.isEmpty()) ByteArray(0) else encodePcmToWavBytes(pcm, SAMPLE_RATE, 1, 16)
    }

    fun cancelAndDiscard() {
        stopInternal()
        pcmBuffer.reset()
    }

    private fun stopInternal() {
        recordingJob?.cancel()
        recordingJob = null
        _amplitude.value = 0f
        _elapsedSeconds.value = 0
    }

    companion object {
        const val SAMPLE_RATE = 16000

        fun computeNormalizedAmplitude(pcmBytes: ByteArray, length: Int): Float {
            if (length < 2) return 0f
            var peak = 0
            var i = 0
            while (i + 1 < length) {
                val sample = (pcmBytes[i + 1].toInt() shl 8) or (pcmBytes[i].toInt() and 0xFF)
                val absSample = abs(sample)
                if (absSample > peak) peak = absSample
                i += 2
            }
            return (peak / 32768f).coerceIn(0f, 1f)
        }

        fun encodePcmToWavBytes(
            pcmData: ByteArray,
            sampleRate: Int = SAMPLE_RATE,
            channels: Int = 1,
            bitsPerSample: Int = 16,
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = (channels * bitsPerSample / 8).toShort()
            val totalDataLen = pcmData.size + 36
            val out = ByteArray(44 + pcmData.size)

            fun writeAscii(offset: Int, text: String) {
                for (idx in text.indices) {
                    out[offset + idx] = text[idx].code.toByte()
                }
            }

            fun writeIntLE(offset: Int, value: Int) {
                out[offset] = (value and 0xFF).toByte()
                out[offset + 1] = ((value shr 8) and 0xFF).toByte()
                out[offset + 2] = ((value shr 16) and 0xFF).toByte()
                out[offset + 3] = ((value shr 24) and 0xFF).toByte()
            }

            fun writeShortLE(offset: Int, value: Short) {
                val v = value.toInt()
                out[offset] = (v and 0xFF).toByte()
                out[offset + 1] = ((v shr 8) and 0xFF).toByte()
            }

            writeAscii(0, "RIFF")
            writeIntLE(4, totalDataLen)
            writeAscii(8, "WAVE")
            writeAscii(12, "fmt ")
            writeIntLE(16, 16) // Subchunk1Size for PCM
            writeShortLE(20, 1) // AudioFormat = 1 (PCM)
            writeShortLE(22, channels.toShort())
            writeIntLE(24, sampleRate)
            writeIntLE(28, byteRate)
            writeShortLE(32, blockAlign)
            writeShortLE(34, bitsPerSample.toShort())
            writeAscii(36, "data")
            writeIntLE(40, pcmData.size)
            System.arraycopy(pcmData, 0, out, 44, pcmData.size)
            return out
        }
    }
}
