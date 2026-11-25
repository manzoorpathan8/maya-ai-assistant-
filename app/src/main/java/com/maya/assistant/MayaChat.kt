package com.maya.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MayaChat(input: String, answer: String, busy: Boolean, status: String,
 onInput: (String) -> Unit, onListen: () -> Unit, onSend: () -> Unit,
 onStop: () -> Unit, onSettings: () -> Unit, onTool: (String) -> Unit) {
 val cyan = Color(0xFF42DFFF)
 val shape = RoundedCornerShape(20.dp)
 val pulse = rememberInfiniteTransition(label = "voice")
 val glow by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "glow")
 Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF020817),Color(0xFF101344)))).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) { Text("Maya ♡", color = cyan, fontSize = 30.sp); Text("AI Girlfriend & Mobile Assistant", color = Color.White) }
   TextButton(onClick = onSettings) { Text("Settings") }
  }
  Surface(shape = shape, color = Color(0xFF102B4B), modifier = Modifier.fillMaxWidth().border(1.dp,cyan,shape)) {
   Text(answer.ifBlank { "جی جان ❤️ میں آپ کے لیے کیا کر سکتی ہوں؟" }, Modifier.padding(16.dp), color = Color.White, fontSize = 20.sp)
  }
  OutlinedTextField(input,onInput, label = { Text("Type a message") }, modifier = Modifier.fillMaxWidth())
  Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceEvenly,verticalAlignment = Alignment.CenterVertically) {
   TextButton(onClick = onSend,enabled = !busy && input.isNotBlank() && !status.startsWith("Recording")) { Text("Send") }
   Button(onClick = onListen,enabled = !busy,shape = CircleShape,modifier = Modifier.size(76.dp).border(3.dp,cyan.copy(alpha = glow),CircleShape)) { Text("Mic") }
   TextButton(onClick = onStop) { Text("Stop") }
  }
  Text(status,color = cyan)
  Text("Tap Mic once for voice conversation. Audio goes to ElevenLabs and transcribed words go to Gemini. Tap Stop to end.", color = Color.White)
  Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceEvenly) {
   listOf("Home","Chat","Tools","Work","History").forEach { item -> TextButton(onClick = { onTool(item) },contentPadding = PaddingValues(2.dp)) { Text(item,fontSize = 11.sp) } }
  }
 }
}
