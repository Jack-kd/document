package com.example.doctree

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.doctree.ui.MainViewModel
import com.example.doctree.utils.ClipboardUtils
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private var currentFolderUri: Uri? = null

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 持久化权限
            contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            currentFolderUri = it
            val path = getPathFromUri(it)
            viewModel.setFolderPath(path)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        setContentView(R.layout.activity_main)

        val input = findViewById<EditText>(R.id.editTree)
        val output = findViewById<TextView>(R.id.textResult)
        val btnGenerate = findViewById<MaterialButton>(R.id.btnGenerate)
        val btnCopy = findViewById<MaterialButton>(R.id.btnCopy)
        val btnModeText = findViewById<MaterialButton>(R.id.btnModeText)
        val btnModeFolder = findViewById<MaterialButton>(R.id.btnModeFolder)
        val layoutTextMode = findViewById<LinearLayout>(R.id.layoutTextMode)
        val layoutFolderMode = findViewById<LinearLayout>(R.id.layoutFolderMode)
        val btnPickFolder = findViewById<MaterialButton>(R.id.btnPickFolder)
        val textFolderPath = findViewById<TextView>(R.id.textFolderPath)

        // 模式切换
        btnModeText.setOnClickListener { switchMode(0, btnModeText, btnModeFolder, layoutTextMode, layoutFolderMode) }
        btnModeFolder.setOnClickListener { switchMode(1, btnModeText, btnModeFolder, layoutTextMode, layoutFolderMode) }

        // 文件夹选择
        btnPickFolder.setOnClickListener { folderPicker.launch(null) }

        // 观察状态
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                output.text = if (state.treeText.isEmpty()) "等待生成..." else state.treeText

                if (state.folderPath.isNotEmpty()) {
                    textFolderPath.text = state.folderPath
                }

                // 同步模式按钮状态
                if (state.mode == 0) {
                    updateToggleStyle(btnModeText, true)
                    updateToggleStyle(btnModeFolder, false)
                    layoutTextMode.visibility = View.VISIBLE
                    layoutFolderMode.visibility = View.GONE
                } else {
                    updateToggleStyle(btnModeText, false)
                    updateToggleStyle(btnModeFolder, true)
                    layoutTextMode.visibility = View.GONE
                    layoutFolderMode.visibility = View.VISIBLE
                }
            }
        }

        // 生成
        btnGenerate.setOnClickListener {
            if (viewModel.state.value.mode == 0) {
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    viewModel.updateText(text)
                }
            } else {
                currentFolderUri?.let { uri ->
                    val path = getPathFromUri(uri)
                    if (path.isNotEmpty()) {
                        viewModel.generateFromFolder(File(path))
                    }
                }
            }
        }

        // 复制
        btnCopy.setOnClickListener {
            ClipboardUtils.copy(this, output.text.toString())
        }
    }

    private fun switchMode(
        mode: Int,
        btnText: MaterialButton,
        btnFolder: MaterialButton,
        layoutText: LinearLayout,
        layoutFolder: LinearLayout
    ) {
        viewModel.setMode(mode)
    }

    private fun updateToggleStyle(btn: MaterialButton, selected: Boolean) {
        if (selected) {
            btn.backgroundTintList = ColorStateList.valueOf(getColor(com.example.doctree.R.color.primary))
            btn.setTextColor(getColor(android.R.color.white))
            btn.strokeWidth = 0
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.transparent))
            btn.setTextColor(getColor(com.example.doctree.R.color.primary))
            btn.strokeColor = ColorStateList.valueOf(getColor(com.example.doctree.R.color.primary))
            btn.strokeWidth = dpToPx(1.5f)
        }
    }

    private fun getPathFromUri(uri: Uri): String {
        // 尝试从 URI 提取路径
        val docId = try {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            null
        }

        if (docId != null) {
            val parts = docId.split(":")
            if (parts.size >= 2) {
                val type = parts[0]
                val path = parts[1]
                return when (type) {
                    "primary" -> "/storage/emulated/0/$path"
                    else -> "/storage/$type/$path"
                }
            }
        }

        // 回退：尝试从 URI path 提取
        val uriPath = uri.path ?: return ""
        val pathSegments = uriPath.split("/")
        val treeIndex = pathSegments.indexOf("tree")
        return if (treeIndex >= 0 && treeIndex + 1 < pathSegments.size) {
            val decoded = pathSegments.subList(treeIndex + 1, pathSegments.size).joinToString("/")
            if (decoded.contains(":")) {
                val colonParts = decoded.split(":")
                when (colonParts[0]) {
                    "primary" -> "/storage/emulated/0/${colonParts[1]}"
                    else -> "/storage/${colonParts[0]}/${colonParts[1]}"
                }
            } else {
                decoded
            }
        } else {
            uriPath
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
