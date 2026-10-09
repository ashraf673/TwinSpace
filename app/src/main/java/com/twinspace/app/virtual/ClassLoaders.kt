package com.twinspace.app.virtual

object ClassLoaders {
    fun isBoot(cl: ClassLoader?): Boolean {
        if (cl == null) return true
        val n = cl.javaClass.name
        return n == "java.lang.BootClassLoader" || n.contains("WarningContextClassLoader")
    }

    fun pin(cl: ClassLoader?) {
        if (cl != null && !isBoot(cl)) {
            Thread.currentThread().contextClassLoader = cl
        }
    }

    fun canLoad(cl: ClassLoader?, name: String?): Boolean {
        if (cl == null || name.isNullOrBlank() || isBoot(cl)) return false
        return try {
            cl.loadClass(name)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun best(candidates: List<ClassLoader?>, names: List<String?>): ClassLoader? {
        val usable = candidates.filter { it != null && !isBoot(it) }.distinct()
        for (name in names) {
            if (name.isNullOrBlank()) continue
            for (cl in usable) {
                if (canLoad(cl, name)) return cl
            }
        }
        return usable.firstOrNull()
    }
}
