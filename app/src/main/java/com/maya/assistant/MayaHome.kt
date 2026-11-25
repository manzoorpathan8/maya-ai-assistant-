package com.maya.assistant

import androidx.compose.material3.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

@Composable
private fun HomeCard(label: String, onTool: (String) -> Unit, modifier: Modifier) {
 OutlinedCard(modifier.clickable(role = Role.Button) { onTool(label) }) {
  Text(if (label == "Voice greeting") "Hi Dear ♥  ·  Tap to hear Maya" else label, Modifier.padding(horizontal = 8.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.primary)
 }
}

@Composable
fun MayaHome(mouth: Float, onTool: (String) -> Unit, modifier: Modifier = Modifier) {
 val context = LocalContext.current
 val bitmap = remember(context) { BitmapFactory.decodeResource(context.resources, R.drawable.maya_reference) }
 val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
 val mesh = remember { FloatArray(2 * 65 * 97) }
 Column(modifier.background(Color(0xFF041021)).padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   Text("Maya ♡", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
   HomeCard("Notifications", onTool, Modifier)
   HomeCard("Settings", onTool, Modifier)
  }
  Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
  Canvas(Modifier.weight(1f).fillMaxHeight()) {
   drawIntoCanvas { canvas ->
    val native = canvas.nativeCanvas
    native.save()
    native.clipRect(0f, 0f, size.width, size.height)
    val scale = maxOf(size.width / 820f, size.height / 785f)
    native.translate((size.width - 820f * scale) / 2f, (size.height - 785f * scale) / 2f)
    native.scale(scale, scale)
    native.translate(0f, -195f)
    if (mouth == 0f) {
     native.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, 1024, 1536), paint)
    } else {
     for (row in 0..96) for (column in 0..64) {
      val x = column * 16f
      val y = row * 16f
      val dx = (x - 515f) / 46f
      val dy = (y - 520f) / 24f
      val index = 2 * (row * 65 + column)
      mesh[index] = x
      mesh[index + 1] = y + (y - 520f) * exp(-dx * dx - dy * dy) * mouth
     }
     native.drawBitmapMesh(bitmap, 64, 96, mesh, 0, null, 0, paint)
    }
    native.restore()
   }
  }
   Column(Modifier.widthIn(max = 100.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf("Call","WhatsApp","Browser","Apps","Mobile Control").forEach { HomeCard(it,onTool,Modifier.fillMaxWidth()) }
   }
  }
  listOf(listOf("Voice greeting"),listOf("Type","Mic","Camera"),listOf("Home","Chat","Tools"),listOf("Work","History","My Profile")).forEach { labels ->
   Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    labels.forEach { HomeCard(it,onTool,Modifier.weight(1f)) }
   }
  }
 }
}
