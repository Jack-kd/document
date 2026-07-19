package com.example.doctree.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doctree.core.FileTreeGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun setMode(mode: Int) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setSource(source: Int) {
        _state.value = _state.value.copy(source = source)
    }

    fun updateText(text: String) {
        _state.value = _state.value.copy(treeText = text)
    }

    fun setFolder(file: File) {
        _state.value = _state.value.copy(
            folderPath = file.absolutePath,
            folderName = file.name
        )
    }

    fun generateFromText(text: String) {
        if (text.isBlank()) return
        _state.value = _state.value.copy(treeText = text, message = "生成完成")
    }

    fun generateFromFolder(folder: File) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val result = FileTreeGenerator.generate(folder)
            _state.value = _state.value.copy(
                treeText = result,
                loading = false,
                message = "生成完成"
            )
        }
    }

    fun clear() {
        _state.value = UiState()
    }
}