package com.pbrp.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pbrp.manager.ui.AppNavigation
import com.pbrp.manager.ui.theme.PBRPTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PBRPTheme {
                AppNavigation()
            }
        }
    }
}
