/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.common.chat

import com.google.ai.edge.gallery.proto.ChatMessageProto
import com.google.ai.edge.gallery.proto.ChatSideProto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioHistoryTest {
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
