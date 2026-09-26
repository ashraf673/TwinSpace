package com.twinspace.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.twinspace.app.ui.TwinApp
import com.twinspace.app.ui.TwinTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Admin.isProfileOwner(this)) {
            TwinAdminReceiver.enableProfile(this)
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            TwinTheme {
                TwinApp()
            }
        }
    }
}
