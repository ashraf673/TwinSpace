package com.twinspace.app.virtual

import java.io.File

object IoNative {
    private val available: Boolean = runCatching {
        System.loadLibrary("twinio")
        true
    }.getOrDefault(false)

    fun install(packageName: String, dataRoot: File) {
        if (!available) return
        val dest = dataRoot.absolutePath
        val ext = File(dataRoot, "ext").apply { mkdirs() }.absolutePath
        val obb = File(dataRoot, "obb").apply { mkdirs() }.absolutePath
        val from = arrayOf(
            "/data/data/$packageName",
            "/data/user/0/$packageName",
            "/storage/emulated/0/Android/data/$packageName",
            "/storage/self/primary/Android/data/$packageName",
            "/sdcard/Android/data/$packageName",
            "/storage/emulated/0/Android/obb/$packageName",
            "/sdcard/Android/obb/$packageName"
        )
        val to = arrayOf(dest, dest, ext, ext, ext, obb, obb)
        runCatching { nativeInstall(from, to) }
    }

    @JvmStatic
    private external fun nativeInstall(from: Array<String>, to: Array<String>)
}
