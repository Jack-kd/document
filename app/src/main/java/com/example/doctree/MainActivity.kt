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

    private lateinit var inputTree: EditText
    private lateinit var generateBtn: Button
    private lateinit var modeGroup: RadioGroup
    private lateinit var pathLayout: LinearLayout
    private lateinit var inputPath: EditText

    private val REQUEST_MANAGE_STORAGE = 1001
    private val REQUEST_WRITE_STORAGE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputTree = findViewById(R.id.inputTree)
        generateBtn = findViewById(R.id.generateBtn)
        modeGroup = findViewById(R.id.modeGroup)
        pathLayout = findViewById(R.id.pathLayout)
        inputPath = findViewById(R.id.inputPath)

        // 初始状态：文本模式
        setupTextMode()

        // 模式切换监听
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.modeText -> {
                    pathLayout.visibility = android.view.View.GONE
                    setupTextMode()
                }
                R.id.modePath -> {
                    pathLayout.visibility = android.view.View.VISIBLE
                    setupPathMode()
                }
            }
        }
    }

    // 设置为文本模式：按钮文字和功能
    private fun setupTextMode() {
        generateBtn.text = "生成 ZIP 并保存到内部存储"
        generateBtn.setOnClickListener {
            if (checkPermission()) {
                generateFromText()
            } else {
                requestPermission()
            }
        }
    }

    // 设置为文件夹模式：按钮文字和功能
    private fun setupPathMode() {
        generateBtn.text = "复制"
        generateBtn.setOnClickListener {
            if (checkPermission()) {
                generateTreeAndCopy()
            } else {
                requestPermission()
            }
        }
    }

    // ---------- 权限处理 ----------
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
                Toast.makeText(this, "权限已获取，请重新操作", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "权限已获取，请重新操作", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- 文本模式：生成 ZIP ----------
    private fun generateFromText() {
        val treeText = inputTree.text.toString().trim()
        if (treeText.isEmpty()) {
            Toast.makeText(this, "请输入文件树", Toast.LENGTH_SHORT).show()
            return
        }
        generateBtn.isEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tmpRoot = File(cacheDir, "tree_gen_${System.currentTimeMillis()}")
                tmpRoot.mkdirs()
                parseAndCreate(treeText, tmpRoot)

                val zipFile = File(cacheDir, "output_${System.currentTimeMillis()}.zip")
                zipDirectory(tmpRoot, zipFile)

                val publicDir = Environment.getExternalStorageDirectory()
                val destFile = File(publicDir, "file_tree_${System.currentTimeMillis()}.zip")
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
                withContext(Dispatchers.Main) { generateBtn.isEnabled = true }
            }
        }
    }

    // ---------- 文件夹模式：生成文件树并复制 ----------
    private fun generateTreeAndCopy() {
        val path = inputPath.text.toString().trim()
        if (path.isEmpty()) {
            Toast.makeText(this, "请输入文件夹路径", Toast.LENGTH_SHORT).show()
            return
        }
        generateBtn.isEnabled = false
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
                    // 填入输入框便于查看
                    inputTree.setText(tree)
                    // 复制到剪贴板
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("文件树", tree)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@MainActivity, "已复制文件树到剪贴板", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { generateBtn.isEnabled = true }
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

    /** 解析文本树，生成目录和空文件 */
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
                } else {
                    break
                }
            }
            var name = line.substring(i).trim()
            if (name.startsWith("├── ") || name.startsWith("└── ")) {
                name = name.substring(4).trim()
            } else if (name.startsWith("├──") || name.startsWith("└──")) {
                name = name.substring(3).trim()
            }
            if (name.isEmpty()) continue

            while (stack.size > depth + 1) {
                stack.removeAt(stack.lastIndex)
            }
            while (stack.size <= depth) {
                stack.add(stack.last())
            }
            val parentDir = stack.last()

            if (name.endsWith("/")) {
                val dirName = name.removeSuffix("/")
                val newDir = File(parentDir, dirName)
                newDir.mkdirs()
                if (stack.size > depth + 1) {
                    stack[depth + 1] = newDir
                } else {
                    stack.add(newDir)
                }
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
                if (file.isFile) {
                    FileInputStream(file).copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
