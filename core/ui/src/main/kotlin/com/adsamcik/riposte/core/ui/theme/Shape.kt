package com.adsamcik.riposte.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

// Custom shapes for specific components
object MoodShapes {
    val MemeCard = RoundedCornerShape(16.dp)
    val EmojiChip = RoundedCornerShape(20.dp)
    val SearchBar = RoundedCornerShape(28.dp)
    val BottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Dialog = RoundedCornerShape(28.dp)
}
