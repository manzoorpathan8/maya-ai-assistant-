package com.maya.assistant

import androidx.compose.foundation.clickable
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import android.content.Intent
import android.media.MediaPlayer
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
 private var browserQuery by mutableStateOf("")
 private var input by mutableStateOf("")
 private var answer by mutableStateOf("")
 private var status by mutableStateOf("")
 private var busy by mutableStateOf(false)
 private val store by lazy { MayaStore(this) }
 private var speaking by mutableStateOf(false)
 private var cues by mutableStateOf(emptyList<AiClient.SpeechCue>())
 private var timedMouth by mutableFloatStateOf(0f)
 private var tts: TextToSpeech? = null
 private var urduVoiceReady = false
 private var player: MediaPlayer? = null
 private var models by mutableStateOf(emptyList<String>())
 private var screen by mutableStateOf("Home")
 private val prefs by lazy { getSharedPreferences("maya", MODE_PRIVATE) }
 private fun secretKey(): SecretKey {
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (ks.getKey("maya_keys",null) as? SecretKey)?.let { return it }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder("maya_keys",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 private fun save(name:String,value:String) {
  if(value.isBlank()) { prefs.edit().remove(name).apply(); return }
  val c=Cipher.getInstance("AES/GCM/NoPadding")
  c.init(Cipher.ENCRYPT_MODE,secretKey())
  prefs.edit().putString(name,Base64.encodeToString(c.iv+c.doFinal(value.toByteArray()),Base64.NO_WRAP)).apply()
 }
 private fun read(name:String):String = runCatching {
  val b=Base64.decode(prefs.getString(name,"") ?: "",Base64.NO_WRAP)
  val c=Cipher.getInstance("AES/GCM/NoPadding")
  c.init(Cipher.DECRYPT_MODE,secretKey(),GCMParameterSpec(128,b.copyOfRange(0,12)))
  String(c.doFinal(b.copyOfRange(12,b.size)))
 }.getOrDefault("")
 private var exportText = ""
 private val exportDraft = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
  if (uri != null) status = runCatching {
   val stream = checkNotNull(contentResolver.openOutputStream(uri))
   stream.use { it.write(exportText.toByteArray(Charsets.UTF_8)) }
   "Draft saved"
  }.getOrDefault("Could not save draft")
 }
 private var recorder: android.media.MediaRecorder? = null
 private var clip: File? = null
 private var recording by mutableStateOf(false)
 private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
  if (allowed) startRecording() else status = "Microphone access was declined"
 }
 private fun listen() {
  if (recording) { finishRecording(true); return }
  if (busy) return
  screen = "Chat"
  if (read("eleven").isBlank()) {
   status = "Save an ElevenLabs key for voice transcription, or type your message."
   return
  }
  if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
   micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
  else startRecording()
 }
 @Suppress("DEPRECATION")
 private fun startRecording() {
  if (recording || isFinishing || isDestroyed) return
  stopVoice()
  try {
   val file = File.createTempFile("maya-mic-", ".m4a", cacheDir)
   clip = file
   val mic = android.media.MediaRecorder()
   recorder = mic
   mic.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
   mic.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
   mic.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
   mic.setOutputFile(file.absolutePath)
   mic.setMaxDuration(60000)
   mic.setOnInfoListener { _, what, _ -> if (what == android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finishRecording(true) }
   mic.prepare()
   mic.start()
   recording = true
   status = "Recording • tap Mic to transcribe with ElevenLabs, or Stop to discard"
  } catch (e: Exception) { finishRecording(false); status = "Could not start the phone microphone" }
 }
 private fun finishRecording(transcribe: Boolean) {
  val file = clip
  clip = null
  val mic = recorder
  recorder = null
  recording = false
  val valid = runCatching { mic?.stop() }.isSuccess
  runCatching { mic?.release() }
  if (file == null) return
  if (!transcribe || !valid) { file.delete(); status = if (valid) "Recording discarded" else "Recording too short"; return }
  val key = read("eleven")
  busy = true
  status = "Transcribing…"
  thread {
   try {
    val words = AiClient.transcribe(key, file)
    runOnUiThread { if (!isDestroyed) { input = words; status = "Review your words and tap Send" } }
   } catch (e: Exception) { runOnUiThread { status = e.message ?: "Transcription failed" } }
   finally { file.delete(); runOnUiThread { busy = false } }
  }
 }
 override fun onStop() { if (recording) finishRecording(false); super.onStop() }
 private fun stopVoice() {
  voiceRequest++
  player?.release()
  player = null
  tts?.stop()
  speaking = false
  cues = emptyList()
  timedMouth = 0f
 }
 private fun speakOnDevice(text: String) {
  if (isDestroyed) return
  if (urduVoiceReady && tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maya") == TextToSpeech.SUCCESS) return
  status = "Urdu audio unavailable. Configure an ElevenLabs voice in Settings."
 }
 private var voiceRequest = 0
 private fun playUrdu(text: String, apiKey: String, selected: String) {
  stopVoice()
  if (apiKey.isBlank()) {
   speakOnDevice(text)
   return
  }
  val request = voiceRequest
  status = "Preparing Urdu voice…"
  thread {
   try {
    val voice = selected.ifBlank { AiClient.femaleVoice(apiKey) }
    val audio = AiClient.timedSpeech(apiKey, voice, text)
    val file = File.createTempFile("maya-", ".mp3", cacheDir)
    file.writeBytes(audio.bytes)
    runOnUiThread {
     try {
      if (isDestroyed || request != voiceRequest) return@runOnUiThread
      cues = audio.cues
      player = MediaPlayer().also { media ->
       media.setDataSource(file.absolutePath)
       media.setOnPreparedListener { it.start(); speaking = true; status = "Speaking" }
       media.setOnCompletionListener { stopVoice(); status = "Ready" }
       media.setOnErrorListener { _, _, _ -> stopVoice(); status = "Voice playback failed"; true }
       media.prepareAsync()
      }
     } catch (e: Exception) {
      stopVoice()
      status = "Voice playback failed"
     } finally { file.delete() }
    }
   } catch (e: Exception) {
    runOnUiThread { if (!isDestroyed && request == voiceRequest) status = "ElevenLabs: " + (e.message ?: "Voice unavailable") }
   }
  }
 }
 private fun send(includeBusiness: Boolean = false){
  if(busy || input.isBlank()) return
  val prompt=input
  if (!includeBusiness) MayaCommands.webSearch(prompt)?.let {
   browserQuery = it
   screen = "Browser"
   input = ""
   status = ""
   return
  }
  if (!includeBusiness) MayaCommands.destination(prompt)?.let {
   screen = it
   input = ""
   status = "Opened $it"
   return
  }
  val key=read("google")
  val model=prefs.getString("model","") ?: ""
  if(key.isBlank() || model.isBlank()){ status="Set Gemini key and select model in Settings"; screen="Settings"; return }
  stopVoice()
  busy=true; status="Thinking..."
  val business = if (includeBusiness) store.list("business").joinToString("\n") { it.text }.take(12000) else ""
  val voiceKey = read("eleven")
  val voice = prefs.getString("voice", "").orEmpty()
  thread {
   try {
    val reply=AiClient.chat(key,model,"Respond only in natural Pakistani Urdu script, in a friendly feminine voice. Understand English and Roman Urdu. User request: "+prompt + if (business.isBlank()) "" else "\nBusiness notes for context:\n$business")
    check(reply.isNotBlank()) { "Gemini returned an empty reply" }
    runOnUiThread {
     if (isDestroyed) return@runOnUiThread
     answer=reply
     status="Reply received"
     runCatching { store.add("memory", "$prompt\n\n$reply") }
    }
    runOnUiThread { if (!isDestroyed) playUrdu(reply, voiceKey, voice) }

   } catch(e:Exception){ runOnUiThread { status=e.message ?: "Request failed" } }
   finally { runOnUiThread { busy=false } }
  }
 }
 override fun onDestroy(){stopVoice();tts?.shutdown();super.onDestroy()}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  @Suppress("DEPRECATION")
  window.decorView.systemUiVisibility = 5894
  tts = TextToSpeech(this) { result ->
   if (result == TextToSpeech.SUCCESS && !isDestroyed) {
    tts?.let { engine ->
     val urdu = Locale.forLanguageTag("ur-PK")
     urduVoiceReady = engine.setLanguage(urdu) >= TextToSpeech.LANG_AVAILABLE
     engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(id: String?) { runOnUiThread { speaking = true; status = "Speaking" } }
      override fun onDone(id: String?) { runOnUiThread { speaking = false; status = "Ready" } }
      @Deprecated("Legacy Android callback")
      override fun onError(id: String?) { runOnUiThread { speaking = false; status = "Urdu speech failed" } }
     })
    }
   }
  }
  setContent {
   MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF36D9FF),background=Color(0xFF020B18),surface=Color(0xFF102339))){
    androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    var google by remember { mutableStateOf(read("google")) }
    var eleven by remember { mutableStateOf(read("eleven")) }
    var voice by remember { mutableStateOf(prefs.getString("voice","") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model","") ?: "") }
    var allowlist by remember { mutableStateOf(prefs.getStringSet("allowed_apps", emptySet()).orEmpty().toSet()) }
    val pulse = rememberInfiniteTransition(label = "Speech animation")
    val mouth by pulse.animateFloat(0.05f, 0.55f, infiniteRepeatable(tween(125), RepeatMode.Reverse), label = "Mouth")
    LaunchedEffect(speaking, cues) {
     if (speaking && cues.isNotEmpty()) {
      while (speaking) {
       val position = player?.currentPosition ?: break
       val cue = cues.firstOrNull { position >= it.startMs && position < it.endMs }
       timedMouth = when {
        cue == null || cue.character.none(Char::isLetterOrDigit) -> 0f
        cue.character in setOf("م", "ب", "پ") -> -0.18f
        cue.character in setOf("ا", "آ", "ہ", "ع") -> 0.7f
        else -> 0.32f
       }
       delay(25)
      }
     }
    }
    LaunchedEffect(screen) { if (screen != "Chat" && recording) finishRecording(false) }
    BackHandler(screen != "Home") { screen = "Home" }
    if (screen == "Home") {
     Box(Modifier.fillMaxSize().background(Color(0xFF020B18)), contentAlignment = androidx.compose.ui.Alignment.Center) {
      MayaHome(if (!speaking) 0f else if (cues.isEmpty()) mouth else timedMouth, { action ->
       when (action) {
        "Mic" -> if (speaking) stopVoice() else if (!busy) listen()
        "Camera" -> runCatching { startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
         .onFailure { status = "Camera unavailable"; screen = "Notifications" }
        "Voice greeting" -> {
         stopVoice()
         playUrdu(answer.ifBlank { "السلام علیکم، میں مایا ہوں۔ آپ کی کیا مدد کر سکتی ہوں؟" }, read("eleven"), prefs.getString("voice", "").orEmpty())
        }
        "Type" -> screen = "Chat"
        else -> screen = action
       }
      }, Modifier.fillMaxSize().displayCutoutPadding())
      if (busy || speaking) Surface(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).offset(y = (-70).dp)) {
       Text(status, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
      }
     }
    } else Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
      .imePadding().displayCutoutPadding().then(if (screen == "Browser") Modifier else Modifier.verticalScroll(rememberScrollState()))
      .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
     Text(screen, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      Button(onClick={screen="Home"}){Text("Home")}
      Button(onClick={screen="Settings"}){Text("Settings")}
     }
     if(screen=="Settings"){
      OutlinedTextField(google,{google=it},label={Text("Google API Key")},visualTransformation=PasswordVisualTransformation())
      OutlinedTextField(eleven,{eleven=it},label={Text("ElevenLabs API Key")},visualTransformation=PasswordVisualTransformation())
      OutlinedTextField(voice,{voice=it},label={Text("Voice ID (blank for automatic female voice)")})
      Button(onClick={
       runCatching { save("google",google.trim());save("eleven",eleven.trim());prefs.edit().putString("voice",voice.trim()).apply();status="Settings saved" }.onFailure{status="Could not save settings"}
      }){Text("Save / update keys")}
      Button(onClick={google="";eleven="";voice="";model="";models=emptyList();prefs.edit().remove("google").remove("eleven").remove("voice").remove("model").apply();status="Keys removed"}){Text("Remove keys")}
      OutlinedButton(enabled = !busy, onClick = {
       stopVoice()
       playUrdu("السلام علیکم، میں مایا ہوں۔ آپ کی کیا مدد کر سکتی ہوں؟", eleven.trim(), voice.trim())
      }) { Text(if (eleven.isBlank()) "Preview device Urdu voice" else "Preview ElevenLabs Urdu voice") }
      Button(enabled=!busy && google.isNotBlank(),onClick={
       busy=true;status="Testing Google..."
       val k=google.trim()
       thread { try { val list=AiClient.models(k);runOnUiThread {models=list; if (model !in list) { model = list.firstOrNull { it.contains("flash") && !it.contains("image") && !it.contains("tts") && !it.contains("audio") } ?: list.firstOrNull().orEmpty() }; save("google", k); prefs.edit().putString("model", model).apply(); status=if (list.isEmpty()) "No chat models available" else "Connected • model saved"} } catch(e:Exception){runOnUiThread{status=e.message ?: "Connection failed"}} finally{runOnUiThread{busy=false}} }
      }){Text("Test connection / load models")}
      Text("Selected model: "+model)
      models.forEach { item -> OutlinedButton(onClick={model=item;prefs.edit().putString("model",item).apply()}){Text(item)} }
     } else when (screen) {
      "Chat" -> MayaChat(input, answer, busy, status, { input = it }, { if (!busy) listen() }, { if (!recording) send() },
       { if (recording) finishRecording(false) else { stopVoice(); status = "Stopped" } }, { screen = "Settings" }, { screen = it })
      "Browser" -> MayaBrowser(browserQuery, { browserQuery = "" }) { status = it }
      "Call", "WhatsApp" -> key(screen) {
       var number by remember { mutableStateOf("") }
       var message by remember { mutableStateOf("") }
       OutlinedTextField(number, { number = it }, label = { Text(if (screen == "WhatsApp") "Number with country code" else "Phone number") })
       if (screen == "WhatsApp") OutlinedTextField(message, { message = it }, label = { Text("Message draft") })
       Button(enabled = if (screen == "Call") number.any(Char::isDigit) else MayaCommands.internationalNumber(number) != null, onClick = {
        val ok = if (screen == "Call") PhoneActions.dial(this@MainActivity, number)
         else PhoneActions.whatsappDraft(this@MainActivity, number, message)
        status = if (ok) "Opened for review" else "Unable to open the requested app"
       }) { Text(if (screen == "Call") "Open dialer" else "Review in WhatsApp") }
       if (screen == "WhatsApp") {
        Text("WhatsApp notifications", style = MaterialTheme.typography.titleMedium)
        Text("Optional Android notification access lets Maya display current WhatsApp alerts here.")
        OutlinedButton(onClick = {
         runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
          .onFailure { status = "Notification access settings unavailable" }
        }) { Text("Manage notification access") }
        val active = WhatsAppNotifications.messages.value
        if (WhatsAppNotifications.connected.value && active.isEmpty()) Text("No current WhatsApp alerts")
        active.forEach { Text(it) }
       }
      }
      "Apps" -> AppsManager(allowlist, {
       allowlist = it
       prefs.edit().putStringSet("allowed_apps", it).apply()
      }) { status = it }
      "Mobile Control" -> {
       Text("Open allowed apps, prepare a call, or manage permissions through Android.")
       listOf("Apps", "Call", "Browser", "WhatsApp").forEach { item ->
        OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { screen = item }) { Text(item, Modifier.padding(16.dp)) }
       }
       Button(onClick = { if (!PhoneActions.systemSettings(this@MainActivity)) status = "Android settings unavailable" }) { Text("Android settings") }
      }
      "Tools" -> listOf("Browser", "Apps", "Mobile Control", "WhatsApp", "Work").forEach { item ->
       OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { screen = item }) { Text(item, Modifier.padding(16.dp)) }
      }
      "Work" -> listOf("Business Knowledge", "Tasks", "Create & Build").forEach { item ->
       OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { screen = item }) { Text(item, Modifier.padding(16.dp)) }
      }
      "Business Knowledge" -> LocalEntries(store, "business", "Business note")
      "Tasks" -> LocalEntries(store, "tasks", "New task") { task ->
       val intent = Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
        .putExtra(android.provider.CalendarContract.Events.TITLE, task.lineSequence().first().take(100))
        .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, task)
       runCatching { startActivity(intent) }.onFailure { status = "Calendar app unavailable" }
      }
      "History" -> LocalEntries(store, "memory", "")
      "Create & Build" -> {
       OutlinedTextField(input, { input = it }, label = { Text("Describe the draft, plan, or website code to generate") }, modifier = Modifier.fillMaxWidth())
       Button(enabled = !busy && input.isNotBlank(), onClick = { send(includeBusiness = true) }) { Text("Generate using business notes") }
       if (answer.isNotBlank()) {
        SelectionContainer { Text(answer) }
        OutlinedButton(onClick = { exportText = answer; exportDraft.launch("maya-draft.txt") }) { Text("Save draft") }
        OutlinedButton(onClick = {
         status = runCatching { store.add("tasks", answer); "Draft saved to Tasks" }.getOrDefault("Could not save task")
        }) { Text("Save as task") }
       }
      }
      "My Profile" -> {
       Text("Maya • Personal assistant")
       Text("Interface: English")
       Text("Speech and replies: Pakistani Urdu")
      }
      "Notifications" -> Text(status.ifBlank { "No recent activity" })
     }
     Text(status,color=MaterialTheme.colorScheme.primary)
    }
   }
  }
    }
 }
}
