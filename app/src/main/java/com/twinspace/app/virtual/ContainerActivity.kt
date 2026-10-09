package com.twinspace.app.virtual

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView

open class ContainerActivity : Activity() {
    private var guest: Activity? = null
    private var guestApp: Application? = null
    private var loaded: GuestPackage? = null
    private var isolated: IsolatedContext? = null
    private var instr: IsolatedInstrumentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(VirtualCore.EXTRA_PKG)
        if (pkg.isNullOrBlank()) {
            finish()
            return
        }
        try {
            bindGuest(pkg)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start $pkg", t)
            showError(pkg, t)
        }
    }

    private fun bindGuest(pkg: String) {
        val loadedPkg = GuestLoader.load(this, pkg)
        PathRedirect.install(loadedPkg.packageName, loadedPkg.dataRoot)
        OsHook.install()
        PmHook.install(loadedPkg.packageName, loadedPkg.appInfo)
        IoNative.install(loadedPkg.packageName, loadedPkg.dataRoot)
        ThreadBind.patchLoadedApk(loadedPkg.loadedApk, loadedPkg.dataRoot, loadedPkg.appInfo)

        val wrapBase = loadedPkg.packageContext ?: this
        val isolatedCtx = IsolatedContext(
            host = wrapBase,
            owner = this,
            guestPackage = loadedPkg.packageName,
            guestAppInfo = loadedPkg.appInfo,
            guestResources = loadedPkg.resources,
            guestLoader = loadedPkg.classLoader,
            dataRoot = loadedPkg.dataRoot
        )
        loaded = loadedPkg
        isolated = isolatedCtx
        val instrumentation = IsolatedInstrumentation(this)
        instr = instrumentation
        ClassLoaders.pin(loadedPkg.classLoader)
        ThreadBind.setClassLoader(loadedPkg.loadedApk, loadedPkg.classLoader)

        var app = ThreadBind.makeApplication(loadedPkg.loadedApk)
        // makeApplication installs a boot/warning class loader. Put the guest loader back
        // before Application.onCreate, which is when apps unpack hidden dex.
        val baseLoader = ClassLoaders.best(
            listOf(ThreadBind.classLoaderOf(loadedPkg.loadedApk), loadedPkg.classLoader, loadedPkg.packageContext?.classLoader),
            listOf(loadedPkg.applicationClass)
        ) ?: loadedPkg.classLoader
        ClassLoaders.pin(baseLoader)
        ThreadBind.setClassLoader(loadedPkg.loadedApk, baseLoader)
        if (app == null) {
            app = instantiateApplication(loadedPkg)
            ActivityAttacher.attachApplication(app, isolatedCtx)
        }
        ThreadBind.registerApplication(app)
        app.onCreate()
        val live = ClassLoaders.best(
            listOf(
                Thread.currentThread().contextClassLoader,
                ThreadBind.classLoaderOf(loadedPkg.loadedApk),
                app.classLoader,
                baseLoader,
                loadedPkg.classLoader,
                loadedPkg.packageContext?.classLoader
            ),
            listOf(loadedPkg.launcher.name, loadedPkg.applicationClass)
        ) ?: baseLoader
        isolatedCtx.guestLoader = live
        ClassLoaders.pin(live)
        ThreadBind.setClassLoader(loadedPkg.loadedApk, live)
        guestApp = app

        startGuest(loadedPkg.launcher.name, Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(loadedPkg.packageName, loadedPkg.launcher.name)
        }, first = true)
    }

    internal fun consumeStart(intent: Intent): Boolean {
        val guestPkg = loaded?.packageName
        val targetPkg = intent.component?.packageName ?: intent.`package`
        if (guestPkg != null && targetPkg == guestPkg) {
            val className = intent.component?.className ?: loaded?.launcher?.name
            if (!className.isNullOrBlank()) {
                startGuest(className, intent, first = false)
            }
            return true
        }
        try {
            val send = Intent(intent)
            if (send.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0) {
                send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(send)
        } catch (_: Exception) {
        }
        return true
    }

    private fun startGuest(className: String, launch: Intent, first: Boolean) {
        val loadedPkg = loaded ?: return
        val isolatedCtx = isolated ?: return
        val app = guestApp ?: return
        val instrumentation = instr ?: IsolatedInstrumentation(this)

        val prev = guest
        if (prev != null && !first) {
            runCatching { Instrumentation().callActivityOnPause(prev) }
            runCatching { Instrumentation().callActivityOnStop(prev) }
            runCatching { Instrumentation().callActivityOnDestroy(prev) }
        }

        val info = loadedPkg.activities.firstOrNull { it.name == className } ?: loadedPkg.launcher
        info.applicationInfo = loadedPkg.appInfo
        val activityClass = resolveClass(className)
        val created = activityClass.getDeclaredConstructor().newInstance() as Activity
        ActivityAttacher.attach(created, isolatedCtx, app, launch, info, this, instrumentation)
        Instrumentation().callActivityOnCreate(created, null)
        if (!first) {
            runCatching { Instrumentation().callActivityOnStart(created) }
            runCatching { Instrumentation().callActivityOnResume(created) }
        }
        guest = created
    }

    private fun instantiateApplication(loadedPkg: GuestPackage): Application {
        val name = loadedPkg.applicationClass
        if (name.isNullOrBlank()) return Application()
        val cls = resolveClass(name)
        return cls.getDeclaredConstructor().newInstance() as Application
    }

    private fun resolveClass(className: String): Class<*> {
        val names = listOf(className)
        val candidates = listOf(
            Thread.currentThread().contextClassLoader,
            isolated?.guestLoader,
            guestApp?.classLoader,
            ThreadBind.classLoaderOf(loaded?.loadedApk),
            loaded?.classLoader,
            loaded?.packageContext?.classLoader
        )
        val chosen = ClassLoaders.best(candidates, names)
        val loaders = linkedSetOf<ClassLoader>()
        if (chosen != null) loaders.add(chosen)
        for (cl in candidates) {
            if (cl != null && !ClassLoaders.isBoot(cl)) loaders.add(cl)
        }
        var last: Throwable? = null
        for (cl in loaders) {
            try {
                return Class.forName(className, false, cl)
            } catch (e: ClassNotFoundException) {
                last = e
            } catch (e: NoClassDefFoundError) {
                last = e
            }
        }
        val detail = last?.message ?: "not in the cloned app class loader"
        throw ClassNotFoundException("$className ($detail)")
    }

    private fun showError(pkg: String, t: Throwable) {
        val label = loaded?.label ?: pkg
        val body = "Couldn't start this copy of $label.\n\n" +
            describe(t) +
            "\n\nYour original app is untouched."
        val text = TextView(this).apply {
            setTextColor(0xFFF4F4F5.toInt())
            textSize = 13f
            setPadding(48, 72, 48, 48)
            this.text = body
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF09090B.toInt())
            addView(text)
        }
        setContentView(root)
    }

    private fun describe(t: Throwable): String {
        val lines = ArrayList<String>()
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < 4) {
            lines.add(current.toString())
            val trace = current.stackTrace
            val shown = if (trace.size > 6) 6 else trace.size
            for (i in 0 until shown) {
                lines.add("  at ${trace[i]}")
            }
            current = current.cause
            depth++
        }
        return lines.joinToString("\n")
    }

    override fun onStart() {
        super.onStart()
        runCatching { Instrumentation().callActivityOnStart(guest ?: return) }
    }

    override fun onResume() {
        super.onResume()
        runCatching { Instrumentation().callActivityOnResume(guest ?: return) }
    }

    override fun onPause() {
        runCatching { Instrumentation().callActivityOnPause(guest ?: return@runCatching) }
        super.onPause()
    }

    override fun onStop() {
        runCatching { Instrumentation().callActivityOnStop(guest ?: return@runCatching) }
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { Instrumentation().callActivityOnDestroy(guest ?: return@runCatching) }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runCatching { guest?.onConfigurationChanged(newConfig) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        runCatching { guest?.onWindowFocusChanged(hasFocus) }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val g = guest
        return try {
            g?.dispatchTouchEvent(ev) ?: super.dispatchTouchEvent(ev)
        } catch (_: Throwable) {
            super.dispatchTouchEvent(ev)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val g = guest
        return try {
            g?.dispatchKeyEvent(event) ?: super.dispatchKeyEvent(event)
        } catch (_: Throwable) {
            super.dispatchKeyEvent(event)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val g = guest
        if (g != null) {
            try {
                g.onBackPressed()
                return
            } catch (_: Throwable) {
            }
        }
        super.onBackPressed()
    }

    companion object {
        private const val TAG = "TwinSpace"
    }
}

class C1 : ContainerActivity()
class C2 : ContainerActivity()
class C3 : ContainerActivity()
class C4 : ContainerActivity()
class C5 : ContainerActivity()
class C6 : ContainerActivity()
class C7 : ContainerActivity()
class C8 : ContainerActivity()
