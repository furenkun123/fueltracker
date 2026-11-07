package com.fueltracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.defaultTextStyles

val AppTextStyles = defaultTextStyles(
    main = TextStyle(fontSize = 16.sp),
    paragraph = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    body1 = TextStyle(fontSize = 16.sp),
    body2 = TextStyle(fontSize = 14.sp),
    button = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    footnote1 = TextStyle(fontSize = 13.sp),
    footnote2 = TextStyle(fontSize = 11.sp),
    headline1 = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headline2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    subtitle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    title1 = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    title2 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    title4 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
)
