# 🚗 FuelTracker（随记油耗）

一款注重简洁体验的 Android 车辆油耗与日常记账应用。基于 **Kotlin** 和 **Jetpack Compose** 开发，采用本地数据存储，并逐步引入 **Miuix** 组件库，打造更贴近 MIUI / HyperOS 设计语言的界面体验。

<p style="text-align: center;">
  <img src="https://github.com/user-attachments/assets/c217f8fb-eadf-4393-befc-a1ca97206981" style="width: 200px;" alt=""/>
  <img src="https://github.com/user-attachments/assets/acf1b8e3-fbd8-4919-965f-d5b8e645c92b" style="width: 200px;" alt=""/>
  <img src="https://github.com/user-attachments/assets/ce3e5352-d449-4179-bf7f-1123b73fbfc1" style="width: 200px;" alt=""/>
</p>

## ✨ 交流反馈 点击链接加入群聊：https://qm.qq.com/q/FiXPEN4Ysm

## ✨ 核心功能

- **⛽ 油耗统计与分析**：以满箱法为基础计算油耗，结合数据校验与异常识别，处理漏记加油记录，减少错误数据对统计结果的影响。
- **🚙 多车辆管理**：支持添加、编辑和切换车辆，按车辆分别管理加油记录与用车费用。
- **🧾 日常费用记录**：记录停车、保养、高速等车辆相关支出，方便查看用车成本。
- **🅿️ 智慧停车**：记录停车位置与车位信息，通过首页卡片和通知辅助记忆停车地点，支持一键取车。
- **🔄 数据备份与迁移**：支持本地 JSON 数据备份和恢复，也支持导入“小熊油耗”导出的 CSV 历史记录，并进行去重与数据校验。
- **🎨 现代化界面**：逐步采用 Miuix 组件与设计语言，支持深色模式、圆角布局和流畅动画；悬浮导航栏等部分界面结合 Kyant Backdrop 实现背景模糊与玻璃质感。

## 🛠️ 技术栈

| 技术                                            | 用途                                                |
|-------------------------------------------------|-----------------------------------------------------|
| Kotlin                                          | 主要开发语言                                        |
| Jetpack Compose                                 | 声明式 UI 框架                                      |
| [Miuix](https://github.com/compose-miuix/miuix) | Compose UI 组件库，用于构建 MIUI / HyperOS 风格界面 |
| MVVM、ViewModel、StateFlow                      | 界面状态管理与业务逻辑组织                          |
| Room                                            | 本地数据库持久化                                    |
| Kotlin Coroutines                               | 异步任务与协程管理                                  |
| Kotlinx Serialization                           | JSON 序列化与数据备份                               |
| Kyant Backdrop                                  | 部分界面的背景模糊与悬浮玻璃效果                    |
| Kyant Shapes                                    | 自定义形状支持                                      |


## 🚗 车辆数据库说明

项目内置车辆数据库（`app/src/main/assets/car_brands.json`），数据来源于第三方公开资料，经整理、清洗后用于车型查询，**仅供学习与交流使用**。

本项目不代表数据来源方的官方授权、认可或合作。若相关内容涉及侵权，请联系作者确认后将立即删除。

## 🔒 隐私与数据

- **本地优先**：车辆、加油和费用等核心数据保存在设备本地的 Room 数据库中。
- **无需账号**：日常记录和核心功能不依赖账号或云端同步。
- **自主备份**：用户可以通过 JSON 备份功能自行导出和迁移数据。

## 🚀 如何构建

### 环境要求

- Android Studio（支持项目所使用的 Android Gradle Plugin）
- **JDK 17**
- Android SDK：**compileSdk 37**
- 最低支持版本：**Android 11（API 30）**

### 构建步骤

1. 使用 Android Studio 打开项目。
2. 等待 Gradle 同步并完成依赖下载。
3. 选择 `app` 运行配置。
4. 点击 Run，安装到设备或模拟器。

## 🙏 开源致谢

FuelTracker 使用了以下优秀的开源项目，感谢开发者和社区的贡献：

- [Kotlin](https://github.com/JetBrains/kotlin)
- [Jetpack Compose](https://developer.android.com/compose)
- [AndroidX](https://developer.android.com/jetpack/androidx)
- [Miuix](https://github.com/compose-miuix/miuix) · [项目文档](https://compose-miuix-ui.github.io/miuix/)
- [Room](https://developer.android.com/training/data-storage/room)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kyant Backdrop](https://github.com/Kyant0/Backdrop)
- [Kyant Shapes](https://github.com/Kyant0/Shapes)

感谢所有开源项目的开发者和维护者。

---

## 📄 开源协议

本项目**源代码**采用 [Apache License 2.0](LICENSE) 开源。

第三方依赖及车辆数据库等外部资料**不在**本项目源代码许可范围内，分别遵循其原有许可或相关使用规定。

Copyright 2026 furenkun123