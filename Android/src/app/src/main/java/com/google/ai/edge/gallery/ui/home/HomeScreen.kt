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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
      modelManagerViewModel.getCustomTaskByTaskId(chatTask.id)?.MainScreen(
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
        CenterAlignedTopAppBar(
          title = { Text(stringResource(R.string.unified_chat_title)) },
          navigationIcon = {
            IconButton(onClick = { showMenu = true }) {
              Icon(Icons.Rounded.Menu, stringResource(R.string.unified_chat_menu))
            }
          },
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (uiState.loadingModelAllowlist && uiState.loadingModelAllowlistError.isEmpty()) {
          CircularProgressIndicator()
          Text(
            stringResource(R.string.loading_model_list),
            modifier = Modifier.padding(top = 16.dp),
          )
        } else {
          Text(
            stringResource(R.string.unified_chat_welcome),
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            stringResource(R.string.unified_chat_setup),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
          )
          Button(onClick = onModelsClicked, enabled = !showTos) {
            Text(stringResource(R.string.unified_chat_choose_model))
          }
        }
      }
    }
  }

  if (showMenu) {
    ModalBottomSheet(onDismissRequest = { showMenu = false }) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(stringResource(R.string.unified_chat_title), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { showMenu = false; onModelsClicked() }) {
          Text(stringResource(R.string.drawer_models_label))
        }
        TextButton(onClick = { showMenu = false; onNotificationsClicked() }) {
          Text(stringResource(R.string.unified_chat_notifications))
        }
        TextButton(onClick = { showMenu = false; showSettings = true }) {
          Text(stringResource(R.string.drawer_settings_label))
        }
      }
    }
  }
  if (showTos) {
    AppTosDialog(onTosAccepted = { tosViewModel.acceptTos(); showTos = false })
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
