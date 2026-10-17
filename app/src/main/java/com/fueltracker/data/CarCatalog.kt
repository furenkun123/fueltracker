package com.fueltracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CarCatalog(
    val version: Int = 1,
    @SerialName("min_year") val minYear: Int = 2008,
    val letters: List<String> = emptyList(),
    @SerialName("brandsByLetter")
    val brandsByLetter: Map<String, List<String>> = emptyMap(),
    val tree: Map<String, Map<String, SeriesNode>> = emptyMap()
)

@Serializable
data class SeriesNode(
    val years: List<String> = emptyList(),
    val trims: Map<String, List<Trim>> = emptyMap()
)

@Serializable
data class Trim(
    val name: String = "",
    @SerialName("fuelTank")   val fuelTank: String = "",
    @SerialName("energyType") val energyType: String = "",
    @SerialName("fuelGrade")  val fuelGrade: String = "",
    val status: String = ""
)