package com.maya.assistant

import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection

object AiClient {
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
   if (c.responseCode !in 200..299) throw IllegalStateException("API HTTP ${c.responseCode}")
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
 fun chat(key: String, model: String, prompt: String): String {
  require(model.matches(Regex("models/[A-Za-z0-9._-]+")))
  val body = JSONObject().put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text","You are Maya, a personal assistant. Reply naturally in the user's language, preferably Urdu or Roman Urdu. Never claim to have performed phone actions unless confirmed.")))).put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt)))))
  val json = JSONObject(String(request("https://generativelanguage.googleapis.com/v1beta/"+model+":generateContent","x-goog-api-key",key,body)))
  val parts = json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
  return (0 until parts.length()).joinToString("\n"){parts.getJSONObject(it).optString("text")}.trim()
 }
 fun speak(key: String, voice: String, text: String): ByteArray {
  require(voice.matches(Regex("[A-Za-z0-9_-]+")))
  return request("https://api.elevenlabs.io/v1/text-to-speech/"+voice,"xi-api-key",key,JSONObject().put("text",text).put("model_id","eleven_v3"))
 }
}
