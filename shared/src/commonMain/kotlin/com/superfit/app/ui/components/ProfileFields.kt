package com.superfit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.superfit.app.domain.BirthMonth
import com.superfit.app.domain.HeightUnits
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

// The "About you" boxes, shared by sign-up and Settings so both look and check the same.

/** A small grey label above a field or a group of choices. */
@Composable
fun ProfileFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = SuperfitTheme.colors.textSecondary, modifier = modifier)
}

/**
 * Pick-one choices as soft pills that wrap onto rows, the selected one solid green
 * (the same look as the app's SegmentedPills, but it can wrap onto two rows).
 */
@Composable
fun <T> ChoiceGrid(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, columns: Int = 2) {
    val colors = SuperfitTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.fieldBg)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (key, label) ->
                    val isOn = key == selected
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                        color = if (isOn) Color.White else colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (isOn) NeonMint else Color.Transparent)
                            .semantics {
                                role = Role.RadioButton
                                this.selected = isOn
                            }
                            .clickable { onSelect(key) }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UnitSuffix(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = SuperfitTheme.colors.textSecondary, modifier = Modifier.padding(end = 12.dp))
}

@Composable
private fun ErrorLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = SuperfitTheme.colors.errorColor)
}

/**
 * Birth month (picked from a list) and year (typed). [error] is the plain line to show under
 * them, or null.
 */
@Composable
fun BirthMonthInput(
    month: Int?,
    year: String,
    onMonth: (Int) -> Unit,
    onYear: (String) -> Unit,
    error: String?
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileFieldLabel("Born in")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1.4f)) {
                SuperfitField(
                    value = month?.let { BirthMonth.MONTH_NAMES[it - 1] } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Month") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SuperfitTheme.colors.textSecondary) },
                    isError = error != null && (month == null),
                    modifier = Modifier.fillMaxWidth()
                )
                // A read-only box doesn't take taps, so a clear layer on top opens the list.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { open = true }
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    BirthMonth.MONTH_NAMES.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onMonth(index + 1)
                                open = false
                            }
                        )
                    }
                }
            }
            SuperfitField(
                value = year,
                onValueChange = { onYear(it.filter(Char::isDigit).take(4)) },
                placeholder = { Text("Year") },
                isError = error != null && month != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f)
            )
        }
        if (error != null) ErrorLine(error)
    }
}

/**
 * Height typed in cm, or in feet and inches; it is always handed back in cm (as text, "" when
 * empty). [refreshKey] changing (for example Health Connect filling the height in) shows the new
 * height in feet and inches when that's the unit in use.
 */
@Composable
fun HeightInput(heightCm: String, onHeightCm: (String) -> Unit, isError: Boolean, refreshKey: Any? = null) {
    var useFeet by rememberSaveable { mutableStateOf(false) }
    val cm = heightCm.toDoubleOrNull()?.takeIf { it > 0 }
    var feet by remember { mutableStateOf("") }
    var inches by remember { mutableStateOf("") }
    LaunchedEffect(useFeet, refreshKey) {
        if (useFeet && cm != null) {
            val (f, i) = HeightUnits.feetInches(cm)
            feet = f.toString()
            inches = i.toString()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileFieldLabel("Height", modifier = Modifier.weight(1f))
            Row(modifier = Modifier.width(150.dp)) {
                ChoiceGrid(options = listOf(false to "cm", true to "ft in"), selected = useFeet, onSelect = { useFeet = it })
            }
        }
        if (!useFeet) {
            val other = cm?.let { HeightUnits.feetInches(it) }?.let { (f, i) -> "cm · $f ft $i in" } ?: "cm"
            SuperfitField(
                value = heightCm,
                onValueChange = onHeightCm,
                placeholder = { Text("e.g. 170") },
                trailingIcon = { UnitSuffix(other) },
                isError = isError,
                supportingText = if (isError) ({ Text("Enter your height") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            val update = { f: String, i: String ->
                val ft = f.toIntOrNull() ?: 0
                val inch = i.toIntOrNull() ?: 0
                onHeightCm(if (ft == 0 && inch == 0) "" else HeightUnits.cm(ft, inch).toString())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SuperfitField(
                    value = feet,
                    onValueChange = {
                        feet = it.take(1)
                        update(feet, inches)
                    },
                    placeholder = { Text("5") },
                    trailingIcon = { UnitSuffix("ft") },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f)
                )
                SuperfitField(
                    value = inches,
                    onValueChange = {
                        inches = it.take(2)
                        update(feet, inches)
                    },
                    placeholder = { Text("7") },
                    trailingIcon = { UnitSuffix("in") },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f)
                )
            }
            if (isError) ErrorLine("Enter your height")
        }
    }
}
