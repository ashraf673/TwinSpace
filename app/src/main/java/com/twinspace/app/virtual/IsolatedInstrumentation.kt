package com.twinspace.app.virtual

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/**
 * Guest Activity.startActivity goes through Instrumentation.execStartActivity
 * (hidden, public at runtime). Matching signatures override it on the JVM so
 * the original installed app is never launched — same-package starts stay
 * inside the TwinSpace container.
 */
class IsolatedInstrumentation(
    private val host: ContainerActivity
) : Instrumentation() {

    fun execStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        target: Activity?,
        intent: Intent?,
        requestCode: Int,
        options: Bundle?
    ): ActivityResult? = handle(intent)

    fun execStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        resultWho: String?,
        intent: Intent?,
        requestCode: Int,
        options: Bundle?
    ): ActivityResult? = handle(intent)

    fun execStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        target: Activity?,
        intent: Intent?,
        requestCode: Int,
        options: Bundle?,
        user: Any?
    ): ActivityResult? = handle(intent)

    fun execStartActivities(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        target: Activity?,
        intents: Array<Intent>?,
        options: Bundle?
    ) {
        intents?.forEach { handle(it) }
    }

    private fun handle(intent: Intent?): ActivityResult? {
        if (intent != null) host.consumeStart(intent)
        return ActivityResult(Activity.RESULT_CANCELED, null)
    }
}
