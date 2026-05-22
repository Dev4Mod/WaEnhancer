package com.wmods.wppenhacer.xposed.core.components

import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import java.io.File
import java.lang.reflect.Field

class StatusItemWpp private constructor(
    val fStatus: FStatusWpp?,
    private val directFMessage: FMessageWpp?
) {
    val fMessage: FMessageWpp?
        get() = directFMessage ?: fStatus?.fMessage

    val isFromMe: Boolean
        get() = directFMessage?.key?.isFromMe ?: fStatus?.fStatusKey?.isFromMe ?: false

    val messageID: String
        get() = directFMessage?.key?.messageID ?: fStatus?.fStatusKey?.messageID ?: ""

    val senderJid: FMessageWpp.UserJid?
        get() = directFMessage?.userJid ?: fStatus?.fStatusKey?.senderJid

    val isMediaFile: Boolean
        get() = directFMessage?.isMediaFile ?: fStatus?.isMediaFile ?: false

    fun getMediaFile(): File? = directFMessage?.mediaFile ?: fStatus?.getMediaFile()

    companion object {
        private val fStatusFieldCache = mutableMapOf<Class<*>, Field?>()

        @JvmStatic
        fun from(obj: Any?): StatusItemWpp? {
            if (obj == null) return null
            val fMsgField = ReflectionUtils.findFieldUsingFilterIfExists(obj.javaClass) { f ->
                FMessageWpp.TYPE.isAssignableFrom(f.type)
            }
            fMsgField?.get(obj)?.let { return StatusItemWpp(null, FMessageWpp(it)) }
            val fStatusField = fStatusFieldCache.getOrPut(obj.javaClass) {
                ReflectionUtils.findFieldUsingFilterIfExists(obj.javaClass) { f ->
                    FStatusWpp.TYPE.isAssignableFrom(f.type)
                }
            }
            fStatusField?.get(obj)?.let { return StatusItemWpp(FStatusWpp(it), null) }
            return null
        }
    }
}