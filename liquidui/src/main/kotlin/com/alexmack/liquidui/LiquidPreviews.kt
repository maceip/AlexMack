package com.alexmack.liquidui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun LiquidSurfacePreview() {
    MaterialTheme {
        LiquidSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(16.dp),
            motionState = LiquidMotionState(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "LiquidSurface", color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun LiquidCardPreview() {
    MaterialTheme {
        LiquidCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(16.dp),
            motionState = LiquidMotionState(),
            containerColor = Color(0xFF0C1924),
        ) {
            Column {
                Text(text = "LiquidCard", color = Color.White)
                Text(text = "Preview content", color = Color(0xFFBBD7FF))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun LiquidBottomSheetPreview() {
    MaterialTheme {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        LiquidModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {},
            motionState = LiquidMotionState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(text = "LiquidModalBottomSheet", color = Color.White)
                Text(text = "Preview content", color = Color(0xFFBBD7FF))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun LiquidSplitCardDemoPreview() {
    MaterialTheme {
        LiquidSplitCardDemo(
            modifier = Modifier.fillMaxWidth(),
            useMotionController = false,
            motionState = LiquidMotionState(),
        )
    }
}
