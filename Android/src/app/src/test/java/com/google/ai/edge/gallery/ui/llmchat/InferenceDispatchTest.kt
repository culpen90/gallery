/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.llmchat

import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InferenceDispatchTest {
  @Test
  fun nativeRejectionBeforeCallbacksBecomesAnErrorInsteadOfEscapingToTheApp() {
    val failure = IllegalArgumentException("Provided less audio than expected in the prompt.")
    val callback = RecordingCallback()

    dispatchInference(callback) { throw failure }

    assertEquals(1, callback.errors.size)
    assertSame(failure, callback.errors.single())
  }

  @Test
  fun startedInferenceKeepsItsNormalCallbackLifecycle() {
    val callback = RecordingCallback()

    dispatchInference(callback) {
      it.onMessage(Message.model("12"))
      it.onDone()
    }

    assertEquals(listOf("12"), callback.messages)
    assertEquals(1, callback.completions)
    assertEquals(emptyList<Throwable>(), callback.errors)
  }

  private class RecordingCallback : MessageCallback {
    val errors = mutableListOf<Throwable>()
    val messages = mutableListOf<String>()
    var completions = 0
    override fun onMessage(message: Message) { messages.add(message.toString()) }
    override fun onDone() { completions++ }
    override fun onError(throwable: Throwable) { errors.add(throwable) }
  }
}
