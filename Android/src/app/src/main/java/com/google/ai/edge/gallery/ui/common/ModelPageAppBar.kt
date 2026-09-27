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

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.GalleryEvent
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.agentchat.agentSkillTopK
import com.google.ai.edge.gallery.customtasks.agentchat.agentSkillTopKAdjusted
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelCapability
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.convertValueToTargetType
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.litertlm.Capabilities

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPageAppBar(
  task: Task,
  model: Model,
  modelManagerViewModel: ModelManagerViewModel,
  onBackClicked: () -> Unit,
  onModelSelected: (prev: Model, cur: Model) -> Unit,
  inProgress: Boolean,
  modelPreparing: Boolean,
  modifier: Modifier = Modifier,
  hideModelSelector: Boolean = false,
  unifiedInterface: Boolean = false,
  useThemeColor: Boolean = false,
  onConfigChanged: (oldConfigValues: Map<String, Any>, newConfigValues: Map<String, Any>) -> Unit =
    { _, _ ->
    },
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  shouldShowHistoryButton: Boolean = false,
  onHistoryClicked: (Model) -> Unit = {},
) {
  var showConfigDialog by remember { mutableStateOf(false) }
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val context = LocalContext.current
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[model.name]
  val initStatus by model.initStatusFlow.collectAsState()
  val isModelInitializing = initStatus is Model.InitializationStatus.Initializing
  val isModelInitialized = initStatus is Model.InitializationStatus.Initialized

  Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
    TopAppBar(
      title = {
        Text(
          if (unifiedInterface) stringResource(R.string.redesign_gallery) else task.label,
          style = MaterialTheme.typography.titleLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      colors =
        TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      navigationIcon = {
        IconButton(onClick = onBackClicked, enabled = !isModelInitializing && !inProgress) {
          Icon(
            imageVector =
              if (unifiedInterface) Icons.Rounded.Menu else Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription =
              stringResource(
                if (unifiedInterface) R.string.unified_chat_menu else R.string.cd_navigate_back_icon
              ),
          )
        }
      },
      actions = {
        val downloadSucceeded = curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
        if (downloadSucceeded && shouldShowHistoryButton) {
          IconButton(
            onClick = { onHistoryClicked(model) },
            enabled = !isModelInitializing && !modelPreparing && !inProgress && isModelInitialized,
          ) {
            Icon(
              Icons.Rounded.History,
              contentDescription = stringResource(R.string.cd_chat_history),
            )
          }
        }
        if (model.configs.isNotEmpty() && downloadSucceeded) {
          IconButton(
            onClick = { showConfigDialog = true },
            enabled = !isModelInitializing && !inProgress && isModelInitialized,
          ) {
            Icon(
              Icons.Rounded.Tune,
              contentDescription = stringResource(R.string.cd_model_settings_icon),
            )
          }
        }
      },
    )
    if (!hideModelSelector) {
      Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
        ModelPickerChip(
          enabled = !isModelInitializing && !inProgress,
          task = task,
          initialModel = model,
          modelManagerViewModel = modelManagerViewModel,
          onModelSelected = onModelSelected,
        )
      }
    }
  }

  // Config dialog.
  if (showConfigDialog) {
    // Remove the reset conversation turn count config for non-tiny-garden tasks.
    //
    // This may happen when user imports a model with "enable tiny garden" turned on and use the
    // model in another non-tiny-garden task.
    val modelConfigs = model.configs.toMutableList()
    if (task.id != BuiltInTaskId.LLM_TINY_GARDEN) {
      modelConfigs.removeIf { it.key == ConfigKeys.RESET_CONVERSATION_TURN_COUNT }
    }
    if (!task.allowCapability(ModelCapability.LLM_THINKING, model)) {
      modelConfigs.removeIf { it.key == ConfigKeys.ENABLE_THINKING }
    }
    var supportsSpeculativeDecoding = false
    // Check if the model file supports speculative decoding.
    try {
      Capabilities(model.getPath(context)).use {
        supportsSpeculativeDecoding = it.hasSpeculativeDecodingSupport()
      }
    } catch (e: Exception) {
      // Ignore exceptions and assume not supported.
    }
    if (
      !supportsSpeculativeDecoding ||
        !task.allowCapability(ModelCapability.SPECULATIVE_DECODING, model)
    ) {
      modelConfigs.removeIf { it.key == ConfigKeys.ENABLE_SPECULATIVE_DECODING }
    }
    ConfigDialog(
      title = stringResource(R.string.config_dialog_title),
      configs = modelConfigs,
      initialValues = model.configValues,
      onDismissed = { showConfigDialog = false },
      onOk = { curConfigValues, oldSystemPrompt, newSystemPrompt ->
        // Hide config dialog.
        showConfigDialog = false

        // Check if the configs are changed or not. Also check if the model needs to be
        // re-initialized.
        var same = true
        var needReinitialization = false
        for (config in modelConfigs) {
          val key = config.key.label
          val oldValue =
            convertValueToTargetType(
              value = model.configValues.getValue(key),
              valueType = config.valueType,
            )
          val newValue =
            convertValueToTargetType(
              value = curConfigValues.getValue(key),
              valueType = config.valueType,
            )
          if (oldValue != newValue) {
            same = false
            if (config.needReinitialization) {
              needReinitialization = true
            }
            break
          }
        }
        val systemPromptChanged = newSystemPrompt != oldSystemPrompt

        if (!same || systemPromptChanged) {
          firebaseAnalytics?.logEvent(
            GalleryEvent.MODEL_CONFIG_CHANGE.id,
            Bundle().apply {
              putString("model_id", model.name)
              putString("capability_name", task.id)
              putString("model_version", model.downloadInfo.version)
              putString("app_version", BuildConfig.VERSION_NAME)
            },
          )
        }

        if (same) {
          if (systemPromptChanged) {
            onSystemPromptChanged(newSystemPrompt)
          }
          return@ConfigDialog
        }

        // Save the config values to Model.
        val oldConfigValues = model.configValues
        model.prevConfigValues = oldConfigValues
        model.configValues = curConfigValues
        if (task.id == BuiltInTaskId.LLM_AGENT_CHAT) {
          model.agentSkillTopKAdjusted = true
          model.agentSkillTopK = curConfigValues[ConfigKeys.TOPK.label]
        }
        modelManagerViewModel.updateConfigValuesUpdateTrigger()

        if (!task.handleModelConfigChangesInTask) {
          // Force to re-initialize the model with the new configs.
          if (needReinitialization) {
            modelManagerViewModel.initializeModel(
              context = context,
              task = task,
              model = model,
              force = true,
              onDone = {
                if (oldSystemPrompt != newSystemPrompt) {
                  onSystemPromptChanged(newSystemPrompt)
                }
              },
            )
          }

          // Notify.
          onConfigChanged(oldConfigValues, model.configValues)
        }
      },
      // AICore doesn't support system prompt yet.
      showSystemPromptEditorTab = allowEditingSystemPrompt && !model.isAiCore,
      defaultSystemPrompt = task.defaultSystemPrompt,
      curSystemPrompt = curSystemPrompt,
    )
  }
}
