/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.common.chat

import com.google.ai.edge.gallery.common.AudioClip
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioComposerTest {
  private val recording = AudioClip(byteArrayOf(0, 1, 2, 3), 16000)

  @Test
  fun voiceOnlySendContainsTheRecordingAndNoFabricatedTextBubble() {
    val messages = createMessagesToSend(emptyList(), listOf(recording), "", AudioInputMode.VOICE_CHAT)

    val clip = messages.single() as ChatMessageAudioClip
    assertArrayEquals(recording.audioData, clip.audioData)
    assertEquals(recording.sampleRate, clip.sampleRate)
    assertEquals(AudioInputRequest(AudioInputMode.VOICE_CHAT, ""), clip.audioInputRequest)
    assertEquals(clip.audioInputRequest, clip.clone().audioInputRequest)
  }

  @Test
  fun transcriptionIntentIsRetainedWithoutPretendingTheUserTypedItsLabel() {
    val messages = createMessagesToSend(emptyList(), listOf(recording), "", AudioInputMode.TRANSCRIBE)

    val clip = messages.single() as ChatMessageAudioClip
    assertEquals(AudioInputMode.TRANSCRIBE, clip.audioInputRequest?.mode)
    assertTrue(audioInputPrompt(clip.audioInputRequest!!.mode, "").contains("Return only the transcript"))
  }

  @Test
  fun writtenContextIsTheOnlyTextShownAlongsideAnAudioClip() {
    val context = "Answer in Spanish."
    val messages = createMessagesToSend(emptyList(), listOf(recording), context, AudioInputMode.VOICE_CHAT)

    assertEquals(2, messages.size)
    assertEquals(context, (messages[0] as ChatMessageAudioClip).audioInputRequest?.typedPrompt)
    assertEquals(context, (messages[1] as ChatMessageText).content)
  }

  @Test
  fun selectedAudioModeDoesNotChangeAnOrdinaryTypedMessage() {
    val messages = createMessagesToSend(emptyList(), emptyList(), "Hello", AudioInputMode.TRANSCRIBE)

    assertEquals("Hello", (messages.single() as ChatMessageText).content)
  }
}
