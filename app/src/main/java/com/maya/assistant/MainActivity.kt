package com.maya.assistant

import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import android.media.MediaPlayer
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
 private var input by mutableStateOf("")
 private var answer by mutableStateOf("")
 private var status by mutableStateOf("")
 private var busy by mutableStateOf(false)
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
 private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  val value=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
  if(!value.isNullOrBlank()){ input=value; send() }
 }
 private fun listen(){
  runCatching { speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ur-PK")) }.onFailure { status="Speech recognition unavailable" }
 }
 private fun send(){
  if(busy || input.isBlank()) return
  val prompt=input
  val key=read("google")
  val model=prefs.getString("model","") ?: ""
  if(key.isBlank() || model.isBlank()){ status="Set Gemini key and select model in Settings"; screen="Settings"; return }
  busy=true; status="Thinking..."
  thread {
   try {
    val reply=AiClient.chat(key,model,"Respond only in natural Pakistani Urdu script, in a friendly feminine voice. Understand English and Roman Urdu. User request: "+prompt)
    runOnUiThread { answer=reply; status="Reply received" }
    val voiceKey=read("eleven")
    val voice=prefs.getString("voice","") ?: ""
    if(voiceKey.isNotBlank() && voice.isNotBlank()){
     val bytes=AiClient.speak(voiceKey,voice,reply)
     val file=File(cacheDir,"maya_voice.mp3").apply{writeBytes(bytes)}
     runOnUiThread {
      runCatching {
       player?.release()
       player=MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); start(); setOnCompletionListener { status="Ready" } }
       status="Speaking"
      }.onFailure { status="Audio playback failed" }
     }
    }
   } catch(e:Exception){ runOnUiThread { status=e.message ?: "Request failed" } }
   finally { runOnUiThread { busy=false } }
  }
 }
 override fun onDestroy(){player?.release();super.onDestroy()}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF36D9FF),background=Color(0xFF020B18),surface=Color(0xFF102339))){
    var google by remember { mutableStateOf(read("google")) }
    var eleven by remember { mutableStateOf(read("eleven")) }
    var voice by remember { mutableStateOf(prefs.getString("voice","") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model","") ?: "") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text("Maya ♡",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      Button(onClick={screen="Home"}){Text("Home")}
      Button(onClick={screen="Settings"}){Text("Settings")}
     }
     androidx.activity.compose.BackHandler(enabled=screen!="Home"){screen="Home"}
     if(screen=="Apps"){
      Text("Apps Manager")
      val apps=remember { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).sortedBy { it.loadLabel(packageManager).toString().lowercase() } }
      apps.forEach { app ->
       OutlinedButton(onClick={
        val info=app.activityInfo
        runCatching { startActivity(Intent.makeMainActivity(android.content.ComponentName(info.packageName,info.name))) }.onFailure {status="Unable to open app"}
       }){ Text(app.loadLabel(packageManager).toString()) }
      }
      if(apps.isEmpty()) Text("No launchable apps found")
     } else if(screen=="Settings"){
      OutlinedTextField(google,{google=it},label={Text("Google API Key")},visualTransformation=PasswordVisualTransformation())
      OutlinedTextField(eleven,{eleven=it},label={Text("ElevenLabs API Key")},visualTransformation=PasswordVisualTransformation())
      OutlinedTextField(voice,{voice=it},label={Text("ElevenLabs Voice ID")})
      Button(onClick={
       runCatching { save("google",google.trim());save("eleven",eleven.trim());prefs.edit().putString("voice",voice.trim()).apply();status="Settings saved" }.onFailure{status="Could not save settings"}
      }){Text("Save / update keys")}
      Button(onClick={google="";eleven="";voice="";model="";models=emptyList();prefs.edit().clear().apply();status="Keys removed"}){Text("Remove keys")}
      Button(enabled=!busy,onClick={
       busy=true;status="Testing Google..."
       val k=google.trim()
       thread { try { val list=AiClient.models(k);runOnUiThread {models=list;status="Connected: "+list.size+" models"} } catch(e:Exception){runOnUiThread{status=e.message ?: "Connection failed"}} finally{runOnUiThread{busy=false}} }
      }){Text("Test connection / load models")}
      Text("Selected model: "+model)
      models.forEach { item -> OutlinedButton(onClick={model=item;prefs.edit().putString("model",item).apply()}){Text(item)} }
     } else {
      MayaHome(input, answer, busy, status, { input=it }, { listen() }, { send() },
       { player?.release(); player=null; status="Stopped" }, { screen="Settings" },
       { tool ->
        when(tool) {
         "Apps" -> {screen="Apps";status=""}
         "Home", "Chat" -> screen="Home"
         "Call" -> runCatching { startActivity(Intent(Intent.ACTION_DIAL)) }.onFailure { status="Dialer unavailable" }
         "Browser" -> runCatching { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.google.com"))) }.onFailure { status="Browser unavailable" }
         "WhatsApp" -> runCatching { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://wa.me/"))) }.onFailure { status="WhatsApp unavailable" }
         else -> status="$tool is not implemented yet"
        }
       })

     }
     Text(status,color=MaterialTheme.colorScheme.primary)
    }
   }
  }
 }
}
