# DocTree / File Tree Studio — Code Wiki

> 项目类型：Android 原生应用（Kotlin）  
> 应用名称：File Tree Studio  
> 包名：`com.example.doctree`  
> 版本：`2.0`（versionCode 2）  
> 最小 SDK：26（Android 8.0）｜目标 SDK：35（Android 15）

---

## 1. 项目概述

**DocTree**（产品名 **File Tree Studio**）是一款 Android 工具类应用，用于将用户输入的目录结构文本转换为格式化的文件树字符串，并提供一键复制功能。应用采用单页面 + `ViewModel` 的 MVVM 风格架构，核心逻辑集中在 `core` 包中，UI 层通过 Kotlin Flow 观察状态变化。

主要能力：

- 解析用户输入的缩进/树形符号文本，提取目录与文件名称。
- 将解析后的路径结构渲染为 ASCII 文件树（`├──` / `└──` / `│`）。
- 将生成的文件树文本复制到系统剪贴板。
- （已预留）将本地文件夹压缩为 ZIP 文件的能力，当前未在 UI 中直接调用。

---

## 2. 项目架构

### 2.1 架构风格

- **MVVM（Model-View-ViewModel）**：`MainActivity` 作为 View，`MainViewModel` 承载 UI 状态与业务调度，`core` 包中的对象负责具体计算。
- **响应式 UI**：`MainActivity` 通过 `lifecycleScope` 收集 `MainViewModel.state`（`StateFlow<UiState>`），状态变更自动刷新界面。
- **协程异步**：核心生成逻辑在 `viewModelScope.launch` 中执行，避免阻塞主线程。

### 2.2 包结构

```
com.example.doctree
├── MainActivity.kt          # 入口 Activity，负责视图绑定与事件监听
├── core
│   ├── FileTreeGenerator.kt # 文件树字符串生成器
│   ├── TreeParser.kt        # 输入文本解析器
│   └── ZipCreator.kt        # ZIP 压缩工具（当前未被 Activity 调用）
├── ui
│   ├── MainViewModel.kt     # 页面级 ViewModel
│   └── UiState.kt           # 页面状态数据类
└── utils
    └── ClipboardUtils.kt    # 剪贴板复制工具
```

### 2.3 模块职责

| 包/模块 | 职责 |
| --- | --- |
| `MainActivity` | 初始化 ViewModel、绑定布局、监听按钮点击、订阅 UI 状态。 |
| `core` | 承载纯业务逻辑：文本解析、文件树生成、ZIP 创建。 |
| `ui` | 定义 UI 状态模型与状态持有者（ViewModel）。 |
| `utils` | 封装 Android 系统能力（剪贴板）。 |
| `res` | 布局、颜色、字符串、主题、矢量图形等 UI 资源。 |

---

## 3. 关键类与函数说明

### 3.1 `MainActivity`（`app/src/main/java/com/example/doctree/MainActivity.kt`）

**职责**：应用主界面，承载输入框、结果展示框、生成按钮与复制按钮。

**核心成员**：

- `private lateinit var viewModel: MainViewModel` — 页面级 ViewModel。

**生命周期与交互**：

- `onCreate(savedInstanceState: Bundle?)`
  - 通过 `ViewModelProvider(this)` 获取 `MainViewModel` 实例。
  - 调用 `setContentView(R.layout.activity_main)`。
  - 绑定控件：`editTree`（输入框）、`textResult`（结果文本）、`btnGenerate`（生成）、`btnCopy`（复制）。
  - 启动 `lifecycleScope.launch` 收集 `viewModel.state`，将 `treeText` 显示到 `textResult`；若为空则显示 `"等待生成..."`。
  - 点击生成按钮：读取输入框文本，非空时调用 `viewModel.updateText(text)`。
  - 点击复制按钮：调用 `ClipboardUtils.copy(this, output.text.toString())` 将当前结果复制到剪贴板。

> 注意：当前 Activity 实际调用的是 `viewModel.updateText(...)`，该方法仅把输入原样写入状态，并未触发 `FileTreeGenerator` 或 `TreeParser`。真正的文件树生成入口 `MainViewModel.generate(folder: File)` 目前未被调用。

---

### 3.2 `MainViewModel`（`app/src/main/java/com/example/doctree/ui/MainViewModel.kt`）

**职责**：持有页面状态，并协调核心逻辑层。

**核心成员**：

- `private val _state = MutableStateFlow(UiState())` — 可变状态流。
- `val state: StateFlow<UiState> = _state` — 对外暴露的只读状态流。

**关键函数**：

- `generate(folder: File)`
  - 在 `viewModelScope.launch` 中异步执行。
  - 设置 `loading = true`。
  - 调用 `FileTreeGenerator.generate(folder)` 生成文件树字符串。
  - 更新 `_state` 为 `treeText = result, message = "生成完成", loading = false`。
  - **当前未被 UI 调用**。

- `updateText(text: String)`
  - 将输入文本直接写入 `_state.value.treeText`。
  - **当前被 `MainActivity` 的生成按钮调用**。

- `clear()`
  - 重置状态为默认 `UiState()`。
  - **当前未被 UI 调用**。

---

### 3.3 `UiState`（`app/src/main/java/com/example/doctree/ui/UiState.kt`）

页面状态数据类：

```kotlin
data class UiState(
    val loading: Boolean = false,
    val treeText: String = "",
    val message: String = "",
    val mode: Int = 0
)
```

| 字段 | 说明 |
| --- | --- |
| `loading` | 是否正在生成（当前未在 UI 中反馈）。 |
| `treeText` | 生成/输入的文本结果。 |
| `message` | 操作提示信息（如 `"生成完成"`）。 |
| `mode` | 预留字段，当前未使用。 |

---

### 3.4 `TreeParser`（`app/src/main/java/com/example/doctree/core/TreeParser.kt`）

**职责**：清理用户输入的树形结构文本，去除缩进和 ASCII 树形符号，提取有效路径行。

**函数**：

- `normalize(text: String): List<String>`
  - 按行处理输入文本。
  - 过滤空行。
  - 去除首尾空白，并移除 `├──`、`└──`、`│` 等符号。
  - 返回非空行的列表。

> 当前 `MainActivity` / `MainViewModel` 未调用此函数。

---

### 3.5 `FileTreeGenerator`（`app/src/main/java/com/example/doctree/core/FileTreeGenerator.kt`）

**职责**：给定本地文件夹，递归生成格式化的 ASCII 文件树字符串。

**函数**：

- `generate(root: File): String`
  - 公开入口，初始化 `StringBuilder` 并调用私有递归函数 `build`。

- `private fun build(file: File, prefix: String, builder: StringBuilder)`
  - 递归遍历文件系统。
  - 输出格式：`prefix + file.name + （目录则追加 `/`） + "\n"`。
  - 对目录内容按名称排序。
  - 使用 `│   ` 表示非最后一项的后续层级，`    ` 表示最后一项的后续层级。

> 当前 `MainActivity` 未调用此函数；它面向本地文件系统，而非用户输入文本。

---

### 3.6 `ZipCreator`（`app/src/main/java/com/example/doctree/core/ZipCreator.kt`）

**职责**：将本地文件夹递归压缩为 ZIP 文件。

**函数**：

- `create(source: File, target: File)`
  - 创建 `ZipOutputStream` 并写入目标文件。

- `private fun zipFolder(file: File, path: String, zip: ZipOutputStream)`
  - 递归处理目录与子文件。
  - 对文件创建 `ZipEntry(path)` 并写入内容。

> 当前未在 UI 或 ViewModel 中使用。

---

### 3.7 `ClipboardUtils`（`app/src/main/java/com/example/doctree/utils/ClipboardUtils.kt`）

**职责**：将文本写入系统剪贴板。

**函数**：

- `copy(context: Context, text: String)`
  - 获取 `ClipboardManager` 服务。
  - 使用 `ClipData.newPlainText("tree", text)` 设置主剪贴板。

---

## 4. 数据流与交互

### 4.1 当前实际数据流

```
用户输入 -> editTree
            ↓
      点击 btnGenerate
            ↓
   MainActivity 读取文本
            ↓
   viewModel.updateText(text)
            ↓
   _state.value.treeText 更新
            ↓
   lifecycleScope 收集 state
            ↓
   textResult.text 刷新
            ↓
   用户点击 btnCopy -> ClipboardUtils.copy()
```

### 4.2 设计意图数据流（未完全接通）

```
用户输入目录结构文本
            ↓
   TreeParser.normalize(text)
            ↓
   解析为路径列表 / 构建 File 对象
            ↓
   FileTreeGenerator.generate(folder)
            ↓
   MainViewModel.generate(folder)
            ↓
   UiState.treeText 更新
            ↓
   UI 展示文件树
```

### 4.3 UI 布局

- 文件：`app/src/main/res/layout/activity_main.xml`
- 根布局：`LinearLayout`（垂直）
- 主要组件：
  - 标题 `File Tree Studio`
  - 副标题 `智能生成项目结构`
  - `MaterialCardView` 包裹输入区（含 `EditText`：`editTree`）
  - `MaterialButton`：`btnGenerate`（生成结构）
  - `MaterialCardView` 包裹结果区（含 `ScrollView` + `TextView`：`textResult`，等宽字体）
  - `MaterialButton`：`btnCopy`（复制结果）

---

## 5. 依赖关系

### 5.1 Gradle 与插件

| 项目 | 版本 |
| --- | --- |
| Android Gradle Plugin | `8.2.2` |
| Kotlin | `1.9.22` |
| Gradle Wrapper | `8.5` |
| Java / JVM Target | `17` |
| compileSdk | `35` |
| minSdk | `26` |
| targetSdk | `35` |

### 5.2 三方库依赖（`app/build.gradle.kts`）

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| `androidx.core:core-ktx` | `1.12.0` | Kotlin 扩展与核心工具 |
| `androidx.appcompat:appcompat` | `1.6.1` | 兼容库与 `AppCompatActivity` |
| `com.google.android.material:material` | `1.11.0` | Material3 组件（Button、CardView 等） |
| `androidx.constraintlayout:constraintlayout` | `2.1.4` | 约束布局（当前布局未使用） |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | `2.7.0` | ViewModel 支持 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `2.7.0` | LifecycleScope 与 Flow 扩展 |
| `androidx.activity:activity-ktx` | `1.8.2` | Activity KTX 扩展 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.7.3` | Android 协程 |

### 5.3 模块依赖图

```
MainActivity
    ├── MainViewModel (ui)
    │   ├── UiState (ui)
    │   └── FileTreeGenerator (core) [设计意图，当前未实际调用]
    ├── ClipboardUtils (utils)
    └── Android SDK / AndroidX / Material

TreeParser (core)        [独立工具，当前未被调用]
ZipCreator (core)        [独立工具，当前未被调用]
```

---

## 6. 项目运行方式

### 6.1 环境要求

- JDK 17
- Android SDK 35（compileSdk）
- Android 设备或模拟器（最低 Android 8.0，API 26）

### 6.2 本地构建

```bash
# 进入项目根目录
cd /workspace

# 授予 gradlew 执行权限（Linux/macOS）
chmod +x gradlew

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

构建产物路径：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

### 6.3 安装到设备

```bash
# 连接 Android 设备或启动模拟器后
./gradlew installDebug
```

### 6.4 通过 Android Studio 运行

1. 打开项目根目录 `/workspace`。
2. 等待 Gradle 同步完成。
3. 选择目标设备，点击 **Run**（`app` 模块）。

---

## 7. CI/CD

### 7.1 GitHub Actions 工作流

- 文件：`.github/workflows/build.yml`
- 名称：`Build Android APK`
- 触发条件：
  - `push` 到 `main` 分支
  - 针对 `main` 分支的 `pull_request`
- 执行步骤：
  1. Checkout 代码
  2. 设置 JDK 17（Temurin）
  3. 设置 Gradle
  4. 授予 `gradlew` 执行权限
  5. 执行 `./gradlew assembleDebug`
  6. 上传产物 `app-debug.apk` 作为 artifact（名为 `app-debug`）

---

## 8. 资源文件说明

### 8.1 颜色资源（`res/values/colors.xml`）

| 名称 | 色值 | 用途 |
| --- | --- | --- |
| `primary` | `#6750A4` | 主按钮背景 |
| `secondary` | `#625B71` | 复制按钮背景 |
| `background` | `#F8F7FC` | 页面背景 |
| `cardBackground` | `#FFFFFF` | 卡片背景 |
| `editBackground` | `#F1EFF5` | 输入框背景 |
| `border` | `#DDD8E7` | 输入框描边 |
| `textPrimary` | `#1C1B1F` | 主文本 |
| `textSecondary` | `#66636B` | 次要文本 |

### 8.2 主题（`res/values/themes.xml`）

- 父主题：`Theme.Material3.DayNight.NoActionBar`
- 状态栏/导航栏颜色：`@color/background`
- 状态栏图标为深色模式（`windowLightStatusBar = true`）

### 8.3 形状 Drawable

| 文件 | 说明 |
| --- | --- |
| `bg_edit.xml` | 圆角输入框背景（14dp，描边 1dp） |
| `bg_button.xml` | 圆角按钮背景（18dp，主色填充） |
| `bg_card.xml` | 圆角卡片背景（20dp，白色填充） |

---

## 9. 功能详细分析

### 9.1 启动与页面初始化

1. **应用入口**：`AndroidManifest.xml` 将 `MainActivity` 声明为 `MAIN` / `LAUNCHER`，启动后直接进入单页面。
2. **ViewModel 创建**：`MainActivity.onCreate` 中通过 `ViewModelProvider(this)[MainViewModel::class.java]` 获取实例，遵循 Activity 作用域，配置变更时状态保留。
3. **布局加载**：`setContentView(R.layout.activity_main)`，页面采用垂直 `LinearLayout`，无 ActionBar。
4. **控件绑定**：
   - `editTree`：多行输入框，`inputType="textMultiLine"`，最大可见高度 150dp，顶部对齐。
   - `textResult`：等宽字体 `TextView`，嵌套在 `ScrollView` 中，用于展示结果。
   - `btnGenerate` / `btnCopy`：圆角 Material 按钮。
5. **状态订阅**：`lifecycleScope.launch { viewModel.state.collect { ... } }` 在 Activity 生命周期内持续收集 `StateFlow`。只要 `UiState.treeText` 变化，结果区立即刷新；为空时回退显示 `"等待生成..."`。

### 9.2 “生成结构”按钮的实际行为

**当前实现**：

```kotlin
btnGenerate.setOnClickListener {
    val text = input.text.toString()
    if (text.isNotBlank()) {
        viewModel.updateText(text)
    }
}
```

- 仅校验输入是否非空（`isNotBlank()` 会过滤仅包含空格的输入）。
- 调用的是 `MainViewModel.updateText(text)`，**没有把输入当作目录结构去解析**。
- 因此结果区显示的是用户输入的原文，而非格式化后的文件树。

**与 UI 文案的落差**：输入框 hint 示例为 `app/\n ├── src/\n └── build.gradle`，暗示应用会解析并重新生成树；实际只是回显。

### 9.3 “复制结果”按钮的行为

```kotlin
btnCopy.setOnClickListener {
    ClipboardUtils.copy(this, output.text.toString())
}
```

- 复制当前结果区文本，包括默认提示 `"等待生成..."`。
- 使用系统 `ClipboardManager`，剪贴板标签为 `"tree"`。
- 无 Toast 或 Snackbar 反馈，用户无法直接感知复制是否成功。

### 9.4 `MainViewModel` 状态机

`UiState` 初始值：

```kotlin
UiState(loading = false, treeText = "", message = "", mode = 0)
```

**状态转换表**：

| 触发条件 | 调用函数 | 状态变化 | 结果区表现 |
| --- | --- | --- | --- |
| 点击生成按钮 | `updateText(text)` | `treeText = text` | 显示原文 |
| 点击复制按钮 | 不经过 ViewModel | 无 | 显示不变 |
| 调用 `generate(folder)` | `generate(folder)` | 先 `loading=true`，再 `treeText=result, message="生成完成"` | 显示生成的文件树 |
| 调用 `clear()` | `clear()` | 重置为初始状态 | 显示 `"等待生成..."` |

> `loading` 与 `message` 当前仅在 `generate()` 路径中被更新，但 UI 没有消费这两个字段（无进度条、无 Toast）。

### 9.5 `TreeParser.normalize` 详细行为

**输入处理流程**：

1. `text.lines()` 按 `\n`、`\r\n` 或 `\r` 拆分为行。
2. `filter { it.isNotBlank() }` 过滤掉空行或仅空白行。
3. 对每一行执行：
   - `it.trim()` 去除首尾空白。
   - 移除子串 `"├──"`、`"└──"`、`"│"`。
   - 再次 `trim()`。
4. 若结果非空，加入返回列表。

**示例**：

| 输入行 | 输出 |
| --- | --- |
| `app/` | `app/` |
| `├── src/` | `src/` |
| `└── build.gradle` | `build.gradle` |
| `│   MainActivity.kt` | `MainActivity.kt` |
| `   ` | （过滤） |

**能力边界**：

- 只能做“清洗”，无法恢复层级关系；它不知道某一行属于第几级目录。
- 如果目录名本身包含 `│`、`├──`、`└──` 字符，会被误删。
- 不会处理缩进空格/制表符与层级之间的映射。

### 9.6 `FileTreeGenerator.generate` 详细行为

**算法**：深度优先递归（DFS），按名称字母顺序遍历。

**输出规则**：

- 每个节点占一行：`prefix + name + （目录则 "/"） + "\n"`。
- 子节点遍历前按 `it.name` 排序。
- 前缀规则：
  - 若当前节点是父目录最后一个子节点，后续行前缀追加 4 个空格。
  - 否则后续行前缀追加 `│   `（竖线 + 3 个空格）。

**示例输出**（对某目录结构）：

```
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/
    │   └── res/
    └── test/
        └── java/
```

**边界情况**：

- 空目录：只输出目录名 + `/`。
- 文件：`listFiles()` 返回 `null`，不追加子节点。
- 无读取权限的目录：`listFiles()` 可能返回 `null`，生成结果可能不完整。
- 循环引用/符号链接：未做防循环处理，可能死循环或栈溢出。

### 9.7 `ZipCreator.create` 详细行为

**算法**：递归遍历源文件夹，为每个文件创建 `ZipEntry`。

**路径规则**：`path` 参数初始为 `source.name`，递归时拼接为 `$path/${it.name}`。

**示例**：源目录 `Documents` 下文件 `a.txt` 在 ZIP 中的路径为 `Documents/a.txt`。

**边界情况**：

- 空目录：不会为该目录本身创建 `ZipEntry`，仅递归其子项；若目录为空，生成的 ZIP 可能不包含该目录条目。
- 大文件：`copyTo` 一次性拷贝，未分块或显示进度。
- 无写入权限：抛出 `FileNotFoundException` 或 `SecurityException`，外层未捕获。
- 同名目标文件：直接覆盖。

### 9.8 当前功能完整度评估

| 功能点 | 是否可用 | 完成度 | 说明 |
| --- | --- | --- | --- |
| 文本输入与展示 | 是 | 100% | 输入框、结果区、滚动均正常。 |
| “生成结构”按钮 | 是 | 30% | 仅做非空校验与原文回显，未实现文件树生成。 |
| “复制结果”按钮 | 是 | 70% | 可复制文本，但缺少成功反馈。 |
| 输入文本解析 | 代码存在 | 40% | `TreeParser.normalize` 已就绪，但未接入流程。 |
| 本地文件树生成 | 代码存在 | 80% | `FileTreeGenerator` 功能完整，但未接入 UI。 |
| ZIP 压缩 | 代码存在 | 20% | `ZipCreator` 已就绪，但无任何入口。 |
| 加载状态反馈 | 否 | 0% | `loading` 字段未在 UI 中体现。 |
| 错误处理 | 弱 | 10% | 无权限、空输入、IO 异常等场景均未处理。 |

---

## 10. 已知问题与注意事项

1. **生成逻辑未真正调用**  
   当前 `btnGenerate` 仅把用户输入原样回显到结果区，没有调用 `TreeParser` 或 `FileTreeGenerator`。若需实现真正的文件树生成，需要修改 `MainActivity` 或 `MainViewModel`，将输入文本解析并转换为文件系统结构，或调整 `FileTreeGenerator` 以接收文本输入。

2. **`MainViewModel.generate(folder: File)` 未使用**  
   该函数面向本地 `File` 对象，适合“选择本地目录后生成文件树”的场景，但当前 UI 没有文件选择器入口。

3. **`ZipCreator` 未接入 UI**  
   ZIP 压缩能力已封装完成，但没有任何按钮或流程触发。

4. **`mode` 字段未使用**  
   `UiState.mode` 为预留字段，当前无业务含义。

5. **输入提示与真实行为不一致**  
   输入框 hint 示例为目录结构文本，但当前逻辑并不会将其解析为树形结构。

6. **复制按钮缺少反馈**  
   复制后无 Toast/Snackbar，用户难以确认操作结果。

7. **异常与权限处理缺失**  
   文件 IO、存储权限、剪贴板操作均未加 try-catch 或权限申请。

---

## 11. 扩展建议

- 在 `MainActivity` 中接入 `TreeParser`，将用户输入的目录结构文本解析为树形节点，再调用新的渲染逻辑生成 ASCII 树。
- 若保持“本地文件夹生成树”的路线，可接入 Storage Access Framework（SAF）让用户选择目录，然后调用 `MainViewModel.generate(folder)`。
- 可将 `ZipCreator` 与文件选择器结合，提供“选择文件夹并导出 ZIP”的功能。
- 对 `UiState.loading` 添加进度条或按钮禁用状态，提升交互反馈。

---

*文档生成时间：2026-07-19*  
*基于仓库当前代码快照编写。*
