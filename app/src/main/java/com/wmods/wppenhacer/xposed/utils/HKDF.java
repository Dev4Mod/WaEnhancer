package com.wmods.wppenhacer.xposed.utils;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public abstract class HKDF {
    public static HKDF createFor(int v) {
        if(v == 3) {
            return new HKDFv3();
        }
        throw new AssertionError("Unknown version: " + v);
    }

    public byte[] deriveSecrets(byte[] arr_b, byte[] arr_b1, int v) {
        return this.deriveSecrets(arr_b, new byte[0x20], arr_b1, v);
    }

    public byte[] deriveSecrets(byte[] arr_b, byte[] arr_b1, byte[] arr_b2, int v) {
        byte[] arr_b3;
        try {
            Mac mac0 = Mac.getInstance("HmacSHA256");
            mac0.init(new SecretKeySpec(arr_b1, "HmacSHA256"));
            arr_b3 = mac0.doFinal(arr_b);
        }
        catch(InvalidKeyException | NoSuchAlgorithmException noSuchAlgorithmException0) {
            throw new AssertionError(noSuchAlgorithmException0);
        }

        try {
            int v1 = (int)Math.ceil(((double)v) / 32.0);
            byte[] arr_b4 = new byte[0];
            ByteArrayOutputStream byteArrayOutputStream0 = new ByteArrayOutputStream();
            for(int v2 = this.getIterationStartOffset(); v2 < this.getIterationStartOffset() + v1; ++v2) {
                Mac mac1 = Mac.getInstance("HmacSHA256");
                mac1.init(new SecretKeySpec(arr_b3, "HmacSHA256"));
                mac1.update(arr_b4);
                if(arr_b2 != null) {
                    mac1.update(arr_b2);
                }

                mac1.update(((byte)v2));
                arr_b4 = mac1.doFinal();
                int v3 = Math.min(v, arr_b4.length);
                byteArrayOutputStream0.write(arr_b4, 0, v3);
                v -= v3;
            }

            return byteArrayOutputStream0.toByteArray();
        }
        catch(InvalidKeyException | NoSuchAlgorithmException noSuchAlgorithmException1) {
            throw new AssertionError(noSuchAlgorithmException1);
        }
    }

    protected abstract int getIterationStartOffset();



}
