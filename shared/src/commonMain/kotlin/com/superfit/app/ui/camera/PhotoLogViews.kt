package com.superfit.app.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.IconBadge
import com.superfit.app.ui.dashboard.NeonGreen

/**
 * "Snap your plate" on iPhone: the meal, a packet's barcode, or its label, the same three as the
 * mode switch on Android's food camera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoKindSheet(onPick: (PhotoKind) -> Unit, onDismiss: () -> Unit) {
    val colors = SuperfitTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.cardBg
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Log with the camera", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            KindRow(Icons.Default.PhotoCamera, "Meal", "Snap your plate, best while you eat") { onPick(PhotoKind.MEAL) }
            KindRow(Icons.Default.QrCodeScanner, "Barcode", "Scan a packet's barcode") { onPick(PhotoKind.BARCODE) }
            KindRow(Icons.AutoMirrored.Filled.ReceiptLong, "Label", "Snap the nutrition table on a packet") { onPick(PhotoKind.LABEL) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KindRow(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    val colors = SuperfitTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.selectedBg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBadge(icon, background = colors.brandSoft, tint = colors.primaryAccent, size = 40)
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(hint, fontSize = 13.sp, color = colors.textSecondary)
        }
    }
}

/**
 * What happens after the picture or barcode: working, the check sheet, or why it didn't work,
 * with the same words as Android's food camera. [onRetake] opens the camera again in that kind.
 */
@Composable
fun PhotoLogOverlay(
    state: PhotoLogState,
    kind: PhotoKind,
    onLog: (entry: NutritionEntryEntity, isPackaged: Boolean) -> Unit,
    onRetake: (PhotoKind) -> Unit,
    onClose: () -> Unit
) {
    when (state) {
        PhotoLogState.Idle -> {}
        is PhotoLogState.Working -> Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = NeonGreen, strokeWidth = 3.dp)
                Text(text = state.message, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClose) { Text("Cancel", color = Color.White) }
            }
        }
        is PhotoLogState.Failed -> Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = state.message, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                Button(
                    onClick = { onRetake(state.retake) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White)
                ) {
                    Text(if (state.retake == PhotoKind.LABEL && kind == PhotoKind.BARCODE) "Snap the label" else "Retake", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onClose) { Text("Close", color = Color.White) }
            }
        }
        is PhotoLogState.MealResult -> CheckMealSheet(
            result = state.result,
            onLog = { onLog(it, false) },
            onRetake = { onRetake(kind) }
        )
        is PhotoLogState.PackResult -> CheckPackSheet(
            food = state.food,
            onLog = { onLog(it, true) },
            onRetake = { onRetake(kind) }
        )
    }
}
