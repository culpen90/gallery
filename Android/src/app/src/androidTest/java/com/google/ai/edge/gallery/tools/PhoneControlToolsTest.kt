/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.tools

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.skills.NoOpSkillsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneControlToolsTest {
  @Test
  fun transcriptionCannotInvokeAnyDirectPhoneAction() {
    val intentTool = RunIntentTool(ApplicationProvider.getApplicationContext(), NoOpSkillsProvider())
    val phoneTools = PhoneControlTools(intentTool)
    phoneTools.onAttach(ToolExecutionContext(taskId = "chat", allowTools = false))

    val results =
      listOf(
        phoneTools.turnOnFlashlight(),
        phoneTools.turnOffFlashlight(),
        phoneTools.openWifiSettings(),
        phoneTools.showLocationOnMap("Boston"),
        phoneTools.createContact("Ada", "Lovelace", "", ""),
        phoneTools.sendEmail("ada@example.com", "Hello", "Draft content"),
        phoneTools.getCurrentDateAndTime(),
        phoneTools.createCalendarEvent("Meeting", "", "2026-10-01T09:00:00", "2026-10-01T10:00:00"),
      )

    for (result in results) {
      assertEquals("failed", result["status"])
      assertEquals(
        "Tools are disabled for this transcription. Return only the spoken words.",
        result["error"],
      )
    }
  }

  @Test
  fun newVoiceTurnClearsTheSharedTranscriptionRestriction() {
    val intentTool = RunIntentTool(ApplicationProvider.getApplicationContext(), NoOpSkillsProvider())
    val phoneTools = PhoneControlTools(intentTool)
    phoneTools.onAttach(ToolExecutionContext(taskId = "chat", allowTools = false))
    assertEquals("failed", intentTool.toolExecutionDeniedResult()?.get("status"))

    phoneTools.onAttach(ToolExecutionContext(taskId = "chat", allowTools = true))

    assertNull(phoneTools.toolExecutionDeniedResult())
    assertNull(intentTool.toolExecutionDeniedResult())
  }
}
