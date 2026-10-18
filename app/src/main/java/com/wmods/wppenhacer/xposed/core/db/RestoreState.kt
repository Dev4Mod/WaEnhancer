package com.wmods.wppenhacer.xposed.core.db

enum class RestoreState(val value: String) {
    Pending("PENDING"),
    Restored("RESTORED"),
    SkippedExists("SKIPPED_EXISTS"),
    Failed("FAILED")
}