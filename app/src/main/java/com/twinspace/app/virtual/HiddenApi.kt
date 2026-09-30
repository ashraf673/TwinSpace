package com.twinspace.app.virtual

import java.lang.reflect.Method

object HiddenApi {
    fun exempt() {
        try {
            val classArray = arrayOf<Class<*>>().javaClass
            val stringArray = arrayOf<String>().javaClass
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclared = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod",
                String::class.java,
                classArray
            )
            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclared.invoke(vmRuntimeClass, "getRuntime", null) as Method
            val setExemptions = getDeclared.invoke(
                vmRuntimeClass,
                "setHiddenApiExemptions",
                arrayOf(stringArray)
            ) as Method
            val vmRuntime = getRuntime.invoke(null)
            setExemptions.invoke(vmRuntime, arrayOf("L") as Any)
        } catch (_: Throwable) {
            // Best-effort: older devices don't need this.
        }
    }
}
