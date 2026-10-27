package com.superfit.app.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.domain.PersonalBest
import com.superfit.app.domain.ShareCard
import com.superfit.app.domain.ShareCards
import com.superfit.app.theme.HyperViolet
import com.superfit.app.theme.SuperfitTheme
import java.time.LocalDate

/** The daily card from what Home is showing right now. Never calls the AI. */
fun dailyShareCard(state: DashboardUiState.Success, coaching: CoachingInsightState): ShareCard.Daily =
    ShareCards.daily(
        date = LocalDate.now(),
        streakDays = state.streakState.currentStreak,
        mealsToday = state.nutritionList.size,
        proteinEatenG = state.proteinEaten,
        proteinGoalG = state.macroTargets.proteinG,
        dietScore = state.dietQualityMetrics.overallScore.takeIf { state.dietQualityMetrics.hasEnoughData },
        steps = state.activity.steps,
        workout = ShareCards.dayWorkout(state.importedWorkouts, state.workoutList),
        coachLine = (coaching as? CoachingInsightState.Success)?.insight?.headline
    )

/** "New personal best: Longest run, 12.40 km" with Share and a close button. Shows once per best. */
@Composable
fun NewBestCard(best: PersonalBest, onShare: () -> Unit, onDismiss: () -> Unit) {
    val card = ShareCards.best(best, isNew = true, today = LocalDate.now())
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuperfitTheme.colors.cardBgTranslucent),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(listOf(HyperViolet.copy(alpha = 0.6f), SuperfitTheme.colors.glassBorderGlow)),
                RoundedCornerShape(20.dp)
            )
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = HyperViolet, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("New personal best", fontSize = 12.sp, color = HyperViolet, fontWeight = FontWeight.Bold)
                Text(
                    "${card.label}, ${card.bigValue} ${card.bigUnit}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuperfitTheme.colors.textPrimary
                )
                card.previous?.let {
                    Text("Previous best $it", fontSize = 12.sp, color = SuperfitTheme.colors.textSecondary)
                }
            }
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = HyperViolet, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Share", fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SuperfitTheme.colors.textSecondary)
            }
        }
    }
}

/** Settings list of every personal best, each shareable any time. */
@Composable
fun PersonalBestsList(bests: List<PersonalBest>, onShare: (PersonalBest) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (bests.isEmpty()) {
            Text(
                "Your bests appear here once you have a few workouts from other apps or a meal streak going.",
                fontSize = 12.sp,
                color = SuperfitTheme.colors.textSecondary
            )
        }
        bests.forEach { best ->
            val card = ShareCards.best(best, isNew = false, today = LocalDate.now())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(card.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary)
                    Text(
                        if (best.workout != null) "${card.bigValue} ${card.bigUnit} · ${card.dateLine}" else "${card.bigValue} ${card.bigUnit}",
                        fontSize = 12.sp,
                        color = SuperfitTheme.colors.textSecondary
                    )
                }
                TextButton(onClick = { onShare(best) }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}
