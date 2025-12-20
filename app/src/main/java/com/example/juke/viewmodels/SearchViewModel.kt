package com.example.juke.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.models.SpotdownSong
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<SpotdownSong> = emptyList(),
    val isSearching: Boolean = false,
    val downloadingId: String? = null,
    val error: String? = null
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }
    
    fun searchSongs(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList())
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            
            try {
                val response = SpotifyApi.searchSongs(query)
                _uiState.value = _uiState.value.copy(
                    results = response.songs,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }
    
    fun setDownloading(songId: String?) {
        _uiState.value = _uiState.value.copy(downloadingId = songId)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
