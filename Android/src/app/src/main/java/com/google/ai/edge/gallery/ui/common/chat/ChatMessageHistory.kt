/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.common.chat

import com.google.ai.edge.litertlm.Message

/** Restores text without inserting unsupported audio into the native conversation preface. */
fun convertToLitertMessages(chatMessages: List<ChatMessage>): List<Message> {
  // LiteRT-LM 0.11 Kotlin initialMessages populate a preface, but the first send supplies only
  // the new message's audio bytes. Audio in that preface therefore fails with "Provided less
  // audio than expected in the prompt." Kotlin exposes no prefill-preface/append-only option.
  // See GetSingleTurnTextFromFullHistory and SendMessageAsync:
  // https://github.com/google-ai-edge/LiteRT-LM/blob/v0.11.0/runtime/conversation/conversation.cc
  // Recordings stay in saved/UI history;
  // only real written context can safely be restored into the model on this SDK.
  val messages = normalizeAudioInputMessages(chatMessages)
  val result = mutableListOf<Message>()
  var index = 0
  while (index < messages.size) {
    val message = messages[index]
    if (message is ChatMessageAudioClip && message.side == ChatSide.USER) {
      val request = message.audioInputRequest
      if (request != null && request.typedPrompt.isNotBlank()) {
        val nextText =
          (messages.getOrNull(index + 1) as? ChatMessageText)?.takeIf {
            it.side == ChatSide.USER && it.content.trim() == request.typedPrompt.trim()
          }
        result.add(Message.user(nextText?.content ?: request.typedPrompt))
        if (nextText != null) index++
      }
    } else if (message is ChatMessageText) {
      when (message.side) {
        ChatSide.USER -> result.add(Message.user(message.content))
        ChatSide.AGENT -> result.add(Message.model(message.content))
        ChatSide.SYSTEM -> {}
      }
    }
    index++
  }
  return result
}

/**
 * Excludes the pending request from replay: executeStream will submit that request once after
 * resetting the stopped session. The composer appends an image bubble, audio clips, then optional
 * text; generation appends the loading placeholder. Counts keep an earlier unanswered user turn.
 */
internal fun historyBeforePendingUserTurn(
  messages: List<ChatMessage>,
  audioClipCount: Int,
  hasImages: Boolean,
): List<ChatMessage> {
  var end = messages.size
  if (messages.getOrNull(end - 1) is ChatMessageLoading) end--
  val lastText = messages.getOrNull(end - 1)
  if (lastText is ChatMessageText && lastText.side == ChatSide.USER) end--
  repeat(audioClipCount) {
    val clip = messages.getOrNull(end - 1)
    if (clip is ChatMessageAudioClip && clip.side == ChatSide.USER) end--
  }
  val image = messages.getOrNull(end - 1)
  if (hasImages && image is ChatMessageImage && image.side == ChatSide.USER) end--
  return messages.take(end)
}
