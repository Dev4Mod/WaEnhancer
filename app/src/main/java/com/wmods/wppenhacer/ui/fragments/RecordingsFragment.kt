package com.wmods.wppenhacer.ui.fragments

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.adapter.RecordingsAdapter
import com.wmods.wppenhacer.databinding.FragmentRecordingsBinding
import com.wmods.wppenhacer.model.Recording
import com.wmods.wppenhacer.ui.dialogs.AudioPlayerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.LinkedHashSet

class RecordingsFragment : Fragment(), RecordingsAdapter.OnRecordingActionListener {

    private var _binding: FragmentRecordingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RecordingsAdapter
    private val allRecordings = ArrayList<Recording>()
    private var isGroupByContact = false
    private var currentSortType = 1 // 1=date, 2=name, 3=duration, 4=contact

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecordingsAdapter(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        adapter.setSelectionChangeListener { count ->
            if (count > 0) {
                binding.selectionBar.visibility = View.VISIBLE
                binding.tvSelectionCount.text = getString(R.string.selected_count, count)
            } else {
                binding.selectionBar.visibility = View.GONE
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadRecordings()
        }

        loadRecordings()
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        loadRecordings()
    }

    private fun getBaseDirs(): List<File> {
        val context = context ?: return emptyList()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val configuredPath = prefs.getString("call_recording_path", null)

        val dirs = ArrayList<File>()
        val addedPaths = LinkedHashSet<String>()

        addBaseDir(dirs, addedPaths, File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "WA Call Recordings"
        ))

        if (!configuredPath.isNullOrEmpty()) {
            addBaseDir(dirs, addedPaths, File(configuredPath, "WA Call Recordings"))
        }

        addBaseDir(dirs, addedPaths, File(Environment.getExternalStorageDirectory(), "WA Call Recordings"))
        addBaseDir(dirs, addedPaths, File("/sdcard/Android/data/com.whatsapp/files/Recordings"))
        addBaseDir(dirs, addedPaths, File("/sdcard/Android/data/com.whatsapp.w4b/files/Recordings"))
        addBaseDir(dirs, addedPaths, File(Environment.getExternalStorageDirectory(), "Music/WaEnhancer/Recordings"))
        return dirs
    }

    private fun addBaseDir(dirs: MutableList<File>, addedPaths: MutableSet<String>, dir: File) {
        val normalizedPath = normalizePath(dir)
        if (addedPaths.add(normalizedPath)) {
            dirs.add(dir)
        }
    }

    private fun normalizePath(dir: File): String {
        return try {
            dir.canonicalPath
        } catch (ignored: IOException) {
            dir.absolutePath
        }
    }

    private fun loadRecordings() {
        if (_binding == null) return

        binding.swipeRefresh.isRefreshing = true
        val baseDirs = getBaseDirs()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val loaded = ArrayList<Recording>()
            for (baseDir in baseDirs) {
                if (baseDir.exists() && baseDir.isDirectory) {
                    traverseDirectory(baseDir, loaded)
                }
            }

            applySort(loaded)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                allRecordings.clear()
                allRecordings.addAll(loaded)
                binding.swipeRefresh.isRefreshing = false

                if (allRecordings.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    adapter.setRecordings(allRecordings)
                }
            }
        }
    }

    private fun traverseDirectory(dir: File, result: MutableList<Recording>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                traverseDirectory(file, result)
            } else {
                val name = file.name.lowercase()
                if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".m4a")) {
                    result.add(Recording(file))
                }
            }
        }
    }

    private fun applySort(list: MutableList<Recording>) {
        when (currentSortType) {
            1 -> list.sortWith { r1, r2 -> r2.date.compareTo(r1.date) }
            2 -> list.sortWith(compareBy { it.contactName })
            3 -> list.sortWith { r1, r2 -> r2.duration.compareTo(r1.duration) }
            4 -> list.sortWith(compareBy<Recording> { it.contactName }.thenByDescending { it.date })
        }
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), binding.fabSort)
        popup.menu.add(0, 1, 0, R.string.sort_date)
        popup.menu.add(0, 2, 0, R.string.sort_name)
        popup.menu.add(0, 3, 0, R.string.sort_duration)
        popup.menu.add(0, 4, 0, R.string.sort_contact)

        popup.setOnMenuItemClickListener { item ->
            currentSortType = item.itemId
            loadRecordings()
            true
        }
        popup.show()
    }

    override fun onPlay(recording: Recording) {
        val dialog = AudioPlayerDialog(requireContext(), recording.file)
        dialog.show()
    }

    override fun onShare(recording: Recording) {
        shareRecording(recording.file)
    }

    override fun onDelete(recording: Recording) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirmation)
            .setMessage(recording.file.name)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val deleted = recording.file.delete()
                    withContext(Dispatchers.Main) {
                        if (deleted) {
                            loadRecordings()
                        } else {
                            Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    override fun onLongPress(recording: Recording, position: Int) {
        adapter.setSelectionMode(true)
        adapter.toggleSelection(position)
    }

    private fun shareRecording(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_recording)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error sharing: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareSelectedRecordings() {
        val selected = adapter.selectedRecordings
        if (selected.isEmpty()) return

        if (selected.size == 1) {
            shareRecording(selected[0].file)
            adapter.clearSelection()
            return
        }

        val uris = ArrayList<Uri>()
        for (rec in selected) {
            try {
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().packageName + ".fileprovider",
                    rec.file
                )
                uris.add(uri)
            } catch (ignored: Exception) {}
        }

        if (uris.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_recordings)))
        }
        adapter.clearSelection()
    }

    private fun deleteSelectedRecordings() {
        val selected = adapter.selectedRecordings
        if (selected.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirmation)
            .setMessage(getString(R.string.delete_multiple_confirmation, selected.size))
            .setPositiveButton(android.R.string.yes) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    var deleted = 0
                    for (rec in selected) {
                        if (rec.file.delete()) {
                            deleted++
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Deleted $deleted recordings", Toast.LENGTH_SHORT).show()
                        adapter.clearSelection()
                        loadRecordings()
                    }
                }
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
