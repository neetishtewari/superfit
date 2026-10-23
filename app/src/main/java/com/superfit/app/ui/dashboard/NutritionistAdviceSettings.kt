package com.superfit.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.NutritionAdviceStore
import com.superfit.app.domain.AdviceTargets
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme
import kotlinx.coroutines.launch

/**
 * "My nutritionist's advice": the user's own guidance in plain words. Meal ratings, the weekly
 * Diet Quality note and the coach follow it. Numbers in it become targets only after the user
 * confirms them.
 */
@Composable
fun NutritionistAdviceSettings(viewModel: DashboardViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(NutritionAdviceStore.advice(context)) }
    var text by remember { mutableStateOf(saved) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var proposed by remember { mutableStateOf<AdviceTargets?>(null) }
    var applied by remember { mutableStateOf(NutritionAdviceStore.targets()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "My nutritionist's advice",
            fontWeight = FontWeight.Bold,
            color = SuperfitTheme.colors.textPrimary,
            fontSize = 14.sp
        )
        Text(
            text = "Write what your nutritionist told you, in your own words. Meal ratings, Diet Quality and the coach will follow it.",
            color = SuperfitTheme.colors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(2000) },
            placeholder = { Text("e.g. Protein in every meal: eggs, paneer, dal or curd. 2 squares of 70% dark chocolate are fine.", fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = SuperfitTheme.colors.textPrimary)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = status.orEmpty(),
                color = SuperfitTheme.colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            Button(
                enabled = !saving && text.trim() != saved.trim(),
                onClick = {
                    saving = true
                    status = "Saving…"
                    scope.launch {
                        val targets = viewModel.saveNutritionistAdvice(text)
                        saved = text.trim()
                        saving = false
                        status = "Saved. Your recent meals will be rated again with it."
                        if (targets != null && targets != applied) proposed = targets
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonMint)
            ) {
                Text("Save", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        applied?.let { t ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your nutritionist's targets in use: ${t.describe()}",
                    color = SuperfitTheme.colors.textPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    viewModel.applyNutritionistTargets(null)
                    applied = null
                }) { Text("Stop using", fontSize = 12.sp) }
            }
        }
    }

    proposed?.let { t ->
        AlertDialog(
            onDismissRequest = { proposed = null },
            title = { Text("Use your nutritionist's targets?") },
            text = {
                Text(
                    "Your advice gives these daily targets: ${t.describe()}.\n\n" +
                        "Superfit will use them in place of its own. Anything not listed stays as Superfit works it out."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyNutritionistTargets(t)
                    applied = t
                    proposed = null
                }) { Text("Use them") }
            },
            dismissButton = {
                TextButton(onClick = { proposed = null }) { Text("Not now") }
            }
        )
    }
}
