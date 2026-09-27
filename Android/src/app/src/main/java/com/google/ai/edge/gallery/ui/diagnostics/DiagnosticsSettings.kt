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

package com.google.ai.edge.gallery.ui.diagnostics

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.diagnostics.DiagnosticsRecorder

/** Local beta capture controls. Saving always uses the system document picker. */
@Composable
fun DiagnosticsSettings(viewModel: DiagnosticsViewModel) {
  val capture by DiagnosticsRecorder.state.collectAsState()
  val note by viewModel.note.collectAsState()
  val writing by viewModel.writing.collectAsState()
  val pickerPending by viewModel.pickerPending.collectAsState()
  val message by viewModel.message.collectAsState()
  var showLogs by rememberSaveable { mutableStateOf(false) }
  var showClear by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current
  val busy = writing || pickerPending || capture.isPreparing
  val saveLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
      uri ->
      viewModel.saveTo(uri)
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        stringResource(
          when {
            capture.isPreparing -> R.string.diagnostics_preparing
            capture.isRecording -> R.string.diagnostics_recording
            else -> R.string.diagnostics_paused
          }
        ),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        stringResource(
          R.string.diagnostics_retained_size,
          Formatter.formatShortFileSize(context, capture.storedBytes),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    val recordingLabel = stringResource(R.string.diagnostics_recording_toggle)
    Switch(
      checked = capture.isRecording,
      enabled = !capture.isPreparing,
      onCheckedChange = { DiagnosticsRecorder.setRecording(it) },
      modifier = Modifier.semantics { contentDescription = recordingLabel },
    )
  }
  if (capture.hasRecoveredCrash) {
    Text(
      stringResource(R.string.diagnostics_recovered_crash),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary,
    )
  }
  Text(
    stringResource(R.string.diagnostics_description),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Text(
    stringResource(R.string.diagnostics_native_status, capture.logcatStatus),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  capture.lastError?.let {
    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
  }
  OutlinedButton(onClick = { showLogs = true }, modifier = Modifier.fillMaxWidth()) {
    Text(stringResource(R.string.diagnostics_view_logs))
  }
  OutlinedTextField(
    value = note,
    onValueChange = viewModel::setNote,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(stringResource(R.string.diagnostics_note_label)) },
    supportingText = { Text(stringResource(R.string.diagnostics_note_hint)) },
    minLines = 2,
    maxLines = 4,
    enabled = !busy,
    shape = RoundedCornerShape(16.dp),
  )
  Button(
    onClick = {
      if (viewModel.beginSave()) {
        try {
          saveLauncher.launch("gallery-diagnostics-${System.currentTimeMillis()}.zip")
        } catch (_: Exception) {
          viewModel.pickerUnavailable()
        }
      }
    },
    enabled = !busy,
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
  ) {
    Text(stringResource(R.string.diagnostics_save_zip))
  }
  Text(
    stringResource(R.string.diagnostics_export_while_recording),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  if (writing) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
      Text(stringResource(R.string.diagnostics_saving), style = MaterialTheme.typography.bodySmall)
    }
  }
  message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
  TextButton(onClick = { showClear = true }, enabled = !busy) {
    Text(stringResource(R.string.diagnostics_clear_logs))
  }
  if (showClear) {
    AlertDialog(
      onDismissRequest = { showClear = false },
      title = { Text(stringResource(R.string.diagnostics_clear_title)) },
      text = { Text(stringResource(R.string.diagnostics_clear_detail)) },
      confirmButton = {
        TextButton(
          onClick = {
            DiagnosticsRecorder.clear()
            showClear = false
          }
        ) {
          Text(stringResource(R.string.diagnostics_clear_logs))
        }
      },
      dismissButton = {
        TextButton(onClick = { showClear = false }) {
          Text(stringResource(R.string.diagnostics_cancel))
        }
      },
    )
  }
  if (showLogs) {
    DiagnosticsLogDialog(
      lines = capture.recentLines,
      sessionId = capture.sessionId,
      onDismiss = { showLogs = false },
    )
  }
}

@Composable
private fun DiagnosticsLogDialog(lines: List<String>, sessionId: String, onDismiss: () -> Unit) {
  val listState = rememberLazyListState()
  var followNewLogs by rememberSaveable { mutableStateOf(true) }
  LaunchedEffect(lines, followNewLogs) {
    if (followNewLogs && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
  }
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(16.dp),
      shape = RoundedCornerShape(24.dp),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          stringResource(R.string.diagnostics_live_logs),
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          stringResource(R.string.diagnostics_session, sessionId),
          style = MaterialTheme.typography.labelSmall,
        )
        Text(
          stringResource(R.string.diagnostics_live_detail, lines.size),
          style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(R.string.diagnostics_follow_logs),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
          )
          val followLabel = stringResource(R.string.diagnostics_follow_logs)
          Switch(
            checked = followNewLogs,
            onCheckedChange = { followNewLogs = it },
            modifier = Modifier.semantics { contentDescription = followLabel },
          )
        }
        SelectionContainer(modifier = Modifier.weight(1f)) {
          LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lines.isEmpty()) {
              item { Text(stringResource(R.string.diagnostics_no_logs)) }
            }
            items(lines) { line ->
              Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
          Text(stringResource(R.string.diagnostics_close))
        }
      }
    }
  }
}
