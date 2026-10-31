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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CarBrand
import com.fueltracker.data.CarDatabase
import com.fueltracker.ui.miui.MiuiBackButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private suspend fun ensureDbLoaded(db: CarDatabase) {
    check(db.ensureCatalogLoaded() != null) { "车型目录加载失败" }
}

private fun letterSortKey(letter: String): Int {
    return when (letter) {
        "#" -> 26
        else -> letter.firstOrNull()?.uppercaseChar()?.let { it - 'A' }?.coerceIn(0, 25) ?: 26
    }
}

@Composable
fun BrandSelectorScreen(
    initialBrand: String,
    onBack: () -> Unit,
    onSelected: (String) -> Unit,
    onManualInput: (String) -> Unit
) {
    val appContext = LocalContext.current.applicationContext
    val carDatabase = remember(appContext) { CarDatabase.get(appContext) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var allBrands by remember { mutableStateOf<List<CarBrand>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            ensureDbLoaded(carDatabase)
            allBrands = carDatabase.brandsByLetter().flatMap { (letter, brands) ->
                brands.map { brand -> CarBrand(brand, letter) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("BrandSelectorScreen", "加载汽车品牌失败", e)
            allBrands = emptyList()
        } finally {
            isLoading = false
        }
    }

    val filteredBrands = remember(searchText, allBrands) {
        val list = if (searchText.isBlank()) allBrands
        else allBrands.filter { it.brand.contains(searchText, true) || it.letter.contains(searchText, true) }
        list.groupBy { it.letter.uppercase() }.toSortedMap(compareBy(::letterSortKey))
    }

    val letters = remember(filteredBrands) { filteredBrands.keys.toList() }

    // 👇 更新索引映射：0.标题 1.搜索框（去掉了手动输入的占位）
    val letterIndexMap = remember(filteredBrands) {
        val map = mutableMapOf<String, Int>()
        var idx = 2
        filteredBrands.forEach { (letter, brands) ->
            map[letter] = idx
            idx += 1 + brands.size
        }
        map
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {

                        // 1. 标题
                        item(key = "top_bar") {
                            val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
                            val titleColor = if (isLight) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MiuiBackButton(onClick = onBack)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "选择品牌",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor
                                )
                            }
                        }

                        // 2. 搜索框
                        stickyHeader(key = "search_box") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                BrandSearchBox(
                                    value = searchText,
                                    onValueChange = { searchText = it },
                                    onSearchAction = {
                                        // 👇 如果按了键盘搜索键，且没有结果，直接触发手动输入
                                        if (filteredBrands.isEmpty() && searchText.isNotBlank()) {
                                            onManualInput(searchText.trim())
                                        } else {
                                            keyboard?.hide()
                                        }
                                    }
                                )
                            }
                        }

                        // 3. 内容区
                        if (filteredBrands.isEmpty()) {
                            item {
                                // 👇 搜索空状态与手动输入联动
                                EmptyBrandState(
                                    searchText = searchText,
                                    onManualInput = {
                                        onManualInput(it)
                                        keyboard?.hide()
                                    }
                                )
                            }
                        } else {
                            filteredBrands.forEach { (letter, brands) ->
                                // 👇 字母头
                                stickyHeader(key = letter) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.background)
                                            // 👈 强制左边缘为 20dp，上下留出合适的空隙
                                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
                                    ) {
                                        Text(
                                            text = letter,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                        thickness = 0.8.dp,
                                        modifier = Modifier.padding(start = 20.dp)
                                    )
                                }

                                items(brands, key = { "${it.letter}_${it.brand}" }) { brand ->
                                    BrandRow(
                                        brand = brand,
                                        isSelected = brand.brand == initialBrand,
                                        onClick = { onSelected(brand.brand) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 右侧索引栏
                if (letters.isNotEmpty()) {
                    AlphabetIndexBar(letters) { letter ->
                        letterIndexMap[letter]?.let { targetIndex ->
                            scope.launch { listState.animateScrollToItem(targetIndex) }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 抽离出的组件
// ============================================================

@Composable
private fun BrandSearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSearchAction: () -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val searchBoxBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(searchBoxBg)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            CommonTextField(
                value = value, onValueChange = onValueChange, placeholder = "搜索汽车品牌...",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                modifier = Modifier.weight(1f)
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Clear, "清除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// 👇 合并后的智能空状态组件
@Composable
private fun EmptyBrandState(
    searchText: String,
    onManualInput: (String) -> Unit
) {
    // 👇 1. 用 Surface 或 background 的 Column 代替 Box，把它做成一个紧凑的卡片
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp) // 控制外边距，紧贴搜索框
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface) // 用卡片色托底
            .padding(vertical = 32.dp, horizontal = 16.dp), // 内部上下留白
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 👇 2. 加一个搜索无结果的图标，提升优雅感 (如果报错，请 Alt+Enter 导入)
        Icon(
            imageVector = Icons.Default.SearchOff, // 或者用 Icons.Outlined.SearchOff
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )

        // 👇 3. 文案优化
        Text(
            text = if (searchText.isBlank()) "输入关键字搜索品牌" else "未找到与 \"$searchText\" 相关的品牌",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (searchText.isNotBlank()) {
            // 👇 4. 按钮更紧凑
            Button(
                onClick = { onManualInput(searchText.trim()) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "使用 \"$searchText\" 作为品牌",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BrandRow(brand: CarBrand, isSelected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                // 👈 左边缘严格锁定为 20dp
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Text(
                text = brand.brand,
                fontSize = 16.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        // 👇 修复：使用 onSurface 加透明度，保证在任何背景色下都能清晰看见分割线
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            thickness = 0.8.dp,
            // 起点严格锁定在 20dp
            modifier = Modifier.padding(start = 20.dp)
        )
    }
}

@Composable
private fun AlphabetIndexBar(letters: List<String>, onLetterClick: (String) -> Unit) {
    Column(
        modifier = Modifier.width(28.dp).fillMaxHeight().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        letters.forEach { letter ->
            Text(
                text = letter, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).clickable { onLetterClick(letter) }.padding(vertical = 2.dp, horizontal = 4.dp)
            )
        }
    }
}

/** 通用的带 placeholder 的输入框 */
@Composable
private fun CommonTextField(
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
                if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                innerTextField()
            }
        }
    )
}