package com.wmods.wppenhacer.ui.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.adapter.RecordingsAdapter;
import com.wmods.wppenhacer.databinding.FragmentRecordingsBinding;
import com.wmods.wppenhacer.model.Recording;
import com.wmods.wppenhacer.ui.dialogs.AudioPlayerDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordingsFragment extends Fragment implements RecordingsAdapter.OnRecordingActionListener {

    private FragmentRecordingsBinding binding;
    private RecordingsAdapter adapter;
    private List<Recording> allRecordings = new ArrayList<>();
    private List<File> baseDirs = new ArrayList<>();
    private boolean isGroupByContact = false;
    private int currentSortType = 1; // 1=date, 2=name, 3=duration, 4=contact

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

        // Set up selection change listener
        adapter.setSelectionChangeListener(count -> {
            if (count > 0) {
                binding.selectionBar.setVisibility(View.VISIBLE);
                binding.tvSelectionCount.setText(getString(R.string.selected_count, count));
            } else {
                binding.selectionBar.setVisibility(View.GONE);
            }
        });

        // Initialize base directories
        initializeBaseDirs();

        // View mode toggle
        binding.chipList.setOnClickListener(v -> {
            isGroupByContact = false;
            loadRecordings();
        });
        
        binding.chipGroupByContact.setOnClickListener(v -> {
            isGroupByContact = true;
            loadRecordings();
        });

        // Selection bar buttons
        binding.btnCloseSelection.setOnClickListener(v -> adapter.clearSelection());
        binding.btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        binding.btnShareSelected.setOnClickListener(v -> shareSelectedRecordings());
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelectedRecordings());

        // Sort FAB
        binding.fabSort.setOnClickListener(v -> showSortMenu());

        loadRecordings();
    }

    private void initializeBaseDirs() {
        var prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String path = prefs.getString("call_recording_path", null);
        
        baseDirs.clear();
        
        // 1. Root folder if MANAGE_EXTERNAL_STORAGE
        if (Environment.isExternalStorageManager()) {
            baseDirs.add(new File(Environment.getExternalStorageDirectory(), "WA Call Recordings"));
        }
        
        // 2. Settings path
        if (path != null && !path.isEmpty()) {
            baseDirs.add(new File(path, "WA Call Recordings"));
        }
        
        // 3. WhatsApp app external files
        baseDirs.add(new File("/sdcard/Android/data/com.whatsapp/files/Recordings"));
        baseDirs.add(new File("/sdcard/Android/data/com.whatsapp.w4b/files/Recordings"));
        
        // 4. Legacy fallback
        baseDirs.add(new File(Environment.getExternalStorageDirectory(), "Music/WaEnhancer/Recordings"));
    }

    private void loadRecordings() {
        allRecordings.clear();
        
        for (File baseDir : baseDirs) {
            if (baseDir.exists() && baseDir.isDirectory()) {
                traverseDirectory(baseDir);
            }
        }

        if (allRecordings.isEmpty()) {
            binding.emptyView.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
            
            // Apply sorting
            applySort();
            
            if (isGroupByContact) {
                // For group by contact, we'll navigate to ContactRecordingsActivity when a contact is clicked
                // For now, just show sorted list (full group UI needs ContactRecordingsActivity)
                adapter.setRecordings(allRecordings);
            } else {
                adapter.setRecordings(allRecordings);
            }
        }
    }

    private void traverseDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    traverseDirectory(file);
                } else {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".m4a")) {
                        allRecordings.add(new Recording(file, requireContext()));
                    }
                }
            }
        }
    }

    private void applySort() {
        switch (currentSortType) {
            case 1 -> allRecordings.sort((r1, r2) -> Long.compare(r2.getDate(), r1.getDate())); // Date desc
            case 2 -> allRecordings.sort(Comparator.comparing(Recording::getContactName)); // Name
            case 3 -> allRecordings.sort((r1, r2) -> Long.compare(r2.getDuration(), r1.getDuration())); // Duration desc
            case 4 -> allRecordings.sort(Comparator.comparing(Recording::getContactName)
                    .thenComparing((r1, r2) -> Long.compare(r2.getDate(), r1.getDate()))); // Contact then date
        }
    }

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), binding.fabSort);
        popup.getMenu().add(0, 1, 0, R.string.sort_date);
        popup.getMenu().add(0, 2, 0, R.string.sort_name);
        popup.getMenu().add(0, 3, 0, R.string.sort_duration);
        popup.getMenu().add(0, 4, 0, R.string.sort_contact);
        
        popup.setOnMenuItemClickListener(item -> {
            currentSortType = item.getItemId();
            applySort();
            adapter.setRecordings(allRecordings);
            return true;
        });
        popup.show();
    }

    // RecordingsAdapter.OnRecordingActionListener implementation

    @Override
    public void onPlay(Recording recording) {
        // Use in-app audio player
        AudioPlayerDialog dialog = new AudioPlayerDialog(requireContext(), recording.getFile());
        dialog.show();
    }

    @Override
    public void onShare(Recording recording) {
        shareRecording(recording.getFile());
    }

    @Override
    public void onDelete(Recording recording) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirmation)
                .setMessage(recording.getFile().getName())
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    if (recording.getFile().delete()) {
                        loadRecordings();
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onLongPress(Recording recording, int position) {
        // Enter selection mode
        adapter.setSelectionMode(true);
        adapter.toggleSelection(position);
    }

    private void shareRecording(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), 
                    requireContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recording)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error sharing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSelectedRecordings() {
        List<Recording> selected = adapter.getSelectedRecordings();
        if (selected.isEmpty()) return;

        if (selected.size() == 1) {
            shareRecording(selected.get(0).getFile());
            adapter.clearSelection();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (Recording rec : selected) {
            try {
                Uri uri = FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".fileprovider", rec.getFile());
                uris.add(uri);
            } catch (Exception ignored) {}
        }

        if (!uris.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("audio/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recordings)));
        }
        adapter.clearSelection();
    }

    private void deleteSelectedRecordings() {
        List<Recording> selected = adapter.getSelectedRecordings();
        if (selected.isEmpty()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirmation)
                .setMessage(getString(R.string.delete_multiple_confirmation, selected.size()))
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    int deleted = 0;
                    for (Recording rec : selected) {
                        if (rec.getFile().delete()) {
                            deleted++;
                        }
                    }
                    Toast.makeText(requireContext(), "Deleted " + deleted + " recordings", Toast.LENGTH_SHORT).show();
                    adapter.clearSelection();
                    loadRecordings();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
