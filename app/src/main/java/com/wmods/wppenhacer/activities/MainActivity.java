package com.wmods.wppenhacer.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.navigation.NavigationBarView;
import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.adapter.MainPagerAdapter;
import com.wmods.wppenhacer.databinding.ActivityMainBinding;
import com.wmods.wppenhacer.utils.FilePicker;

import java.io.File;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.changeLanguage(this);
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setIcon(R.mipmap.launcher);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);

        binding.navView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @SuppressLint("NonConstantResourceId")
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                return switch (item.getItemId()) {
                    case R.id.navigation_home -> {
                        binding.viewPager.setCurrentItem(0);
                        yield true;
                    }
                    case R.id.navigation_chat -> {
                        binding.viewPager.setCurrentItem(1);
                        yield true;
                    }
                    case R.id.navigation_privacy -> {
                        binding.viewPager.setCurrentItem(2);
                        yield true;
                    }
                    case R.id.navigation_media -> {
                        binding.viewPager.setCurrentItem(3);
                        yield true;
                    }
                    case R.id.navigation_colors -> {
                        binding.viewPager.setCurrentItem(4);
                        yield true;
                    }
                    default -> false;
                };
            }
        });

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                binding.navView.getMenu().getItem(position).setChecked(true);
            }
        });
        createMainDir();
        FilePicker.registerFilePicker(this);
    }


    private void createMainDir() {
        var nomedia = new File(App.getWaEnhancerFolder(), ".nomedia");
        if (nomedia.exists()) {
            nomedia.delete();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.header_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static boolean isXposedEnabled() {
        return false;
    }

}