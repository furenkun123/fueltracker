package com.fueltracker.ui

import android.R.attr.subtitle
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
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
import com.fueltracker.ui.miuix.MiuixBackButton
import com.fueltracker.ui.miuix.MiuixSearchTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// ✅ 引入 Miuix 主题
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "PickerScreen"

@Composable
fun SeriesSelectorScreen(brand: String, onBack: () -> Unit, onSelected: (String) -> Unit, onManualInput: (String) -> Unit = onSelected) {
    val appContext = LocalContext.current.applicationContext
    val db = remember(appContext) { CarDatabase.get(appContext) }
    PickerListScreen(
        title = "选择车系",
        manualTitle = "手动输入车系",
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
        title = "选择年款",
        manualTitle = "手动输入年款",
        manualPlaceholder = "未找到年款？如：2024款", searchPlaceholder = "搜索年款，或直接输入...",
        presetLabel = "年款", emptyHint = "暂无预置年款，可手动输入", loadKey = brand to series,
        load = { withContext(Dispatchers.IO) { ensureDbLoaded(db); db.years(brand, series) } },
        itemKey = { it }, itemSearchText = { it },
        row = { year, onClick -> PickerRow(text = year, onClick = onClick) },
        onBack = onBack, onSelected = onSelected, onManualInput = onManualInput
    )
}

@Composable
fun TrimSelectorScreen(brand: String, series: String, year: String, onBack: () -> Unit, onSelected: (Trim) -> Unit, onManualInput: (String) -> Unit = { onSelected(Trim(name = it)) }) {
    val appContext = LocalContext.current.applicationContext
    val db = remember(appContext) { CarDatabase.get(appContext) }
    PickerListScreen(
        title = "选择车型",
        manualTitle = "手动输入车型",
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
    title: String,
    manualTitle: String, manualPlaceholder: String,
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
    val listState = rememberLazyListState()

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

    val onSearchAction = {
        if (filtered.isEmpty() && search.isNotBlank()) {
            onManualInput(search.trim())
        }
    }

    PickerScaffold(
        loading = loading,
        errorMessage = loadError
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            // 标题作为列表首项，和首页标题一样随列表自然滚出。
            item(key = "picker_title") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiuixBackButton(onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                }
            }

            if (showSearch) {
                stickyHeader(key = "picker_search") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(lightenPickerBackground(MiuixTheme.colorScheme.background))
                    ) {
                        PickerSearchBox(search, { search = it }, searchPlaceholder, onSearchAction)
                    }
                }
            }

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
    loading: Boolean,
    errorMessage: String?,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightenPickerBackground(MiuixTheme.colorScheme.background))
            .statusBarsPadding()
            .imePadding()
    ) {
        if (errorMessage != null && !loading) {
            Text(
                errorMessage,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (loading) {
                // ✅ 替换为 Miuix 风格的加载圈
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    top.yukonga.miuix.kmp.basic.CircularProgressIndicator()
                }
            } else {
                content()
            }
        }
    }
}

@Composable
private fun PickerItemWrapper(onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(lightenPickerBackground(MiuixTheme.colorScheme.background))) {
        Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) { content() }
        // ✅ 替换 M3 的 HorizontalDivider
        Box(Modifier.fillMaxWidth().padding(start = 20.dp).height(0.8.dp).background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
    }
}

@Composable
private fun ManualInputCard(title: String, placeholder: String, resetKey: Any?, onConfirm: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var value by remember(resetKey) { mutableStateOf("") }
    val trimmedValue = value.trim()
    Text(text = title, color = MiuixTheme.colorScheme.onSurface)

    fun submit() {
        if (trimmedValue.isEmpty()) return
        onConfirm(trimmedValue); value = ""; keyboard?.hide()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(MiuixTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MiuixSearchTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = placeholder,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp),
                placeholderEllipsis = true
            )
            Spacer(Modifier.width(8.dp))
            // ✅ 替换 M3 的 Button
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (trimmedValue.isNotEmpty()) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primary.copy(alpha = 0.4f))
                    .clickable(enabled = trimmedValue.isNotEmpty()) { submit() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("确定", fontSize = 13.sp, color = MiuixTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun PickerSearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String, onSearchAction: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val bg = darkenPickerSearchColor(MiuixTheme.colorScheme.surfaceVariant)
    val borderColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(bg)
            .border(0.6.dp, borderColor, RoundedCornerShape(21.dp))
            .padding(horizontal = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            MiuixSearchTextField(
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
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Clear, "清除", tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PresetHeader(text: String) = Text(text, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

@Composable
private fun NoMatchText(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyPickerState(searchText: String, hint: String, onManualInput: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )

        Text(
            text = hint,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center
        )

        // ✅ 替换 M3 的 Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.primary)
                .clickable(onClick = onManualInput)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "使用 \"$searchText\" 作为结果",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun PickerRow(text: String, onClick: () -> Unit) {
    PickerItemWrapper(onClick) { Text(text, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface) }
}

private fun String?.displayOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "-" && it != "--" && !it.equals("N/A", true) && it != "未知" }

@Composable
private fun TrimRow(trim: Trim, onClick: () -> Unit) {
    val tankText = trim.fuelTank.displayOrNull()?.let { if (it.endsWith("L", true) || it.endsWith("升")) it else "${it}L" }
    val info = listOfNotNull(trim.energyType.displayOrNull(), trim.fuelGrade.displayOrNull(), tankText).joinToString(" · ")

    PickerItemWrapper(onClick) {
        Column {
            Text(trim.name, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (info.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(info, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Slightly lift the picker pages above the search field without changing the search field material. */
private fun lightenPickerBackground(color: Color): Color {
    val amount = 0.07f
    return Color(
        red = color.red + (1f - color.red) * amount,
        green = color.green + (1f - color.green) * amount,
        blue = color.blue + (1f - color.blue) * amount,
        alpha = color.alpha
    )
}

/** Darken the search field slightly so it remains distinct from the page background. */
private fun darkenPickerSearchColor(color: Color): Color {
    val factor = 0.92f
    return Color(
        red = color.red * factor,
        green = color.green * factor,
        blue = color.blue * factor,
        alpha = color.alpha
    )
}
