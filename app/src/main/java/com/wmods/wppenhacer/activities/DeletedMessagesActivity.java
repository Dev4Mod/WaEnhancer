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
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DeletedMessagesFragment())
                    .commit();
        }
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
