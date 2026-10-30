package com.pokai.codync.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.pokai.codync.android.BuildConfig
import com.pokai.codync.design.AvatarMood
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private data class CrewMember(
    val shape: String,
    val color: String,
    val size: Int,
    val x: Int,
    val y: Int,
    val mood: AvatarMood,
)

private val crew = listOf(
    CrewMember("blob", "blue", 92, 0, 0, AvatarMood.Working),
    CrewMember("squircle", "orange", 64, -104, -34, AvatarMood.Idle),
    CrewMember("teardrop", "violet", 58, 100, -46, AvatarMood.Working),
    CrewMember("hex", "green", 48, -76, 64, AvatarMood.Idle),
    CrewMember("cloud", "magenta", 52, 84, 58, AvatarMood.NeedsInput),
)

/**
 * First-run screen shown once after install. It mirrors the desktop welcome: the animated
 * crew, a short exchange, the product line, then account sign-in and a no-account path.
 */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val colors = CodyncTheme.colors
    val configured = BuildConfig.CLERK_PUBLISHABLE_KEY.isNotBlank()
    val scope = rememberCoroutineScope()
    var beat by remember { mutableStateOf(0) }
    var signingIn by remember { mutableStateOf(false) }
    var replied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        listOf(120L, 380L, 640L, 900L).forEachIndexed { index, at ->
            delay(if (index == 0) at else at - listOf(120L, 380L, 640L, 900L)[index - 1])
            beat = index + 1
        }
    }
    LaunchedEffect(Unit) {
        delay(1700)
        replied = true
    }
    LaunchedEffect(Unit) {
        // A restored Clerk session skips the choice, exactly like the desktop app.
        if (!configured) return@LaunchedEffect
        val result = runCatching { Clerk.auth.getToken() }.getOrNull()
        if (result is ClerkResult.Success) onContinue()
    }
    LaunchedEffect(signingIn) {
        if (!signingIn) return@LaunchedEffect
        repeat(60) {
            delay(1_000)
            val result = runCatching { Clerk.auth.getToken() }.getOrNull()
            if (result is ClerkResult.Success) {
                onContinue()
                return@LaunchedEffect
            }
        }
        signingIn = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            WelcomeCrew(shown = beat >= 1)
            Spacer(Modifier.height(10.dp))
            WelcomeChatGlimpse(shown = beat >= 2, replied = replied)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = beat >= 3,
                enter = fadeIn(tween(520)) + slideInVertically(tween(520)) { it / 8 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your coding agents,\nas teammates.",
                        color = colors.text,
                        style = CodyncType.largeTitle,
                    )
                    Text(
                        "Give each one a name and a project. They work on your computer while you're away.",
                        color = colors.secondary,
                        style = CodyncType.body,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = beat >= 4,
                enter = fadeIn(tween(520)) + slideInVertically(tween(520)) { it / 10 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Continue on this phone",
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = if (signingIn) "Waiting for Google…" else "Continue with Google",
                        onClick = {
                            if (!configured || signingIn) return@SecondaryButton
                            signingIn = true
                            scope.launch {
                                runCatching { Clerk.auth.startHostedAuth() }
                                    .onFailure { signingIn = false }
                            }
                        },
                        enabled = configured && !signingIn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!configured) {
                        Text(
                            "Sign-in isn't configured in this build. You can still use Codync on this phone.",
                            color = colors.tertiary,
                            style = CodyncType.footnote,
                        )
                    }
                    Text(
                        "You can sign in later from Settings. Pairing a computer never needs an account.",
                        color = colors.tertiary,
                        style = CodyncType.caption,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun WelcomeCrew(shown: Boolean) {
    val motion = rememberInfiniteTransition(label = "welcome-crew")
    val phase = motion.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4600, easing = LinearEasing), RepeatMode.Restart),
        label = "welcome-crew-phase",
    )
    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        label = "welcome-crew-entrance",
    )
    Box(Modifier.fillMaxWidth().height(184.dp)) {
        crew.forEachIndexed { index, member ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = member.x.dp, y = member.y.dp)
                    .graphicsLayer {
                        val bob = sin(phase.value * 0.85f + index * 1.7f) * 6.dp.toPx()
                        translationY = bob
                        val scale = 0.35f + 0.65f * entrance
                        scaleX = scale
                        scaleY = scale
                        alpha = entrance
                    },
            ) {
                CharacterAvatar(
                    name = member.shape,
                    colorId = member.color,
                    shapeId = member.shape,
                    size = member.size.dp,
                    mood = member.mood,
                )
            }
        }
    }
}

@Composable
private fun WelcomeChatGlimpse(shown: Boolean, replied: Boolean) {
    val colors = CodyncTheme.colors
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(480)) + slideInVertically(tween(480)) { it / 6 },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Fix the flaky login test",
                    color = colors.text,
                    style = CodyncType.body,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp, 6.dp, 18.dp, 18.dp))
                        .background(colors.bubbleSent)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CharacterAvatar(
                    name = "blob",
                    colorId = "blue",
                    shapeId = "blob",
                    size = 28.dp,
                    mood = if (replied) AvatarMood.Idle else AvatarMood.Working,
                )
                AnimatedVisibility(
                    visible = replied,
                    enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 5 },
                ) {
                    Text(
                        "Done. It raced the session refresh; tests pass on fix/login.",
                        color = colors.text,
                        style = CodyncType.body,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp))
                            .background(colors.bubbleAgent)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
                if (!replied) {
                    Text("Working…", color = colors.tertiary, style = CodyncType.footnote)
                }
                Spacer(Modifier.size(0.dp))
            }
        }
    }
}
