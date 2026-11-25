package com.maya.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

class WhatsAppNotifications : NotificationListenerService() {
 override fun onListenerConnected() = refresh()

 override fun onListenerDisconnected() {
  connected.value = false
  messages.value = emptyList()
 }

 override fun onNotificationPosted(notification: StatusBarNotification) {
  if (notification.packageName in packages) refresh()
 }

 override fun onNotificationRemoved(notification: StatusBarNotification) {
  if (notification.packageName in packages) refresh()
 }

 private fun refresh() {
  val active = runCatching { activeNotifications.orEmpty() }.getOrNull()
  connected.value = active != null
  messages.value = active.orEmpty()
   .filter { it.packageName in packages && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
   .sortedByDescending { it.postTime }
   .mapNotNull {
    val extras = it.notification.extras
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
     ?.toString().orEmpty()
    text.takeIf(String::isNotBlank)?.let { message -> listOf(title, message).filter(String::isNotBlank).joinToString("\n") }
   }.take(25)
 }

 companion object {
  private val packages = setOf("com.whatsapp", "com.whatsapp.w4b")
  internal val connected = mutableStateOf(false)
  internal val messages = mutableStateOf(emptyList<String>())
 }
}
