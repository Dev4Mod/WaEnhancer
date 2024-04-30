package com.wmods.wppenhacer.ui.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.MainActivity;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.databinding.FragmentHomeBinding;
import com.wmods.wppenhacer.ui.base.BaseFragment;

public class HomeFragment extends BaseFragment {

    private FragmentHomeBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        var intentFilter = new IntentFilter(BuildConfig.APPLICATION_ID + ".RECEIVER_WPP");
        ContextCompat.registerReceiver(requireContext(), new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                receiverBroadcastWpp(context, intent);
            }
        }, intentFilter, ContextCompat.RECEIVER_EXPORTED);
    }

    private void receiverBroadcastWpp(Context context, Intent intent) {
        binding.statusTitle2.setText("WhatsApp está rodando");
        binding.status2.setCardBackgroundColor(context.getColor(rikka.material.R.color.material_green_500));
        binding.statusSummary1.setText("Versão " + intent.getStringExtra("VERSION"));
        binding.statusSummary1.setVisibility(View.VISIBLE);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        checkStateWpp(requireActivity());

        binding.rebootBtn.setOnClickListener(view -> {
            Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
            intent.putExtra("PKG", "com.whatsapp");
            requireActivity().sendBroadcast(intent);
            disabledWpp(requireActivity());
        });

        binding.rebootBtn.setOnClickListener(view -> {
            Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART");
            intent.putExtra("PKG", "com.whatsapp.w4b");
            requireActivity().sendBroadcast(intent);
            disabledWpp(requireActivity());
        });


        return binding.getRoot();
    }

    private void checkStateWpp(FragmentActivity activity) {

        if (MainActivity.isXposedEnabled()) {
            binding.statusIcon.setImageResource(R.drawable.ic_round_check_circle_24);
            binding.statusTitle.setText("Modulo está ativado");
            binding.statusSummary.setText("Versão " + BuildConfig.VERSION_NAME);
        } else {
            binding.statusIcon.setImageResource(R.drawable.ic_round_error_outline_24);
            binding.statusTitle.setText("Modulo não está ativado");
            binding.status.setCardBackgroundColor(activity.getColor(rikka.material.R.color.material_red_500));
            binding.statusSummary.setVisibility(View.GONE);
        }
        disabledWpp(activity);
        disableBusiness(activity);
        checkWpp(activity);
    }

    private void disableBusiness(FragmentActivity activity) {
        binding.statusIcon3.setImageResource(R.drawable.ic_round_error_outline_24);
        binding.statusTitle3.setText("Business não está rodando ou não foi iniciado");
        binding.status3.setCardBackgroundColor(activity.getColor(rikka.material.R.color.material_red_500));
        binding.statusSummary3.setVisibility(View.GONE);
    }

    private void disabledWpp(FragmentActivity activity) {
        binding.statusIcon2.setImageResource(R.drawable.ic_round_error_outline_24);
        binding.statusTitle2.setText("Whatsapp não está rodando ou não foi iniciado");
        binding.status2.setCardBackgroundColor(activity.getColor(rikka.material.R.color.material_red_500));
        binding.statusSummary1.setVisibility(View.GONE);
    }

    private static void checkWpp(FragmentActivity activity) {
        Intent checkWpp = new Intent(BuildConfig.APPLICATION_ID + ".CHECK_WPP");
        checkWpp.putExtra("CHECK_ENABLED", true);
        activity.sendBroadcast(checkWpp);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}