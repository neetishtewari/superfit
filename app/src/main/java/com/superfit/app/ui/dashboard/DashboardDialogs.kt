package com.superfit.app.ui.dashboard


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.health.connect.client.PermissionController
import androidx.compose.animation.core.*
import com.superfit.app.theme.*
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.LocalDate
import android.text.format.DateUtils
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.provider.Settings
import android.net.Uri
import com.superfit.app.data.PredictedFood

@Composable
internal fun FoodHistoryDialog(
    foods: List<PredictedFood>,
    onDismiss: () -> Unit,
    onFillInput: (String) -> Unit,
    onLogNow: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredFoods = remember(foods, searchQuery) {
        if (searchQuery.isBlank()) {
            foods
        } else {
            foods.filter { it.foodText.contains(searchQuery, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "MY FOOD HISTORY",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = ThemeTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Search and select a food from your history to log or fill:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search meals, e.g. eggs...", color = ThemeTextSecondary.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = ThemeTextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = ThemeTextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = ThemeGlassBorder,
                        focusedTextColor = ThemeTextPrimary,
                        unfocusedTextColor = ThemeTextPrimary,
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = ThemeTextSecondary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (filteredFoods.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) 
                                "No foods logged yet. Keep tracking to build your history!" 
                                else "No matching meals found.",
                            fontSize = 13.sp,
                            color = ThemeTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredFoods) { food ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = ThemeCardBgTranslucent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = food.foodText,
                                            color = ThemeTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${food.calories.toInt()} kcal • ${food.proteinG.toInt()}g P • ${food.carbsG.toInt()}g C • ${food.fatG.toInt()}g F",
                                            color = ThemeTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { onFillInput(food.foodText) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Fill input",
                                                tint = ThemeTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onLogNow(food.foodText) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Log instantly",
                                                tint = NeonGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Close", color = NeonGreen, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = ThemeBgStart,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CoachChatSheet(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val chatLoading by viewModel.chatLoading.collectAsState()
    var chatInputText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ThemeCardBg,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = ThemeTextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.80f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💬 Chat with Coach",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ThemeTextPrimary
                )
                IconButton(
                    onClick = { viewModel.clearChat() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Clear Chat",
                        tint = ThemeTextSecondary
                    )
                }
            }

            // Messages list
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                val lazyListState = rememberLazyListState()
                LaunchedEffect(chatMessages.size) {
                    if (chatMessages.isNotEmpty()) {
                        lazyListState.animateScrollToItem(chatMessages.size - 1)
                    }
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatMessages) { message ->
                        val isUser = message.sender == MessageSender.User
                        val alignment = if (isUser) Alignment.End else Alignment.Start
                        val bubbleColor = if (isUser) HyperVioletAccent.copy(alpha = 0.25f) else ThemeTextPrimary.copy(alpha = 0.05f)
                        val borderColor = if (isUser) HyperVioletAccent else ThemeGlassBorder

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = alignment
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isUser) 12.dp else 2.dp,
                                            bottomEnd = if (isUser) 2.dp else 12.dp
                                        )
                                    )
                                    .background(bubbleColor)
                                    .border(
                                        width = 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isUser) 12.dp else 2.dp,
                                            bottomEnd = if (isUser) 2.dp else 12.dp
                                        )
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = message.text,
                                    color = ThemeTextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            Text(
                                text = if (isUser) "You" else "Coach",
                                color = ThemeTextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }

                    if (chatLoading) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = NeonGreen,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Coach is thinking...",
                                    color = ThemeTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Input panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = chatInputText,
                    onValueChange = { chatInputText = it },
                    placeholder = { Text("Ask Coach about diet or recovery...", fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = ThemeGlassBorder,
                        focusedTextColor = ThemeTextPrimary,
                        unfocusedTextColor = ThemeTextPrimary,
                        focusedPlaceholderColor = ThemeTextSecondary,
                        unfocusedPlaceholderColor = ThemeTextSecondary
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (chatInputText.isNotBlank()) {
                            viewModel.sendChatMessage(chatInputText)
                            chatInputText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    enabled = !chatLoading && chatInputText.isNotBlank(),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Send", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
