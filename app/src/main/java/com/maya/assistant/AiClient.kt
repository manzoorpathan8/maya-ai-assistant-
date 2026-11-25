package com.maya.assistant

import org.json.JSONObject
import org.json.JSONArray
import android.util.Base64
import java.net.URL
import java.net.HttpURLConnection

object AiClient {
 private class GeminiModelUnavailable : IllegalStateException("Gemini model unavailable")

 fun preferredChatModels(models: List<String>): List<String> = models.filterNot { model ->
  listOf("image", "tts", "audio", "embedding", "robotics").any { it in model }
 }.sortedBy { model ->
  when {
   "flash" in model && "preview" !in model && "experimental" !in model -> 0
   "preview" !in model && "experimental" !in model -> 1
   else -> 2
  }
 }

 fun chatWithRecovery(key: String, model: String, prompt: String, audio: java.io.File? = null,
  onModelRecovered: (String) -> Unit): String {
  try {
   return chat(key, model, prompt, audio)
  } catch (_: GeminiModelUnavailable) {
   for (candidate in preferredChatModels(models(key)).filter { it != model }.take(3)) {
    try {
     val reply = chat(key, candidate, prompt, audio)
     onModelRecovered(candidate)
     return reply
    } catch (_: GeminiModelUnavailable) {
     continue
    }
   }
   throw IllegalStateException("Gemini model unavailable. Open Settings and reload Gemini models.")
  }
 }

 private fun request(url: String, header: String, key: String, body: JSONObject? = null): ByteArray {
  val c = URL(url).openConnection() as HttpURLConnection
  try {
   c.connectTimeout = 20000
   c.readTimeout = 60000
   c.setRequestProperty(header, key)
   if (body != null) {
    c.requestMethod = "POST"
    c.doOutput = true
    c.setRequestProperty("Content-Type", "application/json")
    c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
   }
   if (c.responseCode !in 200..299) {
    val code = c.responseCode
    if (code == 404 && header == "x-goog-api-key" && url.endsWith(":generateContent")) throw GeminiModelUnavailable()
    val detail = runCatching {
     val body = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
     JSONObject(body).optJSONObject("detail")?.optString("status").orEmpty()
    }.getOrDefault("")
    val message = when {
     header != "xi-api-key" -> "Gemini HTTP $code"
     detail == "missing_permissions" && url.contains("/v2/voices") -> "Enable Voices → Read for this ElevenLabs key, or enter a Voice ID."
     detail == "missing_permissions" -> "Enable Text to Speech → Access for this ElevenLabs key."
     detail == "quota_exceeded" || code == 429 -> "ElevenLabs usage limit reached."
     code == 401 || code == 403 -> "ElevenLabs rejected this key or its access. Check the saved key and permissions."
     else -> "ElevenLabs HTTP $code"
    }
    throw IllegalStateException(message)
   }
   return c.inputStream.use { it.readBytes() }
  } finally { c.disconnect() }
 }
 fun models(key: String): List<String> {
  val result = mutableListOf<String>()
  var page = ""
  do {
   val suffix = if (page.isEmpty()) "" else "&pageToken=" + java.net.URLEncoder.encode(page, "UTF-8")
   val json = JSONObject(String(request("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100"+suffix,"x-goog-api-key",key)))
   val items = json.optJSONArray("models") ?: JSONArray()
   for(i in 0 until items.length()) {
    val m = items.getJSONObject(i)
    val methods = m.optJSONArray("supportedGenerationMethods") ?: JSONArray()
    if ((0 until methods.length()).any { methods.optString(it) == "generateContent" }) result.add(m.getString("name"))
   }
   page = json.optString("nextPageToken")
  } while(page.isNotEmpty())
  return result.distinct()
 }
 fun chat(key: String, model: String, prompt: String, audio: java.io.File? = null): String {
  require(model.matches(Regex("models/[A-Za-z0-9._-]+")))
  val body = JSONObject().put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text","You are Maya, a personal assistant. Reply naturally in the user's language, preferably Urdu or Roman Urdu. Never claim to have performed phone actions unless confirmed.")))).put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt)))))
  if (audio != null) {
   val data = JSONObject().put("mimeType", "audio/m4a")
    .put("data", Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP))
   body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
    .put(JSONObject().put("inlineData", data))
  }
  val json = JSONObject(String(request("https://generativelanguage.googleapis.com/v1beta/"+model+":generateContent","x-goog-api-key",key,body)))
  val parts = json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
  return (0 until parts.length()).joinToString("\n"){parts.getJSONObject(it).optString("text")}.trim()
 }
 class TranscriptionAccessDenied : IllegalStateException("ElevenLabs denied Speech to Text access")

 fun transcribe(key: String, file: java.io.File): String {
  val boundary = java.util.UUID.randomUUID().toString()
  val connection = URL("https://api.elevenlabs.io/v1/speech-to-text").openConnection() as HttpURLConnection
  try {
   connection.requestMethod = "POST"
   connection.doOutput = true
   connection.connectTimeout = 20000
   connection.readTimeout = 60000
   connection.setRequestProperty("xi-api-key", key)
   connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
   java.io.DataOutputStream(connection.outputStream).use { out ->
    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"model_id\"\r\n\r\nscribe_v2\r\n")
    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"clip.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n")
    file.inputStream().use { it.copyTo(out) }
    out.writeBytes("\r\n--$boundary--\r\n")
   }
   if (connection.responseCode == 401 || connection.responseCode == 403) throw TranscriptionAccessDenied()
   check(connection.responseCode in 200..299) { "ElevenLabs transcription HTTP " + connection.responseCode }
   val result = connection.inputStream.bufferedReader().use { JSONObject(it.readText()).getString("text").trim() }
   check(result.isNotEmpty()) { "No speech detected" }
   return result
  } finally { connection.disconnect() }
 }
 fun femaleVoice(key: String): String {
  var token = ""
  do {
   val suffix = if (token.isBlank()) "" else "&next_page_token=" + java.net.URLEncoder.encode(token, "UTF-8")
   val response = JSONObject(String(request("https://api.elevenlabs.io/v2/voices?page_size=100" + suffix, "xi-api-key", key)))
   val voices = response.getJSONArray("voices")
   val candidates = (0 until voices.length()).map { voices.getJSONObject(it) }
    .filter { it.optJSONObject("labels")?.optString("gender").equals("female", ignoreCase = true) }
   val selected = candidates.firstOrNull { it.optString("name").substringBefore(" -").trim().equals("Jessica", ignoreCase = true) } ?: candidates.firstOrNull { it.optJSONObject("labels")?.optString("language") in listOf("ur", "Urdu") } ?: candidates.firstOrNull()
   if (selected != null) return selected.getString("voice_id")
   token = if (response.optBoolean("has_more")) response.optString("next_page_token") else ""
  } while (token.isNotBlank())
  error("No female voice available. Enter a voice ID from your account.")
 }
 fun speak(key: String, voice: String, text: String): ByteArray {
  require(voice.matches(Regex("[A-Za-z0-9_-]+")))
  return request("https://api.elevenlabs.io/v1/text-to-speech/$voice", "xi-api-key", key,
   JSONObject().put("text", text).put("model_id", "eleven_v3").put("language_code", "ur"))
 }

 data class SpeechCue(val startMs: Int, val endMs: Int, val character: String)
 data class SpeechAudio(val bytes: ByteArray, val cues: List<SpeechCue>)

 fun geminiSpeech(key: String, text: String): SpeechAudio {
  val voice = JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Kore"))
  val config = JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
   .put("speechConfig", JSONObject().put("voiceConfig", voice))
  val parts = JSONArray().put(JSONObject().put("text", "Read the following message naturally in Pakistani Urdu, without adding an introduction:\n$text"))
  val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
   .put("generationConfig", config)
  val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent"
  val response = JSONObject(request(url, "x-goog-api-key", key, body).toString(Charsets.UTF_8))
  val output = response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
  val audio = (0 until output.length()).firstNotNullOfOrNull { output.getJSONObject(it).optJSONObject("inlineData") }
   ?: error("Gemini returned no speech audio")
  val pcm = Base64.decode(audio.getString("data"), Base64.DEFAULT)
  val mime = audio.optString("mimeType")
  check(mime.startsWith("audio/L16", ignoreCase = true) && pcm.isNotEmpty() && pcm.size % 2 == 0) {
   "Gemini returned an unsupported speech format"
  }
  val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
  val wav = java.nio.ByteBuffer.allocate(44 + pcm.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
  wav.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
  wav.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
  wav.put("data".toByteArray()).putInt(pcm.size).put(pcm)
  return SpeechAudio(wav.array(), emptyList())
 }

 fun timedSpeech(key: String, voice: String, text: String): SpeechAudio {
  require(voice.matches(Regex("[A-Za-z0-9_-]+")))
  val body = JSONObject().put("text", text).put("model_id", "eleven_v3").put("language_code", "ur")
  val endpoint = "https://api.elevenlabs.io/v1/text-to-speech/$voice"
  val response = JSONObject(request("$endpoint/with-timestamps", "xi-api-key", key, body).toString(Charsets.UTF_8))
  val alignment = response.optJSONObject("alignment") ?: response.optJSONObject("normalized_alignment")
  val cues = alignment?.let {
   val characters = it.getJSONArray("characters")
   val starts = it.getJSONArray("character_start_times_seconds")
   val ends = it.getJSONArray("character_end_times_seconds")
   (0 until minOf(characters.length(), starts.length(), ends.length())).map { index ->
    SpeechCue((starts.getDouble(index) * 1000).toInt(), (ends.getDouble(index) * 1000).toInt(), characters.getString(index))
   }
  }.orEmpty()
  return SpeechAudio(Base64.decode(response.getString("audio_base64"), Base64.DEFAULT), cues)
 }
}
