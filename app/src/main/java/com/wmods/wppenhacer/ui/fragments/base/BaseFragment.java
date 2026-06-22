package com.wmods.wppenhacer.ui.fragments.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.wmods.wppenhacer.databinding.BaseFragmentBinding;

public class BaseFragment extends Fragment {

    public BaseFragmentBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BaseFragmentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void setDisplayHomeAsUpEnabled(boolean enabled) {
        if (getActivity() == null) return;
        var actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(enabled);
        }
    }


}
