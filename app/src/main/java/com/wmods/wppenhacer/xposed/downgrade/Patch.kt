package com.wmods.wppenhacer.xposed.downgrade

import android.os.Build
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.wmods.wppenhacer.xposed.core.FeatureLoader

/**
 * Downgrade hooks for WhatsApp / WhatsApp Business.
 *
 * Based on LSPosed CorePatch downgrade handling:
 *   https://github.com/LSPosed/CorePatch
 *
 * The hooks below mirror CorePatch's per-version `checkDowngrade` targets but
 * are scoped to our supported packages only.
 */
object Patch : YukiBaseHooker() {

    override fun onHook() {
        when (Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.BAKLAVA,
            Build.VERSION_CODES.VANILLA_ICE_CREAM -> {
                // CorePatchForV: adds an alternative PackageSetting overload on top of U/T hooks.
                hookCheckDowngradeReturnNull(
                    utilsClass = "com.android.server.pm.PackageManagerServiceUtils",
                    "com.android.server.pm.PackageSetting",
                    "android.content.pm.PackageInfoLite"
                )
                hookCheckDowngradeV34()
            }

            Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> hookCheckDowngradeV34()

            Build.VERSION_CODES.TIRAMISU -> hookCheckDowngradeReturnNull(
                utilsClass = "com.android.server.pm.PackageManagerServiceUtils",
                "com.android.server.pm.parsing.pkg.AndroidPackage",
                "android.content.pm.PackageInfoLite"
            )

            Build.VERSION_CODES.S_V2,
            Build.VERSION_CODES.S,
            Build.VERSION_CODES.R -> hookApi30to32()

            Build.VERSION_CODES.Q,
            Build.VERSION_CODES.P -> hookApi28to29()

            else -> YLog.debug("Patch: Unsupported Android version ${Build.VERSION.SDK_INT}")
        }
    }

    private fun isWppPackage(pkg: String?) =
        pkg == FeatureLoader.PACKAGE_WPP || pkg == FeatureLoader.PACKAGE_BUSINESS

    /**
     * Tries to read a package name from an object:
     *  - first via a public `getPackageName()` method,
     *  - then via the public field `packageName`.
     */
    private fun packageNameOf(obj: Any?): String? = obj?.let {
        runCatching { it.javaClass.getMethod("getPackageName").invoke(it) as? String }.getOrNull()
            ?: runCatching { it.javaClass.getField("packageName").get(it) as? String }.getOrNull()
    }

    /**
     * Hooks `checkDowngrade` returning `null` for the supplied parameter types.
     * CorePatch equivalent: ReturnConstant(prefs, "downgrade", null)
     */
    private fun hookCheckDowngradeReturnNull(
        utilsClass: String,
        vararg paramClassNames: String
    ) {
        val clazz = utilsClass.toClassOrNull() ?: return
        val params = paramClassNames.map { it.toClassOrNull() ?: return }.toTypedArray()

        runCatching {
            clazz.resolve().firstMethodOrNull {
                name = "checkDowngrade"
                parameters(*params)
            }?.hook {
                before {
                    if (isWppPackage(packageNameOf(args[0]))) {
                        result = null
                    }
                }
            }
        }.onFailure { YLog.error("Patch: failed to hook $utilsClass#checkDowngrade", it) }
    }

    /**
     * Android 14 (U): `checkDowngrade(AndroidPackage, PackageInfoLite)`.
     * CorePatchForU uses `com.android.server.pm.pkg.AndroidPackage`.
     */
    private fun hookCheckDowngradeV34() = hookCheckDowngradeReturnNull(
        "com.android.server.pm.PackageManagerServiceUtils",
        "com.android.server.pm.pkg.AndroidPackage",
        "android.content.pm.PackageInfoLite"
    )

    /**
     * Android 12 / 12L / 11 (S/Sv2/R).
     * CorePatchForR hooks `PackageManagerService#checkDowngrade(AndroidPackage, PackageInfoLite)`
     * and a Flyme 9 variant with two `PackageInfoLite` arguments.
     */
    private fun hookApi30to32() {
        val pmClass = "com.android.server.pm.PackageManagerService".toClassOrNull() ?: return
        val liteClass = "android.content.pm.PackageInfoLite".toClassOrNull() ?: return
        val androidPkgClass = "com.android.server.pm.parsing.pkg.AndroidPackage".toClassOrNull()

        androidPkgClass?.let { pkgClass ->
            runCatching {
                pmClass.resolve().firstMethodOrNull {
                    name = "checkDowngrade"
                    parameters(pkgClass, liteClass)
                }?.hook {
                    before {
                        if (isWppPackage(packageNameOf(args[0]))) {
                            result = null
                        }
                    }
                }
            }.onFailure {
                YLog.error(
                    "Patch: failed to hook PMS#checkDowngrade(AndroidPackage, PackageInfoLite)",
                    it
                )
            }
        }

        // Flyme 9 (Android 11) specific overload.
        runCatching {
            pmClass.resolve().firstMethodOrNull {
                name = "checkDowngrade"
                parameters(liteClass, liteClass)
            }?.hook {
                before {
                    if (isWppPackage(packageNameOf(args[0]))) {
                        result = true
                    }
                }
            }
        }.onFailure { YLog.error("Patch: failed to hook Flyme checkDowngrade", it) }
    }

    /**
     * Android 10 / 9 (Q/P).
     * CorePatchForQ zeros out `mVersionCode` and `mVersionCodeMajor` on the
     * `PackageParser.Package` argument before the downgrade check runs.
     */
    private fun hookApi28to29() {
        val packageClazz = "android.content.pm.PackageParser\$Package".toClassOrNull() ?: return
        val pmClass = "com.android.server.pm.PackageManagerService".toClassOrNull() ?: return

        runCatching {
            pmClass.resolve().method {
                name = "checkDowngrade"
            }.hookAll {
                before {
                    val pkg = args[0] ?: return@before
                    val packageName = runCatching {
                        packageClazz.getField("packageName").get(pkg) as? String
                    }.getOrNull() ?: return@before

                    if (isWppPackage(packageName)) {
                        runCatching { packageClazz.getField("mVersionCode").set(pkg, 0) }
                        runCatching { packageClazz.getField("mVersionCodeMajor").set(pkg, 0) }
                    }
                }
            }
        }.onFailure { YLog.error("Patch: failed to hook Q/P checkDowngrade", it) }
    }
}
