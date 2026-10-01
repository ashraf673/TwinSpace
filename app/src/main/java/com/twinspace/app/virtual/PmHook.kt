package com.twinspace.app.virtual

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

object PmHook {
    @Volatile
    private var installed = false

    fun install(packageName: String, appInfo: ApplicationInfo) {
        if (installed) return
        try {
            val threadCls = Class.forName("android.app.ActivityThread")
            val field = threadCls.getDeclaredField("sPackageManager")
            field.isAccessible = true
            val original = field.get(null) ?: return
            val iface = Class.forName("android.content.pm.IPackageManager")
            val handler = InvocationHandler { _, method, args ->
                val result = try {
                    if (args == null) method.invoke(original) else method.invoke(original, *args)
                } catch (e: InvocationTargetException) {
                    throw (e.cause ?: e)
                }
                val name = method.name
                val first = args?.firstOrNull() as? String
                if (first == packageName) {
                    if (name == "getApplicationInfo" && result is ApplicationInfo) {
                        copyDataDirs(result, appInfo)
                    } else if (name.startsWith("getPackageInfo") && result is PackageInfo) {
                        val ai = result.applicationInfo
                        if (ai != null) copyDataDirs(ai, appInfo)
                    }
                }
                result
            }
            field.set(null, Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), handler))
            installed = true
        } catch (_: Throwable) {
        }
    }

    private fun copyDataDirs(dest: ApplicationInfo, src: ApplicationInfo) {
        dest.dataDir = src.dataDir
        dest.nativeLibraryDir = src.nativeLibraryDir
        dest.uid = src.uid
        dest.processName = src.processName
        runCatching { dest.deviceProtectedDataDir = src.deviceProtectedDataDir }
        val names = arrayOf("credentialProtectedDataDir", "credentialEncryptedDataDir", "deviceEncryptedDataDir")
        for (n in names) {
            try {
                val f = ApplicationInfo::class.java.getDeclaredField(n)
                f.isAccessible = true
                f.set(dest, f.get(src) ?: src.dataDir)
            } catch (_: Throwable) {
            }
        }
    }
}
