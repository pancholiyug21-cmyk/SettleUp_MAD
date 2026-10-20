package com.example.splitwsie.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.splitwsie.presentation.theme.SettleTextPrimary
import com.example.splitwsie.presentation.theme.SettleTextSecondary

// A predefined color palette for our charts
val ChartColors = listOf(
    Color(0xFF6366F1), // Indigo
    Color(0xFF8B5CF6), // Violet
    Color(0xFFEC4899), // Pink
    Color(0xFFF43F5E), // Rose
    Color(0xFFF59E0B), // Amber
    Color(0xFF10B981), // Emerald
    Color(0xFF06B6D4), // Cyan
    Color(0xFF3B82F6)  // Blue
)

@Composable
fun DonutChart(
    values: List<Float>,
    colors: List<Color> = ChartColors,
    modifier: Modifier = Modifier,
    thickness: Dp = 32.dp,
    centerText: String = ""
) {
    val total = values.sum()
    if (total == 0f) return

    val sweepAngles = values.map { (it / total) * 360f }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(values) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var startAngle = -90f
            val strokeWidth = thickness.toPx()

            for (i in sweepAngles.indices) {
                val sweepAngle = sweepAngles[i] * animationProgress.value
                val color = colors[i % colors.size]

                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                )

                startAngle += sweepAngles[i]
            }
        }
        
        if (centerText.isNotBlank()) {
            Text(
                text = centerText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SettleTextPrimary
            )
        }
    }
}

@Composable
fun BarChart(
    labels: List<String>,
    values: List<Float>,
    barColor: Color = ChartColors[0],
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return

    val maxVal = values.maxOrNull() ?: 0f
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(values) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / (values.size * 2f) // Leave space between bars
        val maxBarHeight = size.height

        for (i in values.indices) {
            val value = values[i]
            val height = if (maxVal > 0) (value / maxVal) * maxBarHeight * animationProgress.value else 0f
            
            val x = i * (barWidth * 2f) + (barWidth / 2f)
            val y = size.height - height

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(16f, 16f)
            )
        }
    }
}
