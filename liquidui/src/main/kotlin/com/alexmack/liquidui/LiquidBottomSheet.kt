package com.alexmack.liquidui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidModalBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    state: LiquidSurfaceState = rememberLiquidSurfaceState(cornerRadius = 48f),
    motionState: LiquidMotionState = LiquidMotionState(),
    scrimColor: Color = Color(0x99000000),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = scrimColor,
        dragHandle = null,
    ) {
        LiquidSurface(
            modifier = Modifier.fillMaxWidth(),
            state = state,
            motionState = motionState,
        ) {
            androidx.compose.foundation.layout.Column(content = content)
        }
    }
}
