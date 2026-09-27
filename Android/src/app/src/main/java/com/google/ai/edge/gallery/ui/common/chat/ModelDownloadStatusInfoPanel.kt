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

package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.DownloadAndTryButton
import com.google.ai.edge.gallery.ui.common.humanReadableSize
import com.google.ai.edge.gallery.ui.common.modelitem.calculateDownloadProgress
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

@Composable
fun ModelDownloadStatusInfoPanel(
  model: Model,
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val curStatus = modelManagerUiState.modelDownloadStatus[model.name]
  ModelSetupContent(model = model, downloadStatus = curStatus) {
    DownloadAndTryButton(
      task = task,
      model = model,
      enabled = true,
      downloadStatus = curStatus?.status,
      downloadProgress = calculateDownloadProgress(downloadStatus = curStatus),
      modelManagerViewModel = modelManagerViewModel,
      modifier = Modifier.fillMaxWidth(),
      downloadButtonBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
      onClicked = {},
      canShowTryIt = false,
    )
  }
}

/**
 * First-run guidance and download state, with the existing download action supplied by the host.
 */
@Composable
fun ModelSetupContent(
  model: Model,
  downloadStatus: ModelDownloadStatus?,
  downloadAction: @Composable () -> Unit,
) {
  val downloading =
    downloadStatus?.status in
      listOf(
        ModelDownloadStatusType.IN_PROGRESS,
        ModelDownloadStatusType.PARTIALLY_DOWNLOADED,
        ModelDownloadStatusType.UNZIPPING,
      )
  val failed = downloadStatus?.status == ModelDownloadStatusType.FAILED
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      modifier =
        Modifier.widthIn(max = 520.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(80.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          if (downloading) {
            CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
          } else {
            Icon(
              Icons.Rounded.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(36.dp),
            )
          }
        }
      }
      Text(
        stringResource(
          if (downloading) R.string.chat_setup_loading_title else R.string.chat_setup_title
        ),
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 24.dp).semantics { heading() },
      )
      Text(
        stringResource(
          if (downloading) R.string.model_download_background_notice
          else R.string.chat_setup_description
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
      )
      Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(
            stringResource(R.string.chat_setup_selected_model),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            model.displayName.ifBlank { model.name },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.Download,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              if (model.downloadInfo.totalBytes > 0) {
                stringResource(
                  R.string.chat_setup_size,
                  model.downloadInfo.totalBytes.humanReadableSize(),
                )
              } else {
                stringResource(R.string.chat_setup_once)
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (downloading && downloadStatus != null) {
            Text(
              if (downloadStatus.status == ModelDownloadStatusType.UNZIPPING) {
                stringResource(R.string.chat_setup_unpacking)
              } else {
                val totalBytes =
                  downloadStatus.totalBytes.takeIf { it > 0 } ?: model.downloadInfo.totalBytes
                stringResource(
                  R.string.chat_setup_progress,
                  downloadStatus.receivedBytes.humanReadableSize(),
                  totalBytes.humanReadableSize(),
                )
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          if (failed) {
            Text(
              downloadStatus?.errorMessage?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.chat_setup_download_failed),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
          }
          downloadAction()
        }
      }
      Row(
        modifier = Modifier.padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Rounded.Smartphone,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          stringResource(R.string.chat_setup_local_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f, fill = false),
        )
      }
    }
  }
}
