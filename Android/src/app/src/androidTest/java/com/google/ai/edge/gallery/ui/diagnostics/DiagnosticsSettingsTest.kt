/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.diagnostics

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.diagnostics.DiagnosticsRecorder
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsSettingsTest {
  @get:Rule val composeRule = createComposeRule()
  private val application = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var viewModel: DiagnosticsViewModel
  private var initiallyRecording = false

  @Before
  fun prepare() {
    DiagnosticsRecorder.initialize(application)
    composeRule.waitUntil(15_000) { !DiagnosticsRecorder.state.value.isPreparing }
    initiallyRecording = DiagnosticsRecorder.state.value.isRecording
    DiagnosticsRecorder.setRecording(true)
    composeRule.waitUntil(15_000) { DiagnosticsRecorder.state.value.isRecording }
    viewModel = DiagnosticsViewModel(application, SavedStateHandle())
  }

  @After
  fun restoreRecording() {
    DiagnosticsRecorder.setRecording(initiallyRecording)
    composeRule.waitUntil(15_000) {
      DiagnosticsRecorder.state.value.isRecording == initiallyRecording
    }
  }

  @Test
  fun togglePausesCaptureAndShowsPausedStatus() {
    showSettings()
    composeRule
      .onNodeWithContentDescription(application.getString(R.string.diagnostics_recording_toggle))
      .performScrollTo()
      .performClick()
    composeRule.waitUntil(15_000) { !DiagnosticsRecorder.state.value.isRecording }
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_paused))
      .assertIsDisplayed()
  }

  @Test
  fun notesSurviveCompositionRestorationAndCancelledSaveCanBeRetried() {
    val restoration = StateRestorationTester(composeRule)
    restoration.setContent {
      MaterialTheme {
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          DiagnosticsSettings(viewModel)
        }
      }
    }
    val note = "Voice response stopped after changing models."
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_note_label))
      .performScrollTo()
      .performTextInput(note)
    restoration.emulateSavedInstanceStateRestore()
    composeRule.onNodeWithText(note).performScrollTo().assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(note, viewModel.note.value)
      viewModel.beginSave()
    }
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_save_zip))
      .performScrollTo()
      .assertIsNotEnabled()
    // Mirrors CreateDocument returning RESULT_CANCELED; no external picker or file is opened.
    composeRule.runOnIdle { viewModel.saveTo(null) }
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_save_cancelled))
      .performScrollTo()
      .assertIsDisplayed()
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_save_zip))
      .performScrollTo()
      .assertIsEnabled()
    composeRule.runOnIdle { assertEquals(note, viewModel.note.value) }
  }

  @Test
  fun saveWritesReadableZipBeforeReportingSuccess() {
    showSettings()
    val destination = File(application.cacheDir, "diagnostics-save-test-${UUID.randomUUID()}.zip")
    try {
      composeRule.runOnIdle {
        viewModel.setNote("Reproduction steps from the export test")
        viewModel.beginSave()
        viewModel.saveTo(Uri.fromFile(destination))
      }
      composeRule.waitUntil(15_000) { !viewModel.writing.value }
      composeRule
        .onNodeWithText(application.getString(R.string.diagnostics_save_success))
        .performScrollTo()
        .assertIsDisplayed()
      ZipFile(destination).use { zip ->
        val note =
          zip.getInputStream(zip.getEntry("issue-note.txt")).bufferedReader().use { it.readText() }
        assertEquals("Reproduction steps from the export test", note)
      }
    } finally {
      destination.delete()
    }
  }

  @Test
  fun unavailableDocumentProviderReportsFailureAndAllowsRetry() {
    showSettings()
    composeRule.runOnIdle {
      viewModel.beginSave()
      viewModel.saveTo(
        Uri.parse("content://com.google.ai.edge.gallery.nonexistent-test-provider/export.zip")
      )
    }
    composeRule.waitUntil(15_000) { !viewModel.writing.value }
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_save_failed))
      .performScrollTo()
      .assertIsDisplayed()
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_save_zip))
      .performScrollTo()
      .assertIsEnabled()
  }

  @Test
  fun liveLogViewerOpensAndClearRequiresConfirmation() {
    showSettings()
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_view_logs))
      .performScrollTo()
      .performClick()
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_live_logs))
      .assertIsDisplayed()
    composeRule
      .onNodeWithContentDescription(application.getString(R.string.diagnostics_follow_logs))
      .assertIsDisplayed()
      .performClick()
    composeRule.onNodeWithText(application.getString(R.string.diagnostics_close)).performClick()
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_clear_logs))
      .performScrollTo()
      .performClick()
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_clear_title))
      .assertIsDisplayed()
    composeRule.onNodeWithText(application.getString(R.string.diagnostics_cancel)).performClick()
    composeRule
      .onNodeWithText(application.getString(R.string.diagnostics_clear_title))
      .assertDoesNotExist()
  }

  private fun showSettings() {
    composeRule.setContent {
      MaterialTheme {
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          DiagnosticsSettings(viewModel)
        }
      }
    }
  }
}
