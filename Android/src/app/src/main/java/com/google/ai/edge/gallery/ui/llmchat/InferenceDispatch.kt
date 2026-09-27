/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.llmchat

import com.google.ai.edge.litertlm.MessageCallback

/** Native inference may reject a request before it starts delivering asynchronous callbacks. */
internal fun dispatchInference(callback: MessageCallback, send: (MessageCallback) -> Unit) {
  try {
    send(callback)
  } catch (e: Exception) {
    callback.onError(e)
  }
}
