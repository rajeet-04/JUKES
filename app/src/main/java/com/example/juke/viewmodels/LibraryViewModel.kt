package com.example.juke.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import com.example.juke.services.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val showFavoritesOnly: Boolean = false,
    val isLoading: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    private val musicService = MusicService(application)
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    fun loadTracks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val tracks = if (_uiState.value.showFavoritesOnly) {
                trackDao.getFavourites().map { it.toTrack() }
            } else {
                trackDao.getDownloadedTracks().map { it.toTrack() }
            }
            
            _uiState.value = _uiState.value.copy(
                tracks = tracks,
                isLoading = false
            )
        }
    }
    
    fun toggleFavoritesFilter() {
        _uiState.value = _uiState.value.copy(
            showFavoritesOnly = !_uiState.value.showFavoritesOnly
        )
        loadTracks()
    }
    
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            trackDao.updateTrackFavourite(track.uuid, !track.isFavourite)
            loadTracks()
        }
    }
    
    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            musicService.deleteTrackAndFiles(track)
            loadTracks()
        }
    }
}
