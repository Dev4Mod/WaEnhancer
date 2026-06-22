package com.wmods.wppenhacer.xposed.utils

import java.io.ByteArrayOutputStream
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

abstract class HKDF {

    companion object {
        @JvmStatic
        fun createFor(version: Int): HKDF {
            if (version == 3) {
                return HKDFv3()
            }
            throw AssertionError("Unknown version: $version")
        }
    }

    fun deriveSecrets(arr_b: ByteArray, arr_b1: ByteArray, v: Int): ByteArray {
        return deriveSecrets(arr_b, ByteArray(0x20), arr_b1, v)
    }

    fun deriveSecrets(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray?, outputLength: Int): ByteArray {
        val derivedKey: ByteArray
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            derivedKey = mac.doFinal(inputKeyMaterial)
        } catch (e: InvalidKeyException) {
            throw AssertionError(e)
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        }

        try {
            val iterations = Math.ceil(outputLength.toDouble() / 32.0).toInt()
            var outputKey = ByteArray(0)
            val outputStream = ByteArrayOutputStream()
            var remainingLength = outputLength
            var i = iterationStartOffset
            while (i < iterationStartOffset + iterations) {
                val macIteration = Mac.getInstance("HmacSHA256")
                macIteration.init(SecretKeySpec(derivedKey, "HmacSHA256"))
                macIteration.update(outputKey)
                if (info != null) {
                    macIteration.update(info)
                }
                macIteration.update(i.toByte())
                outputKey = macIteration.doFinal()
                val len = minOf(remainingLength, outputKey.size)
                outputStream.write(outputKey, 0, len)
                remainingLength -= len
                ++i
            }
            return outputStream.toByteArray()
        } catch (ex: InvalidKeyException) {
            throw AssertionError(ex)
        } catch (ex: NoSuchAlgorithmException) {
            throw AssertionError(ex)
        }
    }

    protected abstract val iterationStartOffset: Int
}
