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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : AppCompatActivity() {

    // 文本模式组件
    private lateinit var textModeLayout: LinearLayout
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 绑定视图
        textModeLayout = findViewById(R.id.textModeLayout)
        inputTree = findViewById(R.id.inputTree)
        savePath = findViewById(R.id.savePath)
        generateZipBtn = findViewById(R.id.generateZipBtn)

        pathModeLayout = findViewById(R.id.pathModeLayout)
        inputPath = findViewById(R.id.inputPath)
        generateTreeBtn = findViewById(R.id.generateTreeBtn)
        treeOutput = findViewById(R.id.treeOutput)
        copyBtn = findViewById(R.id.copyBtn)

        modeGroup = findViewById(R.id.modeGroup)

        // 默认显示文本模式
        textModeLayout.visibility = android.view.View.VISIBLE
        pathModeLayout.visibility = android.view.View.GONE

        // 模式切换
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

        // 文本模式：生成 ZIP
        generateZipBtn.setOnClickListener {
            if (checkPermission()) {
                generateZipFromText()
            } else {
                requestPermission()
            }
        }

        // 文件夹模式：生成文件树
        generateTreeBtn.setOnClickListener {
            if (checkPermission()) {
                generateTreeFromPath()
            } else {
                requestPermission()
            }
        }

        // 复制按钮
        copyBtn.setOnClickListener {
            val text = treeOutput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "请先生成文件树", Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("文件树", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }

        // 自动检查权限
        checkPermissionOnStart()
    }

    private fun checkPermissionOnStart() {
        if (!checkPermission()) {
            requestPermission()
        }
    }

    // ---------- 权限相关 ----------
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
                this,
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (checkPermission()) {
                Toast.makeText(this, "权限已获取", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要所有文件访问权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已获取", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- 文本模式：生成 ZIP ----------
    private fun generateZipFromText() {
        val treeText = inputTree.text.toString().trim()
        if (treeText.isEmpty()) {
            Toast.makeText(this, "请输入文件树", Toast.LENGTH_SHORT).show()
            return
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

                val destFile = File(destDir, "file_tree_${System.currentTimeMillis()}.zip")
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

    // ---------- 文件夹模式：生成文件树 ----------
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

    /** 递归生成文件树字符串 */
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

    /** 解析文本树创建空文件和目录 */
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
            if (name.startsWith("├── ") || name.startsWith("└── ")) {
                name = name.substring(4).trim()
            } else if (name.startsWith("├──") || name.startsWith("└──")) {
                name = name.substring(3).trim()
            }
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

    /** 压缩目录为 ZIP */
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
