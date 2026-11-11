package com.example.juke.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Original JUKE glyphs: 24 dp grid, 1.8 dp rounded strokes, tint supplied by Icon.
 * Solid transport and selected glyphs remain legible at compact player sizes.
 * Only directional UI glyphs mirror in RTL; media transport keeps its meaning.
 */
object JukeIcons {
    val Home: ImageVector by lazy { glyph("Home", "M3.5 10.5 L12 3.5 L20.5 10.5 V19.5 Q20.5 20.5 19.5 20.5 H15 V14 H9 V20.5 H4.5 Q3.5 20.5 3.5 19.5 Z", "", false) }
    val HomeSelected: ImageVector by lazy { glyph("HomeSelected", "", "M3.5 10.5 L12 3.5 L20.5 10.5 V19.5 Q20.5 20.5 19.5 20.5 H15 V14 H9 V20.5 H4.5 Q3.5 20.5 3.5 19.5 Z", false) }
    val Search: ImageVector by lazy { glyph("Search", "M17 17 L21 21 M18 10.5 A7.5 7.5 0 1 1 3 10.5 A7.5 7.5 0 1 1 18 10.5", "", false) }
    val Library: ImageVector by lazy { glyph("Library", "M4 4 V20 M9 4 V20 M14 4 V20 M18 5 L21 19", "", false) }
    val LibrarySelected: ImageVector by lazy { glyph("LibrarySelected", "M18 5 L21 19", "M3 4 H5 V20 H3 Z M8 4 H10 V20 H8 Z M13 4 H15 V20 H13 Z", false) }
    val Heart: ImageVector by lazy { glyph("Heart", "M12 20 L4.3 12.6 C-1 7.3 6.6 0.7 12 6.7 C17.4 0.7 25 7.3 19.7 12.6 Z", "", false) }
    val HeartSelected: ImageVector by lazy { glyph("HeartSelected", "", "M12 20 L4.3 12.6 C-1 7.3 6.6 0.7 12 6.7 C17.4 0.7 25 7.3 19.7 12.6 Z", false) }
    val Play: ImageVector by lazy { glyph("Play", "", "M8 4.8 Q7 4.2 7 5.5 V18.5 Q7 19.8 8 19.2 L19 12.8 Q20.2 12 19 11.2 Z", false) }
    val Pause: ImageVector by lazy { glyph("Pause", "", "M7 4.5 H9 Q10 4.5 10 5.5 V18.5 Q10 19.5 9 19.5 H7 Q6 19.5 6 18.5 V5.5 Q6 4.5 7 4.5 Z M15 4.5 H17 Q18 4.5 18 5.5 V18.5 Q18 19.5 17 19.5 H15 Q14 19.5 14 18.5 V5.5 Q14 4.5 15 4.5 Z", false) }
    val Next: ImageVector by lazy { glyph("Next", "", "M5.5 5.3 Q4.5 4.7 4.5 6 V18 Q4.5 19.3 5.5 18.7 L15.5 12.8 Q16.7 12 15.5 11.2 Z M18 5 H19 Q20 5 20 6 V18 Q20 19 19 19 H18 Q17 19 17 18 V6 Q17 5 18 5 Z", false) }
    val Previous: ImageVector by lazy { glyph("Previous", "", "M18.5 5.3 Q19.5 4.7 19.5 6 V18 Q19.5 19.3 18.5 18.7 L8.5 12.8 Q7.3 12 8.5 11.2 Z M5 5 H6 Q7 5 7 6 V18 Q7 19 6 19 H5 Q4 19 4 18 V6 Q4 5 5 5 Z", false) }
    val Queue: ImageVector by lazy { glyph("Queue", "M3.5 5 H20.5 M3.5 10 H13 M3.5 15 H10 M16 10 V18 M16 11 L21 10 V17", "M16 18 A2 2 0 1 1 12 18 A2 2 0 1 1 16 18 M21 17 A2 2 0 1 1 17 17 A2 2 0 1 1 21 17", true) }
    val PlaylistAdd: ImageVector by lazy { glyph("PlaylistAdd", "M3.5 5 H20.5 M3.5 10 H14 M3.5 15 H11 M18 13 V21 M14 17 H22", "", true) }
    val Lyrics: ImageVector by lazy { glyph("Lyrics", "M5 3.5 H19 Q20.5 3.5 20.5 5 V17 Q20.5 18.5 19 18.5 H10 L5 21 V18.5 Q3.5 18.5 3.5 17 V5 Q3.5 3.5 5 3.5 Z M7.5 8 H16.5 M7.5 12 H14", "", true) }
    val Download: ImageVector by lazy { glyph("Download", "M12 3.5 V15 M7.5 10.5 L12 15 L16.5 10.5 M4 16 V19 Q4 20.5 5.5 20.5 H18.5 Q20 20.5 20 19 V16", "", false) }
    val Shuffle: ImageVector by lazy { glyph("Shuffle", "M3 6 H5 Q7 6 9 9 L15 17 Q16 18 18 18 H21 M17.5 14.5 L21 18 L17.5 21.5 M3 18 H5 Q7 18 9 15 M13 9 L15 7 Q16 6 18 6 H21 M17.5 2.5 L21 6 L17.5 9.5", "", false) }
    val Repeat: ImageVector by lazy { glyph("Repeat", "M4 10 V8 Q4 5 7 5 H20 M16.5 1.5 L20 5 L16.5 8.5 M20 14 V16 Q20 19 17 19 H4 M7.5 15.5 L4 19 L7.5 22.5", "", false) }
    val RepeatOne: ImageVector by lazy { glyph("RepeatOne", "M4 10 V8 Q4 5 7 5 H20 M16.5 1.5 L20 5 L16.5 8.5 M20 14 V16 Q20 19 17 19 H4 M7.5 15.5 L4 19 L7.5 22.5 M10.5 10 L12 9 V15", "", false) }
    val Back: ImageVector by lazy { glyph("Back", "M20 12 H4 M10 6 L4 12 L10 18", "", true) }
    val Forward: ImageVector by lazy { glyph("Forward", "M4 12 H20 M14 6 L20 12 L14 18", "", true) }
    val ChevronDown: ImageVector by lazy { glyph("ChevronDown", "M6 9 L12 15 L18 9", "", false) }
    val ChevronUp: ImageVector by lazy { glyph("ChevronUp", "M6 15 L12 9 L18 15", "", false) }
    val Close: ImageVector by lazy { glyph("Close", "M6 6 L18 18 M18 6 L6 18", "", false) }
    val Add: ImageVector by lazy { glyph("Add", "M12 5 V19 M5 12 H19", "", false) }
    val AddCircle: ImageVector by lazy { glyph("AddCircle", "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 M12 8 V16 M8 12 H16", "", false) }
    val Check: ImageVector by lazy { glyph("Check", "M4.5 12 L9.5 17 L19.5 7", "", false) }
    val Delete: ImageVector by lazy { glyph("Delete", "M3.5 6 H20.5 M9 6 V3.5 H15 V6 M6 6 L7 19 Q7 20.5 8.5 20.5 H15.5 Q17 20.5 17 19 L18 6 M10 10 V16 M14 10 V16", "", false) }
    val More: ImageVector by lazy { glyph("More", "", "M13.5 5 A1.5 1.5 0 1 1 10.5 5 A1.5 1.5 0 1 1 13.5 5 M13.5 12 A1.5 1.5 0 1 1 10.5 12 A1.5 1.5 0 1 1 13.5 12 M13.5 19 A1.5 1.5 0 1 1 10.5 19 A1.5 1.5 0 1 1 13.5 19", false) }
    val MoreHorizontal: ImageVector by lazy { glyph("MoreHorizontal", "", "M6.5 12 A1.5 1.5 0 1 1 3.5 12 A1.5 1.5 0 1 1 6.5 12 M13.5 12 A1.5 1.5 0 1 1 10.5 12 A1.5 1.5 0 1 1 13.5 12 M20.5 12 A1.5 1.5 0 1 1 17.5 12 A1.5 1.5 0 1 1 20.5 12", false) }
    val Equalizer: ImageVector by lazy { glyph("Equalizer", "M5 5 V19 M12 5 V19 M19 5 V19 M2.5 9 H7.5 M9.5 15 H14.5 M16.5 10 H21.5", "", false) }
    val MusicNote: ImageVector by lazy { glyph("MusicNote", "M10 17 V5 L20 3.5 V15 M10 8 L20 6.5", "M10 17 A3 3 0 1 1 4 17 A3 3 0 1 1 10 17 M20 15 A3 3 0 1 1 14 15 A3 3 0 1 1 20 15", false) }
    val Share: ImageVector by lazy { glyph("Share", "M8 10 L16 6 M8 14 L16 18 M8 12 A3 3 0 1 1 2 12 A3 3 0 1 1 8 12 M22 5 A3 3 0 1 1 16 5 A3 3 0 1 1 22 5 M22 19 A3 3 0 1 1 16 19 A3 3 0 1 1 22 19", "", false) }
    val Refresh: ImageVector by lazy { glyph("Refresh", "M20 10 A8 8 0 1 0 19 17 M20 4 V10 H14", "", false) }
    val Album: ImageVector by lazy { glyph("Album", "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 M15 12 A3 3 0 1 1 9 12 A3 3 0 1 1 15 12 M6.5 9 Q7.5 6.5 10 6", "", false) }
    val List: ImageVector by lazy { glyph("List", "M9 5 H20 M9 12 H20 M9 19 H20", "M5 5 A1 1 0 1 1 3 5 A1 1 0 1 1 5 5 M5 12 A1 1 0 1 1 3 12 A1 1 0 1 1 5 12 M5 19 A1 1 0 1 1 3 19 A1 1 0 1 1 5 19", true) }

    private fun glyph(name: String, outline: String, solid: String, mirrored: Boolean): ImageVector {
        val builder = ImageVector.Builder(
            name = "Juke.$name", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = mirrored
        )
        if (outline.isNotEmpty()) {
            builder.addPath(
                pathData = PathParser().parsePathString(outline).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
        if (solid.isNotEmpty()) {
            builder.addPath(
                pathData = PathParser().parsePathString(solid).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }
        return builder.build()
    }
}
