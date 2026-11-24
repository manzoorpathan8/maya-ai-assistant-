package com.maya.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.ActivityNotFoundException

object PhoneActions {
 private fun launch(context: Context, intent: Intent): Boolean {
  return try {
   context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   true
  } catch (e: ActivityNotFoundException) { false }
    catch (e: SecurityException) { false }
 }
 fun browser(context: Context, url: String): Boolean {
  val uri = Uri.parse(url)
  if (uri.scheme != "https" && uri.scheme != "http") return false
  return launch(context, Intent(Intent.ACTION_VIEW, uri))
 }
 fun dial(context: Context, number: String): Boolean =
  launch(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
 fun whatsappDraft(context: Context, number: String, message: String): Boolean {
  val digits = number.filter { it in '0'..'9' }
  if (digits.isEmpty()) return false
  return browser(context, "https://wa.me/" + digits + "?text=" + Uri.encode(message))
 }
 fun systemSettings(context: Context): Boolean =
  launch(context, Intent(Settings.ACTION_SETTINGS))
 fun appSettings(context: Context, packageName: String): Boolean =
  launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
 fun openApp(context: Context, packageName: String): Boolean {
  val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
  return launch(context, intent)
 }
}
