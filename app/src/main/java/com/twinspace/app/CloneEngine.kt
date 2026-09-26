package com.twinspace.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.CrossProfileApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object CloneEngine {
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_URIS = "apk_uris"

    fun apkFiles(context: Context, packageName: String): List<File> {
        val info: ApplicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val paths = mutableListOf(info.sourceDir)
        info.splitSourceDirs?.let { paths.addAll(it) }
        return paths.map { File(it) }.filter { it.exists() }
    }

    fun stageApks(context: Context, packageName: String): ArrayList<Uri> {
        val dir = File(context.cacheDir, "clone").apply {
            deleteRecursively()
            mkdirs()
        }
        val uris = ArrayList<Uri>()
        apkFiles(context, packageName).forEachIndexed { index, src ->
            val dest = File(dir, "part-$index.apk")
            src.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", dest)
            context.grantUriPermission(
                context.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            uris.add(uri)
        }
        return uris
    }

    fun requestClone(context: Context, packageName: String, uris: ArrayList<Uri>) {
        val intent = Intent(CloneInstallActivity.ACTION_CLONE).apply {
            setClassName(context.packageName, CloneInstallActivity::class.java.name)
            putExtra(EXTRA_PACKAGE, packageName)
            putParcelableArrayListExtra(EXTRA_URIS, uris)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uris.isNotEmpty()) {
                val clip = ClipData.newUri(context.contentResolver, "apk", uris[0])
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                clipData = clip
            }
        }
        startInWorkProfile(context, intent)
    }

    fun requestUninstall(context: Context, packageName: String) {
        val intent = Intent(CloneInstallActivity.ACTION_UNINSTALL).apply {
            setClassName(context.packageName, CloneInstallActivity::class.java.name)
            putExtra(EXTRA_PACKAGE, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startInWorkProfile(context, intent)
    }

    fun requestWipe(context: Context) {
        val intent = Intent(CloneInstallActivity.ACTION_WIPE_SPACE).apply {
            setClassName(context.packageName, CloneInstallActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startInWorkProfile(context, intent)
    }

    fun startInWorkProfile(context: Context, intent: Intent) {
        val work = Admin.workProfile(context)
        if (Build.VERSION.SDK_INT >= 30 && work != null) {
            val cpa = context.getSystemService(CrossProfileApps::class.java)
            if (cpa.canInteractAcrossProfiles()) {
                if (context is Activity) {
                    cpa.startActivity(intent, work, context)
                    return
                }
                context.createContextAsUser(work, 0).startActivity(intent)
                return
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            throw IllegalStateException(
                "Couldn't reach Second Space. Allow connected work apps, then try again."
            )
        }
    }

    fun appLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
