/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.security

import android.content.Context
import com.google.ai.edge.gallery.customtasks.scrapbook.CutoutMediaStorage
import com.google.ai.edge.gallery.ui.common.chat.ChatMediaStorage

/** Must finish after user authentication and before opening the app's private content. */
object PrivateMediaMigration {
  fun migrate(context: Context) {
    ChatMediaStorage.migrateLegacyFiles(context)
    CutoutMediaStorage.migrateLegacyFiles(context)
  }
}
