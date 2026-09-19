/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.lockscreen

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.android.settings.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class CustomizerTab {
    STYLE,
    COLOUR,
    MISC
}

private val PRESET_SWATCHES = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFCFD8DC),
    Color(0xFF90A4AE),
    Color(0xFFFF8A80),
    Color(0xFFFFAB91),
    Color(0xFFFFD54F),
    Color(0xFFA5D6A7),
    Color(0xFF80DEEA),
    Color(0xFFCE93D8)
)

private val RAINBOW_BRUSH = Brush.sweepGradient(
    listOf(
        Color(0xFFFF0000),
        Color(0xFFFF8800),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000)
    )
)

@Composable
fun ClockCustomizerScreen() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ClockCustomizerState.load(context)) }
    var selectedTab by rememberSaveable { mutableStateOf(CustomizerTab.STYLE) }

    LaunchedEffect(state) {
        delay(250)
        ClockCustomizerState.save(context, state)
    }

    var colorPickerTarget by remember { mutableStateOf<String?>(null) }

    val wallpaperBitmap: Bitmap? = remember {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            (wm.getDrawable(WallpaperManager.FLAG_LOCK) ?: wm.getDrawable(WallpaperManager.FLAG_SYSTEM))
                ?.toBitmap(480, 960)
        }.getOrNull()
    }

    val hasStyleChange = state.style != state.initialStyle

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                PhoneMockupPreview(
                    wallpaper = wallpaperBitmap,
                    state = state
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = hasStyleChange,
                    enter = fadeIn() + slideInHorizontally { it / 2 },
                    exit = fadeOut() + slideOutHorizontally { it / 2 },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            ClockCustomizerState.applyAndRestart(context, state)
                            state = state.copy(initialStyle = state.style)
                        },
                        icon = { Icon(Icons.Default.Check, contentDescription = null) },
                        text = { Text(stringResource(R.string.apply)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInHorizontally { width -> width / 3 } + fadeIn())
                                    .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width / 3 } + fadeIn())
                                    .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut())
                            }
                        },
                        label = "tab_content_animation"
                    ) { currentTab ->
                        when (currentTab) {
                            CustomizerTab.STYLE -> {
                                StyleTabContent(
                                    state = state,
                                    onStateChange = { state = it }
                                )
                            }
                            CustomizerTab.COLOUR -> {
                                ColourTabContent(
                                    state = state,
                                    onStateChange = { state = it },
                                    onOpenColorPicker = { target -> colorPickerTarget = target }
                                )
                            }
                            CustomizerTab.MISC -> {
                                MiscTabContent(
                                    state = state,
                                    onStateChange = { state = it }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                FloatingBottomTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        colorPickerTarget?.let { target ->
            val initialHex = when (target) {
                "custom" -> String.format("%06X", state.customColor and 0x00FFFFFF)
                "grad_start" -> String.format("%06X", state.gradientColorStart and 0x00FFFFFF)
                "grad_end" -> String.format("%06X", state.gradientColorEnd and 0x00FFFFFF)
                else -> "FFFFFF"
            }
            ClockColorPickerDialog(
                initialColor = initialHex,
                onDismiss = { colorPickerTarget = null },
                onColorSelected = { color ->
                    val argb = color.toArgb()
                    state = when (target) {
                        "custom" -> state.copy(customColor = argb, colorMode = ClockCustomizerState.COLOR_MODE_CUSTOM)
                        "grad_start" -> state.copy(gradientColorStart = argb)
                        "grad_end" -> state.copy(gradientColorEnd = argb)
                        else -> state
                    }
                    colorPickerTarget = null
                }
            )
        }
    }
}

@Composable
private fun PhoneMockupPreview(
    wallpaper: Bitmap?,
    state: ClockCustomizerState
) {
    val dateStr = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }
    val weatherStr = "30°C • Heavy Rain Showers"

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(9f / 19.5f)
            .shadow(12.dp, shape = RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
            .background(Color.Black),
        contentAlignment = Alignment.TopCenter
    ) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF00687A), Color(0xFF003640))
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 28.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (state.style == 0) {
                GoogleStockClockPreview(dateStr, weatherStr, state.weatherEnabled)
            } else {
                CustomClockViewPreview(state, weatherStr)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_flashlight),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.settings_fp_quick_launch_ic_inner),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_camera),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun GoogleStockClockPreview(
    dateStr: String,
    weatherStr: String,
    weatherEnabled: Boolean
) {
    val timeHour = remember { SimpleDateFormat("HH", Locale.getDefault()).format(Date()) }
    val timeMinute = remember { SimpleDateFormat("mm", Locale.getDefault()).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dateStr,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color.White.copy(alpha = 0.9f)
        )
        if (weatherEnabled) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = weatherStr,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = timeHour,
            fontSize = 62.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 56.sp
        )
        Text(
            text = timeMinute,
            fontSize = 62.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 56.sp
        )
    }
}

@Composable
private fun CustomClockViewPreview(
    state: ClockCustomizerState,
    weatherStr: String
) {
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                clipChildren = false
                clipToPadding = false
                isClickable = false
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        update = { container ->
            container.removeAllViews()
            val layoutRes = ClockUtils.CLOCK_LAYOUTS.getOrElse(state.style) { ClockUtils.CLOCK_LAYOUTS[0] }
            val clockView = LayoutInflater.from(container.context).inflate(layoutRes, container, false)
            container.addView(clockView)

            val baseScale = 0.85f
            val scaleFactor = (state.scale / 100f) * baseScale
            clockView.scaleX = scaleFactor
            clockView.scaleY = scaleFactor

            clockView.alpha = state.opacity / 100f

            val density = container.resources.displayMetrics.density
            clockView.translationY = state.marginTop * density * 0.7f
            clockView.translationX = state.marginStart * density * 0.7f

            applyClockColors(clockView, state)

            fun injectWeatherIntoView(v: View) {
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) injectWeatherIntoView(v.getChildAt(i))
                }
                if (v is TextView) {
                    val idName = runCatching {
                        v.resources.getResourceEntryName(v.id)
                    }.getOrNull() ?: return
                    if (idName.contains("weather", ignoreCase = true) && !idName.contains("image", ignoreCase = true)) {
                        if (state.weatherEnabled) {
                            v.text = weatherStr
                            v.visibility = View.VISIBLE
                        } else {
                            v.visibility = View.GONE
                        }
                    }
                }
            }
            injectWeatherIntoView(clockView)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun applyClockColors(view: View, state: ClockCustomizerState) {
    if (ClockCustomizerState.isNoColorClock(state.style)) {
        return
    }

    val textViews = mutableListOf<TextView>()
    fun collectTextViews(v: View) {
        if (v is TextView) textViews.add(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                collectTextViews(v.getChildAt(i))
            }
        }
    }
    collectTextViews(view)

    val whiteColor = view.context.getColor(android.R.color.white)
    val isGradient = state.gradientEnabled || state.colorMode == ClockCustomizerState.COLOR_MODE_GRADIENT

    for (tv in textViews) {
        val originalColor = tv.currentTextColor
        val isWhiteOriginal = (originalColor and 0x00FFFFFF) == (whiteColor and 0x00FFFFFF)

        if (!isWhiteOriginal) continue

        if (isGradient) {
            val w = if (view.width > 0) view.width.toFloat() else 350f
            val h = if (view.height > 0) view.height.toFloat() else 250f
            val cx = w / 2f
            val cy = h * (state.gradientAnchorY / 100f)
            val len = (h / 2f) * (state.gradientRadius / 100f)
            val y0 = cy - len
            val y1 = cy + len

            val shader = LinearGradient(
                cx, y0, cx, y1,
                state.gradientColorStart,
                state.gradientColorEnd,
                Shader.TileMode.CLAMP
            )
            tv.paint.shader = shader
            tv.setTextColor(android.graphics.Color.WHITE)
        } else when (state.colorMode) {
            ClockCustomizerState.COLOR_MODE_ACCENT -> {
                tv.paint.shader = null
                tv.setTextColor(state.customColor)
            }
            ClockCustomizerState.COLOR_MODE_CUSTOM -> {
                tv.paint.shader = null
                tv.setTextColor(state.customColor)
            }
            else -> {
                tv.paint.shader = null
            }
        }
        tv.invalidate()
    }
}

@Composable
private fun StyleTabContent(
    state: ClockCustomizerState,
    onStateChange: (ClockCustomizerState) -> Unit
) {
    val clockLayouts = ClockUtils.CLOCK_LAYOUTS
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.style.coerceAtLeast(0))

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.clock_face_scale),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${state.scale}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = state.scale.toFloat(),
            onValueChange = { onStateChange(state.copy(scale = it.roundToInt())) },
            valueRange = 50f..150f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.custom_clock_weather_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = state.weatherEnabled,
                onCheckedChange = { onStateChange(state.copy(weatherEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(clockLayouts.toList()) { index, layoutRes ->
                val isSelected = index == state.style
                ClockFaceCard(
                    index = index,
                    layoutRes = layoutRes,
                    isSelected = isSelected,
                    onClick = {
                        onStateChange(state.copy(style = index))
                    }
                )
            }
        }
    }
}

@Composable
private fun ClockFaceCard(
    index: Int,
    layoutRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "card_border"
    )

    Box(
        modifier = Modifier
            .size(90.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = if (isSelected) 2.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isSelected) Color(0xFF1A2030)
                    else Color(0xFF111520)
                )
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (index == 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_date_time),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.default_value),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            clipChildren = false
                            clipToPadding = false
                            isClickable = false
                            isFocusable = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            LayoutInflater.from(ctx).inflate(layoutRes, this, true)
                            if (childCount > 0) {
                                val child = getChildAt(0)
                                child.scaleX = 0.30f
                                child.scaleY = 0.30f
                                fun hideWeather(v: View) {
                                    if (v is ViewGroup) for (i in 0 until v.childCount) hideWeather(v.getChildAt(i))
                                    if (v is TextView) {
                                        val idName = runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull() ?: return
                                        if (idName.contains("weather", ignoreCase = true)) v.visibility = View.GONE
                                    }
                                }
                                hideWeather(getChildAt(0))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

private fun colorModeOrdinal(state: ClockCustomizerState): Int = when {
    state.gradientEnabled || state.colorMode == ClockCustomizerState.COLOR_MODE_GRADIENT -> 2
    state.colorMode == ClockCustomizerState.COLOR_MODE_CUSTOM -> 1
    else -> 0
}

@Composable
private fun ColourTabContent(
    state: ClockCustomizerState,
    onStateChange: (ClockCustomizerState) -> Unit,
    onOpenColorPicker: (String) -> Unit
) {
    val isNoColor = ClockCustomizerState.isNoColorClock(state.style)
    val monetAccents = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.inversePrimary
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        if (isNoColor) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "This clock style uses its own brand colors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.clock_colour),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorModePill(
                title = stringResource(R.string.clock_colour_accent),
                isSelected = state.colorMode == ClockCustomizerState.COLOR_MODE_ACCENT && !state.gradientEnabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    onStateChange(state.copy(
                        colorMode = ClockCustomizerState.COLOR_MODE_ACCENT,
                        gradientEnabled = false
                    ))
                }
            )
            ColorModePill(
                title = stringResource(R.string.clock_colour_custom),
                isSelected = state.colorMode == ClockCustomizerState.COLOR_MODE_CUSTOM && !state.gradientEnabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    onStateChange(state.copy(
                        colorMode = ClockCustomizerState.COLOR_MODE_CUSTOM,
                        gradientEnabled = false
                    ))
                }
            )
            ColorModePill(
                title = stringResource(R.string.clock_colour_gradient),
                isSelected = state.gradientEnabled || state.colorMode == ClockCustomizerState.COLOR_MODE_GRADIENT,
                modifier = Modifier.weight(1f),
                onClick = {
                    onStateChange(state.copy(
                        colorMode = ClockCustomizerState.COLOR_MODE_GRADIENT,
                        gradientEnabled = true
                    ))
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(
            targetState = colorModeOrdinal(state),
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { width -> -width / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut())
                }
            },
            label = "colour_mode_animation"
        ) { colourOrdinal ->
            when (colourOrdinal) {
                2 -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.clock_primary_color),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ColorPickerRow(
                            selectedColor = Color(state.gradientColorStart),
                            onRainbowClick = { onOpenColorPicker("grad_start") },
                            onColorClick = { onStateChange(state.copy(gradientColorStart = it.toArgb())) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.clock_secondary_color),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ColorPickerRow(
                            selectedColor = Color(state.gradientColorEnd),
                            onRainbowClick = { onOpenColorPicker("grad_end") },
                            onColorClick = { onStateChange(state.copy(gradientColorEnd = it.toArgb())) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.clock_gradient_anchor),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${state.gradientAnchorY}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = state.gradientAnchorY.toFloat(),
                            onValueChange = { onStateChange(state.copy(gradientAnchorY = it.roundToInt())) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.clock_gradient_spread),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${state.gradientRadius}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = state.gradientRadius.toFloat(),
                            onValueChange = { onStateChange(state.copy(gradientRadius = it.roundToInt())) },
                            valueRange = 25f..200f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }

                1 -> {
                    ColorPickerRow(
                        selectedColor = Color(state.customColor),
                        onRainbowClick = { onOpenColorPicker("custom") },
                        onColorClick = { onStateChange(state.copy(customColor = it.toArgb())) }
                    )
                }

                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        monetAccents.forEach { color ->
                            val isSelected = state.customColor == color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onStateChange(state.copy(
                                            customColor = color.toArgb(),
                                            colorMode = ClockCustomizerState.COLOR_MODE_ACCENT
                                        ))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.clock_album_art_color_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = state.albumArtColor,
                onCheckedChange = { onStateChange(state.copy(albumArtColor = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun ColorPickerRow(
    selectedColor: Color,
    onRainbowClick: () -> Unit,
    onColorClick: (Color) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(RAINBOW_BRUSH)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onRainbowClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f))
            )
        }

        PRESET_SWATCHES.forEach { color ->
            val isSelected = selectedColor == color
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable { onColorClick(color) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (color == Color.White) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorModePill(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "color_mode_pill_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "color_mode_pill_text"
    )
    val border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null

    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = border
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

@Composable
private fun MiscTabContent(
    state: ClockCustomizerState,
    onStateChange: (ClockCustomizerState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.clock_adjustments),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.clock_opacity),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${state.opacity}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = state.opacity.toFloat(),
            onValueChange = { onStateChange(state.copy(opacity = it.roundToInt())) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.clock_top_margin),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${state.marginTop} dp",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = state.marginTop.toFloat(),
            onValueChange = { onStateChange(state.copy(marginTop = it.roundToInt())) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.clock_start_margin),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${state.marginStart} dp",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = state.marginStart.toFloat(),
            onValueChange = { onStateChange(state.copy(marginStart = it.roundToInt())) },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.lockscreen_custom_clock_wobble_on_charge_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = state.wobbleOnCharge,
                onCheckedChange = { onStateChange(state.copy(wobbleOnCharge = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.lockscreen_custom_clock_aod_anim_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = state.aodAnim,
                onCheckedChange = { onStateChange(state.copy(aodAnim = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun FloatingBottomTabs(
    selectedTab: CustomizerTab,
    onTabSelected: (CustomizerTab) -> Unit
) {
    Surface(
        modifier = Modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingTabItem(
                title = stringResource(R.string.clock_tab_style),
                painter = painterResource(R.drawable.ic_settings_date_time),
                isSelected = selectedTab == CustomizerTab.STYLE,
                onClick = { onTabSelected(CustomizerTab.STYLE) }
            )
            FloatingTabItem(
                title = stringResource(R.string.clock_tab_colour),
                painter = painterResource(R.drawable.ic_settings_accent),
                isSelected = selectedTab == CustomizerTab.COLOUR,
                onClick = { onTabSelected(CustomizerTab.COLOUR) }
            )
            FloatingTabItem(
                title = stringResource(R.string.clock_tab_misc),
                painter = painterResource(R.drawable.ic_settings_24dp),
                isSelected = selectedTab == CustomizerTab.MISC,
                onClick = { onTabSelected(CustomizerTab.MISC) }
            )
        }
    }
}

@Composable
private fun FloatingTabItem(
    title: String,
    painter: Painter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "floating_tab_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "floating_tab_content_color"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
