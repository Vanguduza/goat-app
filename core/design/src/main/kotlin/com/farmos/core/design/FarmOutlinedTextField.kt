package com.farmos.core.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Shared capture field: native Animal Farm tokens with unchanged text/editing semantics. */
@Composable
fun FarmOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(AnimalFarmHomeMetrics.actionRadius),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AnimalFarmTheme.colors.ink,
        unfocusedTextColor = AnimalFarmTheme.colors.ink,
        disabledTextColor = AnimalFarmTheme.colors.mutedInk,
        focusedContainerColor = AnimalFarmTheme.colors.surface,
        unfocusedContainerColor = AnimalFarmTheme.colors.surface,
        disabledContainerColor = AnimalFarmTheme.colors.softSurface,
        cursorColor = AnimalFarmTheme.colors.primary,
        focusedBorderColor = AnimalFarmTheme.colors.primary,
        unfocusedBorderColor = AnimalFarmTheme.colors.divider,
        focusedLabelColor = AnimalFarmTheme.colors.primary,
        unfocusedLabelColor = AnimalFarmTheme.colors.mutedInk,
        errorBorderColor = AnimalFarmTheme.colors.critical,
        errorLabelColor = AnimalFarmTheme.colors.critical,
    ),
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
        enabled = enabled, readOnly = readOnly, textStyle = textStyle,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        prefix = prefix, suffix = suffix, supportingText = supportingText, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        interactionSource = interactionSource, shape = shape, colors = colors,
    )
}
