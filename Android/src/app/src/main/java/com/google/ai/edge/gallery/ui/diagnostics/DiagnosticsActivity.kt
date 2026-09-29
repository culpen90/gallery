// Modified for the Gallery Android fork (Beta 5).
/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.diagnostics

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.diagnostics.DiagnosticsRecorder
import com.google.ai.edge.gallery.security.SecureActivity
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import dagger.hilt.android.AndroidEntryPoint

/** Recovery entry point that deliberately does not initialize the chat, models, or navigation. */
@AndroidEntryPoint
class DiagnosticsActivity : SecureActivity() {
  private val diagnosticsViewModel: DiagnosticsViewModel by viewModels()

  override fun preparePrivateAccess() {
    // Keep recovery independent of a damaged chat/settings vault.
    DiagnosticsRecorder.onUserUnlocked(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setSecureContent {
      GalleryTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          Column(
            modifier =
              Modifier.safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Text(
              stringResource(R.string.diagnostics_title),
              style = MaterialTheme.typography.headlineMedium,
            )
            DiagnosticsSettings(diagnosticsViewModel)
            TextButton(onClick = { finish() }) { Text(stringResource(R.string.diagnostics_close)) }
          }
        }
      }
    }
  }
}
