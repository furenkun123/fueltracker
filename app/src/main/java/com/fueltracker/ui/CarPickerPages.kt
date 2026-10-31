package com.fueltracker.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CarDatabase
import com.fueltracker.data.Trim
import com.fueltracker.ui.miui.MiuiBackButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PickerScreen"

private suspend fun ensureDbLoaded(db: CarDatabase) {
    check(db.ensureCatalogLoaded() != null) { "车型目录加载失败" }
}

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

@Composable
fun YearSelectorScreen(brand: String, series: String, onBack: () -> Unit, onSelected: (String) -> Unit, onManualInput: (String) -> Unit = onSelected) {
    val appContext = LocalContext.current.applicationContext
    val db = remember(appContext) { CarDatabase.get(appContext) }
    PickerListScreen(
        title = "选择年款", subtitle = "$brand · $series", manualTitle = "手动输入年款",
        manualPlaceholder = "未找到年款？如：2024款", searchPlaceholder = "搜索年款，或直接输入...",
        presetLabel = "年款", emptyHint = "暂无预置年款，可手动输入", loadKey = brand to series,
        load = { withContext(Dispatchers.IO) { ensureDbLoaded(db); db.years(brand, series) } },
        itemKey = { it }, itemSearchText = { it },
        row = { year, onClick -> PickerRow(text = year, onClick = onClick) },
        onBack = onBack, onSelected = onSelected, onManualInput = onManualInput
        // 👆 删除了 showSearch = false，现在它也会拥有搜索框和联动逻辑
    )
}

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

    // 👇 核心联动逻辑：如果有搜索框，点击键盘搜索键且没结果时，直接触发手动输入
    val onSearchAction = {
        if (filtered.isEmpty() && search.isNotBlank()) {
            onManualInput(search.trim())
        }
    }

    PickerScaffold(
        title, subtitle, onBack, search, { search = it },
        searchPlaceholder, loading, loadError, showSearch, onSearchAction
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {

            // 👇 如果【没有】搜索框（比如选年款），保留原本的手动输入卡片
            if (!showSearch) {
                item(key = "manual_header") {
                    ManualInputCard(manualTitle, manualPlaceholder, loadKey, onManualInput)
                }
            }

            if (filtered.isNotEmpty()) {
                item(key = "preset_header") { PresetHeader("预置$presetLabel") }
                itemsIndexed(filtered, key = { index, item -> "$index:${itemKey(item)}" }) { _, item ->
                    row(item) { onSelected(item) }
                }
            } else {
                if (loadError == null) {
                    if (search.isNotBlank()) {
                        // 👇 如果【有】搜索框，且搜索为空，出现“使用xxx”的按钮
                        item(key = "manual_fallback") {
                            EmptyPickerState(search, "未找到与 \"$search\" 相关的$presetLabel") {
                                onManualInput(search.trim())
                            }
                        }
                    } else if (emptyHint != null) {
                        item(key = "empty_hint") { NoMatchText(emptyHint) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerScaffold(
    title: String, subtitle: String, onBack: () -> Unit, search: String,
    onSearchChange: (String) -> Unit, placeholder: String, loading: Boolean,
    errorMessage: String?, showSearch: Boolean, onSearchAction: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // 标题与返回键齐平
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuiBackButton(onClick = onBack)

            Spacer(Modifier.width(12.dp))

            val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
            val titleColor = if (isLight) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)
            val subTitleColor = if (isLight) Color(0xFF1F1F1F).copy(alpha = 0.6f) else Color(0xFFE6E6E6).copy(alpha = 0.6f)

            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = subTitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showSearch) {
            PickerSearchBox(search, onSearchChange, placeholder, onSearchAction)
        }

        if (errorMessage != null && !loading) {
            Text(errorMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                content()
            }
        }
    }
}

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
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val inputBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)

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
                modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp)).background(inputBg).padding(horizontal = 10.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { submit() }, enabled = trimmedValue.isNotEmpty(), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(38.dp)) {
                Text("确定", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PickerSearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String, onSearchAction: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)

    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(42.dp).clip(RoundedCornerShape(21.dp)).background(bg).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            PickerTextField(
                value = value, onValueChange = onValueChange, placeholder = placeholder,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearchAction()
                    keyboard?.hide()
                }),
                modifier = Modifier.weight(1f)
            )
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Clear, "清除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun PresetHeader(text: String) = Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

@Composable
private fun NoMatchText(text: String) {
    // 👇 统一改为卡片式的提示
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff, // 如果你没导入这个图标，记得 Alt+Enter
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// 👇 统一的搜索空状态与手动输入联动组件
@Composable
private fun EmptyPickerState(searchText: String, hint: String, onManualInput: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp) // 紧贴搜索框下方
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )

        Text(
            text = hint,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // 按钮样式与品牌页保持一致
        Button(
            onClick = onManualInput,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                text = "使用 \"$searchText\" 作为结果",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

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