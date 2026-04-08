package com.codingdrama.vlmwebrtc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.codingdrama.vlmwebrtc.permission.initPermissionHandler
import com.codingdrama.vlmwebrtc.permission.setPermissionContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize permission handling - MUST be called before setContent
        initPermissionHandler(this)
        setPermissionContext(this, this)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}