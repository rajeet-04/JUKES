package com.example.juke.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.glassFloat
import com.example.juke.utils.rememberJukeHaptics
import kotlin.time.Duration.Companion.milliseconds

/**
 * One compact header shared by Library and Search: title + actions at rest, and the same
 * pill turns into the search field when [open] (tap the search icon). Saves a whole row.
 */
@Composable
fun SearchHeader(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    /** Bump to focus the field, select everything in it and show the keyboard (Search tab re-tap). */
    selectAllTrigger: Int = 0,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val haptic = rememberJukeHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(open) {
        if (open) {
            try { focus.requestFocus() } catch (_: Exception) {}
            keyboard?.show()
        }
    }

    // The field keeps its own selection so a re-tap can select the whole query for replacing.
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (field.text != query) field = TextFieldValue(query, TextRange(query.length))
    }
    var handledSelectAll by remember { mutableIntStateOf(selectAllTrigger) }
    LaunchedEffect(selectAllTrigger, open) {
        if (open && selectAllTrigger != handledSelectAll) {
            handledSelectAll = selectAllTrigger
            delay(60.milliseconds) // let the field enter composition when the bar was closed
            field = field.copy(selection = TextRange(0, field.text.length))
            try { focus.requestFocus() } catch (_: Exception) {}
            keyboard?.show()
        }
    }
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glassFloat(GlassShapes.Pill, GlassLevel.Regular)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = open,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "searchHeader"
        ) { isOpen ->
            if (isOpen) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        haptic.click()
                        keyboard?.hide()
                        onQueryChange("")
                        onOpenChange(false)
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
                    BasicTextField(
                        value = field,
                        onValueChange = {
                            field = it
                            if (it.text != query) onQueryChange(it.text)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { haptic.click(); onQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Clear search")
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 14.dp)
                    )
                    IconButton(onClick = { haptic.click(); onOpenChange(true) }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    actions()
                }
            }
        }
    }
}
