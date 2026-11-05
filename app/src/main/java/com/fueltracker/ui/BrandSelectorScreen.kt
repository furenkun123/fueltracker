package com.fueltracker.ui

import android.util.Log
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CarBrand
import com.fueltracker.data.CarDatabase
import com.fueltracker.ui.miui.MiuiBackButton
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.ui.miui.MiuiSearchTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch


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

    // 0：标题，1：搜索框，后续每个字母分组各占一个列表项。
    val letterIndexMap = remember(filteredBrands) {
        val map = mutableMapOf<String, Int>()
        var idx = 2
        filteredBrands.forEach { (letter, _) ->
            map[letter] = idx
            idx += 1
        }
        map
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                        item(key = "search_box") {
                            BrandSearchBox(
                                value = searchText,
                                onValueChange = { searchText = it },
                                onSearchAction = {
                                    // 按下键盘搜索键且没有结果时，直接使用当前输入。
                                    if (filteredBrands.isEmpty() && searchText.isNotBlank()) {
                                        onManualInput(searchText.trim())
                                    } else {
                                        keyboard?.hide()
                                    }
                                }
                            )
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
                                item(key = "letter_$letter") {
                                    MiuiCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 5.dp),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
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

                                        brands.forEachIndexed { index, brand ->
                                            BrandRow(
                                                brand = brand,
                                                isSelected = brand.brand == initialBrand,
                                                onClick = { onSelected(brand.brand) }
                                            )
                                            if (index != brands.lastIndex) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                                    thickness = 0.8.dp,
                                                    modifier = Modifier.padding(start = 20.dp)
                                                )
                                            }
                                        }
                                    }
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
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(42.dp),
        shape = RoundedCornerShape(21.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                MiuiSearchTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = "搜索汽车品牌...",
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearchAction()
                        }
                    ),
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
}

@Composable
private fun EmptyBrandState(
    searchText: String,
    onManualInput: (String) -> Unit
) {
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
                text = if (searchText.isBlank()) "输入关键字搜索品牌" else "未找到与 \"$searchText\" 相关的品牌",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (searchText.isNotBlank()) {
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
}

@Composable
private fun BrandRow(brand: CarBrand, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Text(
            text = brand.brand,
            fontSize = 16.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
