package de.ronnyporsch.voidcast.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Text that is cut off after [collapsedMaxLines] lines, with a "Show more" / "Show less" toggle
 * that only appears when the text actually overflows.
 */
@Composable
fun ExpandableText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 4,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            modifier = Modifier.animateContentSize(),
        )
        if (overflows) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}
