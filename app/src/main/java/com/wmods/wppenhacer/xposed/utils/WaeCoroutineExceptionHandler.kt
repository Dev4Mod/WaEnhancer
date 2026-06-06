package com.wmods.wppenhacer.xposed.utils

import de.robv.android.xposed.XposedBridge
import kotlinx.coroutines.CoroutineExceptionHandler

val WaeCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    XposedBridge.log(throwable)
}
