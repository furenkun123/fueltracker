
package com.fueltracker.ui.miuix.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

// ============================================================
// 公共弹窗外壳
// ============================================================

@Composable
fun MiuixScrollableSettingsDialog(
    title: String,
    buttonText: String,
    onDismiss: () -> Unit,
    centeredTitle: Boolean = false,
    spacedContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    WindowDialog(
        title = if (centeredTitle) null else title,
        show = true,
        onDismissRequest = onDismiss,
        backgroundColor = MiuixTheme.colorScheme.surface,
        cornerRadius = 28.dp,
        insideMargin = DpSize(24.dp, 20.dp),
        maxWidth = 420.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (centeredTitle) {
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                        .semantics { heading() },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = if (spacedContent) {
                        Arrangement.spacedBy(12.dp)
                    } else {
                        Arrangement.Top
                    }
                ) {
                    content()
                }
            }

            Spacer(Modifier.height(20.dp))

            TextButton(
                text = buttonText,
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                cornerRadius = 24.dp,
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.primary,
                    textColor = MiuixTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

// ============================================================
// 隐私政策内容模型
// ============================================================

private data class PrivacyInfoSection(
    val title: String,
    val body: String
)

// ============================================================
// 隐私政策
// ============================================================

@Composable
fun MiuixPrivacyDialog(onDismiss: () -> Unit) {

    val sections = remember {
        listOf(

            PrivacyInfoSection(
                title = "一、应用基本说明",
                body = """
                FuelTracker（随记油耗）是一款个人车辆油耗与用车费用管理工具。
                1. 应用以本地数据存储为主要设计方式，不提供用户账号注册、云端账号体系或跨设备自动同步功能。
                2. 本应用不运营用于收集用户记账数据的自建服务器。车辆、加油、费用和停车等核心业务数据由应用保存在当前设备中。
                3. 本隐私政策适用于 FuelTracker 应用本身。用户通过系统浏览器或外部应用访问第三方网页时，应同时参考对应第三方的隐私政策。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "二、信息处理范围",
                body = """
                为实现应用功能，应用会处理用户主动填写或选择的以下信息：
                1. 车辆信息：车辆品牌、车系、年款、车型、能源类型、油箱容量及车辆相关配置，用于车辆管理和油耗计算。
                2. 加油记录：加油时间、里程、加油量、燃油标号、油价、原价、优惠金额、实际支付金额、是否加满、加油前剩余油量（如用户填写）及备注，用于油耗计算和历史记录查询。
                3. 费用记录：费用时间、金额、费用分类、停车地点、楼层、区域、车位号、出发地、目的地及备注，用于车辆费用统计。
                4. 个性化设置：主题模式、背景设置及相关界面偏好，用于保存应用显示状态。
                5. 用户选择的文件：通过系统文件选择器主动选择的 CSV、JSON 等文件，用于数据导入、导出和迁移。
                上述信息主要用于应用本身的功能，不用于广告画像或商业营销。用户不填写某项可选信息时，不影响其他主要功能的使用。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "三、位置权限",
                body = """
                应用仅在停车记录功能中使用位置权限，用于辅助填写停车地点。
                需要使用定位功能时，应用会申请 Android 系统的位置权限。获得授权后，应用使用 GPS 卫星定位获取当前位置，并通过 Android Geocoder 地址解析服务尝试转换为区县、街道或附近地点等文字描述。
                需要注意：
                1. 应用使用 GPS_PROVIDER 获取位置，不使用 NETWORK_PROVIDER 进行网络定位。GPS 卫星定位本身不需要互联网连接。
                2. 应用会优先主动请求 GPS 当前位置，最多等待约 15 秒。主动定位未获取到结果时，会尝试使用最近 10 分钟内的 GPS 缓存位置。GPS 获取到坐标后，应用会尝试通过 Android Geocoder 将坐标转换为文字地址，地址解析最多再等待约 10 秒；超时或失败时，会尝试显示 GPS 经纬度。实际定位效果可能受到卫星信号、设备环境和系统定位服务状态影响。
                3. Android Geocoder 是系统提供的地址解析接口，可能使用网络服务。即使 GPS 已成功获取坐标，在没有网络或地址解析服务不可用时，也可能无法获得区县、街道或 POI 名称。
                4. 地址解析失败时，应用会尝试显示 GPS 经纬度，供用户确认或手动填写停车位置。
                5. 定位结果仅供用户确认，用户可以自行修改或填写停车地点。进入新建停车页面时，如果已经授权定位权限，应用可能自动尝试获取位置。
                6. GPS 坐标不会作为独立的经纬度字段写入停车记录。如果离线时返回的是经纬度文字，并由用户确认后保存，该文字会作为停车地点信息保存在设备本地数据库中。
                7. 应用将坐标交给 Android Geocoder 接口进行地址解析。该接口背后的具体服务提供方、网络请求方式及数据处理方式由设备系统或相应服务提供方决定，可能涉及网络传输，本应用无法控制其内部处理过程。
                8. 位置信息可能属于敏感个人信息。用户可以拒绝定位权限，改为手动填写停车位置，不影响其他主要功能。应用仅在需要使用定位功能时请求相应授权。
            """.trimIndent()

            ),

            PrivacyInfoSection(
                title = "四、通知权限",
                body = """
                1. 应用可以使用 Android 通知功能显示停车提醒。
                2. 通知内容可能包含停车地点、楼层、区域和车位号，方便用户取车。
                3. 在 Android 13 及以上系统中，应用会在需要时请求通知权限。用户可以拒绝授权或随时在系统设置中关闭通知。
                4. 通知内容的锁屏显示范围取决于设备系统的通知隐私设置。如用户担心停车信息在锁屏泄露，建议在系统设置中关闭该应用的通知详情或锁屏显示。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "五、本地存储与数据安全",
                body = """
                1. 车辆、加油记录、费用记录、自定义分类及停车信息主要保存在应用本地数据库中。
                2. 应用不提供自动云同步功能，也不会主动将上述业务记录发送到开发者自建服务器。
                3. 应用使用 Android 本地存储机制管理数据库和设置数据。应用声明禁用了 Android 自动备份功能。
                4. 但本地存储并不意味着绝对安全。设备丢失、损坏、系统故障、应用数据被清除或卸载，都可能造成未备份的数据丢失。
                5. 应用内提供单条记录的编辑和删除功能，用户也可以通过 Android 系统设置清除应用数据或卸载应用。清除应用数据或卸载应用会删除本地数据库中的业务记录，但不会自动删除用户此前导出的备份文件。
                请根据需要定期导出备份，并妥善保管。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "六、数据备份与文件导入",
                body = """
                1. 应用提供 JSON 数据备份和恢复功能，也支持导入小熊油耗导出的 CSV 历史数据。
                2. 相关文件由用户通过 Android 系统文件选择器主动选择。
                3. 备份文件包含车辆信息、加油记录、费用记录、停车信息及自定义分类等数据。
                4. JSON 备份文件目前为普通文本格式，不应视为加密文件。能够访问该文件的其他应用或人员，可能读取其中的信息。
                5. 请勿将包含真实车辆信息、停车地点、车位号和费用记录的备份文件公开上传或随意发送给他人。
                6. 恢复备份可能覆盖当前已有数据，操作前建议先备份当前数据，并确认选择了正确的文件。
                7. 用户导出的 JSON、CSV 等文件独立于应用数据库保存。删除应用内记录或卸载应用，不会自动删除用户此前导出的文件。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "七、第三方服务与外部网页",
                body = """
                应用不提供自建用户数据上传服务。在使用以下功能时，可能涉及 Android 系统或第三方服务：
                1. 位置解析（Geocoder）：应用调用 Android 系统 Geocoder 接口，将 GPS 坐标转换为文字地址。根据 Android 官方接口说明，地址解析服务可能访问网络，也可能在没有可用后端服务时返回空结果。具体服务提供方及其数据处理行为取决于设备系统和系统服务配置。
                2. 定位服务：应用通过 Android LocationManager 使用 GPS_PROVIDER 获取卫星定位，不主动使用 NETWORK_PROVIDER 进行网络定位。
                3. 外部网页：用户主动点击查看更新、访问项目主页或打开小熊油耗网页时，应用会调用系统处理程序打开对应网页。
                4. 文件访问：用户通过系统文件选择器选择导入文件或保存备份文件。
                5. 通知与系统权限：通知显示、定位授权和文件选择由 Android 系统及相关系统组件提供。
                需要说明的是，本应用不运营自建地址解析服务器，也不会主动将定位数据上传至开发者自建服务器。但 Geocoder 的具体实现可能涉及系统或第三方服务的网络请求，开发者无法通过 Android 接口确认实际服务提供方、服务器位置或其内部数据处理方式。
                打开外部网页后，浏览器及对应网站可能按照其自身隐私政策处理访问信息。应用不会主动将本地数据库或备份内容附加到这些网页请求中。
                第三方系统服务和网站的具体数据处理行为，应以其各自的说明或隐私政策为准。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "八、未成年人保护",
                body = """
                1. 本应用主要面向个人车辆使用和用车费用管理，不专门面向未成年人设计。
                2. 未成年人应在监护人指导下使用本应用。监护人应帮助未成年人理解本应用的本地数据存储、文件导出和位置权限等事项。
                3. 如果监护人发现未成年人在未经同意的情况下使用本应用或导出了包含个人信息的文件，建议及时删除相关记录和文件。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "九、数据保存与删除",
                body = """
                1. 应用不会主动设定用户记账数据的云端保存期限。
                2. 数据会保留在设备中，直到用户主动删除相关记录、清除应用数据或卸载应用。
                3. 用户可以通过应用提供的编辑和删除功能管理记录，也可以通过 Android 系统设置清除应用数据。
                4. 用户主动导出的 JSON、CSV 等文件独立于应用数据库保存。删除应用内记录或卸载应用，不会自动删除用户此前导出的文件。
                5. 如需删除已导出的备份文件，用户需要自行通过系统文件管理工具或保存位置对应的应用进行删除。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "十、用户的权利",
                body = """
                用户可以根据自身需要：
                1. 自行查看、修改和删除应用内记录。
                2. 通过 JSON 备份功能导出自己的业务数据。
                3. 通过系统权限设置关闭定位或通知权限，撤回相应授权。
                4. 停止使用应用并卸载应用，或通过系统设置清除应用数据。
                5. 通过项目反馈渠道提出隐私或数据处理方面的问题。请勿在公开反馈中上传包含真实车辆、位置、费用或其他个人信息的备份文件。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "十一、隐私政策更新",
                body = """
                1. 当应用功能、权限使用方式或数据处理方式发生较大变化时，开发者可能更新本隐私政策。
                2. 更新后的内容会随应用版本发布，并在必要时通过应用内提示告知用户。
                3. 建议用户在更新版本后查看隐私政策的变化。本政策的最新更新日期会在应用内展示。
            """.trimIndent()
            ),

            PrivacyInfoSection(
                title = "十二、开发者与联系渠道",
                body = """
                应用名称：FuelTracker（随记油耗）
                开发者：FuelTracker 项目个人维护者。
                项目主页：
                https://github.com/furenkun123/fueltracker
                隐私问题、功能反馈及数据处理相关问题，可以通过 GitHub 项目反馈渠道联系维护者。
                请勿在公开反馈中上传包含真实车辆、位置、费用或其他个人信息的备份文件。
            """.trimIndent()
            )
        )
    }
    MiuixScrollableSettingsDialog(
        title = "隐私政策与数据说明",
        buttonText = "我已了解",
        onDismiss = onDismiss,
        centeredTitle = true
    ) {
        Text(
            text = "FuelTracker（随记油耗）",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        )

        Text(
            text = "生效日期：2026年10月12日 · 最后更新：2026年11月13日",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        sections.forEach { section ->
            PrivacySection(
                title = section.title,
                body = section.body
            )
        }
    }
}

// ============================================================
// 隐私政策小节
// ============================================================

@Composable
private fun PrivacySection(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = body,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            lineHeight = 21.sp
        )
    }
}

// ============================================================
// 应用说明
// ============================================================

private data class InstructionItem(
    val title: String,
    val body: String
)

@Composable
fun MiuixInstructionsDialog(onDismiss: () -> Unit) {

    val instructions = remember {
        listOf(

            InstructionItem(
                title = "车辆管理",
                body = """
                    1. 首次使用时先添加车辆，选择对应的品牌、车系、年款和车型，并填写车辆相关信息。
                    2. 支持添加多辆车、编辑车辆信息和切换当前车辆。不同车辆的加油记录和费用记录分别管理。
                """.trimIndent()
            ),

            InstructionItem(
                title = "加油记录",
                body = """
                    1. 每次加油后记录当前里程、加油量、燃油标号和金额等信息。
                    2. 支持记录是否加满、加油前剩余油量（如填写）以及备注。建议每次加油后及时记录，减少遗漏。
    """.trimIndent()
            ),

            InstructionItem(
                title = "油耗统计",
                body = """
                    1. 应用以满箱法作为主要计算依据，通过有效的满箱记录建立计算区间，统计百公里油耗。
                    2. 期间的未加满记录、漏记补录及异常数据会根据算法规则参与处理。
                    3. 当记录不完整或数据存在较大偏差时，统计结果可能受到影响。部分结果属于估算值，并不等同于车辆仪表显示的瞬时油耗。
                """.trimIndent()
            ),

            InstructionItem(
                title = "漏记补录",
                body = """
                    1. 如果遗漏了某次加油，可以在后续记录中使用漏记补录功能。
                    2. 根据实际情况填写漏记的里程和加油量，应用会将补录信息纳入后续统计。
                    3. 补录数据越接近实际情况，统计结果通常越可靠。请尽量避免随意估算。
                """.trimIndent()
            ),

            InstructionItem(
                title = "停车记录",
                body = """
                    1. 可以记录停车地点、楼层、区域、车位号和备注。
                    2. 如果已经授权定位权限，进入新建停车页面时，应用可能自动尝试通过 GPS 获取当前位置。GPS 坐标获取本身不需要互联网。
                    3. 应用会尝试使用 Android Geocoder 将坐标转换为区县、街道或附近地点名称。地址解析可能需要网络，离线时如果无法解析，应用会显示 GPS 经纬度，用户也可以手动填写停车位置。
                    4. 定位结果仅供参考，请确认地点正确后再保存。定位结果可能受到卫星信号和设备环境影响。
                    5. 保存停车记录后，首页停车卡片和通知可以辅助记忆停车地点。
                    6. 取车后可以通过“已取车”等操作结束当前停车状态。
                    """.trimIndent()
            ),

            InstructionItem(
                title = "费用管理",
                body = """
                    1. 支持记录停车、高速、保养、保险、洗车等车辆相关支出。
                    2. 可以使用内置分类或自定义分类管理费用，并通过费用统计和趋势图查看用车成本。
                """.trimIndent()
            ),

            InstructionItem(
                title = "数据备份与恢复",
                body = """
                    1. 在设置页面可以导出 JSON 备份，也可以选择已有备份文件恢复数据。
                    2. 恢复操作可能覆盖当前数据库内容，建议先备份当前数据。
                    3. JSON 备份文件包含车辆、加油、费用和停车等信息，请妥善保管，不要随意公开分享。
                """.trimIndent()
            ),

            InstructionItem(
                title = "小熊油耗数据导入",
                body = """
                    1. 如果需要从小熊油耗迁移历史数据，可以先在小熊油耗网页版导出对应的 CSV 文件。
                    2. 然后进入 FuelTracker 设置中的备份与恢复功能，选择对应的 CSV 文件进行导入。
                    3. 应用会根据导入规则进行数据解析和去重。导入前建议先备份现有数据，并检查导入后的记录。
                """.trimIndent()
            ),

            InstructionItem(
                title = "记录管理",
                body = """
                    1. 在加油记录和费用页面可以查看历史明细。
                    2. 点击对应记录可以进入编辑或删除操作。修改历史里程、油量或金额后，相关统计结果可能随之变化。
                """.trimIndent()
            ),

            InstructionItem(
                title = "主题与个性化",
                body = """
                    1. 设置页面支持切换浅色、深色或跟随系统的主题模式。
                    2. 也可以根据应用提供的选项调整界面背景及部分显示效果。
                """.trimIndent()
            ),
            InstructionItem(
                title = "油耗算法切换（谨慎调整）",
                body = """
        1. 支持在设置中切换油耗偏离值识别模式，建议一般用户保持“标准模式”。
        2. 标准模式：采用默认异常识别规则，适合日常连续记录，推荐一般用户使用。
        3. 宽松模式：放宽异常数据识别条件，适合凭记忆补录、导入历史记录或数据波动较大的情况。
        4. 切换模式不会改变加油记录本身，也不会改变满箱法等基础计算流程，主要影响异常数据的识别与处理。不同模式下的油耗统计结果可能存在差异，确有必要时再调整。
    """.trimIndent()
            ),
            InstructionItem(
                title = "使用建议",
                body = """
                    1. 尽量保持连续、准确的加油记录。
                    2. 记录加油时核对里程、油量和金额。
                    3. 不确定的数据不要随意填写。
                    4. 定期导出 JSON 备份，尤其是在换机、恢复系统或卸载应用之前。
                    5. 如果发现油耗统计异常，建议先检查原始记录，再判断是否需要补录或修改。
                """.trimIndent()
            )
        )
    }

    MiuixScrollableSettingsDialog(
        title = "应用说明",
        buttonText = "关闭",
        onDismiss = onDismiss,
        centeredTitle = true,
        spacedContent = true
    ) {
        instructions.forEachIndexed { index, item ->
            MiuixInstructionItem(
                index = index + 1,
                title = item.title,
                body = item.body
            )
        }
    }
}

// ============================================================
// 应用说明条目
// ============================================================

@Composable
private fun MiuixInstructionItem(
    index: Int,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(23.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onPrimaryContainer
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = body,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                lineHeight = 21.sp
            )
        }
    }
}
