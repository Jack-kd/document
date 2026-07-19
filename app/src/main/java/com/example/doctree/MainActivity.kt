package com.example.doctree

import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.doctree.ui.MainViewModel
import com.example.doctree.utils.ClipboardUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel

    // UI elements
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var btnGenerate: MaterialButton
    private lateinit var btnCopy: MaterialButton
    private lateinit var btnSelectFolder: MaterialButton
    private lateinit var tabTextMode: TextView
    private lateinit var tabFolderMode: TextView
    private lateinit var tabChatGPT: TextView
    private lateinit var tabDeepSeek: TextView
    private lateinit var cardTextInput: MaterialCardView
    private lateinit var cardFolderInput: MaterialCardView
    private lateinit var folderName: TextView
    private lateinit var folderPath: TextView
    private lateinit var sourceText: TextView
    private lateinit var sourceLabel: TextView

    private var selectedFolder: File? = null
    private var currentMode: Int = 0    // 0 = text, 1 = folder
    private var currentSource: Int = 0  // 0 = ChatGPT, 1 = DeepSeek

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        setContentView(R.layout.activity_main)

        bindViews()
        setupTabs()
        setupSourceTabs()
        setupListeners()
        observeState()
    }

    private fun bindViews() {
        input = findViewById(R.id.editTree)
        output = findViewById(R.id.textResult)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnCopy = findViewById(R.id.btnCopy)
        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        tabTextMode = findViewById(R.id.tabTextMode)
        tabFolderMode = findViewById(R.id.tabFolderMode)
        tabChatGPT = findViewById(R.id.tabChatGPT)
        tabDeepSeek = findViewById(R.id.tabDeepSeek)
        cardTextInput = findViewById(R.id.cardTextInput)
        cardFolderInput = findViewById(R.id.cardFolderInput)
        folderName = findViewById(R.id.folderName)
        folderPath = findViewById(R.id.folderPath)
        sourceText = findViewById(R.id.sourceText)
        sourceLabel = findViewById(R.id.sourceLabel)
    }

    private fun setupTabs() {
        tabTextMode.setOnClickListener { switchMode(0) }
        tabFolderMode.setOnClickListener { switchMode(1) }
    }

    private fun setupSourceTabs() {
        tabChatGPT.setOnClickListener { switchSource(0) }
        tabDeepSeek.setOnClickListener { switchSource(1) }
    }

    private val activeColor by lazy { getColor(com.google.android.material.R.color.m3_sys_color_light_on_primary) }
    private val inactiveColor by lazy { getColor(R.color.textTertiary) }

    private fun switchMode(mode: Int) {
        if (currentMode == mode) return
        currentMode = mode
        viewModel.setMode(mode)

        if (mode == 0) {
            activateTab(tabTextMode)
            deactivateTab(tabFolderMode)
            cardTextInput.visibility = View.VISIBLE
            cardFolderInput.visibility = View.GONE
        } else {
            activateTab(tabFolderMode)
            deactivateTab(tabTextMode)
            cardTextInput.visibility = View.GONE
            cardFolderInput.visibility = View.VISIBLE
        }
    }

    private fun switchSource(source: Int) {
        if (currentSource == source) return
        currentSource = source
        viewModel.setSource(source)

        val sourceName = if (source == 0) "ChatGPT" else "DeepSeek"

        if (source == 0) {
            activateTab(tabChatGPT)
            deactivateTab(tabDeepSeek)
        } else {
            activateTab(tabDeepSeek)
            deactivateTab(tabChatGPT)
        }

        sourceText.text = sourceName
        sourceLabel.text = "来源: $sourceName"
    }

    private fun activateTab(tab: TextView) {
        tab.setTextColor(activeColor)
        tab.background = getDrawable(R.drawable.bg_tab_active)
    }

    private fun deactivateTab(tab: TextView) {
        tab.setTextColor(inactiveColor)
        tab.background = getDrawable(R.drawable.bg_tab_inactive)
    }

    private fun setupListeners() {
        btnGenerate.setOnClickListener {
            if (currentMode == 0) {
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    viewModel.generateFromText(text)
                }
            } else {
                selectedFolder?.let { folder ->
                    viewModel.generateFromFolder(folder)
                } ?: Toast.makeText(this, "请先选择文件夹", Toast.LENGTH_SHORT).show()
            }
        }

        btnCopy.setOnClickListener {
            ClipboardUtils.copy(this, output.text.toString())
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        btnSelectFolder.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, REQUEST_FOLDER_PICK)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FOLDER_PICK && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                val path = getPathFromUri(uri)
                if (path != null) {
                    val file = File(path)
                    selectedFolder = file
                    viewModel.setFolder(file)
                } else {
                    val displayName = uri.lastPathSegment ?: "Unknown"
                    folderName.text = displayName
                    folderPath.text = uri.toString()
                    folderPath.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun getPathFromUri(uri: android.net.Uri): String? {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        if (parts.size >= 2) {
            val type = parts[0]
            val id = parts[1]
            return when {
                type.equals("primary", ignoreCase = true) ->
                    Environment.getExternalStorageDirectory().absolutePath + "/" + id
                type.equals("home", ignoreCase = true) ->
                    Environment.getExternalStorageDirectory().absolutePath + "/" + id
                else -> null
            }
        }
        return null
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                output.text = if (state.treeText.isEmpty()) "等待生成…" else state.treeText

                if (state.folderName.isNotEmpty()) {
                    folderName.text = state.folderName
                    folderPath.text = state.folderPath
                    folderPath.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        private const val REQUEST_FOLDER_PICK = 1001
    }
}