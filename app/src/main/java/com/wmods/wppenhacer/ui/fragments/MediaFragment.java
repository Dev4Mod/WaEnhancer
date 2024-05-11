package com.wmods.wppenhacer.ui.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.ui.fragments.base.BasePreFragment;
import com.wmods.wppenhacer.utils.RealPathUtil;

public class MediaFragment extends BasePreFragment {

    private final String TAG = "MediaFragment";
    private ActivityResultLauncher<Uri> mContract;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContract = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), result -> {
            if (result == null) return;
            var realPath  = RealPathUtil.getRealFolderPath(getContext(), result);
            Preference preference = findPreference("localdownload");
            if (preference != null) {
                preference.setSummary(realPath);
            }
            mPrefs.edit().putString("localdownload", realPath).apply();
        });
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.fragment_media, rootKey);
        var localPref = findPreference("localdownload");
        if (localPref == null) {
            Log.e(TAG, "localPref is null!");
            return;
        }
        localPref.setOnPreferenceClickListener((preference) -> {
            mContract.launch(null);
            return true;
        });
        localPref.setSummary(mPrefs.getString("localdownload", Environment.getExternalStorageDirectory().getPath()+"/Download"));
    }
}
