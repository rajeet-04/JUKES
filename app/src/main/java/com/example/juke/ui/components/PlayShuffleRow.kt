package com.example.juke.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.juke.ui.icons.JukeIcons

/** Shuffle and a round Play: the one place a list starts playing from. */
@Composable
fun PlayShuffle(onShuffle: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onShuffle, Modifier.size(48.dp)) { Icon(JukeIcons.Shuffle, "Shuffle") }
        FilledIconButton(onPlay, Modifier.size(48.dp)) { Icon(JukeIcons.Play, "Play", Modifier.size(26.dp)) }
    }
}

/** A list's section title on the left, [PlayShuffle] on the right: the controls sit with the list they play. */
@Composable
fun PlayShuffleRow(title: String, onShuffle: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        PlayShuffle(onShuffle, onPlay)
    }
}
