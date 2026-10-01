package com.twinspace.app.virtual

import java.io.File

object PathRedirect {
    private val fromPaths = ArrayList<String>()
    private val toPaths = ArrayList<String>()

    fun install(packageName: String, dataRoot: File) {
        fromPaths.clear()
        toPaths.clear()
        val dest = dataRoot.absolutePath
        val ext = File(dataRoot, "ext").apply { mkdirs() }.absolutePath
        val obb = File(dataRoot, "obb").apply { mkdirs() }.absolutePath
        add("/data/data/$packageName", dest)
        add("/data/user/0/$packageName", dest)
        for (i in 0..20) {
            add("/data/user/$i/$packageName", dest)
        }
        add("/storage/emulated/0/Android/data/$packageName", ext)
        add("/storage/self/primary/Android/data/$packageName", ext)
        add("/sdcard/Android/data/$packageName", ext)
        add("/mnt/sdcard/Android/data/$packageName", ext)
        add("/storage/emulated/0/Android/obb/$packageName", obb)
        add("/sdcard/Android/obb/$packageName", obb)
    }

    fun rewrite(path: String?): String? {
        if (path == null || path.isEmpty() || path[0] != '/') return path
        val n = fromPaths.size
        for (i in 0 until n) {
            val from = fromPaths[i]
            if (path == from) return toPaths[i]
            if (path.startsWith(from) && path.length > from.length && path[from.length] == '/') {
                return toPaths[i] + path.substring(from.length)
            }
        }
        return path
    }

    private fun add(from: String, to: String) {
        fromPaths.add(from)
        toPaths.add(to)
    }
}
