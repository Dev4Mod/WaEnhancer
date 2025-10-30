package com.wmods.wppenhacer.adapter;

import static com.wmods.wppenhacer.xposed.features.customization.IGStatus.itens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.views.dialog.TabDialogContent;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class IGStatusAdapter extends ArrayAdapter {


    private final Class<?> clazzImageStatus;
    private final Class<?> statusInfoClazz;
    private final Method setCountStatus;
    private static Drawable cacheIcon;

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        var item = itens.get(position);
        IGStatusViewHolder holder;
        if (convertView == null) {
            holder = new IGStatusViewHolder();
            convertView = createLayoutStatus(holder);
            convertView.setTag(holder);
        } else {
            holder = (IGStatusViewHolder) convertView.getTag();
        }
        if (item == null) {
            holder.setInfo("my_status");
            holder.addButton.setVisibility(View.VISIBLE);
        } else if (statusInfoClazz.isInstance(item)) {
            if (item instanceof View v) {
                v.setClickable(false);
            }
            holder.setInfo(item);
            holder.addButton.setVisibility(View.GONE);
        }
        convertView.setOnClickListener(v -> {
            if (holder.myStatus) {
                var activity = WppCore.getCurrentActivity();
                var dialog = WppCore.createBottomDialog(activity);
                var tabdialog = new TabDialogContent(activity);
                tabdialog.setTitle(activity.getString(ResId.string.select_status_type));
                tabdialog.addTab(UnobfuscatorCache.getInstance().getString("mystatus"), DesignUtils.getIconByName("ic_status", true), (view) -> {
                    try {
                        var clazz = Unobfuscator.getClassByName("MyStatusesActivity", getContext().getClassLoader());
                        var intent = new Intent(WppCore.getCurrentActivity(), clazz);
                        WppCore.getCurrentActivity().startActivity(intent);
                    } catch (Exception e) {
                        Utils.showToast(e.getMessage(), 1);
                    }
                    dialog.dismissDialog();
                });

                // Botão da camera
                var iconCamera = DesignUtils.getDrawable(ResId.drawable.camera);
                DesignUtils.coloredDrawable(iconCamera, DesignUtils.isNightMode() ? Color.WHITE : Color.BLACK);
                tabdialog.addTab(activity.getString(ResId.string.open_camera), iconCamera, (view) -> {
                    try {
                        Intent intent = new Intent();
                        var clazz = Unobfuscator.getClassByName("CameraActivity", getContext().getClassLoader());
                        intent.setClassName(activity.getPackageName(), clazz.getName());
                        intent.putExtra("jid", "status@broadcast");
                        intent.putExtra("camera_origin", 4);
                        intent.putExtra("is_coming_from_chat", false);
                        intent.putExtra("media_sharing_user_journey_origin", 32);
                        intent.putExtra("media_sharing_user_journey_start_target", 9);
                        intent.putExtra("media_sharing_user_journey_chat_type", 4);
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Utils.showToast(e.getMessage(), 1);
                    }
                    dialog.dismissDialog();
                });
                // Botão de editar
                var iconEdit = DesignUtils.getDrawable(ResId.drawable.edit2);
                DesignUtils.coloredDrawable(iconEdit, DesignUtils.isNightMode() ? Color.WHITE : Color.BLACK);

                tabdialog.addTab(activity.getString(ResId.string.edit_text), iconEdit, (view) -> {
                    try {
                        Intent intent = new Intent();
                        Class clazz;
                        try {
                            clazz = Unobfuscator.getClassByName("TextStatusComposerActivity", activity.getClassLoader());
                        } catch (Exception ignored) {
                            clazz = Unobfuscator.getClassByName("ConsolidatedStatusComposerActivity", getContext().getClassLoader());
                            intent.putExtra("status_composer_mode", 2);
                        }
                        intent.setClassName(activity.getPackageName(), clazz.getName());
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Utils.showToast(e.getMessage(), 1);
                    }
                    dialog.dismissDialog();
                });
                dialog.setContentView(tabdialog);
                dialog.showDialog();
                return;
            }
            try {
                var clazz = Unobfuscator.getClassByName("StatusPlaybackActivity", getContext().getClassLoader());
                var intent = new Intent(WppCore.getCurrentActivity(), clazz);
                intent.putExtra("jid", holder.userJid.getPhoneRawString());
                WppCore.getCurrentActivity().startActivity(intent);
            } catch (Exception e) {
                Utils.showToast(e.getMessage(), 1);
            }
        });

        return convertView;
    }

    public IGStatusAdapter(@NonNull Context context, @NonNull Class<?> statusInfoClazz) {
        super(context, 0);
        this.clazzImageStatus = XposedHelpers.findClass("com.whatsapp.status.ContactStatusThumbnail", this.getContext().getClassLoader());
        this.statusInfoClazz = statusInfoClazz;
        this.setCountStatus = ReflectionUtils.findMethodUsingFilter(this.clazzImageStatus, m -> m.getParameterCount() == 3 && Arrays.equals(new Class[]{int.class, int.class, int.class}, m.getParameterTypes()));
    }

    @Override
    public int getCount() {
        return itens.size();
    }

    class IGStatusViewHolder {
        public ImageView igStatusContactPhoto;
        public RelativeLayout addButton;
        public TextView igStatusContactName;
        public boolean myStatus;
        private FMessageWpp.UserJid userJid;

        public void setInfo(Object item) {

            if (Objects.equals(item, "my_status")) {
                myStatus = true;
                igStatusContactName.setText(UnobfuscatorCache.getInstance().getString("mystatus"));
                igStatusContactPhoto.setImageDrawable(WppCore.getMyPhoto());
                setCountStatus(0, 0);
                return;
            }
            var statusInfo = XposedHelpers.getObjectField(item, "A01");
            var field = ReflectionUtils.getFieldByExtendType(statusInfo.getClass(), XposedHelpers.findClass("com.whatsapp.jid.Jid", statusInfoClazz.getClassLoader()));
            this.userJid = new FMessageWpp.UserJid(ReflectionUtils.getObjectField(field, statusInfo));
            var contactName = WppCore.getContactName(this.userJid);
            igStatusContactName.setText(contactName);
            var profile = WppCore.getContactPhotoDrawable(this.userJid.getPhoneRawString());
            if (profile == null) profile = DesignUtils.getDrawableByName("avatar_contact");
            igStatusContactPhoto.setImageDrawable(profile);
            var countUnseen = XposedHelpers.getIntField(statusInfo, "A01");
            var total = XposedHelpers.getIntField(statusInfo, "A00");
            setCountStatus(countUnseen, total);
        }

        public void setCountStatus(int countUnseen, int total) {
            if (setCountStatus != null) {
                try {
                    setCountStatus.invoke(igStatusContactPhoto, total, countUnseen, total);
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            }
        }

    }

    @NonNull
    private RelativeLayout createLayoutStatus(IGStatusViewHolder holder) {
        RelativeLayout relativeLayout = new RelativeLayout(this.getContext());
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(86), ViewGroup.LayoutParams.WRAP_CONTENT);
        relativeLayout.setLayoutParams(relativeParams);

        // Criando o FrameLayout
        FrameLayout frameLayout = new FrameLayout(this.getContext());
        frameLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Criando o LinearLayout
        LinearLayout linearLayout = new LinearLayout(this.getContext());
        LinearLayout.LayoutParams linearParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(linearParams);

        // Criando o RelativeLayout interno
        RelativeLayout internalRelativeLayout = new RelativeLayout(this.getContext());
        RelativeLayout.LayoutParams internalRelativeParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(64), Utils.dipToPixels(64));
        internalRelativeLayout.setLayoutParams(internalRelativeParams);

        // Adicionando os elementos ao RelativeLayout interno
        var contactPhoto = (ImageView) XposedHelpers.newInstance(this.clazzImageStatus, this.getContext());
        RelativeLayout.LayoutParams photoParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contactPhoto.setLayoutParams(photoParams);
        contactPhoto.setPadding(Utils.dipToPixels(2.5F), Utils.dipToPixels(2.5F), Utils.dipToPixels(2.5F), Utils.dipToPixels(2.5F));
        contactPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        contactPhoto.setImageDrawable(DesignUtils.getDrawableByName("avatar_contact"));
        holder.igStatusContactPhoto = contactPhoto;
        contactPhoto.setClickable(true);
        XposedHelpers.callMethod(contactPhoto, "setBorderSize", (float) Utils.dipToPixels(2.5f));
        XposedHelpers.callMethod(contactPhoto, "setCornerRadius", (float) Utils.dipToPixels(80f));
        XposedHelpers.setObjectField(contactPhoto, "A02", Color.GRAY);
        XposedHelpers.setObjectField(contactPhoto, "A03", DesignUtils.getUnSeenColor());

        RelativeLayout addBtnRelativeLayout = new RelativeLayout(this.getContext());
        addBtnRelativeLayout.setBackgroundColor(Color.TRANSPARENT);
        RelativeLayout.LayoutParams addBtnParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        addBtnRelativeLayout.setLayoutParams(addBtnParams);
        addBtnRelativeLayout.setVisibility(View.GONE);

        ImageView iconImageView = new ImageView(this.getContext());
        RelativeLayout.LayoutParams iconParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(24), Utils.dipToPixels(24));
        iconImageView.setLayoutParams(iconParams);
        if (cacheIcon == null) {
            var icon = DesignUtils.getDrawableByName("my_status_add_button_new");
            cacheIcon = DesignUtils.generatePrimaryColorDrawable(icon);
        }
        iconImageView.setImageDrawable(cacheIcon);
        iconImageView.setBackgroundColor(Color.TRANSPARENT);
        addBtnRelativeLayout.addView(iconImageView);
        holder.addButton = addBtnRelativeLayout;


        internalRelativeLayout.addView(contactPhoto);
        internalRelativeLayout.addView(addBtnRelativeLayout);

        TextView contactName = new TextView(this.getContext());
        contactName.setEllipsize(TextUtils.TruncateAt.END);
        contactName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contactName.setLayoutParams(nameParams);
        contactName.setText("Name");
        contactName.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        contactName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        contactName.setTypeface(Typeface.DEFAULT_BOLD);
        contactName.setMaxLines(1);
        holder.igStatusContactName = contactName;
        linearLayout.addView(internalRelativeLayout);
        linearLayout.addView(contactName);
        frameLayout.addView(linearLayout);
        relativeLayout.addView(frameLayout);
        return relativeLayout;
    }
}
