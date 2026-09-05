package com.wmods.wppenhacer.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.views.dialog.TabDialogContent
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.WppCore.getCurrentActivity
import com.wmods.wppenhacer.xposed.core.WppCore.getMyPhoto
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp.UserJid
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getClassByName
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache.Companion.getInstance
import com.wmods.wppenhacer.xposed.features.customization.IGStatus
import com.wmods.wppenhacer.xposed.utils.DesignUtils.coloredDrawable
import com.wmods.wppenhacer.xposed.utils.DesignUtils.generatePrimaryColorDrawable
import com.wmods.wppenhacer.xposed.utils.DesignUtils.getDrawable
import com.wmods.wppenhacer.xposed.utils.DesignUtils.getDrawableByName
import com.wmods.wppenhacer.xposed.utils.DesignUtils.getIconByName
import com.wmods.wppenhacer.xposed.utils.DesignUtils.getUnSeenColor
import com.wmods.wppenhacer.xposed.utils.DesignUtils.isNightMode
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils.findMethodUsingFilter
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils.getFieldByExtendType
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils.getObjectField
import com.wmods.wppenhacer.xposed.utils.Utils.application
import com.wmods.wppenhacer.xposed.utils.Utils.dipToPixels
import com.wmods.wppenhacer.xposed.utils.Utils.showToast
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

@Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class IGStatusAdapter(context: Context, private val statusInfoClazz: Class<*>) :
    ArrayAdapter<Any?>(context, 0) {
    private var clazzImageStatus: Class<*> = findFirstClassUsingName(
        this.context.classLoader,
        StringMatchType.EndsWith,
        ".ContactStatusThumbnail"
    )
    private val setCountStatus: Method?

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        if (position >= IGStatus.itens.size) {
            return convertView ?: View(context)
        }
        val item = IGStatus.itens[position]
        val holder: IGStatusViewHolder
        if (convertView == null) {
            holder = IGStatusViewHolder()
            convertView = createLayoutStatus(holder)
            convertView.tag = holder
        } else {
            holder = convertView.tag as IGStatusViewHolder
        }
        if (item == null) {
            holder.setInfo("my_status")
            holder.addButton!!.visibility = View.VISIBLE
        } else if (statusInfoClazz.isInstance(item)) {
            if (item is View) {
                item.isClickable = false
            }
            holder.setInfo(item)
            holder.addButton!!.visibility = View.GONE
        }
        convertView.setOnClickListener {
            if (holder.myStatus) {
                val activity = getCurrentActivity()
                val dialog = WppCore.createBottomDialog(activity!!)
                val tabdialog = TabDialogContent(activity)
                tabdialog.setTitle(activity.getString(R.string.select_status_type))
                tabdialog.addTab(
                    getInstance().getString("mystatus"),
                    getIconByName("ic_status", true))
                {
                    try {
                        val clazz =
                            getClassByName("MyStatusesActivity", context.classLoader)
                        val intent = Intent(getCurrentActivity(), clazz)
                        getCurrentActivity()!!.startActivity(intent)
                    } catch (e: Exception) {
                        showToast(e.message, 1)
                    }
                    dialog.dismissDialog()
                }

                // Botão da camera
                val iconCamera = getDrawable(R.drawable.camera)
                coloredDrawable(iconCamera, if (isNightMode()) Color.WHITE else Color.BLACK)
                tabdialog.addTab(
                    activity.getString(R.string.open_camera),
                    iconCamera
                ) {
                    try {
                        val intent = Intent()
                        val clazz =
                            getClassByName("CameraActivity", context.classLoader)
                        intent.setClassName(activity.packageName, clazz.name)
                        intent.putExtra("jid", "status@broadcast")
                        intent.putExtra("camera_origin", 4)
                        intent.putExtra("is_coming_from_chat", false)
                        intent.putExtra("media_sharing_user_journey_origin", 32)
                        intent.putExtra("media_sharing_user_journey_start_target", 9)
                        intent.putExtra("media_sharing_user_journey_chat_type", 4)
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        showToast(e.message, 1)
                    }
                    dialog.dismissDialog()
                }
                // Botão de editar
                val iconEdit = getDrawable(R.drawable.edit2)
                coloredDrawable(iconEdit, if (isNightMode()) Color.WHITE else Color.BLACK)

                tabdialog.addTab(
                    activity.getString(R.string.edit_text),
                    iconEdit
                ) {
                    try {
                        val intent = Intent()
                        var clazz: Class<*>?
                        try {
                            clazz = getClassByName(
                                "TextStatusComposerActivity",
                                activity.classLoader
                            )
                        } catch (_: Exception) {
                            clazz = getClassByName(
                                "ConsolidatedStatusComposerActivity",
                                context.classLoader
                            )
                            intent.putExtra("status_composer_mode", 2)
                        }
                        intent.setClassName(activity.packageName, clazz.name)
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        showToast(e.message, 1)
                    }
                    dialog.dismissDialog()
                }
                dialog.setContentView(tabdialog)
                dialog.showDialog()
                return@setOnClickListener
            }
            try {
                val clazz = getClassByName("StatusPlaybackActivity", context.classLoader)
                val intent = Intent(getCurrentActivity(), clazz)
                intent.putExtra("jid", holder.userJid!!.phoneRawString)
                getCurrentActivity()!!.startActivity(intent)
            } catch (e: Exception) {
                showToast(e.message, 1)
            }
        }

        return convertView
    }

    init {
        this.clazzImageStatus = findFirstClassUsingName(
            context.classLoader,
            StringMatchType.EndsWith,
            ".ContactStatusThumbnail"
        )
        this.setCountStatus = findMethodUsingFilter(this.clazzImageStatus) { m: Method? ->
            m!!.parameterCount == 3 && arrayOf<Class<*>>(
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            ).contentEquals(m.parameterTypes)
        }
    }

    override fun getCount(): Int {
        return IGStatus.itens.size
    }

    internal inner class IGStatusViewHolder {
        var igStatusContactPhoto: ImageView? = null
        var addButton: RelativeLayout? = null
        var igStatusContactName: TextView? = null
        var myStatus: Boolean = false
        var userJid: UserJid? = null

        fun setInfo(item: Any?) {
            if (item == "my_status") {
                myStatus = true
                igStatusContactName!!.text = getInstance().getString("mystatus")
                var profile = getMyPhoto()
                if (profile == null) profile = application.getDrawable(R.drawable.user_foreground)
                igStatusContactPhoto!!.setImageDrawable(profile)
                setCountStatus(0, 0)
                return
            }
            try {
                val statusInfo = XposedHelpers.getObjectField(item, "A01").takeUnless { it is Number } ?: XposedHelpers.getObjectField(item, "A02")

                val classJid = findFirstClassUsingName(
                    statusInfoClazz.classLoader,
                    StringMatchType.EndsWith,
                    "jid.Jid"
                )
                val field = getFieldByExtendType(statusInfo.javaClass, classJid)
                this.userJid = UserJid(getObjectField(field, statusInfo))
                val waContact = WaContactWpp.getWaContactFromJid(this.userJid!!)
                val contactName = waContact!!.displayName
                igStatusContactName!!.text = contactName
                var profile =
                    BitmapDrawable.createFromStream(waContact.getProfilePhoto(false), "profile")
                if (profile == null) profile = application.getDrawable(R.drawable.user_foreground)
                igStatusContactPhoto!!.setImageDrawable(profile)
                val countUnseen = XposedHelpers.getIntField(statusInfo, "A01")
                val total = XposedHelpers.getIntField(statusInfo, "A00")
                setCountStatus(countUnseen, total)
            } catch (e: Exception) {
                XposedBridge.log(e)
            }
        }

        fun setCountStatus(countUnseen: Int, total: Int) {
            if (setCountStatus != null) {
                try {
                    setCountStatus.invoke(igStatusContactPhoto, total, countUnseen, total)
                } catch (e: Exception) {
                    XposedBridge.log(e)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun createLayoutStatus(holder: IGStatusViewHolder): RelativeLayout {
        val relativeLayout = RelativeLayout(this.context)
        val relativeParams =
            RelativeLayout.LayoutParams(dipToPixels(86), ViewGroup.LayoutParams.WRAP_CONTENT)
        relativeLayout.layoutParams = relativeParams

        // Criando o FrameLayout
        val frameLayout = FrameLayout(this.context)
        frameLayout.layoutParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        // Criando o LinearLayout
        val linearLayout = LinearLayout(this.context)
        val linearParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.layoutParams = linearParams

        // Criando o RelativeLayout interno
        val internalRelativeLayout = RelativeLayout(this.context)
        val internalRelativeParams = RelativeLayout.LayoutParams(dipToPixels(64), dipToPixels(64))
        internalRelativeLayout.layoutParams = internalRelativeParams

        // Adicionando os elementos ao RelativeLayout interno
        val contactPhoto =
            XposedHelpers.newInstance(this.clazzImageStatus, this.context) as ImageView
        val photoParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        contactPhoto.layoutParams = photoParams
        contactPhoto.setPadding(
            dipToPixels(2.5f),
            dipToPixels(2.5f),
            dipToPixels(2.5f),
            dipToPixels(2.5f)
        )
        contactPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
        contactPhoto.setImageDrawable(getDrawableByName("avatar_contact"))
        holder.igStatusContactPhoto = contactPhoto
        contactPhoto.isClickable = true
        XposedHelpers.callMethod(contactPhoto, "setBorderSize", dipToPixels(2.5f).toFloat())
        XposedHelpers.callMethod(contactPhoto, "setCornerRadius", dipToPixels(80f).toFloat())
        XposedHelpers.setObjectField(contactPhoto, "A02", Color.GRAY)
        XposedHelpers.setObjectField(contactPhoto, "A03", getUnSeenColor())

        val addBtnRelativeLayout = RelativeLayout(this.context)
        addBtnRelativeLayout.setBackgroundColor(Color.TRANSPARENT)
        val addBtnParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_END)
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        addBtnRelativeLayout.layoutParams = addBtnParams
        addBtnRelativeLayout.visibility = View.GONE

        val iconImageView = ImageView(this.context)
        val iconParams = RelativeLayout.LayoutParams(dipToPixels(24), dipToPixels(24))
        iconImageView.layoutParams = iconParams
        val icon = getDrawableByName("my_status_add_button_new")
        val coloredIcon = generatePrimaryColorDrawable(icon)
        iconImageView.setImageDrawable(coloredIcon ?: icon)
        iconImageView.setBackgroundColor(Color.TRANSPARENT)
        addBtnRelativeLayout.addView(iconImageView)
        holder.addButton = addBtnRelativeLayout


        internalRelativeLayout.addView(contactPhoto)
        internalRelativeLayout.addView(addBtnRelativeLayout)

        val contactName = TextView(this.context)
        contactName.ellipsize = TextUtils.TruncateAt.END
        contactName.gravity = Gravity.CENTER
        val nameParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        contactName.layoutParams = nameParams
        contactName.text = "Name"
        contactName.textAlignment = View.TEXT_ALIGNMENT_CENTER
        contactName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        contactName.setTypeface(Typeface.DEFAULT_BOLD)
        contactName.maxLines = 1
        holder.igStatusContactName = contactName
        linearLayout.addView(internalRelativeLayout)
        linearLayout.addView(contactName)
        frameLayout.addView(linearLayout)
        relativeLayout.addView(frameLayout)
        return relativeLayout
    }
}
