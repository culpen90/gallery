/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.tools

import com.google.ai.edge.gallery.proto.Skill
import com.google.ai.edge.gallery.skills.NoOpSkillsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptionToolPolicyTest {
  @Test
  fun transcriptionRejectsSkillLoadingBeforeTheProviderIsCalled() {
    var providerWasCalled = false
    val provider =
      object : NoOpSkillsProvider() {
        override suspend fun loadSkill(skillName: String): Skill? {
          providerWasCalled = true
          return null
        }
      }
    val tool = LoadSkillTool(provider)
    tool.onAttach(ToolExecutionContext(taskId = "chat", allowTools = false))

    val result = tool.loadSkill("mobile-actions")

    assertEquals("failed", result["status"])
    assertFalse(providerWasCalled)
  }

  @Test
  fun ordinaryVoiceTurnAfterTranscriptionCanUseToolsAgain() {
    val tool = LoadSkillTool(NoOpSkillsProvider())
    tool.onAttach(ToolExecutionContext(taskId = "chat", allowTools = false))
    assertEquals("failed", tool.toolExecutionDeniedResult()?.get("status"))

    tool.onAttach(ToolExecutionContext(taskId = "chat", allowTools = true))

    assertNull(tool.toolExecutionDeniedResult())
  }

  @Test
  fun defaultExecutionContextPreservesExistingSkillBehavior() {
    val tool = LoadSkillTool(NoOpSkillsProvider())
    tool.onAttach(ToolExecutionContext(taskId = "chat"))

    assertNull(tool.toolExecutionDeniedResult())
  }
}
