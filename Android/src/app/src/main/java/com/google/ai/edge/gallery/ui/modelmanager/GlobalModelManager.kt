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

package com.google.ai.edge.gallery.ui.modelmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.preferredChatTask
import com.google.ai.edge.gallery.data.supportModelBenchmark
import com.google.ai.edge.gallery.huggingface.extractHfUrlInfo
import com.google.ai.edge.gallery.proto.HfModelItemProto
import com.google.ai.edge.gallery.proto.ImportedModel
import com.google.ai.edge.gallery.ui.common.TaskIcon
import com.google.ai.edge.gallery.ui.common.isHttpOrHttps
import com.google.ai.edge.gallery.ui.common.modelitem.ModelItem
import com.google.ai.edge.gallery.ui.common.tos.TosViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGGlobalMM"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalModelManager(
  viewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  onModelSelected: (Task, Model) -> Unit,
  onBenchmarkClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
  tosViewModel: TosViewModel? = null,
  unifiedInterface: Boolean = false,
) {
  val uiState by viewModel.uiState.collectAsState()
  val builtInModels = remember { mutableStateListOf<Model>() }
  val importedModels = remember { mutableStateListOf<Model>() }
  val taskCandidates = remember { mutableStateListOf<Task>() }
  var modelForTaskCandidate by remember { mutableStateOf<Model?>(null) }
  var showTaskSelectorBottomSheet by remember { mutableStateOf(false) }
  var showImportModelSheet by remember { mutableStateOf(false) }
  var showHfExploreScreen by remember { mutableStateOf(false) }
  var showHuggingFaceUrlDialog by remember { mutableStateOf(false) }
  var huggingFaceUrlInput by remember { mutableStateOf("") }
  var showUnsupportedModelDialog by remember { mutableStateOf(false) }
  var unsupportedModelErrorMessage by remember { mutableStateOf("") }
  val selectedLocalModelFileUri = remember { mutableStateOf<Uri?>(null) }
  val selectedImportedModelInfo = remember { mutableStateOf<ImportedModel?>(null) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showImportDialog by remember { mutableStateOf(false) }
  var showImportingDialog by remember { mutableStateOf(false) }
  var selectedModelForDetails by remember { mutableStateOf<HfModelItemProto?>(null) }
  var showModelDetailsSheet by remember { mutableStateOf(false) }
  var isLoadingModelCardDetails by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val modelItemExpandedStates = remember { mutableStateMapOf<String, Boolean>() }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var libraryFilter by rememberSaveable { mutableStateOf("All models") }

  val processModelUri: (Uri, Boolean) -> Unit = { uri, isWebImport ->
    validateAndProcessModelUri(
      uri = uri,
      context = context,
      isWebImport = isWebImport,
      onUnsupportedModelError = { errorMessage ->
        unsupportedModelErrorMessage = errorMessage
        showUnsupportedModelDialog = true
      },
      onValidModelUri = { validUri ->
        selectedLocalModelFileUri.value = validUri
        showImportDialog = true
      },
    )
  }

  val filePickerLauncher: ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri -> processModelUri(uri, /* isWebImport= */ false) }
          ?: run { Log.d(TAG, "No file selected or URI is null.") }
      } else {
        Log.d(TAG, "File picking cancelled.")
      }
    }

  LaunchedEffect(
    uiState.modelImportingUpdateTrigger,
    uiState.loadingModelAllowlist,
    unifiedInterface,
  ) {
    val allowlistModels = viewModel.allowlistModels
    val allowlistOrderMap = allowlistModels.withIndex().associate { it.value.name to it.index }

    val sortedModels =
      viewModel
        .getAllModels()
        // Filter to include only top-level models (those without a parent).
        .filter { !it.isVariant }
        .filter { !unifiedInterface || preferredChatTask(uiState.tasks, it) != null }
        .sortedWith(
          compareBy<Model> { model ->
              // Sort by the index in allowlistModels. Models not in the allowlist come last.
              allowlistOrderMap[model.name] ?: Int.MAX_VALUE
            }
            .thenBy { model ->
              // If not in the allowlist, sort by their names.
              model.name
            }
        )
    builtInModels.clear()
    builtInModels.addAll(sortedModels.filter { !it.downloadInfo.imported })
    importedModels.clear()
    importedModels.addAll(sortedModels.filter { it.downloadInfo.imported })
  }

  // Calculate model variants by grouping models with a parentModelName.
  val modelVariants by
    remember(uiState.modelImportingUpdateTrigger) {
      derivedStateOf {
        val allModels = uiState.tasks.flatMap { it.models }.distinct()
        allModels.filter { it.isVariant }.groupBy { it.hierarchy.parentModelName.orEmpty() }
      }
    }

  val handleClickModel: (Model) -> Unit = { model ->
    val tasks = viewModel.uiState.value.tasks
    val tasksForModel = tasks.filter { task -> task.models.any { it.name == model.name } }
    // If there is only one task for the model, navigate to the model directly.
    if (unifiedInterface) {
      preferredChatTask(tasks, model)?.let { onModelSelected(it, model) }
    } else if (tasksForModel.size == 1) {
      onModelSelected(tasksForModel[0], model)
    }
    // If there are multiple tasks for the model, show a bottom sheet for the user to choose which
    // task to use.
    else if (tasksForModel.size > 1) {
      taskCandidates.clear()
      taskCandidates.addAll(tasksForModel)
      modelForTaskCandidate = model
      showTaskSelectorBottomSheet = true
    }
  }

  // Handle system's edge swipe.
  BackHandler { navigateUp() }

  fun isOnDevice(model: Model): Boolean =
    (listOf(model) + modelVariants.getOrDefault(model.name, emptyList())).any {
      uiState.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED
    }

  fun matchesLibraryFilter(model: Model): Boolean {
    val matchesSearch =
      searchQuery.isBlank() ||
        (listOf(model) + modelVariants.getOrDefault(model.name, emptyList())).any {
          it.name.contains(searchQuery.trim(), ignoreCase = true) ||
            it.displayName.contains(searchQuery.trim(), ignoreCase = true)
        }
    return matchesSearch &&
      when (libraryFilter) {
        "On device" -> isOnDevice(model)
        "Imported" -> model.downloadInfo.imported
        else -> true
      }
  }
  val visibleBuiltInModels = builtInModels.filter(::matchesLibraryFilter)
  val visibleImportedModels = importedModels.filter(::matchesLibraryFilter)
  val readyCount = (builtInModels + importedModels).count(::isOnDevice)

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text(
            "Model library",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
          )
        },
        navigationIcon = {
          IconButton(onClick = navigateUp) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_icon))
          }
        },
        actions = {
          IconButton(onClick = { showImportModelSheet = true }) {
            Icon(
              Icons.Filled.Add,
              contentDescription = stringResource(R.string.cd_import_model_button),
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
    ) {
      item(key = "library_overview") {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(28.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Rounded.CheckCircle,
                null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                "$readyCount on device",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Text(
              "Find your next\nthinking partner.",
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              "Choose a model for your chats. Download once, then use it on your device.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = { showHfExploreScreen = true },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
              ) {
                Icon(Icons.Rounded.Explore, null, modifier = Modifier.size(18.dp))
                Text("Explore", modifier = Modifier.padding(start = 8.dp))
              }
              OutlinedButton(
                onClick = { showImportModelSheet = true },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
              ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Text("Import", modifier = Modifier.padding(start = 8.dp))
              }
            }
          }
        }
      }
      item(key = "library_search") {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          placeholder = { Text("Search your models") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(20.dp),
        )
      }
      item(key = "library_filters") {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (filter in listOf("All models", "On device", "Imported")) {
            FilterChip(
              selected = libraryFilter == filter,
              onClick = { libraryFilter = filter },
              label = { Text(filter) },
              modifier = Modifier.heightIn(min = 48.dp),
            )
          }
        }
      }
      if (visibleBuiltInModels.isNotEmpty()) {
        item(key = "curated_models_label") {
          LibrarySectionLabel("Available models", visibleBuiltInModels.size)
        }
      }
      items(visibleBuiltInModels, key = { "built_in_${it.name}" }) { model ->
        ModelItem(
          model = model,
          modelVariants = modelVariants.getOrDefault(model.name, listOf()),
          task = null,
          modelManagerViewModel = viewModel,
          onModelClicked = handleClickModel,
          onBenchmarkClicked = onBenchmarkClicked,
          expanded = modelItemExpandedStates.getOrDefault(model.name, false),
          isBenchmarkSupported = model.supportModelBenchmark,
          showBenchmarkActionButton = false,
          onExpanded = { modelItemExpandedStates[model.name] = it },
          tosViewModel = tosViewModel,
        )
      }
      if (visibleImportedModels.isNotEmpty()) {
        item(key = "imported_models_label") {
          LibrarySectionLabel("Your imports", visibleImportedModels.size)
        }
      }
      items(visibleImportedModels, key = { "imported_${it.name}" }) { model ->
        ModelItem(
          model = model,
          modelVariants = modelVariants.getOrDefault(model.name, emptyList()),
          task = null,
          modelManagerViewModel = viewModel,
          onModelClicked = handleClickModel,
          onBenchmarkClicked = onBenchmarkClicked,
          expanded = true,
          isBenchmarkSupported = model.supportModelBenchmark,
          showBenchmarkActionButton = false,
          tosViewModel = tosViewModel,
        )
      }
      if (visibleBuiltInModels.isEmpty() && visibleImportedModels.isEmpty()) {
        item(key = "library_empty") {
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            when {
              uiState.loadingModelAllowlist -> {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                Text("Loading your models…", style = MaterialTheme.typography.bodyMedium)
              }
              uiState.loadingModelAllowlistError.isNotBlank() -> {
                Icon(
                  Icons.Rounded.Error,
                  null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(32.dp),
                )
                Text("Couldn’t load the library", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = viewModel::loadModelAllowlist) { Text("Try again") }
              }
              else -> {
                Icon(
                  Icons.Rounded.Search,
                  null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(32.dp),
                )
                Text(
                  if (searchQuery.isNotBlank()) "No matching models"
                  else "Your library starts here",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  if (searchQuery.isNotBlank()) "Try another name or clear your search."
                  else "Download a model or import one to get started.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(
                  onClick = {
                    searchQuery = ""
                    libraryFilter = "All models"
                  }
                ) {
                  Text("Show all models")
                }
              }
            }
          }
        }
      }
    }
  }

  if (showTaskSelectorBottomSheet) {
    ModalBottomSheet(
      onDismissRequest = { showTaskSelectorBottomSheet = false },
      sheetState = sheetState,
    ) {
      Column(
        modifier = Modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          stringResource(R.string.model_manager_select_task_title),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(bottom = 8.dp).padding(start = 16.dp),
        )
        for (task in taskCandidates) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  val model = modelForTaskCandidate
                  if (model != null) {
                    onModelSelected(task, model)
                  }
                  scope.launch {
                    sheetState.hide()
                    showTaskSelectorBottomSheet = false
                  }
                }
                .padding(horizontal = 16.dp, vertical = 4.dp),
          ) {
            Text(
              if (task.id == BuiltInTaskId.LLM_TEST) {
                stringResource(R.string.test_chat)
              } else {
                task.label
              },
              color = MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.titleMedium,
            )
            TaskIcon(task = task, width = 40.dp)
          }
        }
      }
    }
  }

  // Import model bottom sheet.
  if (showImportModelSheet) {
    ModalBottomSheet(onDismissRequest = { showImportModelSheet = false }, sheetState = sheetState) {
      Text(
        "Import model",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp, start = 24.dp, end = 24.dp),
      )
      Text(
        stringResource(R.string.import_model_terms_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
      )
      val cbImportFromLocalFile = stringResource(R.string.cd_import_model_from_local_file_button)
      Box(
        modifier =
          Modifier.clickable {
              scope.launch {
                // Give it sometime to show the click effect.
                delay(200)
                showImportModelSheet = false

                // Show file picker.
                val intent =
                  Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    // Single select.
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                  }
                filePickerLauncher.launch(intent)
              }
            }
            .semantics {
              role = Role.Button
              contentDescription = cbImportFromLocalFile
            }
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 64.dp)
              .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
          Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
          Text("From local model file", modifier = Modifier.clearAndSetSemantics {})
        }
      }
      val cdImportFromHuggingFace = stringResource(R.string.cd_import_model_from_hugging_face)
      Box(
        modifier =
          Modifier.clickable {
              scope.launch {
                delay(200)
                showImportModelSheet = false
                showHuggingFaceUrlDialog = true
              }
            }
            .semantics {
              role = Role.Button
              contentDescription = cdImportFromHuggingFace
            }
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 64.dp)
              .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
          Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
          Text(
            stringResource(R.string.import_model_from_hugging_face),
            modifier = Modifier.clearAndSetSemantics {},
          )
        }
      }
    }
  }

  if (showHfExploreScreen) {
    HfExploreScreen(
      onNavigateUp = { showHfExploreScreen = false },
      onOpenUrlImportDialog = { showHuggingFaceUrlDialog = true },
      onModelCardSelected = { detailedModel ->
        selectedModelForDetails = detailedModel
        showModelDetailsSheet = true
      },
    )
  }

  // Import dialog
  if (showImportDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      ModelImportDialog(
        uri = uri,
        huggingFaceApiClient = viewModel.huggingFaceApiClient,
        onDismiss = { showImportDialog = false },
        onDone = { info ->
          selectedImportedModelInfo.value = info
          showImportDialog = false
          showHfExploreScreen = false
          showImportingDialog = true
        },
        accessToken = viewModel.dataStoreRepository.readAccessTokenData()?.accessToken,
      )
    }
  }

  // Importing in progress dialog.
  if (showImportingDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      selectedImportedModelInfo.value?.let { info ->
        ModelImportingDialog(
          uri = uri,
          info = info,
          onDismiss = { showImportingDialog = false },
          onDone = {
            viewModel.addImportedLlmModel(info = it)
            showImportingDialog = false

            // Show a snack bar for successful import.
            scope.launch { snackbarHostState.showSnackbar("Model imported successfully") }
          },
        )
      }
    }
  }

  // Alert dialog for unsupported model.
  if (showUnsupportedModelDialog) {
    AlertDialog(
      icon = {
        Icon(
          Icons.Rounded.Error,
          contentDescription = stringResource(R.string.cd_error),
          tint = MaterialTheme.colorScheme.error,
        )
      },
      onDismissRequest = { showUnsupportedModelDialog = false },
      title = { Text(stringResource(R.string.unsupported_model_title)) },
      text = { Text(unsupportedModelErrorMessage) },
      confirmButton = {
        Button(onClick = { showUnsupportedModelDialog = false }) {
          Text(stringResource(R.string.ok))
        }
      },
    )
  }

  if (showHuggingFaceUrlDialog) {
    HuggingFaceUrlDialog(
      urlInput = huggingFaceUrlInput,
      onUrlInputChange = { huggingFaceUrlInput = it },
      onDismiss = { showHuggingFaceUrlDialog = false },
      onConfirm = {
        val url = huggingFaceUrlInput.trim()
        if (url.isNotEmpty()) {
          showHuggingFaceUrlDialog = false
          val urlInfo = extractHfUrlInfo(url)
          when {
            urlInfo.isDirectModelFile -> {
              val fileUri =
                if (urlInfo.modelId != null && urlInfo.fileName != null) {
                  "https://huggingface.co/${urlInfo.modelId}/resolve/main/${urlInfo.fileName}?download=true"
                    .toUri()
                } else {
                  url.toUri()
                }
              processModelUri(fileUri, true)
            }
            urlInfo.modelId != null -> {
              val targetModelId = urlInfo.modelId
              if (targetModelId != null) {
                isLoadingModelCardDetails = true
                viewModel.fetchModelDetails(targetModelId) { detailedModel ->
                  isLoadingModelCardDetails = false
                  if (detailedModel != null) {
                    selectedModelForDetails = detailedModel
                    showModelDetailsSheet = true
                  } else {
                    unsupportedModelErrorMessage =
                      getErrorMessage(
                        context,
                        R.string.could_not_fetch_model_details,
                        targetModelId,
                      )
                    showUnsupportedModelDialog = true
                  }
                }
              }
            }
            else -> {
              processModelUri(url.toUri(), true)
            }
          }
        }
      },
    )
  }

  // Model card details sheet
  if (showModelDetailsSheet && selectedModelForDetails != null) {
    HfModelDetailsSheet(
      modelItem = selectedModelForDetails!!,
      onDismiss = {
        showModelDetailsSheet = false
        selectedModelForDetails = null
      },
      onImportModelFile = { modelId, fileName ->
        showModelDetailsSheet = false
        selectedModelForDetails = null
        val fileUrl = "https://huggingface.co/$modelId/resolve/main/$fileName?download=true"
        processModelUri(fileUrl.toUri(), true)
      },
    )
  }
}

private fun validateAndProcessModelUri(
  uri: Uri,
  context: Context,
  isWebImport: Boolean,
  onUnsupportedModelError: (String) -> Unit,
  onValidModelUri: (Uri) -> Unit,
) {
  val fileName = getFileName(context = context, uri = uri)
  Log.d(TAG, "Validating URI: $uri, fileName: $fileName, isWebImport: $isWebImport")
  val hasValidExtension =
    if (isWebImport) {
      fileName != null && fileName.endsWith(".litertlm")
    } else {
      fileName != null && (fileName.endsWith(".task") || fileName.endsWith(".litertlm"))
    }

  if (!hasValidExtension) {
    onUnsupportedModelError(getErrorMessage(context, R.string.unsupported_file_type_error))
  } else if (fileName != null && fileName.lowercase().contains("-web")) {
    onUnsupportedModelError(getErrorMessage(context, R.string.unsupported_web_model_error))
  } else {
    onValidModelUri(uri)
  }
}

private fun getErrorMessage(context: Context, resId: Int, vararg formatArgs: Any): String {
  return context.getString(resId, *formatArgs)
}

// Helper function to get the file name from a URI
private fun getFileName(context: Context, uri: Uri): String? {
  if (uri.scheme == "content") {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1) {
          return cursor.getString(nameIndex)
        }
      }
    }
  } else if (uri.scheme == "file" || isHttpOrHttps(uri)) {
    return uri.lastPathSegment
  }
  return null
}

@Composable
private fun LibrarySectionLabel(title: String, count: Int) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.semantics { heading() },
    )
    Text(
      count.toString(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
