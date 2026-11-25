package com.maya.assistant

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.exp

private data class HomeControl(val label: String, val x: Int, val y: Int, val w: Int, val h: Int)

private val controls = listOf(
 HomeControl("Notifications", 770, 65, 125, 125),
 HomeControl("Settings", 900, 65, 120, 125),
 HomeControl("Call", 855, 298, 165, 138),
 HomeControl("WhatsApp", 855, 441, 165, 136),
 HomeControl("Browser", 855, 580, 165, 136),
 HomeControl("Apps", 855, 720, 165, 139),
 HomeControl("Mobile Control", 855, 866, 165, 144),
 HomeControl("Voice greeting", 210, 984, 575, 157),
 HomeControl("Type", 143, 1214, 135, 137),
 HomeControl("Mic", 402, 1162, 220, 211),
 HomeControl("Camera", 745, 1214, 133, 137)
) + listOf("Home", "Chat", "Tools", "Work", "History", "My Profile").mapIndexed { index, label ->
 HomeControl(label, index * 168 + 8, 1377, 167, 145)
}

private fun fitArtworkY(y: Float, aspect: Float): Float {
 val portraitTop = 192f
 val portraitBottom = 704f
 val portraitHeight = portraitBottom - portraitTop
 val portraitScale = minOf(aspect, 1f / 1536f)
 val surroundScale = (1f - portraitHeight * portraitScale) / (1536f - portraitHeight)
 val withinPortrait = (y - portraitTop).coerceIn(0f, portraitHeight)
 return withinPortrait * portraitScale + (y - withinPortrait) * surroundScale
}

@Composable
fun MayaHome(mouth: Float, onTool: (String) -> Unit, modifier: Modifier = Modifier) {
 val context = LocalContext.current
 val bitmap = remember(context) { BitmapFactory.decodeResource(context.resources, R.drawable.maya_reference) }
 val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
 val mesh = remember { FloatArray(2 * 65 * 97) }
 BoxWithConstraints(modifier) {
  val width = maxWidth
  val height = maxHeight
  val aspect = (width.value / height.value) / 1024f
  Canvas(Modifier.fillMaxSize()) {
   drawIntoCanvas { canvas ->
    val native = canvas.nativeCanvas
    native.save()
    native.scale(size.width / 1024f, size.height)
    run {
     for (row in 0..96) for (column in 0..64) {
      val x = column * 16f
      val y = row * 16f
      val dx = (x - 515f) / 46f
      val dy = (y - 520f) / 24f
      val index = 2 * (row * 65 + column)
      mesh[index] = x
      mesh[index + 1] = fitArtworkY(y + (y - 520f) * exp(-dx * dx - dy * dy) * mouth, aspect)
     }
     native.drawBitmapMesh(bitmap, 64, 96, mesh, 0, null, 0, paint)
    }
    native.restore()
   }
  }
  controls.forEach { control ->
   val top = fitArtworkY(control.y.toFloat(), aspect)
   val bottom = fitArtworkY((control.y + control.h).toFloat(), aspect)
   Box(Modifier.offset(width * (control.x / 1024f), height * top)
    .size(width * (control.w / 1024f), height * (bottom - top))
    .semantics { contentDescription = control.label }
    .clickable(role = Role.Button) { onTool(control.label) })
  }
 }
}
