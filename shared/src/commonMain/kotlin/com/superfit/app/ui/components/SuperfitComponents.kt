package com.superfit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.superfit.app.domain.NumberInput
import com.superfit.app.theme.SuperfitTheme

private val FieldShape = RoundedCornerShape(16.dp)

/**
 * The one text box style used across the app: a softly tinted box with no frame until it's
 * tapped, then a green frame. Takes the same arguments as OutlinedTextField.
 */
@Composable
fun SuperfitField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1
) {
    val colors = SuperfitTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frame = when {
        isError -> colors.errorColor
        focused -> colors.primaryAccent
        else -> Color.Transparent
    }
    // Number boxes stay one line (Enter doesn't grow them) and take at most two decimals.
    val type = keyboardOptions.keyboardType
    val numeric = type == KeyboardType.Number || type == KeyboardType.Decimal
    // Kept here so the cursor can move to the end when the text is set from outside (a
    // suggestion or chip tapped), instead of staying where it was in the old text.
    var edit by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (edit.text == value) edit else TextFieldValue(value, TextRange(value.length))
    val onChange: (TextFieldValue) -> Unit = { typed ->
        val text = if (numeric) NumberInput.clean(typed.text, allowDecimals = type == KeyboardType.Decimal) else typed.text
        if (text != null) {
            edit = if (text == typed.text) typed else TextFieldValue(text, TextRange(text.length))
            if (text != value) onValueChange(text)
        }
    }
    TextField(
        value = shown,
        onValueChange = onChange,
        modifier = modifier.border(2.dp, frame, FieldShape),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.merge(TextStyle(color = colors.textPrimary)),
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine || numeric,
        maxLines = if (numeric) 1 else maxLines,
        minLines = minLines,
        interactionSource = interaction,
        shape = FieldShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.fieldBg,
            unfocusedContainerColor = colors.fieldBg,
            disabledContainerColor = colors.fieldBg,
            errorContainerColor = colors.fieldBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            cursorColor = colors.primaryAccent,
            focusedLabelColor = colors.primaryAccent,
            unfocusedLabelColor = colors.textSecondary,
            focusedPlaceholderColor = colors.textTertiary,
            unfocusedPlaceholderColor = colors.textTertiary,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedTrailingIconColor = colors.textSecondary,
            unfocusedTrailingIconColor = colors.textSecondary,
            focusedLeadingIconColor = colors.textSecondary,
            unfocusedLeadingIconColor = colors.textSecondary
        )
    )
}

/** The mic button, the same everywhere: a green mic on a soft green circle. */
@Composable
fun MicButton(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "Speak instead of typing") {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = SuperfitTheme.colors.brandSoft,
            contentColor = SuperfitTheme.colors.primaryAccent
        )
    ) {
        Icon(Icons.Default.Mic, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/** The action inside a text box (add, send, save): a green circle, greyed until there's something to act on. */
@Composable
fun FieldActionButton(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SuperfitTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = colors.primaryAccent,
            contentColor = Color.White,
            disabledContainerColor = colors.textPrimary.copy(alpha = 0.08f),
            disabledContentColor = colors.textTertiary
        )
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/** A plain card: white (or the dark card colour) with one corner size and a faint outline. */
@Composable
fun SuperfitCard(modifier: Modifier = Modifier, tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = SuperfitTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint ?: colors.cardBg)
            .then(if (tint == null) Modifier.border(1.dp, colors.glassBorder, shape) else Modifier)
            .padding(16.dp),
        content = content
    )
}

/** A small round icon holder, used for the coach's sparkle and similar badges. */
@Composable
fun IconBadge(icon: ImageVector, background: Color, tint: Color, modifier: Modifier = Modifier, size: Int = 32) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}
