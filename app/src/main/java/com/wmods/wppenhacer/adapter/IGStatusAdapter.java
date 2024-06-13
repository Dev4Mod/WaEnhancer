package com.wmods.wppenhacer.adapter;

import static com.wmods.wppenhacer.xposed.features.customization.IGStatus.items;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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

import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.WppCore;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class IGStatusAdapter extends ArrayAdapter {


    private final Class<?> clazzImageStatus;
    private final Class<?> statusInfoClazz;
    private final Method setCountStatus;

    class IGStatusViewHolder {
        public ImageView igStatusContactPhoto;
        public RelativeLayout addButton;
        public TextView igStatusContactName;
        public boolean myStatus;
        private String jid;

        public void setInfo(Object item) {
            if (Objects.equals(item, "my_status")) {
                myStatus = true;
                igStatusContactName.setText(UnobfuscatorCache.getInstance().getString("mystatus"));
                igStatusContactPhoto.setImageDrawable(WppCore.getMyPhoto());
                setCountStatus(0, 0);
                return;
            }
            var statusInfo = XposedHelpers.getObjectField(item, "A01");
            var field = Unobfuscator.getFieldByType(statusInfo.getClass(), XposedHelpers.findClass("com.whatsapp.jid.UserJid", statusInfoClazz.getClassLoader()));
            var userJid = XposedHelpers.getObjectField(statusInfo, field.getName());
            var contactName = WppCore.getContactName(userJid);
            jid = WppCore.getRawString(userJid);
            igStatusContactName.setText(contactName);
            var profile = WppCore.getContactPhotoDrawable(jid);
            if (profile == null) profile = DesignUtils.getDrawableByName("avatar_contact");
            igStatusContactPhoto.setImageDrawable(profile);
            var countUnseen = XposedHelpers.getIntField(statusInfo, "A01");
            var total = XposedHelpers.getIntField(statusInfo, "A00");
            setCountStatus(countUnseen, total);
        }

        public void setCountStatus(int countUnseen, int total) {
            if (setCountStatus != null) {
                try {
                    setCountStatus.invoke(igStatusContactPhoto, countUnseen, total);
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            }
        }

    }

    public IGStatusAdapter(@NonNull Context context, @NonNull Class<?> statusInfoClazz) {
        super(context, 0);
        this.clazzImageStatus = XposedHelpers.findClass("com.whatsapp.status.ContactStatusThumbnail", this.getContext().getClassLoader());
        this.statusInfoClazz = statusInfoClazz;
        this.setCountStatus = Arrays.stream(this.clazzImageStatus.getDeclaredMethods()).filter(m -> m.getParameterCount() == 2 && m.getParameterTypes()[0].equals(int.class) && m.getParameterTypes()[1].equals(int.class)).findFirst().orElse(null);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        var item = items.get(position);
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
        } else if (statusInfoClazz.isInstance(item)) {
            holder.setInfo(item);
        }

        convertView.setOnClickListener(v -> {
            if (holder.myStatus) {
                var intent = new Intent(WppCore.getMainActivity(), XposedHelpers.findClass("com.whatsapp.status.playback.MyStatusesActivity", getContext().getClassLoader()));
                WppCore.getMainActivity().startActivity(intent);
                return;
            }
            var intent = new Intent(WppCore.getMainActivity(), XposedHelpers.findClass("com.whatsapp.status.playback.StatusPlaybackActivity", getContext().getClassLoader()));
            intent.putExtra("jid", holder.jid);
            WppCore.getMainActivity().startActivity(intent);
        });

        return convertView;
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
        holder.addButton = addBtnRelativeLayout;

        ImageView iconImageView = new ImageView(this.getContext());
        RelativeLayout.LayoutParams iconParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(24), Utils.dipToPixels(24));
        iconImageView.setLayoutParams(iconParams);
        var icon = DesignUtils.getDrawableByName("my_status_add_button_new");
        iconImageView.setImageDrawable(icon);
        iconImageView.setBackgroundColor(Color.TRANSPARENT);

        addBtnRelativeLayout.addView(iconImageView);
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
