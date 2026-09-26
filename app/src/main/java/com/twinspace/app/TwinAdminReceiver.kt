package com.twinspace.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build

class TwinAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        enableProfile(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        enableProfile(context)
    }

    companion object {
        fun enableProfile(context: Context) {
            val dpm = Admin.dpm(context)
            val admin = Admin.component(context)
            if (!dpm.isProfileOwnerApp(context.packageName)) return

            dpm.setProfileName(admin, context.getString(R.string.profile_name))
            try {
                dpm.setProfileEnabled(admin)
            } catch (_: IllegalStateException) {
                // Already enabled.
            }

            listOf(
                CloneInstallActivity.ACTION_CLONE,
                CloneInstallActivity.ACTION_UNINSTALL,
                CloneInstallActivity.ACTION_WIPE_SPACE
            ).forEach { action ->
                dpm.addCrossProfileIntentFilter(
                    admin,
                    IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT) },
                    DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
                )
            }

            if (Build.VERSION.SDK_INT >= 30) {
                dpm.setCrossProfilePackages(admin, setOf(context.packageName))
            }

            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                ComponentName(context, CloneInstallActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                ComponentName(context, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
