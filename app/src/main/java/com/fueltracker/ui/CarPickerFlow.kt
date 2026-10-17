package com.fueltracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fueltracker.data.Trim

/** Picker 起点: 从哪一级开始重新选 */
enum class PickerLevel { BRAND, SERIES, YEAR, TRIM }

sealed interface CarSelection {
    /** 只选到品牌 */
    data class Manual(val brand: String) : CarSelection

    /** 品牌 + 车系 (手输车系时) */
    data class BrandSeries(
        val brand: String,
        val series: String
    ) : CarSelection

    /** 品牌 + 车系 + 年款 (手输年款时) */
    data class BrandSeriesYear(
        val brand: String,
        val series: String,
        val year: String
    ) : CarSelection

    /** 完整选到具体车型 */
    data class Full(
        val brand: String,
        val series: String,
        val year: String,
        val trim: Trim
    ) : CarSelection
}

@Composable
fun CarPickerFlow(
    startLevel: PickerLevel = PickerLevel.BRAND,
    initialBrand: String = "",
    initialSeries: String = "",
    initialYear: String = "",
    onBack: () -> Unit,
    onPicked: (CarSelection) -> Unit
) {
    // 统一 key 键
    val stateKey = remember(startLevel, initialBrand, initialSeries, initialYear) {
        arrayOf<Any>(startLevel, initialBrand, initialSeries, initialYear)
    }

    var brand by remember(keys = stateKey) {
        mutableStateOf(if (startLevel != PickerLevel.BRAND) initialBrand else "")
    }

    var series by remember(keys = stateKey) {
        mutableStateOf(
            if (startLevel == PickerLevel.YEAR || startLevel == PickerLevel.TRIM) initialSeries else ""
        )
    }

    var year by remember(keys = stateKey) {
        mutableStateOf(if (startLevel == PickerLevel.TRIM) initialYear else "")
    }

    var level by remember(keys = stateKey) {
        mutableStateOf(startLevel)
    }

    fun goBackTo(prev: PickerLevel) {
        level = prev
        when (prev) {
            PickerLevel.BRAND -> {
                brand = ""
                series = ""
                year = ""
            }
            PickerLevel.SERIES -> {
                series = ""
                year = ""
            }
            PickerLevel.YEAR -> {
                year = ""
            }
            PickerLevel.TRIM -> { }
        }
    }

    when (level) {
        PickerLevel.BRAND -> {
            BrandSelectorScreen(
                initialBrand = initialBrand,
                onBack = onBack,
                onSelected = { selectedBrand ->
                    brand = selectedBrand
                    series = ""
                    year = ""
                    level = PickerLevel.SERIES
                },
                onManualInput = { manualBrand ->
                    onPicked(CarSelection.Manual(manualBrand))
                }
            )
        }

        PickerLevel.SERIES -> {
            SeriesSelectorScreen(
                brand = brand,
                onBack = {
                    if (startLevel == PickerLevel.BRAND) goBackTo(PickerLevel.BRAND)
                    else onBack()
                },
                onSelected = { selectedSeries ->
                    series = selectedSeries
                    year = ""
                    level = PickerLevel.YEAR
                },
                onManualInput = { manualSeries ->
                    onPicked(CarSelection.BrandSeries(brand, manualSeries))
                }
            )
        }

        PickerLevel.YEAR -> {
            YearSelectorScreen(
                brand = brand,
                series = series,
                onBack = {
                    if (startLevel == PickerLevel.BRAND || startLevel == PickerLevel.SERIES) {
                        goBackTo(PickerLevel.SERIES)
                    } else {
                        onBack()
                    }
                },
                onSelected = { selectedYear ->
                    year = selectedYear
                    level = PickerLevel.TRIM
                },
                // 手输年款: 直接结束，带上 品牌 + 车系 + 年款
                onManualInput = { manualYear ->
                    onPicked(CarSelection.BrandSeriesYear(brand, series, manualYear))
                }
            )
        }

        PickerLevel.TRIM -> {
            TrimSelectorScreen(
                brand = brand,
                series = series,
                year = year,
                onBack = {
                    if (startLevel == PickerLevel.TRIM) onBack()
                    else goBackTo(PickerLevel.YEAR)
                },
                onSelected = { trim ->
                    onPicked(CarSelection.Full(brand, series, year, trim))
                },
                onManualInput = { manualName ->
                    onPicked(CarSelection.Full(brand, series, year, Trim(name = manualName)))
                }
            )
        }
    }
}