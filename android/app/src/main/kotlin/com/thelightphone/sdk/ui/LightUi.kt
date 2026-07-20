/*
 * Vendored, dependency-minimized subset of Light SDK UI 0.0.12.
 * Copyright (c) 2026 The Light Phone, licensed under the MIT License.
 * The published sdk-ui artifact is not available from Maven Central, so this
 * normal Android app carries only the primitives it uses.
 */
package com.thelightphone.sdk.ui

import android.content.Context
import android.graphics.fonts.SystemFonts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class LightColors(val background: Color, val content: Color, val contentSecondary: Color)

@Immutable
data class LightTypography(
    val title: TextStyle,
    val subtitle: TextStyle,
    val heading: TextStyle,
    val subheading: TextStyle,
    val copy: TextStyle,
    val button: TextStyle,
    val paragraph: TextStyle,
    val paragraphWide: TextStyle,
    val detail: TextStyle,
    val fine: TextStyle,
    val superfine: TextStyle,
    val micro: TextStyle,
)

object LightThemeColors {
    val Dark = LightColors(Color.Black, Color.White, Color(0xFFBBBBBB))
    val Light = LightColors(Color.White, Color.Black, Color(0xFF666666))
}

private fun typography(font: FontFamily) = LightTypography(
    title = TextStyle(fontSize = 115.sp, fontFamily = font, fontWeight = FontWeight.Light, lineHeight = 126.5.sp),
    subtitle = TextStyle(fontSize = 52.sp, fontFamily = font, lineHeight = 62.4.sp),
    heading = TextStyle(fontSize = 38.sp, fontFamily = font, lineHeight = 51.3.sp),
    subheading = TextStyle(fontSize = 30.sp, fontFamily = font, letterSpacing = .9.sp, lineHeight = 37.5.sp),
    copy = TextStyle(fontSize = 30.sp, fontFamily = font, lineHeight = 45.sp),
    button = TextStyle(fontSize = 30.sp, fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = 4.5.sp, lineHeight = 33.sp),
    paragraph = TextStyle(fontSize = 24.5.sp, fontFamily = font, lineHeight = 30.625.sp),
    paragraphWide = TextStyle(fontSize = 25.sp, fontFamily = font, letterSpacing = .5.sp, lineHeight = 32.5.sp),
    detail = TextStyle(fontSize = 20.sp, fontFamily = font, lineHeight = 29.sp),
    fine = TextStyle(fontSize = 25.sp, fontFamily = font, letterSpacing = .75.sp, lineHeight = 28.75.sp),
    superfine = TextStyle(fontSize = 16.sp, fontFamily = font, lineHeight = 19.2.sp),
    micro = TextStyle(fontSize = 8.sp, fontFamily = font, lineHeight = 9.6.sp),
)

private fun lightFontFamily(context: Context): FontFamily {
    val fonts = SystemFonts.getAvailableFonts()
        .filter { it.file?.name?.startsWith("Akkurat", ignoreCase = true) == true }
        .mapNotNull { systemFont ->
            val file = systemFont.file ?: return@mapNotNull null
            Font(file, FontWeight(systemFont.style.weight), if (systemFont.style.slant != 0) FontStyle.Italic else FontStyle.Normal)
        }
    return if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
}

private val LocalLightColors = staticCompositionLocalOf { LightThemeColors.Dark }
private val LocalLightTypography = staticCompositionLocalOf { typography(FontFamily.Default) }

object LightThemeTokens {
    val colors: LightColors @Composable get() = LocalLightColors.current
    val typography: LightTypography @Composable get() = LocalLightTypography.current
}

object LightThemeController {
    private val mutableColors = MutableStateFlow(LightThemeColors.Dark)
    val colors = mutableColors.asStateFlow()
    fun setDarkTheme() { mutableColors.value = LightThemeColors.Dark }
    fun setLightTheme() { mutableColors.value = LightThemeColors.Light }
    fun toggle() { mutableColors.value = if (mutableColors.value == LightThemeColors.Dark) LightThemeColors.Light else LightThemeColors.Dark }
}

@Composable
fun LightTheme(colors: LightColors = LightThemeColors.Dark, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val type = remember(context) { typography(lightFontFamily(context)) }
    val scheme = if (colors.background.luminance() > .5f) lightColorScheme() else darkColorScheme()
    CompositionLocalProvider(LocalLightColors provides colors, LocalLightTypography provides type) {
        MaterialTheme(
            colorScheme = scheme.copy(
                background = colors.background,
                surface = colors.background,
                onBackground = colors.content,
                onSurface = colors.content,
                primary = colors.content,
            ),
            content = content,
        )
    }
}

object LightGrid { const val WIDTH = 27; const val HEIGHT = 31 }

@Composable fun Float.gridUnitsAsDp(): Dp = (LocalConfiguration.current.screenWidthDp.toFloat() / LightGrid.WIDTH * this).dp
@Composable private fun TextUnit.scaled(): TextUnit = if (this == TextUnit.Unspecified) this else (value * LocalConfiguration.current.screenHeightDp / 600f).sp
@Composable private fun TextStyle.scaled() = copy(fontSize = fontSize.scaled(), lineHeight = lineHeight.scaled(), letterSpacing = letterSpacing.scaled())

enum class LightTextVariant { Title, Subtitle, Heading, Subheading, Copy, Button, Paragraph, ParagraphWide, Detail, Fine, Superfine, Micro }

@Composable
fun LightText(
    text: String,
    variant: LightTextVariant,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    lighten: Boolean = false,
    underline: Boolean = false,
    monospace: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    color: Color? = null,
) {
    val t = LightThemeTokens.typography
    val base = when (variant) {
        LightTextVariant.Title -> t.title
        LightTextVariant.Subtitle -> t.subtitle
        LightTextVariant.Heading -> t.heading
        LightTextVariant.Subheading -> t.subheading
        LightTextVariant.Copy -> t.copy
        LightTextVariant.Button -> t.button
        LightTextVariant.Paragraph -> t.paragraph
        LightTextVariant.ParagraphWide -> t.paragraphWide
        LightTextVariant.Detail -> t.detail
        LightTextVariant.Fine -> t.fine
        LightTextVariant.Superfine -> t.superfine
        LightTextVariant.Micro -> t.micro
    }
    Text(
        text,
        modifier,
        color ?: if (lighten) LightThemeTokens.colors.contentSecondary else LightThemeTokens.colors.content,
        style = base.scaled()
            .let { if (align != null) it.copy(textAlign = align) else it }
            .let { if (underline) it.copy(textDecoration = TextDecoration.Underline) else it }
            .let { if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it },
        maxLines = maxLines,
        overflow = overflow,
    )
}

fun Modifier.lightClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
) = clickable(interactionSource = null, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)

sealed interface LightBarButton {
    val onClick: (() -> Unit)?
    val contentDescription: String?
    data class Text(val text: String, override val contentDescription: String? = null, override val onClick: (() -> Unit)?) : LightBarButton
    data class LightIcon(
        val icon: LightIconConfiguration,
        override val onClick: (() -> Unit)?,
        override val contentDescription: String? = icon.name,
        val sizeUnits: Float = 2f,
    ) : LightBarButton
}
typealias LightTopBarButton = LightBarButton
typealias LightBottomBarItem = LightBarButton

sealed class LightIconConfiguration(val name: String, val resource: Int)
object LightIcons {
    object BACK : LightIconConfiguration("back", com.stan.lightphotobackup.R.drawable.ic_light_back)
    object TOGGLE_OFF : LightIconConfiguration("toggle off", com.stan.lightphotobackup.R.drawable.ic_light_toggle_off)
    object TOGGLE_ON : LightIconConfiguration("toggle on", com.stan.lightphotobackup.R.drawable.ic_light_toggle_on)
    object SELECT_OFF : LightIconConfiguration("not selected", com.stan.lightphotobackup.R.drawable.ic_light_select_off)
    object SELECT_ON : LightIconConfiguration("selected", com.stan.lightphotobackup.R.drawable.ic_light_select_on)
}

@Composable
fun LightIcon(
    icon: LightIconConfiguration,
    modifier: Modifier = Modifier,
    size: Float = 2f,
    contentDescription: String? = icon.name,
) {
    Icon(
        painter = painterResource(icon.resource),
        contentDescription = contentDescription,
        tint = LightThemeTokens.colors.content,
        modifier = modifier.size(size.gridUnitsAsDp()),
    )
}

@Composable
private fun LightBarButtonView(button: LightBarButton?, heightUnits: Float, useSpacerWhenNull: Boolean) {
    if (button == null) {
        if (useSpacerWhenNull) Spacer(Modifier.size(2f.gridUnitsAsDp()))
        return
    }
    when (button) {
        is LightBarButton.Text -> Box(
            Modifier.height(heightUnits.gridUnitsAsDp()).lightClickable(onClickLabel = button.contentDescription, onClick = button.onClick ?: {}),
            contentAlignment = Alignment.Center,
        ) { LightText(button.text, LightTextVariant.Button, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        is LightBarButton.LightIcon -> LightIcon(
            icon = button.icon,
            size = button.sizeUnits,
            contentDescription = button.contentDescription,
            modifier = Modifier.lightClickable(
                onClickLabel = button.contentDescription,
                onClick = button.onClick ?: {},
            ),
        )
    }
}

sealed interface LightTopBarCenter {
    val onClick: (() -> Unit)?
    data class Text(val text: String, override val onClick: (() -> Unit)? = null) : LightTopBarCenter
}

@Composable
fun LightTopBar(
    leftButton: LightTopBarButton? = null,
    center: LightTopBarCenter? = null,
    rightButton: LightTopBarButton? = null,
    modifier: Modifier = Modifier,
) {
    val height = 3f.gridUnitsAsDp()
    Box(modifier.fillMaxWidth().height(height).padding(horizontal = 1f.gridUnitsAsDp())) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            LightBarButtonView(leftButton, 3f, true)
            Spacer(Modifier.weight(1f))
            LightBarButtonView(rightButton, 3f, true)
        }
        if (center is LightTopBarCenter.Text) {
            LightText(center.text, LightTextVariant.Fine, Modifier.fillMaxWidth().align(Alignment.Center), TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun LightBottomBar(items: List<LightBottomBarItem?>, modifier: Modifier = Modifier) {
    require(items.size <= 3)
    Row(
        modifier.fillMaxWidth().padding(top = 1f.gridUnitsAsDp()).height(4f.gridUnitsAsDp()).padding(horizontal = if (items.size > 1) 2f.gridUnitsAsDp() else 0.dp),
        horizontalArrangement = if (items.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) { items.forEach { LightBarButtonView(it, 4f, true) } }
}
