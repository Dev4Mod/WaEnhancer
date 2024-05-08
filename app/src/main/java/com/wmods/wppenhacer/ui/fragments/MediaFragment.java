package com.wmods.wppenhacer.ui.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.ui.fragments.base.BasePreFragment;
import com.wmods.wppenhacer.utils.RealPathUtil;

public class MediaFragment extends BasePreFragment {


    private ActivityResultLauncher<Uri> mContract;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContract = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), result -> {
            if (result == null) return;
            var realPath  = RealPathUtil.getRealPathFromURI_API19(getContext(), result);
            findPreference("localdownload").setSummary(realPath);
            mPrefs.edit().putString("localdownload", realPath).apply();
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

}
