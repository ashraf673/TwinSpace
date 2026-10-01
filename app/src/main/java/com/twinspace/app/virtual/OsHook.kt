package com.twinspace.app.virtual

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

object OsHook {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        try {
            val libcore = Class.forName("libcore.io.Libcore")
            val osField = libcore.getDeclaredField("os")
            osField.isAccessible = true
            val original = osField.get(null) ?: return
            val iface = Class.forName("libcore.io.Os")
            val handler = InvocationHandler { _, method, args ->
                if (args != null) {
                    for (i in args.indices) {
                        val v = args[i]
                        if (v is String && v.startsWith("/")) {
                            args[i] = PathRedirect.rewrite(v)
                        }
                    }
                }
                try {
                    if (args == null) method.invoke(original) else method.invoke(original, *args)
                } catch (e: InvocationTargetException) {
                    throw (e.cause ?: e)
                }
            }
            val proxy = Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), handler)
            osField.set(null, proxy)
            installed = true
        } catch (_: Throwable) {
        }
    }
}
