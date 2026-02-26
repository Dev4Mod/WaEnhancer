package com.wmods.wppenhacer.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.navigation.NavigationBarView;
import com.waseemsabir.betterypermissionhelper.BatteryPermissionHelper;
import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.adapter.MainPagerAdapter;
import com.wmods.wppenhacer.databinding.ActivityMainBinding;
import com.wmods.wppenhacer.ui.fragments.GeneralFragment;
import com.wmods.wppenhacer.ui.fragments.HomeFragment;
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment;
import com.wmods.wppenhacer.utils.FilePicker;

import java.io.File;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private BatteryPermissionHelper batteryPermissionHelper = BatteryPermissionHelper.Companion.getInstance();
    private String pendingScrollToPreference = null;
    private int pendingScrollToFragment = -1;
    private String pendingParentKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.changeLanguage(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);

        binding.viewPager.setPageTransformer(new DepthPageTransformer());

        var prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean("call_recording_enable", false)) {
            binding.navView.getMenu().findItem(R.id.navigation_recordings).setVisible(false);
        }

        binding.navView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @SuppressLint("NonConstantResourceId")
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                return switch (item.getItemId()) {
                    case R.id.navigation_chat -> {
                        binding.viewPager.setCurrentItem(0, true);
                        yield true;
                    }
                    case R.id.navigation_privacy -> {
                        binding.viewPager.setCurrentItem(1, true);
                        yield true;
                    }
                    case R.id.navigation_home -> {
                        binding.viewPager.setCurrentItem(2, true);
                        yield true;
                    }
                    case R.id.navigation_media -> {
                        binding.viewPager.setCurrentItem(3, true);
                        yield true;
                    }
                    case R.id.navigation_colors -> {
                        binding.viewPager.setCurrentItem(4, true);
                        yield true;
                    }
                    case R.id.navigation_recordings -> {
                        binding.viewPager.setCurrentItem(5);
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
                
                // Handle pending scroll after page change
                if (pendingScrollToFragment == position && pendingScrollToPreference != null) {
                    final String scrollKey = pendingScrollToPreference;
                    final String parentKey = pendingParentKey;
                    pendingScrollToPreference = null;
                    pendingScrollToFragment = -1;
                    pendingParentKey = null;
                    
                    // Wait for fragment to be ready
                    binding.viewPager.postDelayed(() -> {
                        scrollToPreferenceInCurrentFragment(scrollKey, parentKey);
                    }, 300);
                }
            }
        });
        binding.viewPager.setCurrentItem(2, false);
        createMainDir();
        FilePicker.registerFilePicker(this);
        
        // Handle incoming navigation from search
        handleIncomingIntent(getIntent());
    }

    private void createMainDir() {
        var nomedia = new File(App.getWaEnhancerFolder(), ".nomedia");
        if (nomedia.exists()) {
            nomedia.delete();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }
    
    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        
        int fragmentPosition = intent.getIntExtra("navigate_to_fragment", -1);
        String preferenceKey = intent.getStringExtra("scroll_to_preference");
        String parentKey = intent.getStringExtra("parent_preference");
        
        if (fragmentPosition >= 0 && preferenceKey != null) {
            // Store the scroll target
            pendingScrollToPreference = preferenceKey;
            pendingScrollToFragment = fragmentPosition;
            pendingParentKey = parentKey;
            
            // Navigate to the fragment (onPageSelected will handle the scroll)
            binding.viewPager.setCurrentItem(fragmentPosition, false);
            
            // Clear intent extras
            intent.removeExtra("navigate_to_fragment");
            intent.removeExtra("scroll_to_preference");
            intent.removeExtra("parent_preference");
        } else if (fragmentPosition >= 0) {
            // Just navigate without scrolling
            binding.viewPager.setCurrentItem(fragmentPosition, true);
        }
    }
    
    private void scrollToPreferenceInCurrentFragment(String preferenceKey, String parentKey) {
        // Get the current fragment from the ViewPager
        int currentItem = binding.viewPager.getCurrentItem();
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + currentItem);
        
        if (fragment == null) return;
        
        // Handle different fragment types
        if (fragment instanceof GeneralFragment || fragment instanceof HomeFragment) {
            // These fragments have child fragments
            if (parentKey != null && !parentKey.isEmpty()) {
                // Navigate to sub-fragment first, then scroll
                navigateToSubFragmentAndScroll(fragment, parentKey, preferenceKey);
            } else {
                // Direct scroll in current child fragment
                scrollInChildFragment(fragment, preferenceKey);
            }
        } else if (fragment instanceof BasePreferenceFragment) {
            // Direct preference fragments (no nesting)
            ((BasePreferenceFragment) fragment).scrollToPreference(preferenceKey);
        }
    }
    
    private void navigateToSubFragmentAndScroll(Fragment parentFragment, String parentKey, String childPreferenceKey) {
        // Directly instantiate the sub-fragment
        Fragment subFragment = null;
        
        switch (parentKey) {
            case "general_home":
                subFragment = new GeneralFragment.HomeGeneralPreference();
                break;
            case "homescreen":
                subFragment = new GeneralFragment.HomeScreenGeneralPreference();
                break;
            case "conversation":
                subFragment = new GeneralFragment.ConversationGeneralPreference();
                break;
        }
        
        if (subFragment != null && parentFragment.getView() != null) {
            final Fragment finalSubFragment = subFragment;
            // Replace the current child fragment
            parentFragment.getChildFragmentManager().beginTransaction()
                .replace(R.id.frag_container, subFragment)
                .commitNow();
            
            // Wait for fragment to be ready, then scroll
            parentFragment.getView().postDelayed(() -> {
                if (finalSubFragment instanceof BasePreferenceFragment) {
                    ((BasePreferenceFragment) finalSubFragment).scrollToPreference(childPreferenceKey);
                }
            }, 400);
        }
    }
    
    private void scrollInChildFragment(Fragment parentFragment, String preferenceKey) {
        Fragment childFragment = parentFragment.getChildFragmentManager().findFragmentById(R.id.frag_container);
        if (childFragment instanceof BasePreferenceFragment) {
            ((BasePreferenceFragment) childFragment).scrollToPreference(preferenceKey);
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
        var powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            menu.findItem(R.id.batteryoptimization).setVisible(false);
        }
        return true;
    }

    @SuppressLint("BatteryLife")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_search) {
            var options = ActivityOptionsCompat.makeCustomAnimation(
                    this, R.anim.slide_in_right, R.anim.slide_out_left);
            startActivity(new Intent(this, SearchActivity.class), options.toBundle());
            return true;
        } else if (item.getItemId() == R.id.menu_about) {
            var options = ActivityOptionsCompat.makeCustomAnimation(
                    this, R.anim.slide_in_right, R.anim.slide_out_left);
            startActivity(new Intent(this, AboutActivity.class), options.toBundle());
            return true;
        } else if (item.getItemId() == R.id.batteryoptimization) {
            if (batteryPermissionHelper.isBatterySaverPermissionAvailable(this, true)) {
                batteryPermissionHelper.getPermission(this, true, true);
            } else {
                var intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public static boolean isXposedEnabled() {
        return false;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }

    private static class DepthPageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.85f;

        @Override
        public void transformPage(@NonNull android.view.View page, float position) {
            int pageWidth = page.getWidth();

            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 0) {
                page.setAlpha(1f);
                page.setTranslationX(0f);
                page.setTranslationZ(0f);
                page.setScaleX(1f);
                page.setScaleY(1f);
            } else if (position <= 1) {
                page.setAlpha(1 - position);
                page.setTranslationX(pageWidth * -position);
                page.setTranslationZ(-1f);
                float scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position));
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
            } else {
                page.setAlpha(0f);
            }
        }
    }
}