package de.ronnyporsch.voidcast.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

/** Single-line text that keeps its color but gets underlined on hover while [onClick] is set. */
@Composable
fun HoverLinkText(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Text(
        text = text,
        style = style,
        fontWeight = fontWeight,
        textDecoration = if (onClick != null && isHovered) TextDecoration.Underline else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (onClick != null) {
            modifier
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        } else {
            modifier
        },
    )
}
