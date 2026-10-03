package com.wmods.wppenhacer.xposed.utils

import kotlinx.coroutines.CoroutineExceptionHandler

val WaeCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    YukiLog.log(throwable)
}
