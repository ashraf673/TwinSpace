package com.twinspace.app.virtual

import android.app.Application
import android.app.Instrumentation
import android.content.pm.ApplicationInfo
import java.io.File

object ThreadBind {
    fun patchAppInfo(info: ApplicationInfo, dataRoot: File, nativeLibDir: String) {
        val path = dataRoot.absolutePath
        info.dataDir = path
        info.nativeLibraryDir = nativeLibDir
        runCatching { info.deviceProtectedDataDir = path }
        val names = arrayOf(
            "credentialProtectedDataDir",
            "credentialEncryptedDataDir",
            "deviceEncryptedDataDir"
        )
        for (n in names) {
            try {
                val f = ApplicationInfo::class.java.getDeclaredField(n)
                f.isAccessible = true
                f.set(info, path)
            } catch (_: Throwable) {
            }
        }
    }

    fun patchLoadedApk(loadedApk: Any?, dataRoot: File, appInfo: ApplicationInfo) {
        if (loadedApk == null) return
        ActivityAttacher.writeField(loadedApk, "mApplicationInfo", appInfo)
        ActivityAttacher.writeField(loadedApk, "mDataDir", dataRoot.absolutePath)
        ActivityAttacher.writeField(loadedApk, "mDataDirFile", dataRoot)
        val protectedDir = File(dataRoot, "device_protected").apply { mkdirs() }
        ActivityAttacher.writeField(loadedApk, "mDeviceProtectedDataDirFile", protectedDir)
        ActivityAttacher.writeField(loadedApk, "mCredentialProtectedDataDirFile", dataRoot)
    }

    fun loadedApkOf(context: android.content.Context): Any? {
        var c: android.content.Context = context
        while (c is android.content.ContextWrapper) {
            val base = c.baseContext ?: break
            if (base === c) break
            c = base
        }
        return ActivityAttacher.readField(c, "mPackageInfo")
    }

    fun makeApplication(loadedApk: Any?, instrumentation: Instrumentation): Application? {
        if (loadedApk == null) return null
        return try {
            val methods = loadedApk.javaClass.methods
            val make = methods.firstOrNull { m ->
                m.name == "makeApplication" && m.parameterTypes.size >= 2
            } ?: return null
            make.isAccessible = true
            val args = Array<Any?>(make.parameterTypes.size) { i ->
                val t = make.parameterTypes[i]
                when {
                    t == Boolean::class.javaPrimitiveType || t == java.lang.Boolean::class.java -> false
                    Instrumentation::class.java.isAssignableFrom(t) -> instrumentation
                    else -> null
                }
            }
            make.invoke(loadedApk, *args) as? Application
        } catch (_: Throwable) {
            null
        }
    }

    fun registerApplication(app: Application) {
        val thread = ActivityAttacher.currentActivityThread() ?: return
        ActivityAttacher.writeField(thread, "mInitialApplication", app)
        val all = ActivityAttacher.readField(thread, "mAllApplications")
        if (all is MutableList<*>) {
            @Suppress("UNCHECKED_CAST")
            val list = all as MutableList<Any>
            if (!list.contains(app)) list.add(app)
        }
    }
}
