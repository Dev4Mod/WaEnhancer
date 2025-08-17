package com.wmods.wppenhacer.xposed.core.devkit;

import de.robv.android.xposed.XposedBridge;

public class CapabilityReport {
    public static void logSummary(ClassLoader cl) {
        try {
            var sb = new StringBuilder("[CapabilityReport]\n");
            var ok1 = Unobfuscator.loadAntiRevokeMessageMethod(cl) != null;
            var ok2 = Unobfuscator.loadVideoViewContainerClass(cl) != null;
            var ok3 = Unobfuscator.loadImageVewContainerClass(cl) != null;
            var ok4 = Unobfuscator.loadStatusPlaybackViewClass(cl) != null;
            sb.append("AntiRevokeMessageMethod: ").append(ok1).append('\n');
            sb.append("VideoViewContainerClass: ").append(ok2).append('\n');
            sb.append("ImageViewContainerClass: ").append(ok3).append('\n');
            sb.append("StatusPlaybackViewClass: ").append(ok4).append('\n');
            XposedBridge.log(sb.toString());
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
