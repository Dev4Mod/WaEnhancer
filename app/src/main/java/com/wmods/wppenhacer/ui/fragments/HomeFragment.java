package com.wmods.wppenhacer.ui.fragments;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.MainActivity;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.databinding.FragmentHomeBinding;
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment;
import com.wmods.wppenhacer.xposed.core.MainFeatures;

import java.util.Arrays;

public class HomeFragment extends BaseFragment {

    private FragmentHomeBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var intentFilter = new IntentFilter(BuildConfig.APPLICATION_ID + ".RECEIVER_WPP");
        ContextCompat.registerReceiver(requireContext(), new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    if (MainFeatures.PACKAGE_WPP.equals(intent.getStringExtra("PKG")))
                        receiverBroadcastWpp(context, intent);
                    else
                        receiverBroadcastBusiness(context, intent);
                } catch (Exception ignored) {
                }
            }
        }, intentFilter, ContextCompat.RECEIVER_EXPORTED);
    }

    @SuppressLint("StringFormatInvalid")
    private void receiverBroadcastBusiness(Context context, Intent intent) {
        binding.statusTitle3.setText(R.string.business_in_background);
        var version = intent.getStringExtra("VERSION");
        var supported_list = Arrays.asList(context.getResources().getStringArray(R.array.supported_versions_wpp));
        if (supported_list.contains(version)) {
            binding.statusSummary3.setText(String.format(getString(R.string.version_s), version));
            binding.status3.setCardBackgroundColor(context.getColor(rikka.material.R.color.material_green_500));
        }else {
            binding.statusSummary3.setText(String.format(getString(R.string.version_s_not_listed), version));
            binding.status3.setCardBackgroundColor(context.getColor(rikka.material.R.color.material_yellow_500));
        }
        binding.statusSummary3.setVisibility(View.VISIBLE);
        binding.statusIcon3.setImageResource(R.drawable.ic_round_check_circle_24);
    }

    private void receiverBroadcastWpp(Context context, Intent intent) {
        binding.statusTitle2.setText(R.string.whatsapp_in_background);
        var version = intent.getStringExtra("VERSION");
        var supported_list = Arrays.asList(context.getResources().getStringArray(R.array.supported_versions_wpp));
        if (supported_list.contains(version)) {
            binding.statusSummary1.setText(String.format(getString(R.string.version_s), version));
            binding.status2.setCardBackgroundColor(context.getColor(rikka.material.R.color.material_green_500));
        }else {
            binding.statusSummary1.setText(String.format(getString(R.string.version_s_not_listed), version));
            binding.status2.setCardBackgroundColor(context.getColor(rikka.material.R.color.material_yellow_500));
        }
        binding.statusSummary1.setVisibility(View.VISIBLE);
        binding.statusIcon2.setImageResource(R.drawable.ic_round_check_circle_24);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        checkStateWpp(requireActivity());

        binding.rebootBtn.setOnClickListener(view -> {
            Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
            intent.putExtra("PKG", MainFeatures.PACKAGE_WPP);
            requireActivity().sendBroadcast(intent);
            disableWpp(requireActivity());
        });

        binding.rebootBtn2.setOnClickListener(view -> {
            Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
            intent.putExtra("PKG", MainFeatures.PACKAGE_BUSINESS);
            requireActivity().sendBroadcast(intent);
            disableBusiness(requireActivity());
        });


        return binding.getRoot();
    }

    private void checkStateWpp(FragmentActivity activity) {

        if (MainActivity.isXposedEnabled()) {
            binding.statusIcon.setImageResource(R.drawable.ic_round_check_circle_24);
            binding.statusTitle.setText(R.string.module_enabled);
            binding.statusSummary.setText(String.format(getString(R.string.version_s), BuildConfig.VERSION_NAME));
        } else {
            binding.statusIcon.setImageResource(R.drawable.ic_round_error_outline_24);
            binding.statusTitle.setText(R.string.module_disabled);
            binding.status.setCardBackgroundColor(activity.getColor(rikka.material.R.color.material_red_500));
            binding.statusSummary.setVisibility(View.GONE);
        }
        disableWpp(activity);
        disableBusiness(activity);
        checkWpp(activity);
        binding.deviceName.setText(Build.MANUFACTURER);
        binding.sdk.setText(String.valueOf(Build.VERSION.SDK_INT));
        binding.modelName.setText(Build.DEVICE);
        binding.listWpp.setText(Arrays.toString(activity.getResources().getStringArray(R.array.supported_versions_wpp)));
        binding.listBusiness.setText(Arrays.toString(activity.getResources().getStringArray(R.array.supported_versions_wpp)));
    }

    private void disableBusiness(FragmentActivity activity) {
        binding.statusIcon3.setImageResource(R.drawable.ic_round_error_outline_24);
        binding.statusTitle3.setText(R.string.business_is_not_running_or_has_not_been_activated_in_lsposed);
        binding.status3.setCardBackgroundColor(activity.getColor(rikka.material.R.color.material_red_500));
        binding.statusSummary3.setVisibility(View.GONE);
    }

    private void disableWpp(FragmentActivity activity) {
        binding.statusIcon2.setImageResource(R.drawable.ic_round_error_outline_24);
        binding.statusTitle2.setText(R.string.whatsapp_is_not_running_or_has_not_been_activated_in_lsposed);
        binding.status2.setCardBackgroundColor(activity.getColor(rikka.material.R.color.material_red_500));
        binding.statusSummary1.setVisibility(View.GONE);
    }

    private static void checkWpp(FragmentActivity activity) {
        Intent checkWpp = new Intent(BuildConfig.APPLICATION_ID + ".CHECK_WPP");
        activity.sendBroadcast(checkWpp);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}