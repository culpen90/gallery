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

package com.google.ai.edge.gallery.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.preferredChatTask
import com.google.ai.edge.gallery.data.unifiedChatModels
import com.google.ai.edge.gallery.ui.common.tos.AppTosDialog
import com.google.ai.edge.gallery.ui.common.tos.TosViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/** The app opens directly into a conversation; capabilities live inside the composer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  modelManagerViewModel: ModelManagerViewModel,
  tosViewModel: TosViewModel,
  onModelsClicked: () -> Unit,
  onNotificationsClicked: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by modelManagerViewModel.uiState.collectAsState()
  var showSettings by remember { mutableStateOf(false) }
  var showMenu by remember { mutableStateOf(false) }
  var showTos by remember { mutableStateOf(!tosViewModel.getIsTosAccepted()) }
  var lastModelName by rememberSaveable { mutableStateOf("") }
  val models = unifiedChatModels(uiState.tasks)
  val selectedModel = models.firstOrNull { it.name == uiState.selectedModel.name }
  val initialModel =
    selectedModel
      ?: models.firstOrNull { it.name == lastModelName }
      ?: models.firstOrNull {
        uiState.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED
      }
      ?: models.firstOrNull()
  val chatTask = initialModel?.let { preferredChatTask(uiState.tasks, it) }

  LaunchedEffect(initialModel?.name, showTos) {
    if (!showTos && initialModel != null) {
      lastModelName = initialModel.name
      modelManagerViewModel.selectModel(initialModel)
    }
  }

  if (!showTos && selectedModel != null && chatTask != null) {
    key(chatTask.id) {
      modelManagerViewModel
        .getCustomTaskByTaskId(chatTask.id)
        ?.MainScreen(
          CustomTaskDataForBuiltinTask(
            modelManagerViewModel = modelManagerViewModel,
            onNavUp = { showMenu = true },
            unifiedInterface = true,
          )
        )
    }
  } else {
    Scaffold(
      modifier = modifier,
      topBar = {
        TopAppBar(
          title = {
            Text(
              stringResource(R.string.redesign_gallery),
              style = MaterialTheme.typography.titleLarge,
            )
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            ),
          navigationIcon = {
            IconButton(onClick = { showMenu = true }) {
              Icon(Icons.Rounded.Menu, stringResource(R.string.unified_chat_menu))
            }
          },
        )
      },
    ) { padding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (uiState.loadingModelAllowlist && uiState.loadingModelAllowlistError.isEmpty()) {
          CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
          Text(
            stringResource(R.string.redesign_loading),
            modifier = Modifier.padding(top = 16.dp),
          )
        } else {
          Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(bottom = 28.dp).size(88.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Rounded.AutoAwesome,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
              )
            }
          }
          Text(
            stringResource(R.string.redesign_get_started),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
          )
          Text(
            stringResource(R.string.redesign_setup_detail),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.widthIn(max = 420.dp).padding(top = 16.dp, bottom = 28.dp),
          )
          Button(
            onClick = onModelsClicked,
            enabled = !showTos,
            modifier = Modifier.heightIn(min = 52.dp),
          ) {
            Text(stringResource(R.string.unified_chat_choose_model))
          }
        }
      }
    }
  }

  if (showMenu) {
    ModalBottomSheet(
      onDismissRequest = { showMenu = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          stringResource(R.string.redesign_gallery),
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          stringResource(R.string.redesign_tagline),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 12.dp),
        )
        NavigationRow(
          Icons.Rounded.Layers,
          stringResource(R.string.redesign_menu_models),
          stringResource(R.string.redesign_menu_models_detail),
        ) {
          showMenu = false
          onModelsClicked()
        }
        NavigationRow(
          Icons.Rounded.NotificationsNone,
          stringResource(R.string.unified_chat_notifications),
          stringResource(R.string.redesign_menu_notifications_detail),
        ) {
          showMenu = false
          onNotificationsClicked()
        }
        NavigationRow(
          Icons.Rounded.Settings,
          stringResource(R.string.drawer_settings_label),
          stringResource(R.string.redesign_menu_settings_detail),
        ) {
          showMenu = false
          showSettings = true
        }
        Text(
          stringResource(R.string.redesign_local_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 12.dp),
        )
      }
    }
  }

  if (showTos) {
    AppTosDialog(
      onTosAccepted = {
        tosViewModel.acceptTos()
        showTos = false
      }
    )
  }
  if (showSettings) {
    SettingsDialog(
      curThemeOverride = modelManagerViewModel.readThemeOverride(),
      curFirebaseAnalytics = modelManagerViewModel.readFirebaseAnalytics(),
      modelManagerViewModel = modelManagerViewModel,
      onDismissed = { showSettings = false },
    )
  }
  if (uiState.loadingModelAllowlistError.isNotEmpty()) {
    AlertDialog(
      title = { Text(uiState.loadingModelAllowlistError) },
      text = { Text(stringResource(R.string.error_internet_connection)) },
      onDismissRequest = { modelManagerViewModel.clearLoadModelAllowlistError() },
      confirmButton = {
        TextButton(onClick = { modelManagerViewModel.loadModelAllowlist() }) {
          Text(stringResource(R.string.retry))
        }
      },
      dismissButton = {
        TextButton(onClick = { modelManagerViewModel.clearLoadModelAllowlistError() }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}

@Composable
private fun NavigationRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        Icons.Rounded.ChevronRight,
        null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}
