package com.wmods.wppenhacer.activities;

import android.os.Bundle;
import android.view.MenuItem;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.databinding.ActivityDeletedMessagesBinding;
import com.wmods.wppenhacer.ui.fragments.DeletedMessagesFragment;

public class DeletedMessagesActivity extends BaseActivity {

    private ActivityDeletedMessagesBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeletedMessagesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            setupViewPager();
        }
    }

    private void setupViewPager() {
        binding.viewPager.setAdapter(new androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            @androidx.annotation.NonNull
            @Override
            public androidx.fragment.app.Fragment createFragment(int position) {
                return DeletedMessagesFragment.newInstance(position == 1); // 0 = Individual, 1 = Group
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new com.google.android.material.tabs.TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Individuals" : "Groups");
        }).attach();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
