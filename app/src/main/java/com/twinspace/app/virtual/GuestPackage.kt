package com.twinspace.app.virtual

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import dalvik.system.DexClassLoader
import java.io.File

data class GuestPackage(
    val packageName: String,
    val label: String,
    val applicationClass: String?,
    val launcher: ActivityInfo,
    val activities: List<ActivityInfo>,
    val appInfo: ApplicationInfo,
    val apkFiles: List<File>,
    val libDir: File,
    val dataRoot: File,
    val classLoader: ClassLoader,
    val resources: Resources
)

object GuestLoader {
    fun load(host: Context, packageName: String): GuestPackage {
        val root = VirtualCore.root(host, packageName)
        val apkDir = File(root, "apk")
        val apkFiles = apkDir.listFiles { _, name -> name.endsWith(".apk") }?.sortedBy { it.name }
            ?: throw IllegalStateException("Clone is missing APKs")
        val base = apkFiles.firstOrNull { it.name == "base.apk" } ?: apkFiles.first()
        val pm = host.packageManager
        val parsed: PackageInfo = pm.getPackageArchiveInfo(
            base.absolutePath,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
        ) ?: throw IllegalStateException("Could not read cloned APK")
        parsed.applicationInfo.sourceDir = base.absolutePath
        parsed.applicationInfo.publicSourceDir = base.absolutePath

        val activities = parsed.activities?.toList() ?: emptyList()
        val launchClass = runCatching {
            host.packageManager.getLaunchIntentForPackage(packageName)?.component?.className
        }.getOrNull()
        val launcherResolved = activities.firstOrNull { info ->
            info.name == launchClass ||
                (launchClass != null && info.name.endsWith(launchClass.substringAfterLast('.')))
        } ?: activities.firstOrNull()
            ?: throw IllegalStateException("No activity to launch")

        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val libRoot = File(root, "lib")
        val libDir = File(libRoot, abi).apply { mkdirs() }
        val libPath = (libRoot.listFiles()?.filter { it.isDirectory }?.map { it.absolutePath }
            ?: emptyList())
            .ifEmpty { listOf(libDir.absolutePath) }
            .joinToString(File.pathSeparator)
        val odex = File(root, "odex").apply { mkdirs() }
        val dexPath = apkFiles.joinToString(File.pathSeparator) { it.absolutePath }
        val parent = host.classLoader.parent ?: ClassLoader.getSystemClassLoader()
        val loader = DexClassLoader(dexPath, odex.absolutePath, libPath, parent)
        val resources = resourcesFor(host, apkFiles)

        val appInfo = parsed.applicationInfo.apply {
            dataDir = File(root, "data").absolutePath
            nativeLibraryDir = libDir.absolutePath
            processName = packageName
            uid = host.applicationInfo.uid
        }

        val label = parsed.applicationInfo.loadLabel(pm).toString().ifBlank { packageName }
        val appClass = parsed.applicationInfo.className

        activities.forEach { it.applicationInfo = appInfo }
        launcherResolved.applicationInfo = appInfo

        return GuestPackage(
            packageName = parsed.packageName,
            label = label,
            applicationClass = appClass,
            launcher = launcherResolved,
            activities = activities,
            appInfo = appInfo,
            apkFiles = apkFiles,
            libDir = libDir,
            dataRoot = File(root, "data").apply { mkdirs() },
            classLoader = loader,
            resources = resources
        )
    }

    fun resourcesFor(host: Context, apkFiles: List<File>): Resources {
        val ctor = AssetManager::class.java.getDeclaredConstructor()
        ctor.isAccessible = true
        val am = ctor.newInstance()
        val add = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
        apkFiles.forEach { add.invoke(am, it.absolutePath) }
        return Resources(am, host.resources.displayMetrics, host.resources.configuration)
    }
}
