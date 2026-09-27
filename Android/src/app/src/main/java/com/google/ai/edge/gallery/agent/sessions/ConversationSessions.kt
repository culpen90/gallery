/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.agent.sessions

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** A mounted conversation, including the screen instance that owns its visible messages. */
data class ConversationOwner(val viewModelId: String, val taskId: String, val modelName: String)

data class ConversationClaim(
  val sessionId: String,
  val changed: Boolean,
  val previousOwner: ConversationOwner?,
)

/** Separates conversations when the active screen, task, or model changes. */
class ConversationSessions(private val newSessionId: () -> String = { UUID.randomUUID().toString() }) {
  private data class State(val owner: ConversationOwner?, val sessionId: String?)
  private val state = AtomicReference(State(null, newSessionId()))

  var activeSessionId: String?
    get() = state.get().sessionId
    set(value) {
      state.updateAndGet { it.copy(sessionId = value) }
    }

  fun claim(owner: ConversationOwner): ConversationClaim {
    while (true) {
      val previous = state.get()
      if (previous.owner == owner && previous.sessionId != null) {
        return ConversationClaim(previous.sessionId, changed = false, previousOwner = previous.owner)
      }
      val sessionId = newSessionId()
      if (state.compareAndSet(previous, State(owner, sessionId))) {
        return ConversationClaim(sessionId, changed = true, previousOwner = previous.owner)
      }
    }
  }

  fun owns(owner: ConversationOwner): Boolean = state.get().owner == owner

  fun replaceSessionIfCurrent(sessionId: String) {
    while (true) {
      val previous = state.get()
      if (previous.sessionId != sessionId) return
      if (state.compareAndSet(previous, previous.copy(sessionId = newSessionId()))) return
    }
  }
}
