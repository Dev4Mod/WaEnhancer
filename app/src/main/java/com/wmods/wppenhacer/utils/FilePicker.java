package com.wmods.wppenhacer.utils;

import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class FilePicker {

    private static OnFilePickedListener mOnFilePickedListener;
    private static java.lang.ref.WeakReference<AppCompatActivity> mActivityRef;
    public static ActivityResultLauncher<String> fileSalve;
    private static OnUriPickedListener mOnUriPickedListener;
    public static ActivityResultLauncher<String[]> fileCapture;
    public static ActivityResultLauncher<Uri> directoryCapture;
    public static ActivityResultLauncher<PickVisualMediaRequest> imageCapture;

    public static void registerFilePicker(AppCompatActivity activity) {
        mActivityRef = new java.lang.ref.WeakReference<>(activity);
        fileCapture = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(), FilePicker::setFile);
        imageCapture = activity.registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), FilePicker::setFile);
        directoryCapture = activity.registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), FilePicker::setDirectory);
        fileSalve = activity.registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"), FilePicker::setFile);
    }

    private static void setFile(Uri uri) {
        if (uri == null) return;

        if (mOnUriPickedListener != null) {
            mOnUriPickedListener.onUriPicked(uri);
            mOnUriPickedListener = null;
        }

        if (mOnFilePickedListener != null) {
            String realPath = null;
            AppCompatActivity activity = mActivityRef != null ? mActivityRef.get() : null;
            if (activity == null) return;
            try {
                realPath = RealPathUtil.getRealFilePath(activity, uri);
            } catch (Exception ignored) {
            }
            if (realPath == null) return;
            mOnFilePickedListener.onFilePicked(new File(realPath));
            mOnFilePickedListener = null;
        }
    }

    private static void setDirectory(Uri uri) {
        if (uri == null) return;

        if (mOnFilePickedListener == null && mOnUriPickedListener != null) {
            mOnUriPickedListener.onUriPicked(uri);
            mOnUriPickedListener = null;
        }

        if (mOnFilePickedListener != null) {
            String realPath = null;
            AppCompatActivity activity = mActivityRef != null ? mActivityRef.get() : null;
            if (activity == null) return;
            try {
                realPath = RealPathUtil.getRealFolderPath(activity, uri);
            } catch (Exception ignored) {
            }
            if (realPath == null) return;
            mOnFilePickedListener.onFilePicked(new File(realPath));
            mOnFilePickedListener = null;
        }
    }
    
    public static void setOnFilePickedListener(OnFilePickedListener onFilePickedListener) {
        mOnFilePickedListener = onFilePickedListener;
        mOnUriPickedListener = null;
    }

    public static void setOnUriPickedListener(OnUriPickedListener onFilePickedListener) {
        mOnUriPickedListener = onFilePickedListener;
        mOnFilePickedListener = null;
    }

    public static void cleanup() {
        if (mActivityRef != null) {
            mActivityRef.clear();
            mActivityRef = null;
        }
        fileCapture = null;
        imageCapture = null;
        directoryCapture = null;
        fileSalve = null;
        mOnFilePickedListener = null;
        mOnUriPickedListener = null;
    }


    public interface OnFilePickedListener {
        void onFilePicked(File file);
    }

    public interface OnUriPickedListener {
        void onUriPicked(Uri uri);
    }

}
