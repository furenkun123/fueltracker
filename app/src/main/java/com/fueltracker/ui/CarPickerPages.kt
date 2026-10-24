package com.fueltracker.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CarDatabase
import com.fueltracker.data.Trim
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PickerScreen"

// 👇 优化 1：抽取数据库加载检查，消除三个页面 load 块的重复
private suspend fun ensureDbLoaded(db: CarDatabase) {
    check(db.ensureCatalogLoaded() != null) { "车型目录加载失败" }
}

// ============================================================
// 车系选择
// ============================================================
@Composable
fun SeriesSelectorScreen(brand: String, onBack: () -> Unit, onSelected: (String) -> Unit, onManualInput: (String) -> Unit = onSelected) {
    val appContext = LocalContext.current.applicationContext
    val db = remember(appContext) { CarDatabase.get(appContext) }
    PickerListScreen(
        title = "选择车系", subtitle = brand, manualTitle = "手动输入车系",
        manualPlaceholder = "未找到车系？在此直接输入", searchPlaceholder = "搜索车系...",
        presetLabel = "车系", emptyHint = "暂无预置车系，可手动输入", loadKey = brand,
        load = { withContext(Dispatchers.IO) { ensureDbLoaded(db); db.series(brand) } },
        itemKey = { it }, itemSearchText = { it },
        row = { series, onClick -> PickerRow(text = series, onClick = onClick) },
        onBack = onBack, onSelected = onSelected, onManualInput = onManualInput
    )
}

// ============================================================
// 年款选择
// ============================================================
@Composable
fun YearSelectorScreen(brand: String, series: String, onBack: () -> Unit, onSelected: (String) -> Unit, onManualInput: (String) -> Unit = onSelected) {
    val appContext = LocalContext.current.applicationContext
    val db = remember(appContext) { CarDatabase.get(appContext) }
    PickerListScreen(
        title = "选择年款", subtitle = "$brand · $series", manualTitle = "手动输入年款",
        manualPlaceholder = "未找到年款？如：2024款", searchPlaceholder = "", showSearch = false,
        presetLabel = "年款", emptyHint = "暂无预置年款，可手动输入", loadKey = brand to series,
        load = { withContext(Dispatchers.IO) { ensureDbLoaded(db); db.years(brand, series) } },
        itemKey = { it }, itemSearchText = { it },
        row = { year, onClick -> PickerRow(text = year, onClick = onClick) },
        onBack = onBack, onSelected = onSelected, onManualInput = onManualInput
    )
}

// ============================================================
// 车型选择
// ============================================================
@Composable
fun TrimSelectorScreen(brand: String, series: String, year: String, onBack: () -> Unit, onSelected: (Trim) -> Unit, onManualInput: (String) -> Unit = { onSelected(Trim(name = it)) }) {
    val appContext = LocalContext.current.applicationContext
    val db = remember(appContext) { CarDatabase.get(appContext) }
    PickerListScreen(
        title = "选择车型", subtitle = "$brand · $series · $year", manualTitle = "手动输入车型",
        manualPlaceholder = "未找到车型？在此直接输入", searchPlaceholder = "搜索车型...",
        presetLabel = "车型", emptyHint = "暂无预置车型，可手动输入", loadKey = Triple(brand, series, year),
        load = { withContext(Dispatchers.IO) { ensureDbLoaded(db); db.trims(brand, series, year) } },
        itemKey = { "${it.name}|${it.energyType}|${it.fuelGrade}|${it.fuelTank}" }, itemSearchText = { it.name },
        row = { trim, onClick -> TrimRow(trim = trim, onClick = onClick) },
        onBack = onBack, onSelected = onSelected, onManualInput = onManualInput
    )
}

// ============================================================
// 通用选择器
// ============================================================
@Composable
private fun <T> PickerListScreen(
    title: String, subtitle: String, manualTitle: String, manualPlaceholder: String,
    searchPlaceholder: String, presetLabel: String, loadKey: Any?, load: suspend () -> List<T>,
    itemKey: (T) -> Any, itemSearchText: (T) -> String,
    row: @Composable (item: T, onClick: () -> Unit) -> Unit,
    onBack: () -> Unit, onSelected: (T) -> Unit, onManualInput: (String) -> Unit,
    showSearch: Boolean = true, emptyHint: String? = null
) {
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf<List<T>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(loadKey) {
        loading = true; loadError = null; search = ""; all = emptyList()
        try {
            all = load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load catalog, key=$loadKey", e)
            all = emptyList()
            loadError = e.message ?: "加载失败，请尝试手动输入"
        }
        loading = false
    }

    val filtered = remember(all, search) {
        val query = search.trim()
        if (query.isEmpty()) all else all.filter { itemSearchText(it).contains(query, ignoreCase = true) }
    }

    PickerScaffold(title, subtitle, onBack, search, { search = it }, searchPlaceholder, loading, loadError, showSearch) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "manual_header") { ManualInputCard(manualTitle, manualPlaceholder, loadKey, onManualInput) }

            if (filtered.isNotEmpty()) {
                item(key = "preset_header") { PresetHeader("预置$presetLabel") }
                itemsIndexed(filtered, key = { index, item -> "$index:${itemKey(item)}" }) { _, item ->
                    row(item) { onSelected(item) }
                }
            } else if (loadError == null && search.isNotBlank()) {
                item(key = "no_match") { NoMatchText("未找到匹配的$presetLabel") }
            } else if (loadError == null && emptyHint != null) {
                item(key = "empty_hint") { NoMatchText(emptyHint) }
            }
        }
    }
}

// ============================================================
// 通用页面框架
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScaffold(
    title: String, subtitle: String, onBack: () -> Unit, search: String,
    onSearchChange: (String) -> Unit, placeholder: String, loading: Boolean,
    errorMessage: String?, showSearch: Boolean, content: @Composable () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showSearch) PickerSearchBox(search, onSearchChange, placeholder)

            if (errorMessage != null && !loading) {
                Text(errorMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else content()
            }
        }
    }
}

// ============================================================
// 核心抽离组件（移除重复的输入框和列表行）
// ============================================================

/** 👇 优化 2：统一处理 BasicTextField 的 placeholder 逻辑 */
@Composable
private fun PickerTextField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    BasicTextField(
        value = value, onValueChange = onValueChange,
        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), singleLine = true,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, modifier = modifier,
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                innerTextField()
            }
        }
    )
}

/** 👇 优化 3：统一处理列表行的背景和底部 Divider */
@Composable
private fun PickerItemWrapper(onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) { content() }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp, modifier = Modifier.padding(start = 20.dp))
    }
}

@Composable
private fun ManualInputCard(title: String, placeholder: String, resetKey: Any?, onConfirm: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var value by remember(resetKey) { mutableStateOf("") }
    val trimmedValue = value.trim()

    fun submit() {
        if (trimmedValue.isEmpty()) return
        onConfirm(trimmedValue); value = ""; keyboard?.hide()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PickerTextField(
                value = value, onValueChange = { value = it }, placeholder = placeholder,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { submit() }, enabled = trimmedValue.isNotEmpty(), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(38.dp)) {
                Text("确定", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PickerSearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val keyboard = LocalSoftwareKeyboardController.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            PickerTextField(
                value = value, onValueChange = onValueChange, placeholder = placeholder,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f)
            )
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, "清除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun PresetHeader(text: String) = Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

@Composable
private fun NoMatchText(text: String) = Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun PickerRow(text: String, onClick: () -> Unit) {
    PickerItemWrapper(onClick) { Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface) }
}

private fun String?.displayOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "-" && it != "--" && !it.equals("N/A", true) && it != "未知" }

@Composable
private fun TrimRow(trim: Trim, onClick: () -> Unit) {
    val tankText = trim.fuelTank.displayOrNull()?.let { if (it.endsWith("L", true) || it.endsWith("升")) it else "${it}L" }
    val info = listOfNotNull(trim.energyType.displayOrNull(), trim.fuelGrade.displayOrNull(), tankText).joinToString(" · ")

    PickerItemWrapper(onClick) {
        Column {
            Text(trim.name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (info.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(info, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}