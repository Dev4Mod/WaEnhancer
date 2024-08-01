package com.wmods.wppenhacer.xposed.bridge.service;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HookBinder extends WaeIIFace.Stub {

    private static HookBinder mInstance;

    public static HookBinder getInstance() {
        if (mInstance == null) {
            mInstance = new HookBinder();
        }
        return mInstance;
    }

    @Override
    public ParcelFileDescriptor openFile(String path, boolean create) throws RemoteException {
        File file = new File(path);
        if (!file.exists() && create) {
            try {
                file.createNewFile();
            } catch (Exception ignored) {
                return null;
            }
        }
        try {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public boolean createDir(String path) throws RemoteException {
        File file = new File(path);
        return file.mkdirs();
    }

    @Override
    public List listFiles(String path) throws RemoteException {
        var files = new File(path).listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(files);
    }


}
