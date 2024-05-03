package com.wmods.wppenhacer.ui.fragments;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.ui.fragments.base.BasePreFragment;

public class MediaFragment extends BasePreFragment {


    private ActivityResultLauncher<Uri> mContract;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContract = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), result -> {
            findPreference("localdownload").setSummary(getPathFromContentUri(result));
            mPrefs.edit().putString("localdownload", getPathFromContentUri(result)).apply();
        });
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.fragment_media, rootKey);
        var localPref = findPreference("localdownload");
        localPref.setOnPreferenceClickListener((preference) -> {
            mContract.launch(null);
            return true;
        });
        localPref.setSummary(mPrefs.getString("localdownload", Environment.getExternalStorageDirectory().getPath()+"/Download"));
    }


    public String getPathFromContentUri(Uri uri) {
        if (!"com.android.externalstorage.documents".equals(uri.getAuthority()))
            return uri.toString();
        String[] pathsections = uri.getPath().split(":");
        var path = pathsections[pathsections.length - 1];
        if (path.startsWith("/tree"))
            path = "";
        return Environment.getExternalStorageDirectory().getPath() + "/" + path;
    }

}
