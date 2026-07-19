package com.example.doctree.ui

data class UiState(
    val loading: Boolean = false,
    val treeText: String = "",
    val message: String = "",
    val mode: Int = 0,          // 0=文本模式, 1=文件夹模式
    val folderPath: String = ""  // 文件夹模式下的路径
)
