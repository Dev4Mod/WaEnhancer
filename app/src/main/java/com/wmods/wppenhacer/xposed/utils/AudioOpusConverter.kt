package com.wmods.wppenhacer.xposed.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.min

object AudioOpusConverter {

    private const val TAG = "AudioOpusConverter"
    private const val OPUS_SAMPLE_RATE = 48_000
    private const val OPUS_CHANNEL_COUNT = 1
    private const val TIMEOUT_US = 10_000L

    init {
        try {
            System.loadLibrary("audio_opus_converter")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load audio_opus_converter", e)
        }
    }

    @JvmStatic
    private external fun nativeInitOpusEncoder(outputPath: String, sampleRate: Int, channels: Int): Long

    @JvmStatic
    private external fun nativeEncodeOpus(handle: Long, pcmData: ByteArray, lengthBytes: Int)

    @JvmStatic
    private external fun nativeCloseOpusEncoder(handle: Long)

    @JvmStatic
    fun convert(filePath: String): File? {
        val outFile = File(File(filePath).parentFile, "voice_note_${System.currentTimeMillis()}.opus")

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoderHandle: Long = 0

        try {
            extractor.setDataSource(filePath)
            val audioTrack = selectAudioTrack(extractor)
            if (audioTrack < 0) throw IOException("No audio track found in URI")

            extractor.selectTrack(audioTrack)
            val inputFormat = extractor.getTrackFormat(audioTrack)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: throw IOException("No MIME type found")

            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            encoderHandle = nativeInitOpusEncoder(outFile.absolutePath, OPUS_SAMPLE_RATE, OPUS_CHANNEL_COUNT)
            if (encoderHandle == 0L) {
                throw IOException("Failed to initialize native opus encoder")
            }

            transcode(extractor, decoder, encoderHandle)
            return outFile

        } catch (e: Throwable) {
            XposedBridge.log(e)
            Log.e(TAG, "Conversion failed", e)
            if (outFile.exists()) outFile.delete()
            return null
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
            try {
                if (encoderHandle != 0L) nativeCloseOpusEncoder(encoderHandle)
            } catch (_: Exception) {
            }
        }
    }

    private fun transcode(extractor: MediaExtractor, decoder: MediaCodec, encoderHandle: Long) {
        val decoderInfo = MediaCodec.BufferInfo()
        var extractorDone = false
        var decoderDone = false
        var inputSampleRate = OPUS_SAMPLE_RATE
        var inputChannels = 1

        while (!decoderDone) {
            if (!extractorDone) {
                val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val inBuf = decoder.getInputBuffer(inIdx)
                    val sampleSize = inBuf?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        extractorDone = true
                    } else {
                        val pts = extractor.sampleTime
                        decoder.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                        extractor.advance()
                    }
                }
            }

            val outIdx = decoder.dequeueOutputBuffer(decoderInfo, TIMEOUT_US)
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val fmt = decoder.outputFormat
                inputSampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else OPUS_SAMPLE_RATE
                inputChannels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            } else if (outIdx >= 0) {
                val pcmBuf = decoder.getOutputBuffer(outIdx)
                val eos = (decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                if (decoderInfo.size > 0 && pcmBuf != null) {
                    pcmBuf.position(decoderInfo.offset)
                    pcmBuf.limit(decoderInfo.offset + decoderInfo.size)

                    val resampled = resampleAndDownmix(pcmBuf, inputSampleRate, inputChannels)
                    if (resampled.isNotEmpty()) {
                        nativeEncodeOpus(encoderHandle, resampled, resampled.size)
                    }
                }

                decoder.releaseOutputBuffer(outIdx, false)
                if (eos) decoderDone = true
            }
        }
    }

    private fun resampleAndDownmix(pcmBuf: ByteBuffer, inSampleRate: Int, inChannels: Int): ByteArray {
        pcmBuf.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuf = pcmBuf.asShortBuffer()
        val numInputFrames = shortBuf.remaining() / inChannels

        val outSampleRate = OPUS_SAMPLE_RATE
        if (numInputFrames <= 0) return ByteArray(0)

        val numOutputFrames = (numInputFrames.toLong() * outSampleRate / inSampleRate).toInt()
        val outBytes = ByteArray(numOutputFrames * 2)

        for (i in 0 until numOutputFrames) {
            val inPos = i.toFloat() * inSampleRate / outSampleRate
            val inIdx1 = inPos.toInt()
            val inIdx2 = min(inIdx1 + 1, numInputFrames - 1)
            val frac = inPos - inIdx1

            val s1 = getMonoSample(shortBuf, inIdx1, inChannels)
            val s2 = getMonoSample(shortBuf, inIdx2, inChannels)

            var interpolated = s1 + frac * (s2 - s1)

            if (interpolated > 32767.0f) interpolated = 32767.0f
            if (interpolated < -32768.0f) interpolated = -32768.0f

            val outSample = interpolated.toInt().toShort()
            outBytes[i * 2] = (outSample.toInt() and 0xFF).toByte()
            outBytes[i * 2 + 1] = ((outSample.toInt() shr 8) and 0xFF).toByte()
        }
        return outBytes
    }

    private fun getMonoSample(buf: ShortBuffer, frameIndex: Int, channels: Int): Float {
        return if (channels == 1) {
            buf.get(frameIndex).toFloat()
        } else {
            val left = buf.get(frameIndex * channels).toFloat()
            val right = buf.get(frameIndex * channels + 1).toFloat()
            (left + right) / 2.0f
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return i
        }
        return -1
    }
}
