package com.twinspace.app.virtual

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.IBinder
import android.view.Window

object ActivityAttacher {
    fun attach(
        guest: Activity,
        isolated: Context,
        app: Application,
        intent: Intent,
        info: ActivityInfo,
        stub: Activity,
        instrumentation: Instrumentation
    ) {
        val token = readField(stub, "mToken") as? IBinder
        val thread = currentActivityThread()
        val attach = Activity::class.java.declaredMethods
            .filter { it.name == "attach" }
            .maxByOrNull { it.parameterTypes.size }
            ?: throw IllegalStateException("Activity.attach not found")
        attach.isAccessible = true
        val args = Array(attach.parameterTypes.size) { i ->
            val t = attach.parameterTypes[i]
            when {
                Context::class.java.isAssignableFrom(t) && t != Activity::class.java -> isolated
                t.name == "android.app.ActivityThread" -> thread
                Instrumentation::class.java.isAssignableFrom(t) -> instrumentation
                IBinder::class.java.isAssignableFrom(t) -> token
                t == Int::class.javaPrimitiveType -> 0
                Application::class.java.isAssignableFrom(t) -> app
                Intent::class.java.isAssignableFrom(t) -> intent
                ActivityInfo::class.java.isAssignableFrom(t) -> info
                t == CharSequence::class.java || t == String::class.java -> info.name
                Activity::class.java.isAssignableFrom(t) -> null
                t.name == "android.content.res.Configuration" -> isolated.resources.configuration
                Window::class.java.isAssignableFrom(t) -> stub.window
                else -> null
            }
        }
        attach.invoke(guest, *args)
        writeField(guest, "mInstrumentation", instrumentation)
    }

    fun attachApplication(app: Application, context: Context) {
        val m = Application::class.java.getDeclaredMethod("attach", Context::class.java)
        m.isAccessible = true
        m.invoke(app, context)
    }

    fun currentActivityThread(): Any? {
        val cls = Class.forName("android.app.ActivityThread")
        return cls.getMethod("currentActivityThread").invoke(null)
    }

    fun readField(target: Any, name: String): Any? {
        var c: Class<*>? = target.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(target)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }

    fun writeField(target: Any, name: String, value: Any?) {
        var c: Class<*>? = target.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                f.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
    }
}
