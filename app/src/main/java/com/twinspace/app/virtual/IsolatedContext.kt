package com.twinspace.app.virtual

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.sqlite.SQLiteDatabase
import android.view.Display
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class IsolatedContext(
    private val host: Context,
    private val owner: android.app.Activity?,
    private val guestPackage: String,
    private val guestAppInfo: ApplicationInfo,
    private val guestResources: Resources,
    var guestLoader: ClassLoader,
    private val dataRoot: File
) : ContextWrapper(host) {

    private val files = File(dataRoot, "files").apply { mkdirs() }
    private val cache = File(dataRoot, "cache").apply { mkdirs() }
    private val databases = File(dataRoot, "databases").apply { mkdirs() }
    private val noBackup = File(dataRoot, "no_backup").apply { mkdirs() }
    private val prefsDir = File(dataRoot, "shared_prefs").apply { mkdirs() }
    private val guestTheme: Resources.Theme by lazy {
        guestResources.newTheme().apply {
            val resId = if (guestAppInfo.theme != 0) guestAppInfo.theme else android.R.style.Theme_DeviceDefault_NoActionBar
            applyStyle(resId, true)
        }
    }

    override fun getPackageName(): String = guestPackage
    override fun getOpPackageName(): String = guestPackage
    override fun getApplicationInfo(): ApplicationInfo = guestAppInfo
    override fun getClassLoader(): ClassLoader = guestLoader
    override fun getResources(): Resources = guestResources
    override fun getAssets(): AssetManager = guestResources.assets
    override fun getTheme(): Resources.Theme = guestTheme

    override fun getDataDir(): File = dataRoot
    override fun getFilesDir(): File = files
    override fun getCacheDir(): File = cache
    override fun getCodeCacheDir(): File = File(dataRoot, "code_cache").apply { mkdirs() }
    override fun getNoBackupFilesDir(): File = noBackup
    override fun getDir(name: String, mode: Int): File = File(dataRoot, "app_$name").apply { mkdirs() }

    override fun getDatabasePath(name: String): File = File(databases, name)

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?
    ): SQLiteDatabase {
        databases.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
    }

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: android.database.DatabaseErrorHandler?
    ): SQLiteDatabase {
        databases.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(
            getDatabasePath(name).path,
            factory,
            errorHandler
        )
    }

    override fun deleteDatabase(name: String): Boolean =
        SQLiteDatabase.deleteDatabase(getDatabasePath(name))

    override fun databaseList(): Array<String> = databases.list() ?: emptyArray()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val file = File(prefsDir, "$name.xml")
        val impl = sharedPrefsImpl(file, mode)
        if (impl != null) return impl
        return host.getSharedPreferences("iso_${guestPackage}_$name", mode)
    }

    override fun deleteSharedPreferences(name: String): Boolean {
        File(prefsDir, "$name.xml").delete()
        File(prefsDir, "$name.xml.bak").delete()
        host.deleteSharedPreferences("iso_${guestPackage}_$name")
        return true
    }

    override fun openFileInput(name: String): FileInputStream = FileInputStream(File(files, name))

    override fun openFileOutput(name: String, mode: Int): FileOutputStream {
        val file = File(files, name)
        file.parentFile?.mkdirs()
        val append = mode and Context.MODE_APPEND != 0
        return FileOutputStream(file, append)
    }

    override fun deleteFile(name: String): Boolean = File(files, name).delete()

    override fun fileList(): Array<String> = files.list() ?: emptyArray()

    override fun getExternalFilesDir(type: String?): File =
        File(dataRoot, "ext_files/${type ?: ""}").apply { mkdirs() }

    override fun getExternalFilesDirs(type: String?): Array<File> =
        arrayOf(getExternalFilesDir(type)!!)

    override fun getExternalCacheDir(): File = File(dataRoot, "ext_cache").apply { mkdirs() }

    override fun getExternalCacheDirs(): Array<File> = arrayOf(getExternalCacheDir()!!)

    override fun getObbDir(): File = File(dataRoot, "obb").apply { mkdirs() }

    override fun getObbDirs(): Array<File> = arrayOf(getObbDir())

    override fun getPackageCodePath(): String = guestAppInfo.sourceDir ?: super.getPackageCodePath()
    override fun getPackageResourcePath(): String = guestAppInfo.sourceDir ?: super.getPackageResourcePath()

    override fun getApplicationContext(): Context = this

    override fun createPackageContext(packageName: String, flags: Int): Context {
        return if (packageName == guestPackage) this else super.createPackageContext(packageName, flags)
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        return IsolatedContext(
            host.createConfigurationContext(overrideConfiguration),
            owner,
            guestPackage,
            guestAppInfo,
            guestResources,
            guestLoader,
            dataRoot
        )
    }

    override fun getDisplay(): Display? = host.display

    override fun startActivity(intent: Intent) {
        redirect(intent)
    }

    override fun startActivity(intent: Intent, options: android.os.Bundle?) {
        redirect(intent)
    }

    private fun redirect(intent: Intent) {
        val target = intent.component?.packageName ?: intent.`package`
        if (target == guestPackage) {
            val hostActivity = owner ?: host as? ContainerActivity
            if (hostActivity != null) {
                hostActivity.consumeStart(intent)
                return
            }
        }
        val send = Intent(intent)
        if (send.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0) {
            send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        host.startActivity(send)
    }

    private fun sharedPrefsImpl(file: File, mode: Int): SharedPreferences? {
        return try {
            val cls = Class.forName("android.app.SharedPreferencesImpl")
            val ctor = cls.getDeclaredConstructor(File::class.java, Int::class.javaPrimitiveType)
            ctor.isAccessible = true
            ctor.newInstance(file, mode) as SharedPreferences
        } catch (_: Throwable) {
            null
        }
    }
}
