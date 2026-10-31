package com.fueltracker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import com.fueltracker.ui.components.MiuiConfirmDialog
import com.fueltracker.ui.components.MiuiDatePickerDialog
import com.fueltracker.ui.miui.MiuiBackButton
import com.fueltracker.ui.miui.MiuiButton
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.util.toDateTimeText
import com.fueltracker.viewmodel.MainViewModel

@Composable
fun ExpenseEditScreen(
    viewModel: MainViewModel,
    record: ExpenseRecord? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val isEditMode = record != null

    var customCategories by remember { mutableStateOf(ExpenseCategoryStore.getCustom()) }
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
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
                MiuiBackButton(onClick = onBack)

                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isEditMode) "编辑费用记录" else "记费用",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 分类
            ExpenseFormCard {
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
                    Text("提示：长按自定义分类可编辑或删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 金额
            ExpenseFormCard {
                ExpenseInputField(
                    value = amountText, onValueChange = { amountText = it },
                    label = "金额", isRequired = true, suffix = "元", keyboardType = KeyboardType.Decimal
                )
            }

            // 时间
            ExpenseFormCard {
                Column {
                    ExpenseFieldLabel("时间", isRequired = true)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                            .clickable { showDatePicker = true }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(timestamp.toDateTimeText(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 备注
            ExpenseFormCard {
                ExpenseInputField(value = note, onValueChange = { note = it }, label = "备注（可选）")
            }

            Spacer(Modifier.height(8.dp))
            MiuiButton(
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
        MiuiDatePickerDialog(
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
                    ExpenseCategoryStore.add(trimmedNewName)
                    customCategories = ExpenseCategoryStore.getCustom()
                    category = trimmedNewName
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
                        customCategories = ExpenseCategoryStore.getCustom()
                        if (category == cat) category = newName.trim()
                        editingCategory = null
                    }.onFailure { error ->
                        Toast.makeText(context, error.message ?: "重命名失败", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = {
                ExpenseCategoryStore.remove(cat)
                customCategories = ExpenseCategoryStore.getCustom()
                if (category == cat) category = DEFAULT_EXPENSE_CATEGORIES.first()
                editingCategory = null
            },
            onDismiss = { editingCategory = null }
        )
    }
}

// ============================================================
// 复用组件区
// ============================================================

@Composable
private fun ExpenseFieldLabel(text: String, isRequired: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (isRequired) Text(" *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ExpenseFormCard(content: @Composable ColumnScope.() -> Unit) {
    MiuiCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun CommonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isRequired: Boolean = false,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = modifier) {
        label?.let { ExpenseFieldLabel(it, isRequired) }
        if (label != null) Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            placeholder = placeholder?.let { { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            suffix = suffix?.let { { Text(it, fontSize = 14.sp) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent, cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ExpenseInputField(
    value: String, onValueChange: (String) -> Unit, label: String,
    isRequired: Boolean = false, suffix: String? = null, keyboardType: KeyboardType = KeyboardType.Text
) {
    CommonTextField(value, onValueChange, label = label, isRequired = isRequired, suffix = suffix, keyboardType = keyboardType)
}

@Composable
private fun CategoryChip(
    text: String, selected: Boolean, onClick: () -> Unit,
    isCustom: Boolean = false, isAction: Boolean = false, onLongClick: (() -> Unit)? = null
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = when { selected -> MaterialTheme.colorScheme.onPrimary; isAction -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bgColor)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = if (isCustom) onLongClick else null)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 15.sp, fontWeight = if (selected || isAction) FontWeight.SemiBold else FontWeight.Normal, color = textColor, maxLines = 1)
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

    if (showDeleteConfirm) {
        // 👈 核心优化：直接复用公共的 MiuiConfirmDialog
        MiuiConfirmDialog(
            title = "删除分类",
            message = "确定删除分类「$currentName」吗？\n\n已有记录不会丢失，仍保留原分类标签。",
            confirmText = "删除",
            onConfirm = { onDelete() },
            onDismiss = { showDeleteConfirm = false }
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        "编辑分类",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(16.dp))

                    CommonTextField(value = name, onValueChange = { name = it }, label = "分类名")

                    Spacer(Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("删除", fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer) }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("取消", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }

                        Button(
                            onClick = { onSave(name) },
                            enabled = trimmed.isNotEmpty() && trimmed != currentName && !isDuplicate,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("保存", fontSize = 14.sp) }
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
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                CommonTextField(value = value, onValueChange = onValueChange, placeholder = placeholder)
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp)) { Text("取消", color = MaterialTheme.colorScheme.onSurface) }
                    Button(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp)) { Text(confirmText) }
                }
            }
        }
    }
}