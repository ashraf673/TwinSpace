package com.twinspace.app

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

data class ClonedApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val componentPackage: String,
    val componentClass: String,
    val user: UserHandle
)

object WorkApps {
    fun installedOnPhone(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(query, 0)
        return resolved
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

    fun clones(context: Context): List<ClonedApp> {
        val work = Admin.workProfile(context) ?: return emptyList()
        val launcher = context.getSystemService(LauncherApps::class.java)
        return launcher.getActivityList(null, work)
            .filter { it.applicationInfo.packageName != context.packageName }
            .map { info ->
                ClonedApp(
                    packageName = info.applicationInfo.packageName,
                    label = info.label?.toString() ?: info.applicationInfo.packageName,
                    icon = info.getBadgedIcon(0),
                    componentPackage = info.componentName.packageName,
                    componentClass = info.componentName.className,
                    user = work
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun isCloned(context: Context, packageName: String): Boolean =
        clones(context).any { it.packageName == packageName }

    fun launch(context: Context, app: ClonedApp) {
        val launcher = context.getSystemService(LauncherApps::class.java)
        launcher.startMainActivity(
            android.content.ComponentName(app.componentPackage, app.componentClass),
            app.user,
            null,
            null
        )
    }

    fun launchable(context: Context, packageName: String): ResolveInfo? {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        return pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY).firstOrNull()
    }
}
