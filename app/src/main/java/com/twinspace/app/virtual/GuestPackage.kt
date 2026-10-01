package com.twinspace.app.virtual

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import dalvik.system.PathClassLoader
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

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
        val copiedApks = apkDir.listFiles { _, name -> name.endsWith(".apk") }?.sortedBy { it.name }
            ?: emptyList()
        val pm = host.packageManager
        val installedInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        val liveApks = installedInfo?.let { VirtualCore.installedApks(it) } ?: emptyList()
        val apkFiles = (liveApks + copiedApks).distinctBy { it.absolutePath }
        if (apkFiles.isEmpty()) throw IllegalStateException("Clone is missing APKs")

        val installedPkg = runCatching {
            pm.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
            )
        }.getOrNull()
        val archiveBase = copiedApks.firstOrNull { it.name == "base.apk" } ?: copiedApks.firstOrNull()
            ?: liveApks.first()
        val archive = pm.getPackageArchiveInfo(
            archiveBase.absolutePath,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
        )

        val realInfo = installedInfo
            ?: installedPkg?.applicationInfo
            ?: archive?.applicationInfo
            ?: throw IllegalStateException("Could not read cloned APK")

        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val libRoot = File(root, "lib")
        val libDir = File(libRoot, abi).apply { mkdirs() }
        val dataRoot = File(root, "data").apply { mkdirs() }

        val appInfo = ApplicationInfo(realInfo).apply {
            sourceDir = realInfo.sourceDir ?: archiveBase.absolutePath
            publicSourceDir = realInfo.publicSourceDir ?: sourceDir
            splitSourceDirs = realInfo.splitSourceDirs
            dataDir = dataRoot.absolutePath
            nativeLibraryDir = realInfo.nativeLibraryDir?.takeIf { File(it).exists() }
                ?: libDir.absolutePath
            processName = packageName
            uid = host.applicationInfo.uid
        }

        val activities = (installedPkg?.activities?.toList() ?: emptyList())
            .ifEmpty { archive?.activities?.toList() ?: emptyList() }
        val launchClass = runCatching {
            pm.getLaunchIntentForPackage(packageName)?.component?.className
        }.getOrNull()
            ?: activities.firstOrNull()?.name
            ?: throw IllegalStateException("No activity to launch")
        val launcherResolved = activities.firstOrNull { it.name == launchClass }
            ?: ActivityInfo().apply {
                name = launchClass
                this.packageName = packageName
            }
        activities.forEach { it.applicationInfo = appInfo }
        launcherResolved.applicationInfo = appInfo

        val libPath = VirtualCore.nativeLibPath(realInfo, libRoot)
        val parent = host.classLoader.parent ?: ClassLoader.getSystemClassLoader()
        val loader = pickClassLoader(
            host = host,
            packageName = packageName,
            launchClass = launchClass,
            liveApks = liveApks,
            copiedApks = copiedApks,
            libPath = libPath,
            odexDir = File(root, "odex").apply { mkdirs() },
            parent = parent
        )
        val resources = runCatching {
            host.createPackageContext(
                packageName,
                Context.CONTEXT_IGNORE_SECURITY
            ).resources
        }.getOrNull() ?: resourcesFor(host, if (liveApks.isNotEmpty()) liveApks else copiedApks)

        val label = runCatching { pm.getApplicationLabel(realInfo).toString() }.getOrNull()
            .orEmpty().ifBlank { packageName }
        val appClass = installedPkg?.applicationInfo?.className
            ?: archive?.applicationInfo?.className
            ?: realInfo.className

        return GuestPackage(
            packageName = packageName,
            label = label,
            applicationClass = appClass,
            launcher = launcherResolved,
            activities = activities,
            appInfo = appInfo,
            apkFiles = apkFiles,
            libDir = libDir,
            dataRoot = dataRoot,
            classLoader = loader,
            resources = resources
        )
    }

    private fun pickClassLoader(
        host: Context,
        packageName: String,
        launchClass: String,
        liveApks: List<File>,
        copiedApks: List<File>,
        libPath: String,
        odexDir: File,
        parent: ClassLoader
    ): ClassLoader {
        val candidates = mutableListOf<ClassLoader>()

        runCatching {
            val pkgCtx = host.createPackageContext(
                packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            pkgCtx.classLoader?.let { candidates += it }
        }
        if (liveApks.isNotEmpty()) {
            runCatching {
                candidates += PathClassLoader(
                    liveApks.joinToString(File.pathSeparator) { it.absolutePath },
                    libPath,
                    parent
                )
            }
        }
        if (copiedApks.isNotEmpty()) {
            runCatching {
                candidates += PathClassLoader(
                    copiedApks.joinToString(File.pathSeparator) { it.absolutePath },
                    libPath,
                    parent
                )
            }
            runCatching {
                memoryLoader(copiedApks + liveApks, libPath, parent)?.let { candidates += it }
            }
            runCatching {
                candidates += DexClassLoader(
                    copiedApks.joinToString(File.pathSeparator) { it.absolutePath },
                    odexDir.absolutePath,
                    libPath,
                    parent
                )
            }
        } else if (liveApks.isNotEmpty()) {
            runCatching {
                memoryLoader(liveApks, libPath, parent)?.let { candidates += it }
            }
        }

        val found = candidates.firstOrNull { cl ->
            runCatching { cl.loadClass(launchClass); true }.getOrDefault(false)
        }
        return found
            ?: candidates.firstOrNull()
            ?: throw IllegalStateException("Could not load $packageName")
    }

    private fun memoryLoader(
        apkFiles: List<File>,
        libPath: String,
        parent: ClassLoader
    ): ClassLoader? {
        val buffers = ArrayList<ByteBuffer>()
        for (apk in apkFiles.distinctBy { it.absolutePath }) {
            buffers.addAll(dexBuffers(apk))
        }
        if (buffers.isEmpty()) return null
        val array = buffers.toTypedArray()
        if (Build.VERSION.SDK_INT >= 29) {
            return InMemoryDexClassLoader(array, libPath, parent)
        }
        if (Build.VERSION.SDK_INT >= 27) {
            return InMemoryDexClassLoader(array, parent)
        }
        var loader: ClassLoader = parent
        for (i in buffers.indices) {
            loader = InMemoryDexClassLoader(buffers[i], loader)
        }
        return loader
    }

    private fun dexBuffers(apk: File): List<ByteBuffer> {
        if (!apk.exists()) return emptyList()
        val buffers = ArrayList<ByteBuffer>()
        try {
            ZipFile(apk).use { zip ->
                val names = zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.matches(Regex("^classes\\d*\\.dex$")) }
                    .sorted()
                    .toList()
                for (name in names) {
                    val entry = zip.getEntry(name) ?: continue
                    val bytes = zip.getInputStream(entry).use { input -> input.readBytes() }
                    if (bytes.size < 8) continue
                    val buf = ByteBuffer.allocateDirect(bytes.size)
                    buf.put(bytes)
                    buf.flip()
                    buffers.add(buf)
                }
            }
        } catch (_: Throwable) {
            return emptyList()
        }
        return buffers
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
