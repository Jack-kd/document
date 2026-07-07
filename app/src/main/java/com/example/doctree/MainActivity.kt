package com.example.doctree

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : AppCompatActivity() {

    // 文本模式组件
    private lateinit var textModeLayout: LinearLayout
    private lateinit var sourceGroup: RadioGroup
    private lateinit var inputTree: EditText
    private lateinit var savePath: EditText
    private lateinit var generateZipBtn: Button

    // 文件夹模式组件
    private lateinit var pathModeLayout: LinearLayout
    private lateinit var inputPath: EditText
    private lateinit var generateTreeBtn: Button
    private lateinit var treeOutput: TextView
    private lateinit var copyBtn: Button

    private lateinit var modeGroup: RadioGroup

    private val REQUEST_MANAGE_STORAGE = 1001
    private val REQUEST_WRITE_STORAGE = 1002

    private var isDeepSeekSource = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textModeLayout = findViewById(R.id.textModeLayout)
        sourceGroup = findViewById(R.id.sourceGroup)
        inputTree = findViewById(R.id.inputTree)
        savePath = findViewById(R.id.savePath)
        generateZipBtn = findViewById(R.id.generateZipBtn)

        pathModeLayout = findViewById(R.id.pathModeLayout)
        inputPath = findViewById(R.id.inputPath)
        generateTreeBtn = findViewById(R.id.generateTreeBtn)
        treeOutput = findViewById(R.id.treeOutput)
        copyBtn = findViewById(R.id.copyBtn)

        modeGroup = findViewById(R.id.modeGroup)

        textModeLayout.visibility = android.view.View.VISIBLE
        pathModeLayout.visibility = android.view.View.GONE

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.modeText -> {
                    textModeLayout.visibility = android.view.View.VISIBLE
                    pathModeLayout.visibility = android.view.View.GONE
                }
                R.id.modePath -> {
                    textModeLayout.visibility = android.view.View.GONE
                    pathModeLayout.visibility = android.view.View.VISIBLE
                }
            }
        }

        sourceGroup.setOnCheckedChangeListener { _, checkedId ->
            isDeepSeekSource = checkedId == R.id.sourceDeepSeek
        }

        generateZipBtn.setOnClickListener {
            if (checkPermission()) generateZipFromText() else requestPermission()
        }

        generateTreeBtn.setOnClickListener {
            if (checkPermission()) generateTreeFromPath() else requestPermission()
        }

        copyBtn.setOnClickListener {
            val text = treeOutput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "请先生成文件树", Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("文件树", text))
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }

        checkPermissionOnStart()
    }

    private fun checkPermissionOnStart() {
        if (!checkPermission()) requestPermission()
    }

    // ---------- 权限 ----------
    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_STORAGE
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            Toast.makeText(
                this,
                if (checkPermission()) "权限已获取" else "需要所有文件访问权限",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE) {
            Toast.makeText(
                this,
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) "权限已获取" else "需要存储权限",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------- 文本模式生成 ZIP ----------
    private fun generateZipFromText() {
        var treeText = inputTree.text.toString().trim()
        if (treeText.isEmpty()) {
            Toast.makeText(this, "请输入文件树", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isDeepSeekSource) {
            treeText = preprocessChatGPTTree(treeText)
        }

        val destDirPath = savePath.text.toString().trim()
        if (destDirPath.isEmpty()) {
            Toast.makeText(this, "请输入保存路径", Toast.LENGTH_SHORT).show()
            return
        }

        generateZipBtn.isEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val destDir = File(destDirPath)
                if (!destDir.exists()) destDir.mkdirs()

                val tmpRoot = File(cacheDir, "tree_gen_${System.currentTimeMillis()}")
                tmpRoot.mkdirs()
                parseAndCreate(treeText, tmpRoot)

                val zipFile = File(cacheDir, "output_${System.currentTimeMillis()}.zip")
                zipDirectory(tmpRoot, zipFile)

                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val destFile = File(destDir, "${sdf.format(Date())}.zip")
                zipFile.copyTo(destFile, overwrite = true)

                tmpRoot.deleteRecursively()
                zipFile.delete()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "ZIP 已保存至: ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { generateZipBtn.isEnabled = true }
            }
        }
    }

    /**
     * 预处理 ChatGPT 风格的文件树文本，清除多余符号并重建为标准格式
     */
    private fun preprocessChatGPTTree(text: String): String {
        val lines = text.lines().filter { it.isNotBlank() }
        val items = mutableListOf<Pair<Int, String>>() // (深度, 名称)

        for (line in lines) {
            var depth = 0
            var i = 0
            while (i < line.length) {
                val sub = line.substring(i)
                if (sub.startsWith("│   ") || sub.startsWith("    ")) {
                    depth++
                    i += 4
                } else break
            }

            var remaining = line.substring(i).trimStart()
            // 反复移除前导的树符号，直到只剩下名称
            var changed = true
            while (changed) {
                changed = false
                for (sym in listOf("├── ", "└── ", "├──", "└──", "│── ", "│──")) {
                    if (remaining.startsWith(sym)) {
                        remaining = remaining.substring(sym.length).trimStart()
                        changed = true
                        break
                    }
                }
            }

            // 移除残留的 │─ 等字符
            remaining = remaining.trimStart('│', '─', ' ')
            if (remaining.isEmpty()) continue

            items.add(Pair(depth, remaining))
        }

        // 为有子项的条目自动添加 "/"
        for (j in 0 until items.size - 1) {
            val (curDepth, curName) = items[j]
            val (nextDepth, _) = items[j + 1]
            if (nextDepth > curDepth && !curName.endsWith("/")) {
                items[j] = Pair(curDepth, curName + "/")
            }
        }

        // 重建标准格式的文本（DeepSeek 兼容）
        val sb = StringBuilder()
        val prefixStack = mutableListOf<String>() // 每个深度对应的前缀字符串

        for (i in items.indices) {
            val (depth, name) = items[i]

            while (prefixStack.size > depth) prefixStack.removeAt(prefixStack.lastIndex)
            while (prefixStack.size < depth) prefixStack.add("│   ")

            val isLast = if (i == items.size - 1) true else {
                val nextDepth = items[i + 1].first
                nextDepth <= depth
            }

            val linePrefix = prefixStack.joinToString("")
            val connector = if (isLast) "└── " else "├── "
            sb.appendLine(linePrefix + connector + name)

            if (isLast && depth > 0) {
                prefixStack[depth - 1] = "    "
            }
        }

        return sb.toString().trimEnd()
    }

    // ---------- 文件夹模式 ----------
    private fun generateTreeFromPath() {
        val path = inputPath.text.toString().trim()
        if (path.isEmpty()) {
            Toast.makeText(this, "请输入文件夹路径", Toast.LENGTH_SHORT).show()
            return
        }
        generateTreeBtn.isEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sourceDir = File(path)
                if (!sourceDir.exists() || !sourceDir.isDirectory) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "路径不存在或不是文件夹", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val tree = buildFileTree(sourceDir)
                withContext(Dispatchers.Main) {
                    treeOutput.text = tree
                    Toast.makeText(this@MainActivity, "生成完毕，可点击复制", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { generateTreeBtn.isEnabled = true }
            }
        }
    }

    private fun buildFileTree(dir: File, prefix: String = ""): String {
        val sb = StringBuilder()
        val children = dir.listFiles()?.sortedBy { it.name } ?: return ""
        val dirs = children.filter { it.isDirectory }
        val files = children.filter { it.isFile }
        val allItems = dirs + files
        for (i in allItems.indices) {
            val item = allItems[i]
            val isLast = i == allItems.size - 1
            val connector = if (isLast) "└── " else "├── "
            val nextPrefix = if (isLast) "    " else "│   "
            if (item.isDirectory) {
                sb.appendLine(prefix + connector + item.name + "/")
                sb.append(buildFileTree(item, prefix + nextPrefix))
            } else {
                sb.appendLine(prefix + connector + item.name)
            }
        }
        return sb.toString()
    }

    // ---------- 解析工具 ----------
    private fun parseAndCreate(text: String, rootDir: File) {
        val lines = text.lines().filter { it.isNotBlank() }
        val stack = mutableListOf(rootDir)
        for (line in lines) {
            var depth = 0
            var i = 0
            while (i < line.length) {
                val sub = line.substring(i)
                if (sub.startsWith("│   ") || sub.startsWith("    ")) {
                    depth++
                    i += 4
                } else break
            }
            var name = line.substring(i).trim()
            if (name.startsWith("├── ") || name.startsWith("└── ")) name = name.substring(4).trim()
            else if (name.startsWith("├──") || name.startsWith("└──")) name = name.substring(3).trim()
            if (name.isEmpty()) continue

            while (stack.size > depth + 1) stack.removeAt(stack.lastIndex)
            while (stack.size <= depth) stack.add(stack.last())
            val parentDir = stack.last()

            if (name.endsWith("/")) {
                val dirName = name.removeSuffix("/")
                val newDir = File(parentDir, dirName)
                newDir.mkdirs()
                if (stack.size > depth + 1) stack[depth + 1] = newDir
                else stack.add(newDir)
            } else {
                val file = File(parentDir, name)
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
        }
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                val entryName = file.relativeTo(sourceDir).path.let {
                    if (file.isDirectory && !it.endsWith("/")) "$it/" else it
                }
                if (entryName.isEmpty()) return@forEach
                zos.putNextEntry(ZipEntry(entryName))
                if (file.isFile) FileInputStream(file).copyTo(zos)
                zos.closeEntry()
            }
        }
    }
}
