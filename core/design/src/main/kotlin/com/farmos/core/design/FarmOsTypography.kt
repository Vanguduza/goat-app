package com.farmos.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FarmOsInter =
    FontFamily(
        Font(R.font.inter_variable, weight = FontWeight.Normal),
        Font(R.font.inter_variable, weight = FontWeight.Medium),
        Font(R.font.inter_variable, weight = FontWeight.SemiBold),
        Font(R.font.inter_variable, weight = FontWeight.Bold),
    )

private val AnimalFarmDisplay =
    TextStyle(
        fontFamily = FarmOsInter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )
private val AnimalFarmLargeTitle =
    TextStyle(
        fontFamily = FarmOsInter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
private val AnimalFarmSmallTitle =
    TextStyle(
        fontFamily = FarmOsInter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
private val AnimalFarmBody =
    TextStyle(
        fontFamily = FarmOsInter,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
private val AnimalFarmLabel =
    TextStyle(
        fontFamily = FarmOsInter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

val FarmOsTypography =
    Typography(
        displayLarge = AnimalFarmDisplay,
        displayMedium = AnimalFarmDisplay,
        displaySmall = AnimalFarmDisplay,
        headlineLarge = AnimalFarmDisplay,
        headlineMedium = AnimalFarmLargeTitle,
        headlineSmall = AnimalFarmLargeTitle,
        titleLarge = AnimalFarmLargeTitle,
        titleMedium = AnimalFarmSmallTitle,
        titleSmall = AnimalFarmSmallTitle,
        bodyLarge = AnimalFarmBody,
        bodyMedium = AnimalFarmBody,
        bodySmall = AnimalFarmLabel,
        labelLarge = AnimalFarmLabel,
        labelMedium = AnimalFarmLabel,
        labelSmall = AnimalFarmLabel,
    )

/** Compatibility aliases for pre-lock surfaces; intentionally Inter, never script. */
val FarmOsAccentLarge = AnimalFarmDisplay
val FarmOsAccentMedium = AnimalFarmLargeTitle.copy(fontWeight = FontWeight.Medium)
