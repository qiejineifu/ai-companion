package com.aicompanion.feature.live2d

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun Live2DComposeView(
    manager: Live2DManager,
    modifier: Modifier = Modifier
) {
    val state by manager.state.collectAsState()

    // Idle breathing animation
    val infiniteTransition = rememberInfiniteTransition()
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2D2D3A))
            .pointerInput(Unit) {
                detectTapGestures { manager.onTap() }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    manager.onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (state.isLoaded) {
            // Live2D model placeholder - in production replaced by GLSurfaceView
            Canvas(modifier = Modifier.fillMaxSize(0.6f)) {
                val w = size.width
                val h = size.height
                val cx = w / 2
                val cy = h / 2

                // Head
                drawCircle(
                    color = Color(0xFFFFE0BD),
                    radius = w * 0.2f * breathScale,
                    center = Offset(cx, cy - h * 0.1f)
                )
                // Hair
                drawArc(
                    color = Color(0xFF4A3728),
                    startAngle = 180f, sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.22f, cy - h * 0.28f),
                    size = Size(w * 0.44f, h * 0.35f),
                    style = Stroke(width = 2f)
                )
                // Eyes
                drawCircle(Color.Black, radius = 4f, center = Offset(cx - w * 0.06f, cy - h * 0.1f))
                drawCircle(Color.Black, radius = 4f, center = Offset(cx + w * 0.06f, cy - h * 0.1f))
                // Mouth
                drawArc(
                    color = Color(0xFFE57373),
                    startAngle = 0f, sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - 6f, cy - h * 0.02f),
                    size = Size(12f, 8f)
                )
            }

            // Emotion indicator
            if (state.currentEmotion != com.aicompanion.core.common.Emotion.NEUTRAL) {
                Text(
                    state.currentEmotion.emoji,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("😊", style = MaterialTheme.typography.displayMedium)
                Text("模型未加载", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f))
            }
        }

        // Motion indicator
        if (state.isPlayingMotion) {
            Text(
                text = "▶ ${state.motionName ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp)
            )
        }
    }
}
