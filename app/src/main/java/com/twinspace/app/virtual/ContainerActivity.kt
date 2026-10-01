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
        Thread.currentThread().contextClassLoader = loadedPkg.classLoader

        var app = ThreadBind.makeApplication(loadedPkg.loadedApk, instrumentation)
        if (app == null) {
            app = instantiateApplication(loadedPkg)
            ActivityAttacher.attachApplication(app, isolatedCtx)
        }
        ThreadBind.registerApplication(app)
        runCatching { app.onCreate() }
        guestApp = app
        isolatedCtx.guestLoader = harvestLoader(app, loadedPkg)
        Thread.currentThread().contextClassLoader = isolatedCtx.guestLoader

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

    private fun harvestLoader(app: Application, loadedPkg: GuestPackage): ClassLoader {
        val loaders = linkedSetOf<ClassLoader>()
        fun add(cl: ClassLoader?) {
            var c = cl
            while (c != null && loaders.add(c)) {
                c = c.parent
            }
        }
        add(app.classLoader)
        add(Thread.currentThread().contextClassLoader)
        add(loadedPkg.classLoader)
        add(loadedPkg.packageContext?.classLoader)
        runCatching {
            for (f in app.javaClass.declaredFields) {
                f.isAccessible = true
                val v = f.get(app)
                if (v is ClassLoader) add(v)
            }
        }
        return loaders.firstOrNull() ?: loadedPkg.classLoader
    }

    private fun resolveClass(className: String): Class<*> {
        val loaders = linkedSetOf<ClassLoader>()
        fun add(cl: ClassLoader?) {
            var c = cl
            while (c != null && loaders.add(c)) {
                c = c.parent
            }
        }
        add(Thread.currentThread().contextClassLoader)
        add(guestApp?.classLoader)
        add(isolated?.guestLoader)
        add(loaded?.classLoader)
        add(loaded?.packageContext?.classLoader)
        runCatching {
            val app = guestApp
            if (app != null) {
                for (f in app.javaClass.declaredFields) {
                    f.isAccessible = true
                    val v = f.get(app)
                    if (v is ClassLoader) add(v)
                }
            }
        }
        var last: ClassNotFoundException? = null
        for (cl in loaders) {
            try {
                return Class.forName(className, true, cl)
            } catch (e: ClassNotFoundException) {
                last = e
            } catch (e: NoClassDefFoundError) {
                last = ClassNotFoundException(className, e)
            }
        }
        throw last ?: ClassNotFoundException(className)
    }

    private fun showError(pkg: String, t: Throwable) {
        val cause = generateSequence(t) { it.cause }.last()
        val label = loaded?.label ?: pkg
        val body = "Couldn't start this copy of $label.\n\n" +
            "${cause.javaClass.simpleName}: ${cause.message}\n\n" +
            "Your original app is untouched. Remove this clone and add it again after updating TwinSpace, or try another app."
        val text = TextView(this).apply {
            setTextColor(0xFFF4F4F5.toInt())
            textSize = 15f
            setPadding(48, 96, 48, 48)
            this.text = body
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF09090B.toInt())
            addView(text)
        }
        setContentView(root)
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
