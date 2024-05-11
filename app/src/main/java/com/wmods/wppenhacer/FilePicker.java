package com.wmods.wppenhacer;

import android.net.Uri;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.wmods.wppenhacer.utils.RealPathUtil;

import java.io.File;

public class FilePicker {

    private static OnFilePickedListener mOnFilePickedListener;
    private static AppCompatActivity mActivity;
    public static ActivityResultLauncher<String> fileSalve;
    private static OnUriPickedListener mOnUriPickedListener;
    public static ActivityResultLauncher<String[]> fileCapture;

    public static void registerFilePicker(AppCompatActivity activity) {
        mActivity = activity;
        fileCapture = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(), FilePicker::setFile);
        fileSalve = activity.registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"), FilePicker::setFile);
    }

    private static void setFile(Uri uri) {
        if (uri == null) return;

        if (mOnFilePickedListener == null) {
            mOnUriPickedListener.onUriPicked(uri);
            mOnUriPickedListener = null;
        }

        if (mOnFilePickedListener != null) {
            var realPath = RealPathUtil.getRealFilePath(mActivity, uri);
            if (realPath == null) return;
            mOnFilePickedListener.onFilePicked(new File(realPath));
            mOnFilePickedListener = null;
        }
    }



    public static void setOnFilePickedListener(OnFilePickedListener onFilePickedListener) {
        mOnFilePickedListener = onFilePickedListener;
    }

    public static void setOnUriPickedListener(OnUriPickedListener onFilePickedListener) {
        mOnUriPickedListener = onFilePickedListener;
    }

    public interface OnFilePickedListener {
        void onFilePicked(File file);
    }

    public interface OnUriPickedListener {
        void onUriPicked(Uri uri);
    }

}
