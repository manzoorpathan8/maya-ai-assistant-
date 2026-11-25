package com.maya.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp

private val cyan = Color(0xFF45DBFF)
private val violet = Color(0xFF9663FF)

@Composable
private fun HomeButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
 OutlinedButton(onClick, modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
   Icon(icon, contentDescription = null, tint = cyan)
   Text(label, fontSize = 11.sp)
  }
 }
}

@Composable
fun MayaHome(mouth: Float, onTool: (String) -> Unit, modifier: Modifier = Modifier) {
 Column(modifier.background(Color(0xFF020B18)).padding(horizontal = 12.dp)) {
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("Maya ♥", Modifier.weight(1f), color = cyan, style = MaterialTheme.typography.headlineMedium)
    HomeButton("Notifications", Icons.Default.Notifications, { onTool("Notifications") })
    HomeButton("Settings", Icons.Default.Settings, { onTool("Settings") })
   }
   MayaPortrait(mouth, Modifier.fillMaxWidth().height(250.dp))
   val shortcuts = listOf("Call" to Icons.Default.Call, "WhatsApp" to Icons.Default.Send,
    "Browser" to Icons.Default.Search, "Apps" to Icons.Default.List, "Mobile Control" to Icons.Default.Phone)
   shortcuts.chunked(3).forEach { row ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
     row.forEach { (label, icon) -> HomeButton(label, icon, { onTool(label) }, Modifier.weight(1f)) }
    }
   }
   TextButton({ onTool("Voice greeting") }, Modifier.align(Alignment.CenterHorizontally)) {
    Text("Hi Dear ♥  •  What can I do for you?", color = cyan)
   }
  }
  Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
   HomeButton("Type", Icons.Default.Edit, { onTool("Type") })
   Button({ onTool("Mic") }, colors = ButtonDefaults.buttonColors(containerColor = violet, contentColor = Color.White)) {
    Icon(Icons.Default.Mic, contentDescription = null)
    Spacer(Modifier.width(6.dp))
    Text("Mic")
   }
   HomeButton("Camera", Icons.Default.CameraAlt, { onTool("Camera") })
  }
  val navigation = listOf("Home" to Icons.Default.Home, "Chat" to Icons.Default.Email, "Tools" to Icons.Default.Build,
   "Work" to Icons.Default.Folder, "History" to Icons.Default.DateRange, "My Profile" to Icons.Default.Person)
  NavigationBar(containerColor = Color(0xFF0A1A30)) {
   navigation.forEach { (label, icon) ->
    NavigationBarItem(label == "Home", { onTool(label) }, icon = { Icon(icon, null) },
     label = { Text(label, fontSize = 9.sp, maxLines = 1) }, alwaysShowLabel = true)
   }
  }
 }
}

@Composable
private fun MayaPortrait(mouth: Float, modifier: Modifier) {
 val context = androidx.compose.ui.platform.LocalContext.current
 val portrait = remember(context) {
  val source = BitmapFactory.decodeResource(context.resources, R.drawable.maya_reference)
  Bitmap.createBitmap(source, source.width * 245 / 1024, source.height * 200 / 1536,
   source.width * 580 / 1024, source.height * 580 / 1536)
 }
 val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
 val mesh = remember { FloatArray(2 * 33 * 33) }
 Canvas(modifier) {
  val side = minOf(size.width, size.height)
  for (row in 0..32) for (column in 0..32) {
   val x = column / 32f
   val y = row / 32f
   val dx = (x - (515f - 245f) / 580f) / 0.079f
   val dy = (y - (520f - 200f) / 580f) / 0.041f
   val index = 2 * (row * 33 + column)
   mesh[index] = (size.width - side) / 2 + x * side
   mesh[index + 1] = (size.height - side) / 2 + (y + dy * 0.041f * exp(-dx * dx - dy * dy) * mouth) * side
  }
  drawIntoCanvas { it.nativeCanvas.drawBitmapMesh(portrait, 32, 32, mesh, 0, null, 0, paint) }
 }
}
