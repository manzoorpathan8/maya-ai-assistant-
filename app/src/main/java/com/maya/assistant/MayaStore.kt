package com.maya.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MayaStore(context: Context) {
 private val prefs = context.getSharedPreferences("maya_local_data", Context.MODE_PRIVATE)
 data class Entry(val id: String, val text: String, val created: Long, val done: Boolean)
 private val allowed = setOf("memory", "business", "tasks")
 @Synchronized fun list(category: String): List<Entry> {
  require(category in allowed)
  val array = JSONArray(prefs.getString(category, "[]") ?: "[]")
  return (0 until array.length()).map {
   val item = array.getJSONObject(it)
   Entry(item.getString("id"), item.getString("text"), item.getLong("created"), item.optBoolean("done"))
  }
 }
 private fun write(category: String, entries: List<Entry>) {
  require(category in allowed)
  val array = JSONArray()
  entries.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("created", it.created).put("done", it.done)) }
  check(prefs.edit().putString(category, array.toString()).commit())
 }
 @Synchronized fun add(category: String, text: String): Entry {
  require(text.isNotBlank())
  val item = Entry(UUID.randomUUID().toString(), text.trim(), System.currentTimeMillis(), false)
  write(category, list(category) + item)
  return item
 }
 @Synchronized fun update(category: String, id: String, text: String) {
  require(text.isNotBlank())
  write(category, list(category).map { if (it.id == id) it.copy(text = text.trim()) else it })
 }
 @Synchronized fun remove(category: String, id: String) {
  write(category, list(category).filterNot { it.id == id })
 }
 @Synchronized fun complete(id: String, done: Boolean) {
  write("tasks", list("tasks").map { if (it.id == id) it.copy(done = done) else it })
 }
 @Synchronized fun clear(category: String) {
  require(category in allowed)
  check(prefs.edit().remove(category).commit())
 }
}
