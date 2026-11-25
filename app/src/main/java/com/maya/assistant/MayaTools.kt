package com.maya.assistant

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal fun browserAddress(input: String): String? {
 val text = input.trim()
 if (text.isEmpty()) return null
 val address = when {
  text.contains("://") -> text
  text.contains('.') && text.none(Char::isWhitespace) -> "https://$text"
  else -> "https://www.google.com/search?q=" + Uri.encode(text)
 }
 val uri = Uri.parse(address)
 return address.takeIf { uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() }
}

@Composable
internal fun ColumnScope.MayaBrowser(initialSearch: String, onSearchConsumed: () -> Unit, onAction: (String) -> Unit) {
 val context = LocalContext.current
 val portrait = remember(context) {
  val source = BitmapFactory.decodeResource(context.resources, R.drawable.maya_reference)
  Bitmap.createBitmap(source, 325, 180, 410, 490).asImageBitmap()
 }
 var address by remember { mutableStateOf(initialSearch) }
 var destination by remember {
  mutableStateOf(if (initialSearch.isBlank()) "" else "https://www.google.com/search?q=" + Uri.encode(initialSearch))
 }
 LaunchedEffect(initialSearch) { if (initialSearch.isNotBlank()) onSearchConsumed() }
 var progress by remember { mutableIntStateOf(0) }
 var web by remember { mutableStateOf<WebView?>(null) }
 Image(portrait, "Maya", Modifier.fillMaxWidth().height(130.dp), contentScale = ContentScale.Fit)
 OutlinedTextField(address, { address = it }, label = { Text("URL or search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
 Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
  Button(onClick = {
   val url = browserAddress(address)
   if (url == null) onAction("Enter a valid web address or search term")
   else {
    destination = url
    web?.loadUrl(url)
   }
  }) { Text("Open") }
  OutlinedButton(onClick = { web?.goBack() }, enabled = web?.canGoBack() == true) { Text("Back") }
 }
 if (destination.isNotEmpty()) {
  Text(if (progress < 100) "Loading page • $progress%" else "Page loaded")
  AndroidView(
   modifier = Modifier.fillMaxWidth().weight(1f),
   factory = {
    WebView(it).apply {
     settings.javaScriptEnabled = true
     settings.domStorageEnabled = true
     settings.allowFileAccess = false
     settings.allowContentAccess = false
     webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
       request.url.scheme !in setOf("http", "https")
     }
     webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
     }
     web = this
     loadUrl(destination)
    }
   },
   onReset = null,
   onRelease = { it.destroy() }
  )
 }
}

@Composable
internal fun AppsManager(allowed: Set<String>, setAllowed: (Set<String>) -> Unit, status: (String) -> Unit) {
 val context = LocalContext.current
 val apps = remember(context) {
  val manager = context.packageManager
  manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
   .filter { it.activityInfo.packageName != context.packageName }
   .distinctBy { it.activityInfo.packageName }
   .map { it.activityInfo.packageName to it.loadLabel(manager).toString() }
   .sortedBy { it.second.lowercase() }
 }
 Text("Choose which apps Maya can open.")
 apps.forEach { (pkg, label) ->
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
   Text(label, Modifier.weight(1f))
   Switch(pkg in allowed, { enabled -> setAllowed(if (enabled) allowed + pkg else allowed - pkg) })
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   OutlinedButton(onClick = { if (!PhoneActions.openApp(context, pkg)) status("App unavailable") }, enabled = pkg in allowed) { Text("Open") }
   TextButton(onClick = { if (!PhoneActions.appSettings(context, pkg)) status("App settings unavailable") }) { Text("Android permissions") }
  }
 }
}

@Composable
internal fun LocalEntries(store: MayaStore, category: String, hint: String, onSchedule: ((String) -> Unit)? = null) {
 var entries by remember(category) { mutableStateOf(store.list(category).asReversed()) }
 var draft by remember(category) { mutableStateOf("") }
 var editingId by remember(category) { mutableStateOf<String?>(null) }
 if (category != "memory") {
  OutlinedTextField(draft, { draft = it }, label = { Text(hint) }, modifier = Modifier.fillMaxWidth())
  Button(enabled = draft.isNotBlank(), onClick = {
   val id = editingId
   if (id == null) store.add(category, draft) else store.update(category, id, draft)
   editingId = null
   entries = store.list(category).asReversed()
   draft = ""
  }) { Text(if (editingId == null) "Add" else "Save changes") }
  if (editingId != null) TextButton(onClick = { editingId = null; draft = "" }) { Text("Cancel edit") }
 }
 if (entries.isEmpty()) Text("No entries yet")
 entries.forEach { entry ->
  key(entry.id) {
   Text(entry.text)
   Row {
    if (category == "tasks") {
     TextButton(onClick = {
      store.complete(entry.id, !entry.done)
      entries = store.list(category).asReversed()
     }) { Text(if (entry.done) "Reopen" else "Mark complete") }
    }
    if (onSchedule != null && !entry.done) TextButton(onClick = { onSchedule(entry.text) }) { Text("Schedule") }
    if (category != "memory") TextButton(onClick = {
     editingId = entry.id
     draft = entry.text
    }) { Text("Edit") }
    TextButton(onClick = {
     if (editingId == entry.id) { editingId = null; draft = "" }
     store.remove(category, entry.id)
     entries = store.list(category).asReversed()
    }) { Text("Delete") }
   }
  }
 }
}
