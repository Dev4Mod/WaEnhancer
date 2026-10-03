package com.wmods.wppenhacer.xposed.utils

import com.highcapable.yukihookapi.hook.log.YLog

/**
 * Logging helper routed through YukiHookAPI's logger.
 */
object YukiLog {

    @JvmStatic
    fun log(obj: Any?) {
        if (obj is Throwable) {
            YLog.error(obj.message ?: obj.javaClass.name, obj)
        } else {
            YLog.debug(obj.toString())
        }
    }
}
