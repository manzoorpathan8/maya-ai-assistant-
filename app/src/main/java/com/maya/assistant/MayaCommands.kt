package com.maya.assistant

import java.util.Locale

internal object MayaCommands {
 private val destinations = mapOf(
  "browser" to "Browser", "براؤزر" to "Browser",
  "whatsapp" to "WhatsApp", "واٹس ایپ" to "WhatsApp",
  "settings" to "Settings", "سیٹنگز" to "Settings",
  "apps" to "Apps", "ایپس" to "Apps",
  "tasks" to "Tasks", "کام" to "Tasks",
  "mobile control" to "Mobile Control", "فون کنٹرول" to "Mobile Control"
 )

 fun webSearch(input: String): String? {
  val text = input.trim()
  val prefixes = listOf("search the web for ", "search web for ", "google search ", "گوگل پر تلاش کرو ")
  val prefix = prefixes.firstOrNull { text.startsWith(it, ignoreCase = true) } ?: return null
  return text.drop(prefix.length).trim().takeIf { it.isNotEmpty() }
 }

 fun destination(input: String): String? {
  val command = input.trim().lowercase(Locale.ROOT)
   .trimEnd('.', '!', '؟', '۔')
   .removePrefix("open ")
   .removeSuffix(" kholo")
   .removeSuffix(" کھولو")
   .trim()
  return destinations[command]
 }

 fun internationalNumber(input: String): String? {
  val number = input.trim()
  if (number.any { it !in '0'..'9' && it !in "+ ()-" }) return null
  if ('+' in number.removePrefix("+")) return null
  return number.filter { it in '0'..'9' }.takeIf { it.length in 7..15 && !it.startsWith('0') }
 }
}
