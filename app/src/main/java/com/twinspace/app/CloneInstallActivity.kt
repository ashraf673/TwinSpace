package com.twinspace.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.concurrent.thread

class CloneInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Admin.isProfileOwner(this)) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF09090B.toInt())
            setPadding(48, 96, 48, 48)
        }
        root.addView(ProgressBar(this))
        val label = TextView(this).apply {
            setTextColor(0xFFF4F4F5.toInt())
            textSize = 16f
            text = "Installing a fresh copy…"
            setPadding(0, 32, 0, 0)
        }
        root.addView(label)
        setContentView(root)

        when (intent.action) {
            ACTION_UNINSTALL -> uninstall(intent.getStringExtra(CloneEngine.EXTRA_PACKAGE))
            ACTION_WIPE_SPACE -> wipe()
            else -> clone(intent)
        }
    }

    private fun clone(intent: Intent) {
        val pkg = intent.getStringExtra(CloneEngine.EXTRA_PACKAGE)
        if (pkg.isNullOrBlank()) {
            finish()
            return
        }
        thread {
            val dpm = Admin.dpm(this)
            val admin = Admin.component(this)
            val existing = try {
                if (Build.VERSION.SDK_INT >= 28) dpm.installExistingPackage(admin, pkg) else false
            } catch (_: Exception) {
                false
            }
            if (existing) {
                runOnUiThread { finish() }
                return@thread
            }
            try {
                dpm.enableSystemApp(admin, pkg)
                runOnUiThread { finish() }
                return@thread
            } catch (_: Exception) {
                // Not a system app, or already handled.
            }
            val uris = urisFrom(intent)
            if (uris.isEmpty()) {
                runOnUiThread { finish() }
                return@thread
            }
            try {
                commitSession(pkg, uris)
            } catch (_: Exception) {
                runOnUiThread { finish() }
            }
        }
    }

    private fun urisFrom(intent: Intent): List<Uri> {
        val extras = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(CloneEngine.EXTRA_URIS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(CloneEngine.EXTRA_URIS)
        }
        if (!extras.isNullOrEmpty()) return extras
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }

    private fun commitSession(packageName: String, uris: List<Uri>) {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            uris.forEachIndexed { index, uri ->
                contentResolver.openInputStream(uri)?.use { input ->
                    session.openWrite("split-$index.apk", 0, -1).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
            }
            val callback = Intent(this, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(this, sessionId, callback, flags)
            session.commit(pending.intentSender)
        } finally {
            session.close()
        }
        runOnUiThread { finish() }
    }

    private fun uninstall(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            finish()
            return
        }
        val installer = packageManager.packageInstaller
        val callback = Intent(this, InstallResultReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(this, packageName.hashCode(), callback, flags)
        installer.uninstall(packageName, pending.intentSender)
        finish()
    }

    private fun wipe() {
        try {
            Admin.dpm(this).wipeData(0)
        } catch (_: Exception) {
            finish()
        }
    }

    companion object {
        const val ACTION_CLONE = "com.twinspace.app.action.CLONE"
        const val ACTION_UNINSTALL = "com.twinspace.app.action.UNINSTALL"
        const val ACTION_WIPE_SPACE = "com.twinspace.app.action.WIPE_SPACE"
    }
}
