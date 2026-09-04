package com.farmos.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FarmOsInter = FontFamily(
    Font(R.font.inter_variable, weight = FontWeight.Normal),
    Font(R.font.inter_variable, weight = FontWeight.Medium),
    Font(R.font.inter_variable, weight = FontWeight.SemiBold),
    Font(R.font.inter_variable, weight = FontWeight.Bold),
)

val FarmOsCaveat = FontFamily(
    Font(R.font.caveat_variable, weight = FontWeight.Normal),
    Font(R.font.caveat_variable, weight = FontWeight.Medium),
    Font(R.font.caveat_variable, weight = FontWeight.SemiBold),
    Font(R.font.caveat_variable, weight = FontWeight.Bold),
)

val FarmOsTypography = Typography(
    displayLarge = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp),
    displaySmall = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = FarmOsInter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

val FarmOsAccentLarge = TextStyle(
    fontFamily = FarmOsCaveat,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 32.sp,
)

val FarmOsAccentMedium = TextStyle(
    fontFamily = FarmOsCaveat,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    lineHeight = 27.sp,
)
