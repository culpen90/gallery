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

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.diagnostics.DiagnosticsRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NOTE_KEY = "diagnosticsNote"
private const val EXPORT_NOTE_KEY = "diagnosticsExportNote"
private const val PICKER_PENDING_KEY = "diagnosticsPickerPending"
private const val WRITING_KEY = "diagnosticsWriting"

/** Keeps the issue note and document picker request through activity recreation. */
@HiltViewModel
class DiagnosticsViewModel
@Inject
constructor(private val application: Application, private val savedState: SavedStateHandle) :
  ViewModel() {
  val note = savedState.getStateFlow(NOTE_KEY, "")
  val pickerPending = savedState.getStateFlow(PICKER_PENDING_KEY, false)
  private val _writing = MutableStateFlow(false)
  val writing = _writing.asStateFlow()
  private val _message =
    MutableStateFlow<Int?>(
      if (savedState.get<Boolean>(WRITING_KEY) == true) R.string.diagnostics_save_interrupted
      else null
    )
  val message = _message.asStateFlow()

  init {
    // A new process cannot continue an old coroutine. A surviving ViewModel keeps its active work.
    savedState[WRITING_KEY] = false
  }

  fun setNote(value: String) {
    savedState[NOTE_KEY] = value.take(2000)
  }

  fun beginSave(): Boolean {
    if (_writing.value || pickerPending.value) return false
    savedState[EXPORT_NOTE_KEY] = note.value
    savedState[PICKER_PENDING_KEY] = true
    _message.value = null
    return true
  }

  fun pickerUnavailable() {
    savedState[PICKER_PENDING_KEY] = false
    _message.value = R.string.diagnostics_picker_unavailable
  }

  fun saveTo(uri: Uri?) {
    savedState[PICKER_PENDING_KEY] = false
    if (uri == null) {
      _message.value = R.string.diagnostics_save_cancelled
      return
    }
    if (_writing.value) return
    val exportNote: String = savedState[EXPORT_NOTE_KEY] ?: note.value
    _writing.value = true
    savedState[WRITING_KEY] = true
    _message.value = null
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) {
          var archive: File? = null
          try {
            val readyArchive = DiagnosticsRecorder.createExport(application, exportNote)
            archive = readyArchive
            writeDiagnosticsArchive(readyArchive) {
              application.contentResolver.openOutputStream(uri, "w")
            }
          } catch (error: Exception) {
            // The picker created a new document. Remove a partial ZIP when its provider allows it.
            runCatching { DocumentsContract.deleteDocument(application.contentResolver, uri) }
            throw error
          } finally {
            archive?.delete()
          }
        }
        _message.value = R.string.diagnostics_save_success
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        _message.value = R.string.diagnostics_save_failed
      } finally {
        _writing.value = false
        savedState[WRITING_KEY] = false
      }
    }
  }
}
