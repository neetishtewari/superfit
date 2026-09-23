package com.superfit.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.StreakStateEntity
import com.superfit.app.domain.TrainerWorkoutRecommendation
import com.superfit.app.theme.*

@Composable
fun StreakHeaderCard(
    streakState: StreakStateEntity,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SuperfitTheme.colors.cardBgTranslucent,
        border = BorderStroke(1.dp, SuperfitTheme.colors.glassBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Streak Fire",
                    tint = CoralRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${streakState.currentStreak} DAY STREAK",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SuperfitTheme.colors.textPrimary,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (streakState.graceDaysRemaining == 0 && streakState.currentStreak > 0) {
                // Grace day was consumed to protect the streak!
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CoralRed.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Grace Active",
                            tint = CoralRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Grace Used",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoralRed,
                            maxLines = 1
                        )
                    }
                }
            } else if (streakState.longestStreak > 1) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SuperfitTheme.colors.textPrimary.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Best Streak",
                            tint = ElectricCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Best: ${streakState.longestStreak}d",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuperfitTheme.colors.textPrimary,
                            maxLines = 1
                        )
                    }
                }
            } else if (streakState.currentStreak > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonMint.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = NeonMint,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonMint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutRecommendationCard(
    recommendation: TrainerWorkoutRecommendation,
    onLogWorkout: (String, String) -> Unit,
    onDisableClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Keep track of selected exercises
    val selectedIds = remember(recommendation) {
        mutableStateMapOf<String, Boolean>().apply {
            recommendation.exercises.forEach { put(it.id, true) }
        }
    }

    var showRpeDialog by remember { mutableStateOf(false) }
    var pendingWorkoutDesc by remember { mutableStateOf("") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(SuperfitTheme.colors.glassBorder, SuperfitTheme.colors.glassBorderGlow)),
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SuperfitTheme.colors.cardBgTranslucent
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // Header Row with squish-proof flex layout
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
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI TRAINER WORKOUT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = SuperfitTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ElectricCyan.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = recommendation.levelLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (onDisableClick != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onDisableClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hide Workout Suggestions",
                                tint = SuperfitTheme.colors.textTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = recommendation.splitTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SuperfitTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = recommendation.trainerNotes,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = SuperfitTheme.colors.textSecondary
            )

            if (recommendation.exercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))

                // Exercise List with Checkbox Toggles
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recommendation.exercises.forEach { exercise ->
                        val isChecked = selectedIds[exercise.id] ?: true
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChecked)
                                SuperfitTheme.colors.textPrimary.copy(alpha = 0.08f)
                            else
                                SuperfitTheme.colors.textPrimary.copy(alpha = 0.03f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedIds[exercise.id] = !isChecked }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isChecked) ElectricCyan else SuperfitTheme.colors.textTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = exercise.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isChecked) SuperfitTheme.colors.textPrimary else SuperfitTheme.colors.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "${exercise.sets} sets x ${exercise.repsOrDuration}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isChecked) ElectricCyan else SuperfitTheme.colors.textTertiary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val selectedCount = selectedIds.values.count { it }
                Button(
                    onClick = {
                        val selectedExercises = recommendation.exercises.filter { selectedIds[it.id] == true }
                        if (selectedExercises.isNotEmpty()) {
                            pendingWorkoutDesc = "${recommendation.splitTitle}: " + selectedExercises.joinToString(", ") { "${it.name} (${it.sets}x${it.repsOrDuration})" }
                            showRpeDialog = true
                        }
                    },
                    enabled = selectedCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (selectedCount == recommendation.exercises.size)
                            "LOG ALL EXERCISES (1-TAP)"
                        else
                            "LOG $selectedCount SELECTED EXERCISES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showRpeDialog) {
        AlertDialog(
            onDismissRequest = { showRpeDialog = false },
            containerColor = SuperfitTheme.colors.cardBg,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Session Feedback",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = SuperfitTheme.colors.textPrimary
                    )
                    IconButton(
                        onClick = { showRpeDialog = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SuperfitTheme.colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "How did that feel? Your AI Coach will calibrate future intensity based on your rating:",
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    RpeOptionCard(
                        title = "Too Easy",
                        subtitle = "Boost intensity & volume for next session",
                        icon = Icons.Default.TrendingUp,
                        iconTint = SuperfitTheme.colors.secondaryAccent,
                        onClick = {
                            onLogWorkout(pendingWorkoutDesc, "EASY")
                            showRpeDialog = false
                        }
                    )

                    RpeOptionCard(
                        title = "Just Right",
                        subtitle = "Optimal progression — maintain current load",
                        icon = Icons.Default.CheckCircle,
                        iconTint = SuperfitTheme.colors.primaryAccent,
                        onClick = {
                            onLogWorkout(pendingWorkoutDesc, "JUST_RIGHT")
                            showRpeDialog = false
                        }
                    )

                    RpeOptionCard(
                        title = "Tough Session",
                        subtitle = "Exhausting — scale down load next time",
                        icon = Icons.Default.LocalFireDepartment,
                        iconTint = CoralRed,
                        onClick = {
                            onLogWorkout(pendingWorkoutDesc, "HARD")
                            showRpeDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = null
        )
    }
}

@Composable
private fun RpeOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SuperfitTheme.colors.cardBgTranslucent,
        border = BorderStroke(1.dp, SuperfitTheme.colors.glassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuperfitTheme.colors.textPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = SuperfitTheme.colors.textSecondary,
                    lineHeight = 15.sp
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = SuperfitTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
