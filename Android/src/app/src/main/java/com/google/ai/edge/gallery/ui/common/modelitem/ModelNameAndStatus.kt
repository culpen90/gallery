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

package com.google.ai.edge.gallery.ui.common.modelitem

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.MODEL_INFO_ICON_SIZE
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelCapability
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.ClickableLink
import com.google.ai.edge.gallery.ui.common.humanReadableSize
import com.google.ai.edge.gallery.ui.theme.customColors

/**
 * Composable function to display the model name and its download status information.
 *
 * This function renders the model's name and its current download status, including:
 * - Model name.
 * - Failure message (if download failed).
 * - "Unzipping..." status for unzipping processes.
 * - Model size for successful downloads.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun ModelNameAndStatus(
  model: Model,
  task: Task?,
  downloadStatus: ModelDownloadStatus?,
  isExpanded: Boolean,
  modifier: Modifier = Modifier,
  showModelSizeAndDownloadProgressLabel: Boolean = true,
) {
  var showUpdateDialog by remember { mutableStateOf(false) }

  Column(modifier = modifier) {
    // Show "best overall" only for the first model if it is indeed the best for this task.
    if (task != null && model.bestForTaskIds.contains(task.id) && task.models[0] == model) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 6.dp),
      ) {
        Icon(
          Icons.Filled.Star,
          tint = MaterialTheme.colorScheme.primary,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Text(
          stringResource(R.string.best_overall),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Show "Update available" info message label if the model is updatable.
    // Tap to show the detailed update info in a dialog.
    if (downloadStatus?.isUpdatable == true) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
          Modifier.heightIn(min = 48.dp)
            .padding(bottom = 8.dp)
            .then(
              if (model.downloadInfo.updateInfo.isNotEmpty()) {
                Modifier.clickable(role = Role.Button) { showUpdateDialog = true }
              } else {
                Modifier
              }
            ),
      ) {
        Icon(
          Icons.Filled.Info,
          tint = MaterialTheme.colorScheme.primary,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Text(
          stringResource(R.string.update_available),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (showUpdateDialog) {
      AlertDialog(
        onDismissRequest = { showUpdateDialog = false },
        title = { Text(stringResource(R.string.about_this_update)) },
        text = { Text(model.downloadInfo.updateInfo) },
        confirmButton = {
          TextButton(onClick = { showUpdateDialog = false }) {
            Text(stringResource(android.R.string.ok))
          }
        },
      )
    }

    // Model name and action buttons.
    Text(
      model.displayName.ifEmpty { model.name },
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(end = 64.dp),
    )

    // Status icon + size + download progress details.
    if (!model.isAiCore && showModelSizeAndDownloadProgressLabel) {
      ModelStatusDetails(
        model = model,
        task = task,
        downloadStatus = downloadStatus,
        isExpanded = isExpanded,
        modifier = Modifier.padding(top = 4.dp),
      )
    }

    val capabilityLabels = buildList {
      if (model.isLlm) add("Text")
      if (model.supportImage) add("Images")
      if (model.supportAudio) add("Audio")
      if (ModelCapability.LLM_THINKING in model.capabilities) add("Reasoning")
    }
    if (capabilityLabels.isNotEmpty()) {
      FlowRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        for (label in capabilityLabels) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
          ) {
            Text(
              label,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }
    }

    // Learn more url.
    if (isExpanded && !model.downloadInfo.imported && model.learnMoreUrl.isNotEmpty()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          Icons.AutoMirrored.Outlined.OpenInNew,
          tint = MaterialTheme.customColors.modelInfoIconColor,
          contentDescription = null,
          modifier = Modifier.size(MODEL_INFO_ICON_SIZE).offset(y = 1.dp),
        )
        ClickableLink(
          model.learnMoreUrl,
          linkText = stringResource(R.string.learn_more),
          textAlign = TextAlign.Left,
        )
      }
    }
  }
}

@Composable
fun ModelStatusDetails(
  model: Model,
  task: Task?,
  downloadStatus: ModelDownloadStatus?,
  isExpanded: Boolean,
  modifier: Modifier = Modifier,
) {
  val inProgress = downloadStatus?.status == ModelDownloadStatusType.IN_PROGRESS
  val isPartiallyDownloaded = downloadStatus?.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED
  var curDownloadProgress = 0f

  Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
    // Status icon.
    StatusIcon(
      task = task,
      model = model,
      downloadStatus = downloadStatus,
      modifier = Modifier.padding(end = 4.dp),
    )

    // Failure message.
    if (downloadStatus != null && downloadStatus.status == ModelDownloadStatusType.FAILED) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          downloadStatus.errorMessage,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    // Status label
    else {
      var sizeLabel = model.downloadInfo.totalBytes.humanReadableSize()
      if (model.downloadInfo.localRelativeDirPathOverride.isNotEmpty()) {
        sizeLabel = "Stored on device"
      }

      // Populate the status label.
      if (downloadStatus != null) {
        // For in-progress model, show {receivedSize} / {totalSize} - {rate} - {remainingTime}
        if (inProgress || isPartiallyDownloaded) {
          var totalSize = downloadStatus.totalBytes
          if (totalSize == 0L) {
            totalSize = model.downloadInfo.totalBytes
          }
          sizeLabel =
            "${downloadStatus.receivedBytes.humanReadableSize(extraDecimalForGbAndAbove = true)} of ${totalSize.humanReadableSize()}"
          if (downloadStatus.bytesPerSecond > 0) {
            sizeLabel = "$sizeLabel · ${downloadStatus.bytesPerSecond.humanReadableSize()} / s"
            // if (downloadStatus.remainingMs >= 0) {
            //   sizeLabel =
            //     "$sizeLabel\n${downloadStatus.remainingMs.formatToHourMinSecond()} left"
            // }
          }
          if (isPartiallyDownloaded) {
            sizeLabel = "$sizeLabel (resuming...)"
          }
          curDownloadProgress =
            downloadStatus.receivedBytes.toFloat() / downloadStatus.totalBytes.toFloat()
          if (curDownloadProgress.isNaN()) {
            curDownloadProgress = 0f
          }
        } else if (downloadStatus.status == ModelDownloadStatusType.SUCCEEDED) {
          sizeLabel = "Ready · $sizeLabel"
        }
        // Status for unzipping.
        else if (downloadStatus.status == ModelDownloadStatusType.UNZIPPING) {
          sizeLabel = "Preparing model…"
        }
      }

      Column(horizontalAlignment = Alignment.Start) {
        for ((index, line) in sizeLabel.split("\n").withIndex()) {
          Text(
            line,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            style =
              MaterialTheme.typography.bodyMedium.copy(
                // This stops numbers from "jumping around" when being updated.
                fontFeatureSettings = "tnum"
              ),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(y = if (index == 0) 0.dp else (-1).dp),
          )
        }
      }
    }
  }
}
