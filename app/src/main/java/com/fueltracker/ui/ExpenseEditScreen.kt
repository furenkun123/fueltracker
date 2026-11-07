package com.fueltracker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fueltracker.data.DEFAULT_EXPENSE_CATEGORIES
import com.fueltracker.data.ExpenseCategoryStore
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.ui.miuix.MiuixConfirmDialog
import com.fueltracker.ui.miuix.MiuixDatePickerDialog
import com.fueltracker.ui.miuix.MiuixBackButton
import com.fueltracker.ui.miuix.MiuixButton
import com.fueltracker.ui.miuix.MiuixCard
import com.fueltracker.ui.miuix.MiuixChip
import com.fueltracker.ui.miuix.MiuixFormCard
import com.fueltracker.ui.miuix.MiuixTextField
import com.fueltracker.util.toDateTimeText
import com.fueltracker.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun ExpenseEditScreen(
    viewModel: MainViewModel,
    record: ExpenseRecord? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val isEditMode = record != null
    val scope = rememberCoroutineScope()

    val customCategoriesState by ExpenseCategoryStore.customCategories.collectAsState()
    if (customCategoriesState == null) {
        // 显示加载状态
        CircularProgressIndicator()
        return
    }
    val customCategories = customCategoriesState!!

    val allCategories = remember(customCategories) { DEFAULT_EXPENSE_CATEGORIES + customCategories }

    var category by remember(record) { mutableStateOf(record?.category ?: DEFAULT_EXPENSE_CATEGORIES.first()) }
    var amountText by remember(record) { mutableStateOf(record?.amount?.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "") }
    var note by remember(record) { mutableStateOf(record?.note ?: "") }
    var timestamp by remember(record) { mutableLongStateOf(record?.timestamp?.takeIf { it > 0 } ?: System.currentTimeMillis()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val trimmedNewName = newCategoryName.trim()
    val isDuplicate = trimmedNewName.isNotEmpty() && trimmedNewName in allCategories
    val amount = amountText.toDoubleOrNull()
    val canSave = amount != null && amount > 0 && !isSaving

    Scaffold(containerColor = MiuixTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixBackButton(onClick = onBack)

                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isEditMode) "编辑费用记录" else "记费用",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 分类
            MiuixFormCard {
                Text("分类", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allCategories.forEach { cat ->
                        CategoryChip(
                            text = cat, selected = category == cat, isCustom = cat in customCategories,
                            onClick = { category = cat },
                            onLongClick = if (cat in customCategories) { { editingCategory = cat } } else null
                        )
                    }
                    CategoryChip("＋ 新建分类", selected = false, isAction = true, onClick = {
                        newCategoryName = ""; showAddCategoryDialog = true
                    })
                }
                if (customCategories.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("提示：长按自定义分类可编辑或删除", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            }

            // 金额
            MiuixFormCard {
                MiuixTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "金额",
                    isRequired = true,
                    suffix = "元",
                    keyboardType = KeyboardType.Decimal
                )
            }

            // 时间
            MiuixFormCard {
                Column {
                    Text("时间 *", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                            .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                            .clickable { showDatePicker = true }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(timestamp.toDateTimeText(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
                    }
                }
            }

            // 备注
            MiuixFormCard {
                MiuixTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注（可选）"
                )
            }

            Spacer(Modifier.height(8.dp))
            MiuixButton(
                text = if (isEditMode) "保存修改" else "保存",
                enabled = canSave,
                onClick = {
                    isSaving = true
                    val newRecord = amount?.let { record?.copy(timestamp = timestamp, category = category, amount = it, note = note.trim()) }
                        ?: amount?.let { ExpenseRecord(vehicleId = 0, timestamp = timestamp, category = category, amount = it, note = note.trim()) }

                    val onResult: (Result<Unit>) -> Unit = { result ->
                        isSaving = false
                        if (result.isSuccess) onSaved() else Toast.makeText(context, "保存失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                    if (isEditMode) newRecord?.let { viewModel.updateExpense(it, onResult) } else newRecord?.let { viewModel.addExpense(it, onResult) }
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }


    if (showDatePicker) {
        MiuixDatePickerDialog(
            initialTimestamp = timestamp,
            title = "选择时间",
            onSelected = { timestamp = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }

    // 新建分类弹窗
    if (showAddCategoryDialog) {
        MiuixInputDialog(
            title = "新建分类", value = newCategoryName, onValueChange = { newCategoryName = it },
            placeholder = if (isDuplicate) "该分类已存在" else "如：过路费、洗车、装饰",
            confirmText = "创建", confirmEnabled = trimmedNewName.isNotEmpty() && !isDuplicate,
            onConfirm = {
                if (trimmedNewName.isNotEmpty()) {
                    scope.launch {
                        ExpenseCategoryStore.add(trimmedNewName)
                        category = trimmedNewName
                    }
                }
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }

    // 编辑分类弹窗
    editingCategory?.let { cat ->
        EditCategoryDialog(
            currentName = cat, existingNames = allCategories,
            onSave = { newName ->
                viewModel.renameExpenseCategory(cat, newName) { result ->
                    result.onSuccess {
                        if (category == cat) category = newName.trim()
                        editingCategory = null
                    }.onFailure { error ->
                        Toast.makeText(context, error.message ?: "重命名失败", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = {
                scope.launch {
                    ExpenseCategoryStore.remove(cat)
                    // customCategories 会在 StateFlow 中自动更新，不需要手动赋值
                    if (category == cat) category = DEFAULT_EXPENSE_CATEGORIES.first()
                    editingCategory = null
                }
            },
            onDismiss = { editingCategory = null }
        )
    }
}

@Composable
private fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    isCustom: Boolean = false,
    isAction: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val bgColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        selected -> MiuixTheme.colorScheme.onPrimary
        isAction -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = if (isCustom) onLongClick else null
            )
    ) {
        MiuixChip(
            text = text,
            selected = selected,
            backgroundColor = bgColor,
            contentColor = textColor,
            fontWeight = if (selected || isAction) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            }
        )
    }
}

@Composable
private fun EditCategoryDialog(
    currentName: String,
    existingNames: List<String>,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val trimmed = name.trim()
    val isDuplicate = trimmed.isNotEmpty() && trimmed != currentName && trimmed in existingNames
    val isValid = trimmed.isNotEmpty() && trimmed != currentName && !isDuplicate

    if (showDeleteConfirm) {
        MiuixConfirmDialog(
            title = "删除分类",
            message = "确定删除分类「$currentName」吗？\n\n已有记录不会丢失，仍保留原分类标签。",
            confirmText = "删除",
            onConfirm = { onDelete() },
            onDismiss = { showDeleteConfirm = false }
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            MiuixCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                cornerRadius = 20.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        "编辑分类", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))

                    MiuixTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "分类名"
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 删除按钮
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                                .background(MiuixTheme.colorScheme.errorContainer)
                                .clickable { showDeleteConfirm = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("删除", fontSize = 14.sp, color = MiuixTheme.colorScheme.onErrorContainer)
                        }

                        // 取消按钮
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("取消", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
                        }

                        // 保存按钮
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                                .background(if (isValid) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primary.copy(alpha = 0.4f))
                                .clickable(enabled = isValid) { onSave(name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("保存", fontSize = 14.sp, color = MiuixTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixInputDialog(
    title: String, value: String, onValueChange: (String) -> Unit, placeholder: String,
    confirmText: String, confirmEnabled: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MiuixCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            cornerRadius = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    title, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                MiuixTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = placeholder
                )

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 取消
                    Box(
                        modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", color = MiuixTheme.colorScheme.onSurface)
                    }
                    // 确定
                    Box(
                        modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                            .background(if (confirmEnabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primary.copy(alpha = 0.4f))
                            .clickable(enabled = confirmEnabled, onClick = onConfirm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(confirmText, color = MiuixTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
