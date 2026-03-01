package com.wmods.wppenhacer.ui.helpers;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.wmods.wppenhacer.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Global helper for showing professional bottom sheets throughout the app.
 * Replaces AlertDialogs with consistent EU-aesthetic bottom sheets.
 */
public class BottomSheetHelper {

    public interface OnConfirmListener {
        void onConfirm();
    }

    public interface OnInputConfirmListener {
        void onConfirm(String input);
    }

    // Extracted only the contributor profile methods.

    /**
     * Create a styled BottomSheetDialog with transparent background.
     */
    private static BottomSheetDialog createDialog(Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });
        return dialog;
    }

    private static final okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

    public static void showUserProfile(
            Context context,
            String username,
            String avatarUrl,
            String htmlUrl,
            int contributions) {

        BottomSheetDialog bottomSheet = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_user_profile, null);
        bottomSheet.setContentView(view);

        com.google.android.material.imageview.ShapeableImageView ivAvatar = view.findViewById(R.id.bsAvatar);
        com.google.android.material.textview.MaterialTextView tvName = view.findViewById(R.id.bsName);
        com.google.android.material.textview.MaterialTextView tvUsername = view.findViewById(R.id.bsUsername);

        com.facebook.shimmer.ShimmerFrameLayout shimmerLayout = view.findViewById(R.id.bsShimmerLayout);
        View contentLayout = view.findViewById(R.id.bsContentLayout);

        com.bumptech.glide.Glide.with(context)
                .load(new com.bumptech.glide.load.model.GlideUrl(avatarUrl,
                        new com.bumptech.glide.load.model.LazyHeaders.Builder()
                                .addHeader("User-Agent", "WaEnhancer-App")
                                .build()))
                .placeholder(R.drawable.ic_github)
                .into(ivAvatar);

        tvName.setText(username);
        tvUsername.setText("@" + username);

        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        contentLayout.setVisibility(View.GONE);

        bottomSheet.show();

        android.content.SharedPreferences prefs = context.getSharedPreferences("github_user_cache",
                Context.MODE_PRIVATE);
        long lastFetch = prefs.getLong(username + "_time", 0);
        String cachedJson = prefs.getString(username + "_json", null);

        if (cachedJson != null && (System.currentTimeMillis() - lastFetch < 3600000)) {
            parseAndPopulateProfile(context, view, bottomSheet, cachedJson, htmlUrl, avatarUrl, contributions);
            return;
        }

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://api.github.com/users/" + username)
                .header("User-Agent", "WaEnhancer-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull okhttp3.Call call,
                    @androidx.annotation.NonNull java.io.IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (cachedJson != null) {
                        parseAndPopulateProfile(context, view, bottomSheet, cachedJson, htmlUrl, avatarUrl,
                                contributions);
                    } else {
                        android.widget.Toast
                                .makeText(context, "Failed to load user profile", android.widget.Toast.LENGTH_SHORT)
                                .show();
                        bottomSheet.dismiss();
                    }
                });
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull okhttp3.Call call,
                    @androidx.annotation.NonNull okhttp3.Response response) throws java.io.IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (cachedJson != null) {
                            parseAndPopulateProfile(context, view, bottomSheet, cachedJson, htmlUrl, avatarUrl,
                                    contributions);
                        } else {
                            android.widget.Toast
                                    .makeText(context, "Error fetching user", android.widget.Toast.LENGTH_SHORT).show();
                            bottomSheet.dismiss();
                        }
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    prefs.edit()
                            .putLong(username + "_time", System.currentTimeMillis())
                            .putString(username + "_json", json)
                            .apply();

                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> parseAndPopulateProfile(context, view, bottomSheet, json, htmlUrl, avatarUrl,
                                    contributions));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void parseAndPopulateProfile(Context context, View view, BottomSheetDialog bottomSheet, String json,
            String fallbackHtmlUrl, String avatarUrl, int contributions) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            String name = obj.optString("name", "");
            String login = obj.optString("login", "");
            String location = obj.optString("location", "");
            String bio = obj.optString("bio", "");
            boolean hireable = obj.optBoolean("hireable", false);
            int followers = obj.optInt("followers", 0);
            String twitter = obj.optString("twitter_username", "");
            String blog = obj.optString("blog", "");
            String htmlUrl = obj.optString("html_url", fallbackHtmlUrl);

            if (name.isEmpty() || name.equals("null"))
                name = login;

            com.google.android.material.textview.MaterialTextView tvName = view.findViewById(R.id.bsName);
            com.google.android.material.textview.MaterialTextView tvLocation = view.findViewById(R.id.bsLocation);
            com.google.android.material.textview.MaterialTextView tvFollowers = view.findViewById(R.id.bsFollowers);
            com.google.android.material.textview.MaterialTextView tvBio = view.findViewById(R.id.bsBio);
            com.google.android.material.textview.MaterialTextView tvContributions = view
                    .findViewById(R.id.bsContributions);

            android.widget.ImageView ivLocationIcon = view.findViewById(R.id.bsLocationIcon);
            View locationContainer = view.findViewById(R.id.bsLocationContainer);
            View chipsScroll = view.findViewById(R.id.bsChipsScroll);

            com.google.android.material.chip.Chip chipHireable = view.findViewById(R.id.chipHireable);
            com.google.android.material.chip.Chip chipTwitter = view.findViewById(R.id.chipTwitter);

            com.google.android.material.button.MaterialButton btnGithub = view.findViewById(R.id.bsGithubBtn);
            com.google.android.material.button.MaterialButton btnWebsite = view.findViewById(R.id.bsWebsiteBtn);
            com.google.android.material.button.MaterialButton btnContributions = view
                    .findViewById(R.id.bsContributionsBtn);

            tvName.setText(name);

            boolean hasLocation = location != null && !location.isEmpty() && !location.equals("null");
            if (hasLocation || followers > 0 || contributions > 0) {
                locationContainer.setVisibility(View.VISIBLE);
                if (hasLocation) {
                    ivLocationIcon.setVisibility(View.VISIBLE);
                    tvLocation.setVisibility(View.VISIBLE);
                    tvLocation.setText(location);
                } else {
                    ivLocationIcon.setVisibility(View.GONE);
                    tvLocation.setVisibility(View.GONE);
                }
                if (followers > 0) {
                    tvFollowers.setVisibility(View.VISIBLE);
                    tvFollowers.setText(hasLocation ? "• " + followers + " followers" : followers + " followers");
                } else {
                    tvFollowers.setVisibility(View.GONE);
                }
                if (contributions > 0) {
                    tvContributions.setVisibility(View.VISIBLE);
                    boolean hasPrev = hasLocation || followers > 0;
                    tvContributions.setText(hasPrev ? "• " + contributions + " commits" : contributions + " commits");

                    btnContributions.setVisibility(View.VISIBLE);
                    btnContributions.setVisibility(View.VISIBLE);
                    final String finalName = name;
                    btnContributions.setOnClickListener(v -> {
                        bottomSheet.dismiss();
                        showContributions(context, login, finalName, avatarUrl, htmlUrl, contributions);
                    });
                } else {
                    tvContributions.setVisibility(View.GONE);
                    btnContributions.setVisibility(View.GONE);
                }
            }

            if (bio != null && !bio.isEmpty() && !bio.equals("null")) {
                tvBio.setVisibility(View.VISIBLE);
                tvBio.setText(bio);
            }

            boolean hasChips = false;
            if (hireable) {
                hasChips = true;
                chipHireable.setVisibility(View.VISIBLE);
                chipHireable.setText("Open to hire");
            }

            if (twitter != null && !twitter.isEmpty() && !twitter.equals("null")) {
                hasChips = true;
                chipTwitter.setVisibility(View.VISIBLE);
                chipTwitter.setText("@" + twitter);
                chipTwitter.setOnClickListener(v -> {
                    try {
                        context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://x.com/" + twitter)));
                    } catch (Exception ignored) {
                    }
                });
            }

            if (blog != null && !blog.isEmpty() && !blog.equals("null")) {
                btnWebsite.setVisibility(View.VISIBLE);
                btnWebsite.setOnClickListener(v -> {
                    String url = blog;
                    if (!url.startsWith("http"))
                        url = "https://" + url;
                    try {
                        context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)));
                    } catch (Exception ignored) {
                    }
                });
            }

            if (hasChips) {
                chipsScroll.setVisibility(View.VISIBLE);
            }

            btnGithub.setOnClickListener(v -> {
                try {
                    context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(htmlUrl)));
                    bottomSheet.dismiss();
                } catch (Exception ignored) {
                }
            });

            com.facebook.shimmer.ShimmerFrameLayout shimmerLayout = view.findViewById(R.id.bsShimmerLayout);
            View contentLayout = view.findViewById(R.id.bsContentLayout);

            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            contentLayout.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(context, "Failed to parse profile", android.widget.Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        }
    }

    private static void showContributions(Context context, String login, String displayName, String avatarUrl,
            String htmlUrl, int contributions) {
        BottomSheetDialog bottomSheet = createDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_contributions, null);
        bottomSheet.setContentView(view);

        android.widget.ImageButton btnBack = view.findViewById(R.id.bsContribBackBtn);
        btnBack.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showUserProfile(context, login, avatarUrl, htmlUrl, contributions);
        });

        com.google.android.material.textview.MaterialTextView tvTitle = view.findViewById(R.id.bsContribTitle);
        tvTitle.setText(displayName + "'s Contributions");

        com.facebook.shimmer.ShimmerFrameLayout shimmerLayout = view.findViewById(R.id.bsContribShimmer);
        View contentLayout = view.findViewById(R.id.bsContribContent);

        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        contentLayout.setVisibility(View.GONE);

        bottomSheet.show();

        android.content.SharedPreferences prefs = context.getSharedPreferences("github_user_cache",
                Context.MODE_PRIVATE);
        long lastFetch = prefs.getLong("repo_stats_time", 0);
        String cachedJson = prefs.getString("repo_stats_json", null);

        if (cachedJson != null && (System.currentTimeMillis() - lastFetch < 3600000)) {
            parseAndPopulateContributions(context, view, bottomSheet, cachedJson, login);
            return;
        }

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://api.github.com/repos/mubashardev/WaEnhancer/stats/contributors")
                .header("User-Agent", "WaEnhancer-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull okhttp3.Call call,
                    @androidx.annotation.NonNull java.io.IOException e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (cachedJson != null) {
                        parseAndPopulateContributions(context, view, bottomSheet, cachedJson, login);
                    } else {
                        android.widget.Toast
                                .makeText(context, "Failed to load contributions", android.widget.Toast.LENGTH_SHORT)
                                .show();
                        bottomSheet.dismiss();
                    }
                });
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull okhttp3.Call call,
                    @androidx.annotation.NonNull okhttp3.Response response) throws java.io.IOException {

                // GitHub stats API can return 202 Accepted if calculating
                if (!response.isSuccessful() || response.body() == null || response.code() == 202) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (cachedJson != null) {
                            parseAndPopulateContributions(context, view, bottomSheet, cachedJson, login);
                        } else {
                            android.widget.Toast.makeText(context, "GitHub is calculating stats, try again later.",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            bottomSheet.dismiss();
                        }
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    prefs.edit()
                            .putLong("repo_stats_time", System.currentTimeMillis())
                            .putString("repo_stats_json", json)
                            .apply();

                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> parseAndPopulateContributions(context, view, bottomSheet, json, login));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void parseAndPopulateContributions(Context context, View view, BottomSheetDialog bottomSheet,
            String json, String login) {
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray(json);
            int totalCommits = 0;
            long linesAdded = 0;
            long linesDeleted = 0;

            for (int i = 0; i < jsonArray.length(); i++) {
                org.json.JSONObject obj = jsonArray.getJSONObject(i);
                org.json.JSONObject author = obj.optJSONObject("author");
                if (author != null) {
                    String authorLogin = author.optString("login");
                    if (authorLogin.equalsIgnoreCase(login)) {
                        totalCommits = obj.optInt("total", 0);
                        org.json.JSONArray weeks = obj.optJSONArray("weeks");
                        if (weeks != null) {
                            for (int j = 0; j < weeks.length(); j++) {
                                org.json.JSONObject week = weeks.getJSONObject(j);
                                linesAdded += week.optLong("a", 0);
                                linesDeleted += week.optLong("d", 0);
                            }
                        }
                        break;
                    }
                }
            }

            com.google.android.material.textview.MaterialTextView tvTotal = view.findViewById(R.id.bsContribTotal);
            com.google.android.material.textview.MaterialTextView tvAdded = view.findViewById(R.id.bsContribAdded);
            com.google.android.material.textview.MaterialTextView tvDeleted = view.findViewById(R.id.bsContribDeleted);
            com.facebook.shimmer.ShimmerFrameLayout shimmerLayout = view.findViewById(R.id.bsContribShimmer);
            View contentLayout = view.findViewById(R.id.bsContribContent);

            java.text.NumberFormat format = java.text.NumberFormat.getInstance();
            tvTotal.setText(format.format(totalCommits));
            tvAdded.setText("+ " + format.format(linesAdded));
            tvDeleted.setText("~ " + format.format(linesDeleted));

            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            contentLayout.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(context, "Failed to parse contributions", android.widget.Toast.LENGTH_SHORT)
                    .show();
            bottomSheet.dismiss();
        }
    }
}
