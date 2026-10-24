package com.fueltracker.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CarBrand
import com.fueltracker.data.CarDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 提取 DB 校验逻辑
private suspend fun ensureDbLoaded(db: CarDatabase) {
    check(db.ensureCatalogLoaded() != null) { "车型目录加载失败" }
}

/**
 * 字母分组排序键：A-Z 按字母顺序排，'#' 放最后。
 */
private fun letterSortKey(letter: String): Int {
    return when (letter) {
        "#" -> 26
        else -> letter.firstOrNull()?.uppercaseChar()?.let { it - 'A' }?.coerceIn(0, 25) ?: 26
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val letterIndexMap = remember(filteredBrands) {
        val map = mutableMapOf<String, Int>()
        var idx = 1 // 1个手动输入 header
        filteredBrands.forEach { (letter, brands) ->
            map[letter] = idx
            idx += 1 + brands.size
        }
        map
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("选择品牌", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f)) {
                    // 搜索框
                    BrandSearchBox(value = searchText, onValueChange = { searchText = it })

                    if (filteredBrands.isEmpty()) {
                        EmptyBrandState(searchText, onManualInput) { keyboard?.hide() }
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            item(key = "manual_header") {
                                BrandInputCard(
                                    onConfirm = { onManualInput(it); keyboard?.hide() }
                                )
                            }

                            filteredBrands.forEach { (letter, brands) ->
                                stickyHeader(key = letter) {
                                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 6.dp)) {
                                        Text(letter, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                items(brands, key = { "${it.letter}_${it.brand}" }) { brand ->
                                    BrandRow(
                                        brand = brand, isSelected = brand.brand == initialBrand,
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
                            scope.launch { listState.scrollToItem(targetIndex) }
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
private fun BrandSearchBox(value: String, onValueChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(42.dp)
            .clip(RoundedCornerShape(21.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            CommonTextField(
                value = value, onValueChange = onValueChange, placeholder = "搜索汽车品牌...",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
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

@Composable
private fun BrandInputCard(onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { if (input.isNotBlank()) { onConfirm(input.trim()); keyboard?.hide() } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text("手动输入品牌", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CommonTextField(
                value = input, onValueChange = { input = it }, placeholder = "未找到品牌？在此直接输入",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = submit, enabled = input.isNotBlank(), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(38.dp)) {
                Text("确定", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BrandRow(brand: CarBrand, isSelected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = brand.brand, fontSize = 16.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp, modifier = Modifier.padding(start = 20.dp))
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

@Composable
private fun EmptyBrandState(searchText: String, onManualInput: (String) -> Unit, onHideKeyboard: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(32.dp))
            Text("未找到相关品牌", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (searchText.isNotBlank()) {
                Button(onClick = { onManualInput(searchText.trim()); onHideKeyboard() }, shape = RoundedCornerShape(20.dp)) {
                    Text("使用手动输入 \"$searchText\"")
                }
            }
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