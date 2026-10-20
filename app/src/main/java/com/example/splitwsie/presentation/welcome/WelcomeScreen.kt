package com.example.splitwsie.presentation.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitwsie.presentation.theme.SettleBackground
import com.example.splitwsie.presentation.theme.SettlePrimary
import com.example.splitwsie.presentation.theme.SettleTextPrimary
import com.example.splitwsie.presentation.theme.SettleTextSecondary
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    isLoggedIn: Boolean = false,
    onGetStarted: () -> Unit
) {
    var isLogoVisible by remember { mutableStateOf(false) }
    var isTextVisible by remember { mutableStateOf(false) }
    var isButtonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        isLogoVisible = true
        delay(400)
        isTextVisible = true
        delay(500)
        isButtonVisible = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (isLogoVisible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "Logo Scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SettleBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Animated Logo
            AnimatedVisibility(
                visible = isLogoVisible,
                enter = fadeIn(animationSpec = tween(800))
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(logoScale)
                        .background(SettlePrimary.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 64.sp
                        ),
                        color = SettlePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Animated Text
            AnimatedVisibility(
                visible = isTextVisible,
                enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
                    initialOffsetY = { 50 },
                    animationSpec = tween(600, easing = FastOutSlowInEasing)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Settle Up Effortlessly",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = SettleTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Track shared expenses, split bills, and never argue about money again.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SettleTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Animated Button
            AnimatedVisibility(
                visible = isButtonVisible,
                enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
                    initialOffsetY = { 20 },
                    animationSpec = tween(600, easing = FastOutSlowInEasing)
                )
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SettlePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (isLoggedIn) "Continue" else "Get Started",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
