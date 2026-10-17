package com.fueltracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CarBrand(
    @SerialName("brand")
    val brand: String,  // 品牌名称，例如："领克"
    @SerialName("letter")
    val letter: String, // 首字母分组，例如："L"
    @SerialName("series")
    val series: List<String> = emptyList() // ★ 新增：包含的车系列表
)

