package com.wmods.wppenhacer.xposed.utils;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public abstract class HKDF {
    public static HKDF createFor(int version) {
        if (version == 3) {
            return new HKDFv3();
        }
        throw new AssertionError("Unknown version: " + version);
    }

    public byte[] deriveSecrets(byte[] arr_b, byte[] arr_b1, int v) {
        return this.deriveSecrets(arr_b, new byte[0x20], arr_b1, v);
    }

    public byte[] deriveSecrets(byte[] inputKeyMaterial, byte[] salt, byte[] info, int outputLength) {
        byte[] derivedKey;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            derivedKey = mac.doFinal(inputKeyMaterial);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }

        try {
            int iterations = (int) Math.ceil(((double) outputLength) / 32.0);
            byte[] outputKey = new byte[0];
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (int i = getIterationStartOffset(); i < getIterationStartOffset() + iterations; ++i) {
                Mac macIteration = Mac.getInstance("HmacSHA256");
                macIteration.init(new SecretKeySpec(derivedKey, "HmacSHA256"));
                macIteration.update(outputKey);
                if (info != null) {
                    macIteration.update(info);
                }
                macIteration.update((byte) i);
                outputKey = macIteration.doFinal();
                int remainingLength = Math.min(outputLength, outputKey.length);
                outputStream.write(outputKey, 0, remainingLength);
                outputLength -= remainingLength;
            }
            return outputStream.toByteArray();
        } catch (InvalidKeyException | NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }

    protected abstract int getIterationStartOffset();



}
