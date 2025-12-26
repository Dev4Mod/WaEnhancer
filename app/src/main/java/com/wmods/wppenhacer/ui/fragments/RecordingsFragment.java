package com.wmods.wppenhacer.ui.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.adapter.RecordingsAdapter;
import com.wmods.wppenhacer.databinding.FragmentRecordingsBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RecordingsFragment extends Fragment implements RecordingsAdapter.OnRecordingActionListener {

    private FragmentRecordingsBinding binding;
    private RecordingsAdapter adapter;
    private List<File> recordingFiles = new ArrayList<>();
    private File baseDir;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRecordingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new RecordingsAdapter(this);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        var prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String path = prefs.getString("call_recording_path", Environment.getExternalStorageDirectory() + "/Music/WaEnhancer/Recordings");
        baseDir = new File(path);

        binding.fabSort.setOnClickListener(v -> showSortMenu());

        loadRecordings();
    }

    private void loadRecordings() {
        recordingFiles.clear();
        if (baseDir.exists() && baseDir.isDirectory()) {
             traverseDirectory(baseDir);
        }

        if (recordingFiles.isEmpty()) {
            binding.emptyView.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
            // Default sort by date desc
            recordingFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            adapter.setFiles(recordingFiles);
        }
    }

    private void traverseDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    traverseDirectory(file);
                } else {
                    if (file.getName().endsWith(".wav") || file.getName().endsWith(".mp3") || file.getName().endsWith(".aac")) {
                        recordingFiles.add(file);
                    }
                }
            }
        }
    }

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), binding.fabSort);
        popup.getMenu().add(0, 1, 0, R.string.sort_date);
        popup.getMenu().add(0, 2, 0, R.string.sort_name);
        popup.getMenu().add(0, 3, 0, R.string.sort_duration); 
        
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> recordingFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                case 2 -> recordingFiles.sort(Comparator.comparing(File::getName));
                case 3 -> recordingFiles.sort((f1, f2) -> Long.compare(f2.length(), f1.length())); // Approximation by size
            }
            adapter.setFiles(recordingFiles);
            return true;
        });
        popup.show();
    }

    @Override
    public void onPlay(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error playing file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onShare(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.getType(); // check
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recording)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDelete(File file) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirmation)
                .setMessage(file.getName())
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    if (file.delete()) {
                        loadRecordings();
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }
}
