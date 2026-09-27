/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.common.chat

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageHistoryTest {
  @Test
  fun voiceOnlyHistoryDoesNotPutUnsupportedAudioInTheNativePreface() {
    val clip = audioClip(AudioInputRequest(AudioInputMode.VOICE_CHAT, ""))

    val replay = convertToLitertMessages(listOf(clip))

    assertTrue(replay.isEmpty())
  }

  @Test
  fun writtenContextIsRestoredExactlyOnceWithSurroundingText() {
    val request = AudioInputRequest(AudioInputMode.VOICE_CHAT, "  Explain this sound  ")
    val clip = audioClip(request)

    val replay =
      convertToLitertMessages(
        listOf(
          ChatMessageText("Hello", ChatSide.USER),
          ChatMessageText("Hi", ChatSide.AGENT),
          clip,
          ChatMessageText(request.typedPrompt, ChatSide.USER),
          ChatMessageText("It is a bird.", ChatSide.AGENT),
        )
      )

    assertEquals(listOf(Role.USER, Role.MODEL, Role.USER, Role.MODEL), replay.map { it.role })
    assertEquals(request.typedPrompt, (replay[2].contents.contents.single() as Content.Text).text)
    assertEquals("Hello", (replay[0].contents.contents.single() as Content.Text).text)
    assertEquals("It is a bird.", (replay[3].contents.contents.single() as Content.Text).text)
  }

  @Test
  fun transcriptionHistoryRestoresWrittenContextWithoutGeneratedInstructions() {
    val request = AudioInputRequest(AudioInputMode.TRANSCRIBE, "A planning meeting")

    val replay =
      convertToLitertMessages(
        listOf(audioClip(request), ChatMessageText(request.typedPrompt, ChatSide.USER))
      )

    val contents = replay.single().contents.contents
    assertEquals(request.typedPrompt, (contents.single() as Content.Text).text)
  }

  @Test
  fun legacyVoiceModeLabelDoesNotBecomeASpuriousUserMessage() {
    val clip = audioClip()
    val label =
      ChatMessageText(
        content = "Talk to the model",
        side = ChatSide.USER,
        data = AudioInputRequest(AudioInputMode.VOICE_CHAT, ""),
      )

    val replay = convertToLitertMessages(listOf(clip, label))

    assertTrue(replay.isEmpty())
  }

  @Test
  fun legacyAudioKeepsItsExplicitTextPrompt() {
    val replay =
      convertToLitertMessages(
        listOf(audioClip(), ChatMessageText("Describe the recording", ChatSide.USER))
      )

    val contents = replay.single().contents.contents
    assertEquals("Describe the recording", (contents.single() as Content.Text).text)
  }

  @Test
  fun anUnrelatedUserMessageAfterAnAudioOnlyTurnIsPreserved() {
    val replay =
      convertToLitertMessages(
        listOf(
          audioClip(AudioInputRequest(AudioInputMode.VOICE_CHAT, "")),
          ChatMessageText("Actually, never mind", ChatSide.USER),
        )
      )

    assertEquals(
      "Actually, never mind",
      (replay.single().contents.contents.single() as Content.Text).text,
    )
  }

  @Test
  fun nativeHistoryOmitsRecordingsAndGeneratedInstructionsButKeepsFollowingMessages() {
    for (mode in AudioInputMode.entries) {
      val replay =
        convertToLitertMessages(
          listOf(
            audioClip(AudioInputRequest(mode, "")),
            ChatMessageText("Earlier response", ChatSide.AGENT),
            ChatMessageText("A new question", ChatSide.USER),
          )
        )

      assertEquals(listOf(Role.MODEL, Role.USER), replay.map { it.role })
      assertEquals(
        listOf("Earlier response", "A new question"),
        replay.map { (it.contents.contents.single() as Content.Text).text },
      )
    }
  }

  @Test
  fun bothAudioModesPreserveTypedContextExactlyOnce() {
    for (mode in AudioInputMode.entries) {
      val typedPrompt = "  Context the user typed  "
      val replay =
        convertToLitertMessages(
          listOf(
            audioClip(AudioInputRequest(mode, typedPrompt)),
            ChatMessageText(typedPrompt, ChatSide.USER),
          )
        )

      assertEquals(Role.USER, replay.single().role)
      assertEquals(typedPrompt, (replay.single().contents.contents.single() as Content.Text).text)
    }
  }

  @Test
  fun nativeHistoryKeepsStoredContextWithoutASeparateTextBubble() {
    val replay =
      convertToLitertMessages(
        listOf(audioClip(AudioInputRequest(AudioInputMode.TRANSCRIBE, "A planning meeting")))
      )

    assertEquals(
      "A planning meeting",
      (replay.single().contents.contents.single() as Content.Text).text,
    )
  }

  @Test
  fun nativeHistoryMigratesLegacyLabelsWithoutReplayingThem() {
    val replay =
      convertToLitertMessages(
        listOf(
          audioClip(),
          ChatMessageText(
            content = "Transcribe audio",
            side = ChatSide.USER,
            data = AudioInputRequest(AudioInputMode.TRANSCRIBE, "A user note"),
          ),
        )
      )

    assertEquals("A user note", (replay.single().contents.contents.single() as Content.Text).text)
  }

  @Test
  fun stoppedSessionKeepsEarlierHistoryAndExcludesTheWholePendingTurn() {
    val previous =
      listOf(
        audioClip(AudioInputRequest(AudioInputMode.VOICE_CHAT, "")),
        ChatMessageText("First reply", ChatSide.AGENT),
      )
    val pending =
      listOf(
        ChatMessageImage(emptyList(), emptyList(), side = ChatSide.USER),
        audioClip(AudioInputRequest(AudioInputMode.VOICE_CHAT, "Another question")),
        ChatMessageText("Another question", ChatSide.USER),
        ChatMessageLoading(),
      )

    val history = historyBeforePendingUserTurn(previous + pending, audioClipCount = 1, hasImages = true)

    assertEquals(previous, history)
    val replay = convertToLitertMessages(history)
    assertEquals(Role.MODEL, replay.single().role)
    assertEquals("First reply", (replay.single().contents.contents.single() as Content.Text).text)
  }

  @Test
  fun stoppedAudioOnlyTurnDoesNotDiscardAnEarlierUnansweredUserTurn() {
    val previous = listOf(audioClip(AudioInputRequest(AudioInputMode.VOICE_CHAT, "")))
    val pending = listOf(audioClip(), ChatMessageLoading())

    assertEquals(
      previous,
      historyBeforePendingUserTurn(previous + pending, audioClipCount = 1, hasImages = false),
    )
  }

  @Test
  fun stoppedTextTurnExcludesOnlyItsOwnTextAndPlaceholder() {
    val previous = listOf(ChatMessageText("Earlier question", ChatSide.USER))
    val pending = listOf(ChatMessageText("Current question", ChatSide.USER), ChatMessageLoading())

    assertEquals(
      previous,
      historyBeforePendingUserTurn(previous + pending, audioClipCount = 0, hasImages = false),
    )
  }

  private fun audioClip(request: AudioInputRequest? = null) =
    ChatMessageAudioClip(
      audioData = byteArrayOf(0, 0, -1, 127, 0, -128),
      sampleRate = 16000,
      side = ChatSide.USER,
      audioInputRequest = request,
    )
}
