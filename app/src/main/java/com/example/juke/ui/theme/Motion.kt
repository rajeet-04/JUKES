package com.example.juke.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/**
 * One motion language for the app, the same one the player sheet uses: critically damped springs for
 * anything that moves (no bounce, it just settles and takes over a gesture's velocity), short fades
 * for anything that only appears, and exits faster than entrances.
 */
object Motion {
    /** Spatial movement: screens sliding, sheets settling. */
    fun <T> spatial(stiffness: Float = 380f) =
        spring<T>(dampingRatio = 1f, stiffness = stiffness, visibilityThreshold = null)

    private val slideSpring = spring<IntOffset>(
        dampingRatio = 1f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold
    )
    private val exitSpring = spring<IntOffset>(
        dampingRatio = 1f, stiffness = 560f, visibilityThreshold = IntOffset.VisibilityThreshold
    )
    private val decelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    private val topLevel = setOf("home", "search", "library")
    private fun String?.isTopLevel() = this != null && substringBefore('/') in topLevel

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.between() =
        initialState.destination.route.isTopLevel() && targetState.destination.route.isTopLevel()

    /** Tab to tab: fade-through. The old screen leaves quickly, the new one settles in from 98%. */
    private val fadeThroughIn: EnterTransition =
        fadeIn(tween(210, delayMillis = 70, easing = decelerate)) +
            scaleIn(tween(280, delayMillis = 70, easing = decelerate), initialScale = 0.98f)
    private val fadeThroughOut: ExitTransition = fadeOut(tween(90))

    fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
        if (between()) fadeThroughIn
        else slideInHorizontally(slideSpring) { it / 7 } + fadeIn(tween(200, easing = decelerate))

    fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
        if (between()) fadeThroughOut
        else slideOutHorizontally(exitSpring) { -it / 12 } + fadeOut(tween(140))

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
        if (between()) fadeThroughIn
        else slideInHorizontally(slideSpring) { -it / 12 } + fadeIn(tween(200, easing = decelerate))

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
        if (between()) fadeThroughOut
        else slideOutHorizontally(exitSpring) { it / 7 } + fadeOut(tween(140))
}

/**
 * Press feedback shared by rows and cards: they settle slightly in while pressed and spring back,
 * driven by the same [InteractionSource] as the click, so a scroll that steals the touch releases it.
 */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.98f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
