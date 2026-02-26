package com.wmods.wppenhacer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.databinding.ActivityAboutBinding;

public class AboutActivity extends BaseActivity {

    private static final String[][] CONTRIBUTORS = {
            {"Dev4Mod", "https://github.com/Dev4Mod"},
            {"frknkrc44", "https://github.com/frknkrc44"},
            {"mubashardev", "https://github.com/mubashardev"},
            {"masbentoooredoo", "https://github.com/masbentoooredoo"},
            {"zhongerxll", "https://github.com/zhongerxll"},
            {"BryanGIG", "https://github.com/BryanGIG"},
            {"rizqi-developer", "https://github.com/rizqi-developer"},
            {"pedroborraz", "https://github.com/pedroborraz"},
            {"ahmedtohamy1", "https://github.com/ahmedtohamy1"},
            {"mohdafix", "https://github.com/mohdafix"},
            {"maulana-kurniawan", "https://github.com/maulana-kurniawan"},
            {"erzachn", "https://github.com/erzachn"},
            {"cvnertnc", "https://github.com/cvnertnc"},
            {"rkorossy", "https://github.com/rkorossy"},
            {"StupidRepo", "https://github.com/StupidRepo"},
            {"Blank517", "https://github.com/Blank517"},
            {"astola-studio", "https://github.com/astola-studio"},
            {"Strange-IPmart", "https://github.com/Strange-IPmart"}
    };


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityAboutBinding binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnTelegram.setOnClickListener(v -> openUrl("https://t.me/waenhancer"));
        binding.btnGithub.setOnClickListener(view -> openUrl("https://github.com/Dev4Mod/WaEnhancer"));

        int topMargin = getResources().getDimensionPixelSize(R.dimen.spacing_small);
        for (int i = 0; i < CONTRIBUTORS.length; i++) {
            String[] contributor = CONTRIBUTORS[i];
            MaterialButton button = new MaterialButton(new ContextThemeWrapper(this, R.style.ModernButton_Outlined));
            button.setText(contributor[0]);
            button.setIconResource(R.drawable.ic_github);
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setIconPadding(getResources().getDimensionPixelSize(R.dimen.spacing_small));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                params.topMargin = topMargin;
            }
            button.setLayoutParams(params);
            button.setOnClickListener(v -> openUrl(contributor[1]));
            binding.contributorsContainer.addView(button);
        }

    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);

    }
}
