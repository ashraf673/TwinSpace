package com.twinspace.app.virtual

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.twinspace.app.CloneLedger
import com.twinspace.app.InstalledApp
import java.io.File
import java.util.zip.ZipFile

object VirtualCore {
    const val EXTRA_PKG = "twinspace.pkg"
    private const val SLOTS = 8

    fun root(context: Context, packageName: String): File =
        File(context.filesDir, "container/$packageName")

    fun installFromInstalled(context: Context, packageName: String) {
        val pm = context.packageManager
        val info: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
        val root = root(context, packageName)
        val apkDir = File(root, "apk")
        apkDir.deleteRecursively()
        apkDir.mkdirs()
        installedApks(info).forEachIndexed { index, src ->
            val name = src.name.ifBlank {
                if (index == 0) "base.apk" else "split-$index.apk"
            }
            src.copyTo(File(apkDir, name), overwrite = true)
        }
        val libRoot = File(root, "lib")
        extractNativeLibs(apkDir, libRoot)
        copyInstalledNativeLibs(info, libRoot)
        File(root, "data").mkdirs()
        CloneLedger.add(context, packageName)
    }

    fun installedApks(info: ApplicationInfo): List<File> {
        val out = linkedSetOf<File>()
        info.sourceDir?.let { out += File(it) }
        info.publicSourceDir?.let { out += File(it) }
        info.splitSourceDirs?.forEach { out += File(it) }
        info.splitPublicSourceDirs?.forEach { out += File(it) }
        info.sourceDir?.let { src ->
            File(src).parentFile?.listFiles()?.forEach { f ->
                if (f.isFile && f.extension == "apk") out += f
            }
        }
        return out.filter { it.exists() && it.isFile }
    }

    fun nativeLibPath(info: ApplicationInfo, extracted: File): String {
        val dirs = linkedSetOf<String>()
        info.nativeLibraryDir?.takeIf { File(it).exists() }?.let { dirs += it }
        info.sourceDir?.let { src ->
            File(src).parentFile?.resolve("lib")?.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { dirs += it.absolutePath }
        }
        extracted.listFiles()?.filter { it.isDirectory }?.forEach { dirs += it.absolutePath }
        return dirs.joinToString(File.pathSeparator)
    }

    fun uninstall(context: Context, packageName: String) {
        CloneLedger.remove(context, packageName)
        root(context, packageName).deleteRecursively()
        context.getSharedPreferences("twinspace.slots", Context.MODE_PRIVATE)
            .edit().remove(packageName).apply()
        clearFallbackPrefs(context, packageName)
    }

    fun wipe(context: Context) {
        CloneLedger.clear(context)
        File(context.filesDir, "container").deleteRecursively()
        context.getSharedPreferences("twinspace.slots", Context.MODE_PRIVATE).edit().clear().apply()
        clearFallbackPrefs(context, null)
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        val dir = File(root(context, packageName), "apk")
        return dir.listFiles { _, name -> name.endsWith(".apk") }?.isNotEmpty() == true
    }

    fun launch(activity: Activity, packageName: String) {
        if (!isInstalled(activity, packageName)) {
            throw IllegalStateException("Clone is not ready")
        }
        val slot = slotFor(activity, packageName)
        val cls = Class.forName("com.twinspace.app.virtual.C$slot")
        activity.startActivity(
            Intent(activity, cls).putExtra(EXTRA_PKG, packageName)
        )
    }

    fun clones(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        return CloneLedger.packages(context).mapNotNull { pkg ->
            iconAndLabel(context, pm, pkg)
        }.sortedBy { it.label.lowercase() }
    }

    fun installedOnPhone(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .map { info ->
                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm)
                )
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun iconAndLabel(context: Context, pm: PackageManager, pkg: String): InstalledApp? {
        return try {
            val info = pm.getApplicationInfo(pkg, 0)
            InstalledApp(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
        } catch (_: Exception) {
            val base = File(root(context, pkg), "apk/base.apk")
            if (!base.exists()) return null
            val parsed = pm.getPackageArchiveInfo(base.absolutePath, 0) ?: return null
            val appInfo = parsed.applicationInfo ?: return null
            appInfo.sourceDir = base.absolutePath
            appInfo.publicSourceDir = base.absolutePath
            InstalledApp(
                pkg,
                appInfo.loadLabel(pm).toString(),
                appInfo.loadIcon(pm)
            )
        }
    }

    private fun slotFor(context: Context, packageName: String): Int {
        val prefs = context.getSharedPreferences("twinspace.slots", Context.MODE_PRIVATE)
        val existing = prefs.getInt(packageName, -1)
        if (existing in 1..SLOTS) return existing
        val used = prefs.all.values.mapNotNull { it as? Int }.toSet()
        val slot = (1..SLOTS).firstOrNull { it !in used }
            ?: throw IllegalStateException("TwinSpace can host 8 apps at once. Remove one first.")
        prefs.edit().putInt(packageName, slot).apply()
        return slot
    }

    private fun clearFallbackPrefs(context: Context, packageName: String?) {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefix = if (packageName == null) "iso_" else "iso_${packageName}_"
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix) && file.name.endsWith(".xml")) {
                file.delete()
            }
        }
    }

    private fun copyInstalledNativeLibs(info: ApplicationInfo, dest: File) {
        val srcLib = info.sourceDir?.let { File(it).parentFile?.resolve("lib") } ?: return
        srcLib.listFiles()?.forEach { abiDir ->
            if (!abiDir.isDirectory) return@forEach
            val out = File(dest, abiDir.name).apply { mkdirs() }
            abiDir.listFiles()?.forEach { so ->
                if (so.isFile) runCatching { so.copyTo(File(out, so.name), overwrite = true) }
            }
        }
    }

    private fun extractNativeLibs(apkDir: File, libRoot: File) {
        val abis = android.os.Build.SUPPORTED_ABIS.toList()
        apkDir.listFiles { _, name -> name.endsWith(".apk") }?.forEach { apk ->
            ZipFile(apk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val name = e.name
                    if (!name.startsWith("lib/") || !name.endsWith(".so")) continue
                    val parts = name.split('/')
                    if (parts.size < 3) continue
                    val abi = parts[1]
                    if (abi !in abis) continue
                    val out = File(File(libRoot, abi).apply { mkdirs() }, parts.last())
                    zip.getInputStream(e).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}
