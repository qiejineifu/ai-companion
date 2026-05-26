package com.aicompanion.feature.live2d

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aicompanion.core.common.Emotion

/**
 * Compose wrapper for Live2D Cubism rendering via AndroidView + GLSurfaceView.
 */
@Composable
fun Live2DComposeView(
    model: Live2DModel?,
    emotion: Emotion = Emotion.NEUTRAL,
    isSpeaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    var glView by remember { mutableStateOf<CubismRendererView?>(null) }

    // Mouth animation when speaking
    val mouthTransition = rememberInfiniteTransition()
    val mouthOpen by mouthTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(150),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Update expression and mouth on state change
    LaunchedEffect(isSpeaking, emotion) {
        model?.let { m ->
            if (isSpeaking) {
                m.setMouthOpen(mouthOpen * 0.6f + 0.2f)
            } else {
                m.setMouthOpen(0f)
            }
            m.setExpressionByName(emotion.toCubismExpression())
        }
    }

    Box(
        modifier = modifier
            .pointerInput(model) {
                detectTapGestures {
                    model?.setExpressionByName(listOf("happy", "surprise", "shy").random())
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AndroidView(
                factory = { ctx ->
                    CubismRendererView(ctx).also { view ->
                        view.setModel(model)
                        glView = view
                    }
                },
                update = { view ->
                    if (view.getCubismModel() !== model) {
                        view.setModel(model)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("😊", style = MaterialTheme.typography.displayMedium)
                Text("模型未加载", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { glView?.cleanup() }
    }
}

private fun Emotion.toCubismExpression(): String = when (this) {
    Emotion.HAPPY -> "happy"
    Emotion.SAD -> "sad"
    Emotion.ANGRY -> "angry"
    Emotion.SURPRISED -> "surprise"
    Emotion.SHY -> "shy"
    Emotion.THINKING -> "neutral"
    Emotion.NEUTRAL -> "neutral"
}
