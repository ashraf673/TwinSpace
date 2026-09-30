package com.twinspace.app

import android.app.Application
import android.content.Context
import com.twinspace.app.virtual.HiddenApi

class TwinSpaceApp : Application() {
    override fun attachBaseContext(base: Context) {
        HiddenApi.exempt()
        super.attachBaseContext(base)
    }
}
