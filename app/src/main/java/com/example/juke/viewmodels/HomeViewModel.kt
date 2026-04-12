package com.example.juke.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val greeting: String = "",
    val recentlyPlayed: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private fun buildGreeting(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        calendar.getDisplayName(
            Calendar.DAY_OF_WEEK,
            Calendar.LONG,
            java.util.Locale.getDefault()
        )
        return when {
            hour in 5..11 -> "Rise & Shine!"
            hour in 12..16 -> "Good Afternoon!"
            hour in 17..20 -> "Good Evening!"
            else -> "Night Vibes!"
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            fetchData()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            fetchData()
        }
    }

    private suspend fun fetchData() {
        val recentlyPlayed = trackDao.getRecentlyPlayed(10).map { it.toTrack() }
        val mostPlayed = trackDao.getMostPlayed(10).map { it.toTrack() }
        val favorites = trackDao.getFavourites().take(10).map { it.toTrack() }

        _uiState.value = HomeUiState(
            greeting = buildGreeting(),
            recentlyPlayed = recentlyPlayed,
            mostPlayed = mostPlayed,
            favorites = favorites,
            isLoading = false,
            isRefreshing = false
        )
    }
}
