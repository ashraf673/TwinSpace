package com.twinspace.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.CrossProfileApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

object Admin {
    fun component(context: Context) = ComponentName(context, TwinAdminReceiver::class.java)

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    fun isProfileOwner(context: Context): Boolean =
        dpm(context).isProfileOwnerApp(context.packageName)

    fun workProfile(context: Context): UserHandle? {
        val um = context.getSystemService(UserManager::class.java)
        val me = Process.myUserHandle()
        return um.userProfiles.firstOrNull { it != me }
    }

    fun hasWorkProfile(context: Context): Boolean = workProfile(context) != null

    fun canProvision(context: Context): Boolean =
        dpm(context).isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)

    fun canTalkAcrossProfiles(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 30) return hasWorkProfile(context)
        val cpa = context.getSystemService(CrossProfileApps::class.java)
        return cpa.canInteractAcrossProfiles()
    }
}
