package com.example.doctree.ui

data class UiState(
    val loading: Boolean = false,
    val treeText: String = "",
    val message: String = "",
    val mode: Int = 0,           // 0 = text mode, 1 = folder mode
    val folderPath: String = "",  // selected folder path
    val folderName: String = "",  // selected folder name
    val source: Int = 0            // 0 = ChatGPT, 1 = DeepSeek
)