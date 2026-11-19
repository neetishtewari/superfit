package com.superfit.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.Now
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.domain.AboutYou
import com.superfit.app.domain.ActivityLevels
import com.superfit.app.domain.BirthMonth
import com.superfit.app.domain.BodyReadings
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.BirthMonthInput
import com.superfit.app.ui.components.ChoiceGrid
import com.superfit.app.ui.components.HeightInput
import com.superfit.app.ui.components.ProfileFieldLabel

/**
 * About you: sex, birth month, height and how active a normal week is, as given at sign-up, with
 * an Edit that shows what saving would do to the daily target. Weight isn't here: weigh-ins keep
 * it up to date. [ownTargets] is true when the person set their own calories and macros.
 */
@Composable
fun AboutYouCard(profile: UserProfileEntity, ownTargets: Boolean, onSave: (UserProfileEntity) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    SettingsCard(spacing = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("About you", fontWeight = FontWeight.Bold, color = SuperfitTheme.colors.textPrimary, fontSize = 14.sp)
                Text(AboutYou.summary(profile), color = SuperfitTheme.colors.textSecondary, fontSize = 12.sp)
            }
            if (!editing) {
                TextButton(onClick = { editing = true }) {
                    Text("Edit", color = NeonMint, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!editing && profile.birthYearMonth == 0) {
            Text(
                "Add your birth month so your age stays up to date.",
                color = SuperfitTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
        if (editing) {
            AboutYouEditor(
                profile = profile,
                ownTargets = ownTargets,
                onCancel = { editing = false },
                onSave = {
                    onSave(it)
                    editing = false
                }
            )
        }
    }
}

@Composable
private fun AboutYouEditor(
    profile: UserProfileEntity,
    ownTargets: Boolean,
    onCancel: () -> Unit,
    onSave: (UserProfileEntity) -> Unit
) {
    val given = profile.birthYearMonth.takeIf { it > 0 }
    var isMale by remember { mutableStateOf(profile.isMale) }
    var month by remember { mutableStateOf(given?.let(BirthMonth::month)) }
    var year by remember { mutableStateOf(given?.let { BirthMonth.year(it).toString() } ?: "") }
    var height by remember {
        mutableStateOf(profile.heightCm.let { if (it % 1.0 == 0.0) it.toInt().toString() else BodyReadings.fmt(it) })
    }
    var activity by remember { mutableStateOf(ActivityLevels.closest(profile.activityMultiplier)) }
    var tried by remember { mutableStateOf(false) }

    val today = Now.today()
    // Someone who signed up with a typed age can still fix just their height: the birth month is
    // only checked once they start filling it in.
    val keepTypedAge = given == null && month == null && year.isEmpty()
    val birthProblem = if (keepTypedAge) null else BirthMonth.problem(year.toIntOrNull(), month, today)
    val heightProblem = AboutYou.heightProblem(height)
    val updated = if (birthProblem == null && heightProblem == null) {
        val withBirth = if (keepTypedAge) {
            profile
        } else {
            val birth = BirthMonth.encode(year.toInt(), month!!)
            profile.copy(birthYearMonth = birth, age = BirthMonth.ageOn(birth, today))
        }
        withBirth.copy(isMale = isMale, heightCm = height.toDouble(), activityMultiplier = activity)
    } else {
        null
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileFieldLabel("Sex (used only for the calorie formula)")
            ChoiceGrid(options = listOf(true to "Male", false to "Female"), selected = isMale, onSelect = { isMale = it })
        }
        BirthMonthInput(
            month = month,
            year = year,
            onMonth = { month = it },
            onYear = { year = it },
            // Only point at the year once it has 4 digits, or after they try to save.
            error = birthProblem?.takeIf { tried || (month != null && year.length == 4) }
        )
        if (keepTypedAge) {
            Text(
                "You told us you were ${profile.age} when you signed up. Your birth month keeps it right from now on.",
                color = SuperfitTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
        HeightInput(heightCm = height, onHeightCm = { height = it }, isError = tried && heightProblem != null)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileFieldLabel("How active is a normal week?")
            ChoiceGrid(
                options = ActivityLevels.map { it.multiplier to it.label },
                selected = activity,
                onSelect = { activity = it }
            )
            Text(ActivityLevels.describe(activity), fontSize = 12.sp, color = SuperfitTheme.colors.textSecondary)
        }

        if (updated != null) {
            Text(
                AboutYou.preview(profile, updated, ownTargets),
                color = SuperfitTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel", color = SuperfitTheme.colors.textSecondary)
            }
            Button(
                onClick = {
                    tried = true
                    updated?.let(onSave)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonMint),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
