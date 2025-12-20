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

data class HomeUiState(
    val recentlyPlayed: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val isLoading: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val recentlyPlayed = trackDao.getRecentlyPlayed(10).map { it.toTrack() }
            val mostPlayed = trackDao.getMostPlayed(10).map { it.toTrack() }
            
            _uiState.value = HomeUiState(
                recentlyPlayed = recentlyPlayed,
                mostPlayed = mostPlayed,
                isLoading = false
            )
        }
    }
}
