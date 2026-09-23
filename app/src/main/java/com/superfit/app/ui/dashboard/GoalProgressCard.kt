package com.superfit.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.AreaOfConcern
import com.superfit.app.domain.WeightGoalMetrics

@Composable
fun GoalProgressCard(
    metrics: WeightGoalMetrics,
    onLogWeight: (Double, String) -> Unit,
    onUpdateGoal: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showWeighInDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Scale,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WEIGHT & GOAL TRACKER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onUpdateGoal != null) {
                        IconButton(
                            onClick = { showEditGoalDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Target Goal",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Button(
                        onClick = { showWeighInDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Weigh In",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Metrics Highlight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "${String.format("%.1f", metrics.currentWeightKg)} kg",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val changePrefix = if (metrics.totalWeightChangeKg <= 0) "" else "+"
                    val changeLabel = "${changePrefix}${String.format("%.1f", metrics.totalWeightChangeKg)} kg total"
                    Text(
                        text = "Last Known Weight ($changeLabel)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${metrics.progressPercentage}% Goal",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Linear Progress Bar with Start & Target Labels
            LinearProgressIndicator(
                progress = { metrics.progressPercentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Start: ${String.format("%.1f", metrics.startingWeightKg)} kg",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (onUpdateGoal != null) showEditGoalDialog = true
                    }
                ) {
                    Text(
                        text = "Goal: ${String.format("%.1f", metrics.targetWeightKg)} kg",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (onUpdateGoal != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Goal",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Pace Indicator if available
            if (metrics.weeklyPaceKg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val paceStr = String.format("%.2f", metrics.weeklyPaceKg)
                val paceLabel = if (metrics.weeklyPaceKg < 0) "$paceStr kg/week" else "+$paceStr kg/week"
                Text(
                    text = "⚡ Current Pace: $paceLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Areas of Concern Alert Box
            val concern = metrics.areaOfConcern
            if (concern !is AreaOfConcern.None) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (concern) {
                        is AreaOfConcern.StaleLog -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        is AreaOfConcern.Plateau -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        is AreaOfConcern.RapidLoss -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (concern) {
                                is AreaOfConcern.StaleLog -> Icons.Default.Info
                                is AreaOfConcern.Plateau -> Icons.Default.Warning
                                is AreaOfConcern.RapidLoss -> Icons.Default.Warning
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = when (concern) {
                                is AreaOfConcern.StaleLog -> MaterialTheme.colorScheme.onErrorContainer
                                is AreaOfConcern.Plateau -> MaterialTheme.colorScheme.onTertiaryContainer
                                is AreaOfConcern.RapidLoss -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (concern) {
                                is AreaOfConcern.StaleLog -> "It's been ${concern.daysAgo} days since your last weigh-in. Tap 'Weigh In' for a quick update!"
                                is AreaOfConcern.Plateau -> "Plateau Alert: Weight has been steady over the past ${concern.daysCount} days."
                                is AreaOfConcern.RapidLoss -> "Rapid Drop: Losing weight fast (${String.format("%.1f", concern.pacePerWeek)} kg/wk). Prioritize protein & hydration!"
                                else -> ""
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (concern) {
                                is AreaOfConcern.StaleLog -> MaterialTheme.colorScheme.onErrorContainer
                                is AreaOfConcern.Plateau -> MaterialTheme.colorScheme.onTertiaryContainer
                                is AreaOfConcern.RapidLoss -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }

    if (showWeighInDialog) {
        WeighInDialog(
            currentWeight = metrics.currentWeightKg,
            onDismiss = { showWeighInDialog = false },
            onConfirm = { weight ->
                onLogWeight(weight, "")
                showWeighInDialog = false
            }
        )
    }

    if (showEditGoalDialog && onUpdateGoal != null) {
        EditGoalDialog(
            startingWeight = metrics.startingWeightKg,
            targetWeight = metrics.targetWeightKg,
            onDismiss = { showEditGoalDialog = false },
            onConfirm = { start, target ->
                onUpdateGoal(start, target)
                showEditGoalDialog = false
            }
        )
    }
}

@Composable
fun WeighInDialog(
    currentWeight: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var weightInput by remember { mutableStateOf(currentWeight.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Today's Weight",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter your current scale reading in kilograms (kg):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = {
                        weightInput = it
                        isError = false
                    },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Please enter a valid weight number (e.g. 74.5)",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val doubleVal = weightInput.toDoubleOrNull()
                    if (doubleVal != null && doubleVal > 20.0 && doubleVal < 300.0) {
                        onConfirm(doubleVal)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("Save Weight")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditGoalDialog(
    startingWeight: Double,
    targetWeight: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var startInput by remember { mutableStateOf(startingWeight.toString()) }
    var targetInput by remember { mutableStateOf(targetWeight.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Weight Goals",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Update your starting weight and target goal weight:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = startInput,
                    onValueChange = {
                        startInput = it
                        isError = false
                    },
                    label = { Text("Starting Weight (kg)") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetInput,
                    onValueChange = {
                        targetInput = it
                        isError = false
                    },
                    label = { Text("Target Goal Weight (kg)") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isError) {
                    Text(
                        text = "Please enter valid weight numbers.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sVal = startInput.toDoubleOrNull()
                    val tVal = targetInput.toDoubleOrNull()
                    if (sVal != null && tVal != null && sVal > 20.0 && tVal > 20.0) {
                        onConfirm(sVal, tVal)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("Save Goal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
