/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.agent.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSessionsTest {
  private val owner = ConversationOwner("main-screen", "chat", "model-one")

  @Test
  fun returningToTheSameMountedConversationKeepsItsSession() {
    val sessions = ConversationSessions()
    val first = sessions.claim(owner)
    val second = sessions.claim(owner)

    assertTrue(first.changed)
    assertFalse(second.changed)
    assertEquals(first.sessionId, second.sessionId)
    assertTrue(sessions.owns(owner))
  }

  @Test
  fun changingModelTaskOrScreenCannotReuseAnotherConversationsHistory() {
    for (otherOwner in listOf(
      owner.copy(modelName = "model-two"),
      owner.copy(taskId = "other-chat"),
      owner.copy(viewModelId = "deep-link-screen"),
    )) {
      val sessions = ConversationSessions()
      val first = sessions.claim(owner)
      val next = sessions.claim(otherOwner)

      assertTrue(next.changed)
      assertNotEquals(first.sessionId, next.sessionId)
      assertEquals(owner, next.previousOwner)
      assertFalse(sessions.owns(owner))
      assertTrue(sessions.owns(otherOwner))
    }
  }

  @Test
  fun restoringASavedConversationKeepsItsIdOnTheNextClaim() {
    val sessions = ConversationSessions()
    sessions.claim(owner)
    sessions.activeSessionId = "saved-history"

    val claim = sessions.claim(owner)

    assertFalse(claim.changed)
    assertEquals("saved-history", claim.sessionId)
  }

  @Test
  fun deletingInactiveHistoryDoesNotReplaceTheCurrentConversation() {
    val sessions = ConversationSessions()
    val first = sessions.claim(owner)
    val next = sessions.claim(owner.copy(modelName = "model-two"))

    sessions.replaceSessionIfCurrent(first.sessionId)
    assertEquals(next.sessionId, sessions.activeSessionId)

    sessions.replaceSessionIfCurrent(next.sessionId)
    assertNotEquals(next.sessionId, sessions.activeSessionId)
  }
}
