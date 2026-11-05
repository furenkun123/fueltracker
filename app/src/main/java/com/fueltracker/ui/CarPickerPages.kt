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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CarDatabase
import com.fueltracker.data.Trim
import com.fueltracker.ui.miui.MiuiBackButton
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.ui.miui.MiuiSearchTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PickerScreen"

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
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        search = search,
        onSearchChange = { search = it },
        placeholder = searchPlaceholder,
        loading = loading,
        errorMessage = loadError,
        showSearch = showSearch,
        onSearchAction = onSearchAction,
        content = {
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
    )
}

@Composable
private fun PickerScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    placeholder: String,
    loading: Boolean,
    errorMessage: String?,
    showSearch: Boolean,
    onSearchAction: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "picker_title") {
                PickerTitleRow(title = title, subtitle = subtitle, onBack = onBack)
            }

            if (showSearch) {
                item(key = "picker_search") {
                    PickerSearchBox(search, onSearchChange, placeholder, onSearchAction)
                }
            }

            if (errorMessage != null && !loading) {
                item(key = "picker_error") {
                    Text(
                        errorMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }

            if (loading) {
                item(key = "picker_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                content()
            }
        }
    }
}

@Composable
private fun PickerTitleRow(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuiBackButton(onClick = onBack)
        Spacer(Modifier.width(12.dp))

        val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
        val titleColor = if (isLight) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)
        val subtitleColor =
            if (isLight) Color(0xFF1F1F1F).copy(alpha = 0.64f)
            else Color(0xFFE6E6E6).copy(alpha = 0.64f)

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
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}



@Composable
private fun PickerItemWrapper(onClick: () -> Unit, content: @Composable () -> Unit) {
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            content()
        }
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

    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiuiSearchTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = placeholder,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(inputBg)
                        .padding(horizontal = 10.dp),
                    placeholderEllipsis = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { submit() },
                    enabled = trimmedValue.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text("确定", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PickerSearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String, onSearchAction: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(42.dp),
        shape = RoundedCornerShape(21.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                MiuiSearchTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = placeholder,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearchAction()
                        keyboard?.hide()
                    }),
                    modifier = Modifier.weight(1f),
                    placeholderEllipsis = true
                )
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Clear, "清除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

@Composable
private fun PresetHeader(text: String) = Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

@Composable
private fun NoMatchText(text: String) {
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp),
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
                text = text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 👇 统一的搜索空状态与手动输入联动组件
@Composable
private fun EmptyPickerState(searchText: String, hint: String, onManualInput: () -> Unit) {
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp),
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
