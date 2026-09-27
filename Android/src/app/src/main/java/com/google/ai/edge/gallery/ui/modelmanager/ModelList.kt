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

package com.google.ai.edge.gallery.ui.modelmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.supportModelBenchmark
import com.google.ai.edge.gallery.ui.common.ClickableLink
import com.google.ai.edge.gallery.ui.common.TaskIcon
import com.google.ai.edge.gallery.ui.common.modelitem.ModelItem
import com.google.ai.edge.gallery.ui.common.rememberDelayedAnimationProgress

/** A focused model library for a particular task. */
@Composable
fun ModelList(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  contentPadding: PaddingValues,
  enableAnimation: Boolean,
  onModelClicked: (Model) -> Unit,
  onBenchmarkClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
) {
  val allModels by
    remember(task) {
      derivedStateOf {
        task.updateTrigger.value
        task.models.toList()
      }
    }
  val models = allModels.filter { !it.isVariant && !it.downloadInfo.imported }
  val importedModels = allModels.filter { !it.isVariant && it.downloadInfo.imported }
  val modelVariants =
    allModels.filter { it.isVariant }.groupBy { it.hierarchy.parentModelName.orEmpty() }
  val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
  val progress =
    if (enableAnimation) {
      rememberDelayedAnimationProgress(
        initialDelay = 0,
        animationDurationMs = 220,
        animationLabel = "models",
      )
    } else 1f

  LazyColumn(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding =
      PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = contentPadding.calculateTopPadding() + 16.dp,
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
      ),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "task_header") {
      Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            TaskIcon(task = task, width = 44.dp)
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                task.label,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
              )
              Text(
                "${models.size + importedModels.size} models available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Text(
            task.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (task.experimental) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Text(
                stringResource(R.string.model_list_experimental_label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }
          if (task.docUrl.isNotEmpty() || task.sourceCodeUrl.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (task.docUrl.isNotEmpty()) {
                ClickableLink(
                  task.docUrl,
                  linkText = stringResource(R.string.api_doc),
                  icon = Icons.Outlined.Description,
                )
              }
              if (task.sourceCodeUrl.isNotEmpty()) {
                ClickableLink(
                  task.sourceCodeUrl,
                  linkText = stringResource(R.string.example_code),
                  icon = Icons.Outlined.Code,
                )
              }
            }
          }
        }
      }
    }
    if (models.isNotEmpty()) {
      item(key = "recommended_title") {
        Text(
          stringResource(R.string.model_list_recommended_models_title),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
        )
      }
    }
    items(models, key = { "model_${it.name}" }) { model ->
      ModelItem(
        model = model,
        modelVariants = modelVariants.getOrDefault(model.name, emptyList()),
        task = task,
        modelManagerViewModel = modelManagerViewModel,
        onModelClicked = onModelClicked,
        onBenchmarkClicked = onBenchmarkClicked,
        expanded = expandedStates.getOrDefault(model.name, false),
        onExpanded = { expandedStates[model.name] = it },
        isBenchmarkSupported = model.supportModelBenchmark,
        modifier = Modifier.graphicsLayer { alpha = progress },
      )
    }
    if (importedModels.isNotEmpty()) {
      item(key = "imported_title") {
        Text(
          stringResource(R.string.model_list_imported_models_title),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
        )
      }
    }
    items(importedModels, key = { "imported_${it.name}" }) { model ->
      ModelItem(
        model = model,
        modelVariants = modelVariants.getOrDefault(model.name, emptyList()),
        task = task,
        modelManagerViewModel = modelManagerViewModel,
        onModelClicked = onModelClicked,
        onBenchmarkClicked = onBenchmarkClicked,
        expanded = true,
        isBenchmarkSupported = model.supportModelBenchmark,
        modifier = Modifier.graphicsLayer { alpha = progress },
      )
    }
  }
}
