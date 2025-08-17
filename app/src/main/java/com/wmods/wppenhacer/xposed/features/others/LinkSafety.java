package com.wmods.wppenhacer.xposed.features.others;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;
import com.wmods.wppenhacer.xposed.core.WppCore;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class LinkSafety extends Feature {

    private static final String EXTRA_BYPASS = "wae.link_safety.bypass";
    private static volatile String BYPASS_URL = "";
    private static volatile long BYPASS_UNTIL = 0L;

    private static final String ACTION_LINK_PROMPT = "com.wmods.wppenhacer.action.LINK_PROMPT";
    private static final String ACTION_ADD_TRUST = "com.wmods.wppenhacer.action.ADD_TRUSTED_DOMAIN";
    private static final String ACTION_ADD_BLOCK = "com.wmods.wppenhacer.action.ADD_BLOCKED_DOMAIN";
    private static final String NOTIF_CHANNEL_ID = "wae_link_safety";
    private static final int NOTIF_ID = 424242;

    private static final Set<String> DEFAULT_SHORTLINKS = new HashSet<String>(Arrays.asList(
            "bit.ly","t.co","tinyurl.com","goo.gl","is.gd","buff.ly","cutt.ly","ow.ly","rebrand.ly","t.ly"
    ));

    public LinkSafety(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override public void doHook() throws Throwable {
        if (!prefs.getBoolean("link_safety_enabled", false)) return;

        XposedHelpers.findAndHookMethod(Activity.class, "startActivity", Intent.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                handleStartActivity(param, (Activity) param.thisObject, (Intent) param.args[0]);
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "startActivity", Intent.class, Bundle.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                handleStartActivity(param, (Activity) param.thisObject, (Intent) param.args[0]);
            }
        });
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    handleStartActivity(param, (Activity) param.thisObject, (Intent) param.args[0]);
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    handleStartActivity(param, (Activity) param.thisObject, (Intent) param.args[0]);
                }
            });
        } catch (Throwable ignored) {}

        XposedHelpers.findAndHookMethod(android.content.ContextWrapper.class, "startActivity", Intent.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Context ctx = (Context) param.thisObject;
                handleContextStart(param, ctx, (Intent) param.args[0]);
            }
        });
        XposedHelpers.findAndHookMethod(android.content.ContextWrapper.class, "startActivity", Intent.class, Bundle.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Context ctx = (Context) param.thisObject;
                handleContextStart(param, ctx, (Intent) param.args[0]);
            }
        });

        try {
            Class<?> frag = XposedHelpers.findClassIfExists("androidx.fragment.app.Fragment", classLoader);
            if (frag != null) {
                XposedHelpers.findAndHookMethod(frag, "startActivity", Intent.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object f = param.thisObject;
                        Context ctx = (Context) XposedHelpers.callMethod(f, "getContext");
                        handleContextStart(param, ctx, (Intent) param.args[0]);
                    }
                });
                XposedHelpers.findAndHookMethod(frag, "startActivity", Intent.class, Bundle.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object f = param.thisObject;
                        Context ctx = (Context) XposedHelpers.callMethod(f, "getContext");
                        handleContextStart(param, ctx, (Intent) param.args[0]);
                    }
                });
                XposedHelpers.findAndHookMethod(frag, "startActivityForResult", Intent.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object f = param.thisObject;
                        Context ctx = (Context) XposedHelpers.callMethod(f, "getContext");
                        handleContextStart(param, ctx, (Intent) param.args[0]);
                    }
                });
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> cti = XposedHelpers.findClassIfExists("androidx.browser.customtabs.CustomTabsIntent", classLoader);
            if (cti != null) {
                XposedHelpers.findAndHookMethod(cti, "launchUrl", Context.class, Uri.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Context ctx = (Context) param.args[0];
                        Uri uri = (Uri) param.args[1];
                        if (ctx == null || uri == null) return;
                        if (shouldBypassUri(uri)) return;
                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                        handleContextStart(param, ctx, i);
                    }
                });
            }
        } catch (Throwable ignored) {}

        try {
            XposedBridge.hookAllMethods(Instrumentation.class, "execStartActivity", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Intent in = null;
                        for (Object a : param.args) { if (a instanceof Intent) { in = (Intent) a; break; } }
                        if (in == null) return;
                        Context ctx = null;
                        if (param.args.length > 0 && param.args[0] instanceof Context) ctx = (Context) param.args[0];
                        if (ctx == null) ctx = WppCore.getCurrentActivity();
                        if (ctx == null) return;
                        if (shouldBypassUri(in.getData())) return;
                        handleContextStart(param, ctx, in);
                    } catch (Throwable t) { XposedBridge.log(t); }
                }
            });
        } catch (Throwable ignored) {}
    }

    private boolean shouldBypassUri(Uri uri) {
        if (uri == null) return false;
        if (!TextUtils.isEmpty(BYPASS_URL)) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now <= BYPASS_UNTIL && uri.toString().equals(BYPASS_URL)) return true;
        }
        return false;
    }

    private void handleContextStart(XC_MethodHook.MethodHookParam param, Context context, Intent intent) {
        try {
            if (context == null || intent == null) return;
            if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;
            Uri data = intent.getData();
            if (data == null) return;
            String scheme = data.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return;
            if (intent.hasExtra(EXTRA_BYPASS) || shouldBypassUri(data)) return;

            String pkg = context.getPackageName();
            if (!FeatureLoader.PACKAGE_WPP.equals(pkg) && !FeatureLoader.PACKAGE_BUSINESS.equals(pkg)) return;

            Activity activity = WppCore.getCurrentActivity();
            if (activity == null) return;

            handleStartActivity(param, activity, intent);
        } catch (Throwable t) { XposedBridge.log(t); }
    }

    private void handleStartActivity(XC_MethodHook.MethodHookParam param, Activity activity, Intent intent) {
        try {
            if (intent == null) return;
            if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;
            Uri data = intent.getData();
            if (data == null) return;
            String scheme = data.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return;
            if (intent.hasExtra(EXTRA_BYPASS) || shouldBypassUri(data)) return;

            String pkg = activity.getPackageName();
            if (!FeatureLoader.PACKAGE_WPP.equals(pkg) && !FeatureLoader.PACKAGE_BUSINESS.equals(pkg)) return;

            String original = data.toString();
            ParsedLink parsed = parseAndClean(original);

            boolean warnShort = prefs.getBoolean("link_safety_warn_shortlinks", true) && isShortLink(parsed.host);
            boolean blocked = isBlocked(parsed.host);
            boolean silent = prefs.getBoolean("link_safety_silent_mode", false);
            boolean trusted = isAllowlisted(parsed.host);

            if (!blocked && (trusted || (silent && !warnShort))) {
                Intent i2 = new Intent(intent);
                i2.setData(Uri.parse(parsed.cleaned));
                i2.putExtra(EXTRA_BYPASS, true);
                BYPASS_URL = parsed.cleaned;
                BYPASS_UNTIL = android.os.SystemClock.uptimeMillis() + 5000;
                activity.startActivity(i2);
                param.setResult(null);
                return;
            }

            if (!warnShort && !blocked && parsed.cleaned.equals(original)) {
                return;
            }

            try { prefs.reload(); } catch (Throwable ignored) {}
            String uiMode = prefs.getString("link_safety_ui_mode", "wa_dialog");
            boolean preferModule = "module".equals(uiMode);
            boolean preferWa = "wa_dialog".equals(uiMode);

            if (preferWa) {
                if (showWaDialog(activity, intent, original, parsed, warnShort, blocked)) { param.setResult(null); return; }
                if (launchModulePrompt(activity, original, parsed, warnShort, blocked)) { param.setResult(null); return; }
            } else if (preferModule) {
                if (launchModulePrompt(activity, original, parsed, warnShort, blocked)) { param.setResult(null); return; }
                if (showWaDialog(activity, intent, original, parsed, warnShort, blocked)) { param.setResult(null); return; }
            } else {
                if (launchModulePrompt(activity, original, parsed, warnShort, blocked)) { param.setResult(null); return; }
                if (showWaDialog(activity, intent, original, parsed, warnShort, blocked)) { param.setResult(null); return; }
            }

            if (!blocked) {
                Intent i = new Intent(intent);
                i.setData(Uri.parse(parsed.cleaned));
                i.putExtra(EXTRA_BYPASS, true);
                BYPASS_URL = parsed.cleaned;
                BYPASS_UNTIL = android.os.SystemClock.uptimeMillis() + 5000;
                activity.startActivity(i);
                param.setResult(null);
                return;
            } else {
                enqueuePromptNotification(activity, original, parsed, warnShort, true);
                Toast.makeText(activity, "Blocked by Link Safety (tap the notification to review)", Toast.LENGTH_LONG).show();
                param.setResult(null);
                return;
            }
        } catch (Throwable t) { XposedBridge.log(t); }
    }

    private boolean showWaDialog(Activity activity, Intent origIntent, String original, ParsedLink parsed, boolean warnShort, boolean blocked) {
        try {
            activity.runOnUiThread(new Runnable() {
                @Override public void run() {
                    showInterstitial(activity, original, parsed, warnShort, blocked, new Runnable() {
                        @Override public void run() {
                            try {
                                Intent i = new Intent(origIntent);
                                i.setData(Uri.parse(parsed.cleaned));
                                i.putExtra(EXTRA_BYPASS, true);
                                BYPASS_URL = parsed.cleaned;
                                BYPASS_UNTIL = android.os.SystemClock.uptimeMillis() + 5000;
                                activity.startActivity(i);
                            } catch (Throwable t) { XposedBridge.log(t); }
                        }
                    });
                }
            });
            return true;
        } catch (Throwable t) { XposedBridge.log(t); return false; }
    }

    private boolean launchModulePrompt(Activity activity, String original, ParsedLink parsed, boolean warnShort, boolean blocked) {
        try {
            String modulePkg = prefs.getString("module_package", "com.wmods.wppenhacer");
            Intent prompt = new Intent(ACTION_LINK_PROMPT);
            prompt.addCategory(Intent.CATEGORY_DEFAULT);
            prompt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            prompt.putExtra("original", original);
            prompt.putExtra("cleaned", parsed.cleaned);
            prompt.putExtra("host", parsed.host);
            prompt.putExtra("warnShort", warnShort);
            prompt.putExtra("blocked", blocked);

            PackageManager pm = activity.getPackageManager();
            if (!TextUtils.isEmpty(modulePkg)) prompt.setPackage(modulePkg);
            List<ResolveInfo> ri = pm.queryIntentActivities(prompt, 0);
            if (ri == null || ri.isEmpty()) {
                prompt.setPackage(null);
                ri = pm.queryIntentActivities(prompt, 0);
                if (ri == null || ri.isEmpty()) {
                    XposedBridge.log("[LinkSafety] No activity resolves ACTION_LINK_PROMPT");
                    return false;
                }
            }
            activity.startActivity(prompt);
            return true;
        } catch (Throwable t) { XposedBridge.log(t); return false; }
    }

    private void enqueuePromptNotification(Context ctx, String original, ParsedLink parsed, boolean warnShort, boolean blocked) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(NOTIF_CHANNEL_ID, "Link Safety", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Intent prompt = new Intent(ACTION_LINK_PROMPT);
            prompt.addCategory(Intent.CATEGORY_DEFAULT);
            prompt.putExtra("original", original);
            prompt.putExtra("cleaned", parsed.cleaned);
            prompt.putExtra("host", parsed.host);
            prompt.putExtra("warnShort", warnShort);
            prompt.putExtra("blocked", blocked);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(ctx, 1, prompt, flags);

            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, NOTIF_CHANNEL_ID) : new Notification.Builder(ctx);
            nb.setContentTitle("Link blocked: " + parsed.host)
              .setContentText("Tap to review and open safely")
              .setSmallIcon(android.R.drawable.ic_dialog_info)
              .setAutoCancel(true)
              .setContentIntent(pi);
            nm.notify(NOTIF_ID, nb.build());
        } catch (Throwable t) { XposedBridge.log(t); }
    }

    private static class ParsedLink { String host = ""; String cleaned = ""; }

    private ParsedLink parseAndClean(String url) {
        ParsedLink p = new ParsedLink();
        p.cleaned = url;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null && !TextUtils.isEmpty(uri.getAuthority())) host = uri.getAuthority();
            if (host != null) p.host = IDN.toUnicode(host.toLowerCase(Locale.ROOT));
            boolean stripUtm = prefs.getBoolean("link_safety_strip_utm", true);
            if (stripUtm) {
                Map<String, String> q = splitQuery(uri.getRawQuery());
                if (!q.isEmpty()) {
                    Map<String, String> kept = new LinkedHashMap<String, String>();
                    for (Map.Entry<String, String> e : q.entrySet()) {
                        String k = e.getKey() != null ? e.getKey().toLowerCase(Locale.ROOT) : "";
                        if (k.startsWith("utm_") || k.equals("fbclid") || k.equals("_hsenc") || k.equals("_hsmi")) continue;
                        kept.put(e.getKey(), e.getValue());
                    }
                    String newQuery = joinQuery(kept);
                    URI cleaned = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), newQuery, uri.getFragment());
                    p.cleaned = cleaned.toString();
                }
            }
        } catch (URISyntaxException ignored) {}
        return p;
    }

    private Map<String, String> splitQuery(String raw) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (raw == null || raw.isEmpty()) return out;
        String[] pairs = raw.split("&");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            int idx = pair.indexOf('=');
            if (idx > 0) out.put(Uri.decode(pair.substring(0, idx)), Uri.decode(pair.substring(idx + 1)));
            else if (!pair.isEmpty()) out.put(Uri.decode(pair), "");
        }
        return out;
    }

    private String joinQuery(Map<String, String> m) {
        if (m.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(Uri.encode(e.getKey()));
            if (e.getValue() != null && !e.getValue().isEmpty()) sb.append('=').append(Uri.encode(e.getValue()));
        }
        return sb.toString();
    }

    private Set<String> getShortlinkHosts() {
        Set<String> out = new HashSet<String>(DEFAULT_SHORTLINKS);
        String custom = prefs.getString("link_safety_shortlinks_list", "");
        if (!TextUtils.isEmpty(custom)) {
            String[] arr = custom.split(",");
            for (int i = 0; i < arr.length; i++) {
                String t = arr[i].trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    private boolean isShortLink(String host) {
        if (TextUtils.isEmpty(host)) return false;
        return getShortlinkHosts().contains(host.toLowerCase(Locale.ROOT));
    }

    private boolean matchesList(String host, String csv) {
        if (TextUtils.isEmpty(csv) || TextUtils.isEmpty(host)) return false;
        String h = host.toLowerCase(Locale.ROOT);
        String[] tokens = csv.split(",");
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i].trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            if (h.equals(t) || h.endsWith("." + t)) return true;
        }
        return false;
    }

    private boolean isBlocked(String host) {
        String raw = prefs.getString("link_safety_blocklist", "");
        return matchesList(host, raw);
    }

    private boolean isAllowlisted(String host) {
        if (TextUtils.isEmpty(host)) return false;
        try { prefs.reload(); } catch (Throwable ignored) {}
        String raw = prefs.getString("link_safety_allowlist", "");
        return matchesList(host, raw);
    }

    private void showInterstitial(final Activity activity, final String originalUrl, final ParsedLink parsed,
                                  boolean warnShort, final boolean blocked, final Runnable onOpen) {
        try {
            AlertDialog.Builder b = new AlertDialog.Builder(activity);
            b.setTitle("Link Safety");

            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (activity.getResources().getDisplayMetrics().density * 16);
            layout.setPadding(pad, pad, pad, pad);

            TextView domain = new TextView(activity);
            domain.setText("Domain: " + parsed.host);
            layout.addView(domain);

            if (!TextUtils.equals(originalUrl, parsed.cleaned)) {
                TextView cleaned = new TextView(activity);
                cleaned.setText("Cleaned URL: " + parsed.cleaned);
                layout.addView(cleaned);
            }

            if (warnShort) {
                TextView warn = new TextView(activity);
                warn.setText("⚠ Short link (destination unknown)");
                layout.addView(warn);
            }
            if (blocked) {
                TextView warn = new TextView(activity);
                warn.setText("⛔ Blocked domain (change in settings if needed).");
                layout.addView(warn);
            }

            final CheckBox trust = new CheckBox(activity);
            trust.setText("Always trust this domain");
            layout.addView(trust);

            b.setView(layout);
            b.setCancelable(true);

            // Positive: Open
            b.setPositiveButton("Open", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    if (!blocked) {
                        try {
                            if (trust.isChecked() && !TextUtils.isEmpty(parsed.host)) {
                                Intent sync = new Intent(ACTION_ADD_TRUST);
                                String modulePkg = prefs.getString("module_package", "com.wmods.wppenhacer");
                                if (!TextUtils.isEmpty(modulePkg)) sync.setPackage(modulePkg);
                                sync.putExtra("host", parsed.host);
                                activity.sendBroadcast(sync);
                            }
                        } catch (Throwable ignored) {}
                        onOpen.run();
                    } else {
                        Toast.makeText(activity, "Blocked by Link Safety", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // Neutral: Copy
            b.setNeutralButton("Copy", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    try {
                        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("url", parsed.cleaned));
                        Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) { XposedBridge.log(t); }
                }
            });

            // Negative: Block (new)
            b.setNegativeButton("Block", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    try {
                        if (!TextUtils.isEmpty(parsed.host)) {
                            Intent sync = new Intent(ACTION_ADD_BLOCK);
                            String modulePkg = prefs.getString("module_package", "com.wmods.wppenhacer");
                            if (!TextUtils.isEmpty(modulePkg)) sync.setPackage(modulePkg);
                            sync.putExtra("host", parsed.host);
                            activity.sendBroadcast(sync);
                            Toast.makeText(activity, "Blocked " + parsed.host, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable t) { XposedBridge.log(t); }
                }
            });

            b.show();
        } catch (Throwable t) { XposedBridge.log(t); }
    }

    @NonNull @Override public String getPluginName() { return "Link Safety"; }
}
