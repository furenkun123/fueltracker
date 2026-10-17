package com.fueltracker.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

// ============================================================
// 车系选择
// ============================================================

@Composable
fun SeriesSelectorScreen(
    brand: String,
    onBack: () -> Unit,
    onSelected: (String) -> Unit,
    onManualInput: (String) -> Unit = onSelected
) {
    val appContext = LocalContext.current.applicationContext
    val db = remember(appContext) { CarDatabase.get(appContext) }

    PickerListScreen(
        title = "选择车系",
        subtitle = brand,
        manualTitle = "手动输入车系",
        manualPlaceholder = "未找到车系？在此直接输入",
        searchPlaceholder = "搜索车系...",
        presetLabel = "车系",
        emptyHint = "暂无预置车系，可手动输入",
        loadKey = brand,
        load = {
            withContext(Dispatchers.IO) {
                db.ensureCatalogLoaded()
                db.series(brand)
            }
        },
        itemKey = { it },
        itemSearchText = { it },
        row = { series, onClick ->
            PickerRow(text = series, onClick = onClick)
        },
        onBack = onBack,
        onSelected = onSelected,
        onManualInput = onManualInput
    )
}

// ============================================================
// 年款选择
// ============================================================

@Composable
fun YearSelectorScreen(
    brand: String,
    series: String,
    onBack: () -> Unit,
    onSelected: (String) -> Unit,
    onManualInput: (String) -> Unit = onSelected
) {
    val appContext = LocalContext.current.applicationContext
    val db = remember(appContext) { CarDatabase.get(appContext) }

    PickerListScreen(
        title = "选择年款",
        subtitle = "$brand · $series",
        manualTitle = "手动输入年款",
        manualPlaceholder = "未找到年款？如：2024款",
        searchPlaceholder = "",
        presetLabel = "年款",
        showSearch = false,
        emptyHint = "暂无预置年款，可手动输入",
        loadKey = brand to series,
        load = {
            withContext(Dispatchers.IO) {
                db.ensureCatalogLoaded()
                db.years(brand, series)
            }
        },
        itemKey = { it },
        itemSearchText = { it },
        row = { year, onClick ->
            PickerRow(text = year, onClick = onClick)
        },
        onBack = onBack,
        onSelected = onSelected,
        onManualInput = onManualInput
    )
}

// ============================================================
// 车型选择
// ============================================================

@Composable
fun TrimSelectorScreen(
    brand: String,
    series: String,
    year: String,
    onBack: () -> Unit,
    onSelected: (Trim) -> Unit,
    onManualInput: (String) -> Unit = { name -> onSelected(Trim(name = name)) }
) {
    val appContext = LocalContext.current.applicationContext
    val db = remember(appContext) { CarDatabase.get(appContext) }

    PickerListScreen(
        title = "选择车型",
        subtitle = "$brand · $series · $year",
        manualTitle = "手动输入车型",
        manualPlaceholder = "未找到车型？在此直接输入",
        searchPlaceholder = "搜索车型...",
        presetLabel = "车型",
        emptyHint = "暂无预置车型，可手动输入",
        loadKey = Triple(brand, series, year),
        load = {
            withContext(Dispatchers.IO) {
                db.ensureCatalogLoaded()
                db.trims(brand, series, year)
            }
        },
        itemKey = { "${it.name}|${it.energyType}|${it.fuelGrade}|${it.fuelTank}" },
        itemSearchText = { it.name },
        row = { trim, onClick ->
            TrimRow(trim = trim, onClick = onClick)
        },
        onBack = onBack,
        onSelected = onSelected,
        onManualInput = onManualInput
    )
}

// ============================================================
// 通用选择器
// ============================================================

@Composable
private fun <T> PickerListScreen(
    title: String,
    subtitle: String,
    manualTitle: String,
    manualPlaceholder: String,
    searchPlaceholder: String,
    presetLabel: String,
    loadKey: Any?,
    load: suspend () -> List<T>,
    itemKey: (T) -> Any,
    itemSearchText: (T) -> String,
    row: @Composable (item: T, onClick: () -> Unit) -> Unit,
    onBack: () -> Unit,
    onSelected: (T) -> Unit,
    onManualInput: (String) -> Unit,
    showSearch: Boolean = true,
    emptyHint: String? = null
) {
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf<List<T>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    // 加载数据
    LaunchedEffect(loadKey) {
        loading = true
        loadError = null
        search = ""
        all = emptyList()

        try {
            all = load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load catalog, key=$loadKey", e)
            all = emptyList()
            loadError = "加载失败，请尝试手动输入"
        }

        // 只有成功或普通异常处理完成后才执行。
        // 协程被取消时会直接抛出，不会执行到这里。
        loading = false
    }

    // 搜索过滤
    // 搜索过滤
    val query = search.trim()

    // 直接计算，不用 remember：
    // itemSearchText 是调用点传入的内联 lambda，
    // 每次重组都是新实例，作为 remember key 会导致缓存永不命中。
    val filtered: List<T> = if (query.isEmpty()) {
        all
    } else {
        all.filter { item ->
            itemSearchText(item).contains(
                other = query,
                ignoreCase = true
            )
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
        showSearch = showSearch
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "manual_header") {
                ManualInputCard(
                    title = manualTitle,
                    placeholder = manualPlaceholder,
                    resetKey = loadKey,
                    onConfirm = onManualInput
                )
            }

            when {
                filtered.isNotEmpty() -> {
                    item(key = "preset_header") {
                        PresetHeader(text = "预置$presetLabel")
                    }

                    itemsIndexed(
                        items = filtered,
                        key = { index, item ->
                            "$index:${itemKey(item)}"
                        }
                    ) { _, item ->
                        row(item) {
                            onSelected(item)
                        }
                    }
                }

                loadError == null && search.isNotBlank() -> {
                    item(key = "no_match") {
                        NoMatchText("未找到匹配的$presetLabel")
                    }
                }

                loadError == null && emptyHint != null -> {
                    item(key = "empty_hint") {
                        NoMatchText(emptyHint)
                    }
                }
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
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    placeholder: String,
    loading: Boolean,
    errorMessage: String? = null,
    showSearch: Boolean = true,
    content: @Composable () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSearch) {
                PickerSearchBox(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = placeholder
                )
            }

            if (errorMessage != null && !loading) {
                Text(
                    text = errorMessage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    content()
                }
            }
        }
    }
}

// ============================================================
// 手动输入卡片
// ============================================================

@Composable
private fun ManualInputCard(
    title: String,
    placeholder: String,
    resetKey: Any?,
    onConfirm: (String) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var value by remember(resetKey) { mutableStateOf("") }

    val trimmedValue = value.trim()
    val canConfirm = trimmedValue.isNotEmpty()

    fun submit() {
        if (!canConfirm) return
        onConfirm(trimmedValue)
        value = ""
        keyboard?.hide()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { submit() },
                enabled = canConfirm,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = "确定",
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ============================================================
// 预置列表标题
// ============================================================

@Composable
private fun PresetHeader(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

// ============================================================
// 无匹配 / 空状态提示
// ============================================================

@Composable
private fun NoMatchText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================
// 普通列表行
// ============================================================

@Composable
private fun PickerRow(
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = 0.8.dp,
            modifier = Modifier.padding(start = 20.dp)
        )
    }
}

// ============================================================
// 车型列表行
// ============================================================

private fun String?.displayOrNull(): String? =
    this?.trim()?.takeIf {
        it.isNotEmpty() &&
                it != "-" &&
                it != "--" &&
                !it.equals("N/A", ignoreCase = true) &&
                it != "未知"
    }

@Composable
private fun TrimRow(
    trim: Trim,
    onClick: () -> Unit
) {
    val tankText = trim.fuelTank.displayOrNull()?.let { value ->
        when {
            value.endsWith("L", ignoreCase = true) -> value
            value.endsWith("升") -> value
            else -> "${value}L"
        }
    }

    val energyText = trim.energyType.displayOrNull()
    val gradeText = trim.fuelGrade.displayOrNull()

    val info = buildString {
        if (energyText != null) append(energyText)
        if (gradeText != null) {
            if (isNotEmpty()) append(" · ")
            append(gradeText)
        }
        if (tankText != null) {
            if (isNotEmpty()) append(" · ")
            append(tankText)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = trim.name,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (info.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = info,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = 0.8.dp,
            modifier = Modifier.padding(start = 20.dp)
        )
    }
}

// ============================================================
// 搜索框
// ============================================================

@Composable
private fun PickerSearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(8.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清除搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}