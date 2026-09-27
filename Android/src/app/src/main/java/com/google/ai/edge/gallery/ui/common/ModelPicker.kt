/*
 * Copyright 2025 Google LLC
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

package com.google.ai.edge.gallery.ui.common

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.preview.PreviewModelManagerViewModel
// import com.google.ai.edge.gallery.ui.preview.TASK_TEST1
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.modelitem.StatusIcon
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

@Composable
fun ModelPicker(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  onModelSelected: (Model) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  var showMemoryWarning by remember { mutableStateOf(false) }
  var modelToPick by remember { mutableStateOf<Model?>(null) }
  val context = LocalContext.current

  Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
    Column(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text("Choose a model", style = MaterialTheme.typography.headlineSmall)
      Text(
        "Switch the model for this conversation.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(task.models, key = { it.name }) { model ->
        val selected = model.name == modelManagerUiState.selectedModel.name
        val downloadStatus = modelManagerUiState.modelDownloadStatus[model.name]
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
              )
              .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                  if (isMemoryLow(context = context, model = model)) {
                    modelToPick = model
                    showMemoryWarning = true
                  } else {
                    onModelSelected(model)
                  }
                },
              )
              .heightIn(min = 80.dp)
              .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
          StatusIcon(task = task, model = model, downloadStatus = downloadStatus)
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              model.displayName.ifEmpty { model.name },
              style = MaterialTheme.typography.titleSmall,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              color =
                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            val details = buildList {
              add(
                when (downloadStatus?.status) {
                  ModelDownloadStatusType.SUCCEEDED -> "Ready on device"
                  ModelDownloadStatusType.IN_PROGRESS -> "Downloading…"
                  ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> "Resuming download…"
                  ModelDownloadStatusType.UNZIPPING -> "Preparing model…"
                  ModelDownloadStatusType.FAILED -> "Download needs attention"
                  else -> "Download to use"
                }
              )
              if (!model.isAiCore && model.downloadInfo.sizeInBytes > 0L) {
                add(model.downloadInfo.sizeInBytes.humanReadableSize())
              }
            }
            Text(
              details.joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
              color =
                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val capabilities = buildList {
              if (model.supportImage) add("Images")
              if (model.supportAudio) add("Audio")
            }
            if (capabilities.isNotEmpty()) {
              Text(
                capabilities.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (selected) {
            Icon(
              Icons.Filled.CheckCircle,
              modifier = Modifier.size(24.dp),
              tint = MaterialTheme.colorScheme.primary,
              contentDescription = stringResource(R.string.cd_selected_icon),
            )
          }
        }
      }
    }
  }

  if (showMemoryWarning) {
    MemoryWarningAlert(
      onProceeded = {
        val curModelToPick = modelToPick
        if (curModelToPick != null) {
          onModelSelected(curModelToPick)
        }
        showMemoryWarning = false
      },
      onDismissed = { showMemoryWarning = false },
    )
  }
}

// @Preview(showBackground = true)
// @Composable
// fun ModelPickerPreview() {
//   val context = LocalContext.current

//   GalleryTheme {
//     ModelPicker(
//       task = TASK_TEST1,
//       modelManagerViewModel = PreviewModelManagerViewModel(context = context),
//       onModelSelected = {},
//     )
//   }
// }
