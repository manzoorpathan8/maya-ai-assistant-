package com.maya.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MayaCommandsTest {
 @Test fun routesEnglishUrduAndRomanUrdu() {
  assertEquals("Browser", MayaCommands.destination(" Open Browser! "))
  assertEquals("WhatsApp", MayaCommands.destination("واٹس ایپ کھولو۔"))
  assertEquals("Apps", MayaCommands.destination("apps kholo"))
 }

 @Test fun arbitraryRequestsDoNotTriggerPhoneActions() {
  assertNull(MayaCommands.destination("How do I open WhatsApp?"))
  assertNull(MayaCommands.destination("Send a WhatsApp message"))
 }

 @Test fun extractsOnlyExplicitSearchCommands() {
  assertEquals("car repair", MayaCommands.webSearch("Search the web for car repair"))
  assertEquals("موسم", MayaCommands.webSearch("گوگل پر تلاش کرو موسم"))
  assertNull(MayaCommands.webSearch("Should I search the web for car repair?"))
 }

 @Test fun formatsInternationalNumbers() {
  assertEquals("923001234567", MayaCommands.internationalNumber("+92 (300) 123-4567"))
  assertNull(MayaCommands.internationalNumber("03001234567"))
  assertNull(MayaCommands.internationalNumber("92+3001234567"))
  assertNull(MayaCommands.internationalNumber("call me on +923001234567"))
 }
}
