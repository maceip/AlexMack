package com.alexmack.liquidui.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.alexmack.liquidui.LiquidSplitCardDemo

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B1220)),
                    contentAlignment = Alignment.Center,
                ) {
                    LiquidSplitCardDemo()
                }
            }
        }
    }
}
