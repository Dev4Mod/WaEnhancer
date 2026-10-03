package com.wmods.wppenhacer.xposed.core.patch

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.YukiHookAPI.Status.isXposedEnvironment
import com.highcapable.yukihookapi.hook.core.api.reflect.AndroidHiddenApiBypassResolver
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.injectModuleAppResources
import com.highcapable.yukihookapi.hook.log.YLog
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class GlobalResourceHooker : YukiBaseHooker() {

    companion object {
        private const val COPIED_APK_NAME = "wae_module.apk"
    }

    private val injectedModulePath: String? by lazy { copyModuleApkToHostFilesDir() }

    private fun copyModuleApkToHostFilesDir(): String? {
        val sourcePath = moduleAppFilePath
        val hostDataDir = appInfo.dataDir.takeIf { it.isNotBlank() } ?: return null
        val hostFilesDir = File(hostDataDir, "files")
        val targetFile = File(hostFilesDir, COPIED_APK_NAME)
        val hashFile = File(hostFilesDir, "$COPIED_APK_NAME.hash")

        return runCatching {
            hostFilesDir.mkdirs()

            val currentFingerprint = getFileFingerprint(sourcePath)
            val savedFingerprint = hashFile.takeIf { it.exists() }?.readText()

            if (currentFingerprint != savedFingerprint || !targetFile.exists()) {
                Files.copy(
                    File(sourcePath).toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                currentFingerprint?.let { hashFile.writeText(it) }
            }

            targetFile.absolutePath
        }.onFailure {
            YLog.error(
                "Failed to copy module APK to host files dir, falling back to original path",
                it
            )
        }.getOrNull() ?: sourcePath
    }

    private fun getFileFingerprint(path: String): String? {
        val file = File(path)
        if (!file.isFile) return null
        return "${file.length()}:${file.lastModified()}"
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun injectIntoAssetManager(assetManager: AssetManager) {
        if (!isXposedEnvironment) {
            YLog.error("You can only inject module resources in Xposed Environment")
            return
        }

        val pathToInject = injectedModulePath ?: moduleAppFilePath

        runCatching {
            assetManager.asResolver()
                .processor(AndroidHiddenApiBypassResolver.get())
                .optional(silent = true)
                .firstMethodOrNull {
                    name = "addAssetPath"
                    parameters(String::class)
                }?.invoke(pathToInject)
        }.onFailure {
            YLog.error("Failed to inject module resources into [$assetManager]", it)
        }
    }

    override fun onHook() {
        "android.content.res.ResourcesImpl".toClass().resolve().constructor {}.hookAll {
            after {
                var assetManager: AssetManager? = null
                for (arg in args) {
                    if (arg is AssetManager) {
                        assetManager = arg
                        break
                    }
                }
                if (assetManager != null) {
                    injectIntoAssetManager(assetManager)
                } else {
                    YLog.error("AssetManager not found in ResourcesImpl arguments")
                }
            }
        }

        "android.content.res.Resources".toClass().resolve().constructor {}.hookAll {
            after {
                var assetManager: AssetManager? = null
                for (arg in args) {
                    if (arg is AssetManager) {
                        assetManager = arg
                        break
                    }
                }
                if (assetManager != null) {
                    injectIntoAssetManager(assetManager)
                }
            }
        }

        "android.app.ResourcesManager".toClassOrNull()?.resolve()?.apply {
            firstMethod {
                name = "getResources"
            }.hook {
                after {
                    (result as? Resources)?.injectModuleAppResources()
                }
            }
        }

        "android.app.ContextImpl".toClass().resolve().optional(silent = true).apply {
            firstMethodOrNull {
                name = "createResources"
            }?.hook {
                after {
                    (result as? Resources)?.injectModuleAppResources()
                }
            }
            firstMethodOrNull {
                name = "setResources"
            }?.hook {
                after {
                    (args[0] as? Resources)?.injectModuleAppResources()
                }
            }
            firstMethodOrNull {
                name = "createApplicationContext"
            }?.hook {
                after {
                    (result as? Context)?.injectModuleAppResources()
                }
            }
            firstMethodOrNull {
                name = "createConfigurationContext"
            }?.hook {
                after {
                    (result as? Context)?.injectModuleAppResources()
                }
            }
            firstMethodOrNull {
                name = "createDisplayContext"
            }?.hook {
                after {
                    (result as? Context)?.injectModuleAppResources()
                }
            }
            firstMethodOrNull {
                name = "createActivityContext"
            }?.hook {
                after {
                    (result as? Context)?.injectModuleAppResources()
                }
            }
        }
    }
}