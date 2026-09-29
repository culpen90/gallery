// Modified for the Gallery Android fork (Beta 5).
/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.common.chat

import com.google.ai.edge.gallery.proto.ChatMessageProto
import com.google.ai.edge.gallery.proto.ChatSideProto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioHistoryTest {
  @Test
  fun savingRecordingKeepsSampleRateAndIntentMetadataTogether() = runBlocking {
    val pcm = byteArrayOf(0, 1, 2, 3)
    for (mode in AudioInputMode.entries) {
      val request = AudioInputRequest(mode, "Answer in Spanish.")
      val path = "/private/audio_audio-test.pcm.enc"
      val original = ChatMessageAudioClip(pcm, 16000, ChatSide.USER,
        persistedPath = path, audioInputRequest = request)
      val saved = checkNotNull(ChatMessageMapper.serializeMessage(original, "audio-test"))
      assertEquals(path, saved.audioClipsList.single().filePath)
      assertEquals(16000, saved.audioClipsList.single().sampleRate)
      assertEquals(mode.name, saved.audioInputMode)
      assertEquals(request.typedPrompt, saved.audioInputContext)
    }
  }

  @Test
  fun oldAudioModeLabelIsRemovedAndItsIntentMovesToTheClip() {
    val request = AudioInputRequest(AudioInputMode.VOICE_CHAT, "")
    val clip = ChatMessageAudioClip(byteArrayOf(0, 1), 16000, ChatSide.USER)
    val label = ChatMessageText("Talk to the model", ChatSide.USER, data = request)

    val restored = normalizeAudioInputMessages(listOf(clip, label)).single() as ChatMessageAudioClip

    assertEquals(request, restored.audioInputRequest)
    assertArrayEquals(clip.audioData, restored.audioData)
  }

  @Test
  fun oldAudioTurnPreservesWrittenContextOnce() {
    val context = "A planning meeting"
    val request = AudioInputRequest(AudioInputMode.TRANSCRIBE, context)
    val clip = ChatMessageAudioClip(byteArrayOf(0, 1), 16000, ChatSide.USER)
    val text = ChatMessageText(context, ChatSide.USER, data = request)

    val restored = normalizeAudioInputMessages(listOf(clip, text))

    assertEquals(2, restored.size)
    assertEquals(request, (restored[0] as ChatMessageAudioClip).audioInputRequest)
    assertEquals(context, (restored[1] as ChatMessageText).content)
    assertNull((restored[1] as ChatMessageText).data)
  }

  @Test
  fun missingRecordingNeverLeavesBehindAnInstructionToListenToIt() {
    val label = ChatMessageText("Talk to the model", ChatSide.USER,
      data = AudioInputRequest(AudioInputMode.VOICE_CHAT, ""))

    assertEquals(emptyList<ChatMessage>(), normalizeAudioInputMessages(listOf(label)))
  }

  @Test
  fun savingAndRestoringPreservesAudioIntentWithoutChangingVisibleText() = runBlocking {
    for (mode in AudioInputMode.entries) {
      val request = AudioInputRequest(mode, "A planning meeting")
      val original =
        ChatMessageText(content = "Audio recording", side = ChatSide.USER, data = request)

      val saved = checkNotNull(ChatMessageMapper.serializeMessage(original, sessionId = "audio-test"))
      val restored = ChatMessageMapper.deserializeProtoMessage(saved) as ChatMessageText

      assertEquals("Audio recording", restored.content)
      assertEquals(request, restored.data)
      assertEquals(ChatSide.USER, restored.side)
    }
  }

  @Test
  fun oldSavedTextMessagesRemainOrdinaryConversation() = runBlocking {
    val oldMessage =
      ChatMessageProto.newBuilder()
        .setMessageType("TEXT")
        .setContent("Hello")
        .setSide(ChatSideProto.CHAT_SIDE_USER)
        .build()

    val restored = ChatMessageMapper.deserializeProtoMessage(oldMessage) as ChatMessageText

    assertEquals("Hello", restored.content)
    assertNull(restored.data)
  }

  @Test
  fun unknownAudioModesDoNotCrashHistoryRestoration() {
    assertNull(restoreAudioInputRequest("FUTURE_AUDIO_MODE", "context"))
    assertNull(restoreAudioInputRequest("", ""))
  }
}
