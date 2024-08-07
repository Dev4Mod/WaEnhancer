package com.wmods.wppenhacer.utils;

import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class FilePicker {

    private static OnFilePickedListener mOnFilePickedListener;
    private static AppCompatActivity mActivity;
    public static ActivityResultLauncher<String> fileSalve;
    private static OnUriPickedListener mOnUriPickedListener;
    public static ActivityResultLauncher<String[]> fileCapture;
    public static ActivityResultLauncher<Uri> directoryCapture;
    public static ActivityResultLauncher<PickVisualMediaRequest> imageCapture;

    public static void registerFilePicker(AppCompatActivity activity) {
        mActivity = activity;
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
            try {
                realPath = RealPathUtil.getRealFilePath(mActivity, uri);
            }catch (Exception ignored) {
            }
            if (realPath == null) return;
            mOnFilePickedListener.onFilePicked(new File(realPath));
            mOnFilePickedListener = null;
        }
    }

    private static void setDirectory(Uri uri) {
        if (uri == null) return;

        if (mOnFilePickedListener == null) {
            mOnUriPickedListener.onUriPicked(uri);
            mOnUriPickedListener = null;
        }

        if (mOnFilePickedListener != null) {
            String realPath = null;
            try {
                realPath = RealPathUtil.getRealFolderPath(mActivity, uri);
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


    public interface OnFilePickedListener {
        void onFilePicked(File file);
    }

    public interface OnUriPickedListener {
        void onUriPicked(Uri uri);
    }

}
