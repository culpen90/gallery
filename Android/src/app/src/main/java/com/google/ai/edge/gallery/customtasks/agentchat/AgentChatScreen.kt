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

package com.google.ai.edge.gallery.customtasks.agentchat

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.ai.edge.gallery.GalleryEvent
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.agent.PromptExpander
import com.google.ai.edge.gallery.common.LOCAL_URL_BASE
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.skills.formatSelectedSkills
import com.google.ai.edge.gallery.tools.AskInfoToolAction
import com.google.ai.edge.gallery.tools.AskMcpToolCallPermissionAction
import com.google.ai.edge.gallery.tools.CallJsToolAction
import com.google.ai.edge.gallery.tools.PermissionResult
import com.google.ai.edge.gallery.tools.RequestPermissionToolAction
import com.google.ai.edge.gallery.tools.SkillProgressToolAction
import com.google.ai.edge.gallery.ui.common.BaseGalleryWebViewClient
import com.google.ai.edge.gallery.ui.common.GalleryWebView
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageCollapsableProgressPanel
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageImage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageInfo
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageType
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageWebView
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.LogMessage
import com.google.ai.edge.gallery.ui.common.chat.LogMessageLevel
import com.google.ai.edge.gallery.ui.common.chat.SendMessageTrigger
import com.google.ai.edge.gallery.ui.common.chat.convertToLitertMessage
import com.google.ai.edge.gallery.ui.llmchat.LlmChatScreen
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.lang.Exception
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

private const val TAG = "AGAgentChatScreen"
private val chatViewJavascriptInterface = ChatWebViewJavascriptInterface()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentChatScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  agentTools: AgentTools,
  viewModel: AgentChatViewModel = hiltViewModel(),
  skillManagerViewModel: SkillManagerViewModel = hiltViewModel(),
  mcpManagerViewModel: McpManagerViewModel = hiltViewModel(),
  initialQuery: String? = null,
  unifiedInterface: Boolean = false,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  agentTools.context = context
  agentTools.skillsProvider = skillManagerViewModel.skillManager
  agentTools.dataStoreRepository = skillManagerViewModel.skillManager.dataStoreRepository
  agentTools.mcpManagerViewModel = mcpManagerViewModel
  agentTools.taskId = task.id
  val density = LocalDensity.current
  val windowInfo = LocalWindowInfo.current
  val screenWidthDp = remember { with(density) { windowInfo.containerSize.width.toDp() } }
  var showSkillManagerBottomSheet by remember { mutableStateOf(false) }
  var showMcpManagerBottomSheet by remember { mutableStateOf(false) }
  var showAskInfoDialog by remember { mutableStateOf(false) }
  var currentAskInfoAction by remember { mutableStateOf<AskInfoToolAction?>(null) }
  var currentMcpPermissionAction by remember {
    mutableStateOf<AskMcpToolCallPermissionAction?>(null)
  }
  var askInfoInputValue by remember { mutableStateOf("") }
  var webViewRef: WebView? by remember { mutableStateOf(null) }
  val chatWebViewClient = remember { ChatWebViewClient(context = context) }
  var curSystemPrompt by remember { mutableStateOf(task.defaultSystemPrompt) }
  val systemPromptUpdatedMessage = stringResource(R.string.system_prompt_updated)
  var sendMessageTrigger by remember { mutableStateOf<SendMessageTrigger?>(null) }

  var currentPermissionAction by remember { mutableStateOf<RequestPermissionToolAction?>(null) }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      permissionGranted ->
      currentPermissionAction?.result?.complete(permissionGranted)
      currentPermissionAction = null
    }

  LaunchedEffect(task) { viewModel.loadSystemPrompt(task) }
  val uiSystemPrompt by viewModel.uiSystemPrompt.collectAsState()

  // Collect UI states from view models. Ensure launched effect is triggered when the UI state is
  // updated.
  val llmChatUiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val skillUiState by skillManagerViewModel.uiState.collectAsState()
  val mcpUiState by mcpManagerViewModel.uiState.collectAsState()

  val skillCount = skillUiState.skills.count { it.skill.selected }
  val mcpCount = mcpUiState.mcpServers.count { it.mcpServer.enabled }
  val mcpToolsCount =
    mcpUiState.mcpServers
      .filter { it.mcpServer.enabled }
      .sumOf { it.mcpServer.toolsList.count { tool -> tool.enabled } }

  LaunchedEffect(uiSystemPrompt, mcpToolsCount) {
    curSystemPrompt = getEffectiveBaseSystemPrompt(uiSystemPrompt, mcpToolsCount > 0)
  }

  val selectedModel = modelManagerUiState.selectedModel
  val modelInitStatus by selectedModel.initStatusFlow.collectAsState()

  DisposableEffect(selectedModel.name, task.id) {
    if (selectedModel.setupAgentSkillTopK()) {
      modelManagerViewModel.updateConfigValuesUpdateTrigger()
    }

    onDispose {
      if (selectedModel.cleanupAgentSkillTopK()) {
        modelManagerViewModel.updateConfigValuesUpdateTrigger()
      }
    }
  }

  var initialQueryConsumed by remember { mutableStateOf(false) }

  LaunchedEffect(
    llmChatUiState.isResettingSession,
    modelInitStatus,
    selectedModel.name,
    initialQuery,
  ) {
    // Send the optional initial query to the model if the model is initialized and the initial
    // query is not consumed yet.
    if (
      !initialQuery.isNullOrEmpty() &&
        !initialQueryConsumed &&
        modelInitStatus is Model.InitializationStatus.Initialized &&
        viewModel.ownsConversation(task.id, selectedModel) &&
        !llmChatUiState.isResettingSession
    ) {
      initialQueryConsumed = true
      sendMessageTrigger =
        SendMessageTrigger(
          model = selectedModel,
          messages = listOf(ChatMessageText(content = initialQuery, side = ChatSide.USER)),
        )
    }
  }

  LlmChatScreen(
    modelManagerViewModel = modelManagerViewModel,
    taskId = task.id,
    unifiedInterface = unifiedInterface,
    navigateUp = navigateUp,
    viewModel = viewModel,
    skillCount = skillCount,
    mcpCount = mcpCount,
    mcpToolsCount = mcpToolsCount,
    onFirstToken = { model ->
      scope.launch(Dispatchers.Main) {
        updateProgressPanel(viewModel = viewModel, model = model, agentTools = agentTools)
      }
    },
    onGenerateResponseDone = { model ->
      scope.launch(Dispatchers.Main) {
        // Show any image produced by tools.
        agentTools.resultImageToShow?.let { resultImage ->
          resultImage.base64?.let { base64 ->
            decodeBase64ToBitmap(base64String = base64)?.let { bitmap ->
              viewModel.addMessage(
                model = model,
                message =
                  ChatMessageImage(
                    bitmaps = listOf(bitmap),
                    imageBitMaps = listOf(bitmap.asImageBitmap()),
                    side = ChatSide.AGENT,
                    maxSize = (screenWidthDp.value * 0.8).toInt(),
                    latencyMs = -1.0f,
                    hideSenderLabel = true,
                  ),
              )
            }
          }
          // Clean up.
          agentTools.resultImageToShow = null
        }

        // Show any webview produced by tools.
        agentTools.resultWebviewToShow?.let { webview ->
          val url = webview.url ?: ""
          val iframe = webview.iframe == true
          val aspectRatio = webview.aspectRatio ?: 1.333f
          viewModel.addMessage(
            model = model,
            message =
              ChatMessageWebView(
                url = url,
                iframe = iframe,
                aspectRatio = aspectRatio,
                hideSenderLabel = true,
              ),
          )
          // Clean up.
          agentTools.resultWebviewToShow = null
        }
        updateProgressPanel(viewModel = viewModel, model = model, agentTools = agentTools)
      }
    },
    onResetSessionClickedOverride = { task, _, initialMessages, clearHistory ->
      resetSessionWithCurrentSkillsAndMcps(
        viewModel,
        modelManagerViewModel,
        skillManagerViewModel,
        task,
        curSystemPrompt,
        agentTools,
        initialMessages = initialMessages,
        clearHistory = clearHistory,
      )
    },
    onSkillClicked = { showSkillManagerBottomSheet = true },
    onMcpClicked = { showMcpManagerBottomSheet = true },
    showImagePicker = true,
    showAudioPicker = true,
    getActiveSkills = {
      skillManagerViewModel.getSelectedSkills().map { skill ->
        skillManagerViewModel.getSkillShortId(skill)
      }
    },
    composableBelowMessageList = { model ->
      val actionChannel = agentTools.receiveActionChannel
      val doneIcon = ImageVector.vectorResource(R.drawable.skill)
      // Use rememberUpdatedState to ensure that LaunchedEffect captures the
      // latest active model when the model is switched during an ongoing skill execution.
      val currentModel by androidx.compose.runtime.rememberUpdatedState(model)
      LaunchedEffect(actionChannel) {
        for (action in actionChannel) {
          Log.d(TAG, "Handling action: $action")
          when (action) {
            is SkillProgressToolAction -> {
              viewModel.updateCollapsableProgressPanelMessage(
                model = currentModel,
                title = action.label,
                inProgress = action.inProgress,
                doneIcon = doneIcon,
                addItemTitle = action.addItemTitle,
                addItemDescription = action.addItemDescription,
                customData = action.customData,
              )
            }
            is CallJsToolAction -> {
              val skillName =
                if (action.url.contains("/skills/")) {
                  action.url.substringAfter("/skills/").substringBefore("/")
                } else if (action.url.startsWith(LOCAL_URL_BASE + "/")) {
                  action.url.substringAfter(LOCAL_URL_BASE + "/").substringBefore("/")
                } else {
                  action.url
                }
              val skill = skillManagerViewModel.getSkill(name = skillName)
              val skillId = skill?.let { skillManagerViewModel.getSkillShortId(it) } ?: "xxxx"
              try {
                // Set up a safety net timeout so we NEVER hang the chat or tool execution
                launch {
                  delay(60000L) // 60 seconds max
                  if (!action.result.isCompleted) {
                    Log.e(TAG, "JS Execution timed out, completing with error.")
                    Log.d(
                      TAG,
                      "Analytics: skill_execution, capability_name=${task.id}, skill_name=$skillName, success=false, error_type=timeout",
                    )
                    firebaseAnalytics?.logEvent(
                      GalleryEvent.SKILL_EXECUTION.id,
                      Bundle().apply {
                        putString("capability_name", task.id)
                        putString("skill_name", skillName)
                        putString("skill_id", skillId)
                        putBoolean("success", false)
                        putString("error_type", "timeout")
                      },
                    )
                    action.result.complete(
                      "{\"error\": \"Skill execution timed out. Please check network connection.\"}"
                    )
                  }
                }

                // Load url.
                suspendCancellableCoroutine<Unit> { continuation ->
                  chatWebViewClient.setPageLoadListener {
                    chatWebViewClient.setPageLoadListener(null)
                    continuation.resume(Unit)
                  }
                  Log.d(TAG, "Loading url: ${action.url}")
                  webViewRef?.loadUrl(action.url)
                }

                // Execute JS.
                Log.d(TAG, "Start to run js")
                chatViewJavascriptInterface.onResultListener = { result ->
                  Log.d(TAG, "Got result:\n$result")
                  action.result.complete(result)
                  val isSuccess = !result.contains("\"error\":")
                  val errorType = if (isSuccess) "" else "js_error"
                  Log.d(
                    TAG,
                    "Analytics: skill_execution, capability_name=${task.id}, skill_name=$skillName, success=$isSuccess, error_type=$errorType",
                  )
                  firebaseAnalytics?.logEvent(
                    GalleryEvent.SKILL_EXECUTION.id,
                    Bundle().apply {
                      putString("capability_name", task.id)
                      putString("skill_name", skillName)
                      putString("skill_id", skillId)
                      putBoolean("success", isSuccess)
                      putString("error_type", errorType)
                    },
                  )
                }

                val safeData = JSONObject.quote(action.data)
                val safeSecret = JSONObject.quote(action.secret)
                val script =
                  """
                  (async function() {
                      var startTs = Date.now();
                      while(true) {
                        if (typeof ai_edge_gallery_get_result === 'function') {
                          break;
                        }
                        await new Promise(resolve=>{
                          setTimeout(resolve, 100)
                        });
                        if (Date.now() - startTs > 10000) {
                          break;
                        }
                      }
                      var result = await ai_edge_gallery_get_result($safeData, $safeSecret);
                      AiEdgeGallery.onResultReady(result);
                  })()
                  """
                    .trimIndent()
                webViewRef?.evaluateJavascript(script, null)
              } catch (e: Exception) {
                Log.d(
                  TAG,
                  "Analytics: skill_execution, capability_name=${task.id}, skill_name=$skillName, success=false, error_type=exception",
                )
                firebaseAnalytics?.logEvent(
                  GalleryEvent.SKILL_EXECUTION.id,
                  Bundle().apply {
                    putString("capability_name", task.id)
                    putString("skill_name", skillName)
                    putString("skill_id", skillId)
                    putBoolean("success", false)
                    putString("error_type", "exception")
                  },
                )
                action.result.completeExceptionally(e)
              }
            }
            is AskInfoToolAction -> {
              currentAskInfoAction = action
              askInfoInputValue = "" // Reset input
              showAskInfoDialog = true
            }
            is RequestPermissionToolAction -> {
              currentPermissionAction = action
              permissionLauncher.launch(action.permission)
            }
            is AskMcpToolCallPermissionAction -> {
              currentMcpPermissionAction = action
            }
          }
        }
      }

      GalleryWebView(
        modifier = Modifier.size(300.dp),
        onWebViewCreated = { webView ->
          webViewRef = webView
          webView.addJavascriptInterface(chatViewJavascriptInterface, "AiEdgeGallery")
        },
        customWebViewClient = chatWebViewClient,
        onConsoleMessage = { consoleMessage ->
          consoleMessage?.let { curConsoleMessage ->
            // Create a LogMessage from the ConsoleMessage and add it to the progress panel.
            val logMessage =
              LogMessage(
                level =
                  when (curConsoleMessage.messageLevel()) {
                    ConsoleMessage.MessageLevel.LOG -> LogMessageLevel.Info
                    ConsoleMessage.MessageLevel.ERROR -> LogMessageLevel.Error
                    ConsoleMessage.MessageLevel.WARNING -> LogMessageLevel.Warning
                    else -> LogMessageLevel.Info
                  },
                source = curConsoleMessage.sourceId(),
                lineNumber = curConsoleMessage.lineNumber(),
                message = curConsoleMessage.message(),
              )
            viewModel.addLogMessageToLastCollapsableProgressPanel(
              model = model,
              logMessage = logMessage,
            )
            Log.d(
              TAG,
              "${curConsoleMessage.message()} " +
                "-- From line ${curConsoleMessage.lineNumber()} of ${curConsoleMessage.sourceId()}",
            )
          }
        },
      )
    },
    allowEditingSystemPrompt = true,
    curSystemPrompt = curSystemPrompt,
    onSystemPromptChanged = { newPrompt ->
      curSystemPrompt = newPrompt
      viewModel.saveAgentSystemPrompt(
        task = task,
        newPrompt = newPrompt,
      ) {
        val model = modelManagerViewModel.uiState.value.selectedModel
        resetSessionWithCurrentSkillsAndMcps(
          viewModel,
          modelManagerViewModel,
          skillManagerViewModel,
          task,
          newPrompt,
          agentTools,
          initialMessages = viewModel.uiState.value.messagesByModel[model.name].orEmpty().toList(),
          clearHistory = false,
          onDone = {
            viewModel.addMessage(it, ChatMessageInfo(content = systemPromptUpdatedMessage))
          },
        )
      }
    },
    emptyStateComposable = { model ->
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
          !WindowInsets.isImeVisible,
          enter = fadeIn(animationSpec = tween(200)),
          exit = fadeOut(animationSpec = tween(200)),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 36.dp).padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              stringResource(R.string.unified_chat_welcome),
              style = MaterialTheme.typography.headlineMedium,
              modifier = Modifier.semantics { heading() },
              textAlign = TextAlign.Center,
            )
            Text(
              stringResource(R.string.unified_chat_welcome_description),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            if (model.supportAudio) {
              Text(
                stringResource(R.string.unified_chat_voice_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }
    },
    sendMessageTrigger = sendMessageTrigger,
  )

  if (showAskInfoDialog && currentAskInfoAction != null) {
    val action = currentAskInfoAction!!
    SecretEditorDialog(
      title = action.dialogTitle,
      fieldLabel = action.fieldLabel,
      value = askInfoInputValue,
      onValueChange = { askInfoInputValue = it },
      onDone = {
        action.result.complete(askInfoInputValue)
        showAskInfoDialog = false
        currentAskInfoAction = null
      },
      onDismiss = {
        action.result.complete("")
        showAskInfoDialog = false
        currentAskInfoAction = null
      },
    )
  }

  if (currentMcpPermissionAction != null) {
    val action = currentMcpPermissionAction!!
    McpToolCallPermissionDialog(
      toolName = action.toolName,
      argument = action.argument,
      onResult = { result ->
        action.result.complete(result)
        if (result == PermissionResult.ALWAYS_ALLOW) {
          val serverState =
            mcpManagerViewModel.uiState.value.mcpServers.find { serverState ->
              serverState.mcpServer.toolsList.any { it.name == action.toolName }
            }
          serverState?.mcpServer?.url?.let { url ->
            mcpManagerViewModel.setMcpToolAlwaysAllow(
              url = url,
              toolName = action.toolName,
              alwaysAllow = true,
            )
          }
        }
        currentMcpPermissionAction = null
      },
    )
  }

  if (showSkillManagerBottomSheet) {
    SkillManagerBottomSheet(
      agentTools = agentTools,
      skillManagerViewModel = skillManagerViewModel,
      onDismiss = { selectedSkillsChanged ->
        // Hide sheet.
        showSkillManagerBottomSheet = false

        // Reset session when selected skills changed.
        if (selectedSkillsChanged) {
          Log.d(TAG, "Selected skill changed. Resetting conversation.")
          resetSessionWithCurrentSkillsAndMcps(
            viewModel,
            modelManagerViewModel,
            skillManagerViewModel,
            task,
            curSystemPrompt,
            agentTools,
            initialMessages =
              viewModel.uiState.value.messagesByModel[selectedModel.name].orEmpty().toList(),
            clearHistory = false,
          )
        }
      },
    )
  }

  if (showMcpManagerBottomSheet) {
    McpManagerBottomSheet(
      mcpManagerViewModel = mcpManagerViewModel,
      onDismiss = { selectMcpsAndToolsChanged ->
        showMcpManagerBottomSheet = false
        if (selectMcpsAndToolsChanged) {
          Log.d(TAG, "Selected MCPs or tools changed. Resetting conversation.")
          resetSessionWithCurrentSkillsAndMcps(
            viewModel,
            modelManagerViewModel,
            skillManagerViewModel,
            task,
            curSystemPrompt,
            agentTools,
            initialMessages =
              viewModel.uiState.value.messagesByModel[selectedModel.name].orEmpty().toList(),
            clearHistory = false,
          )
        }
      },
    )
  }
}

private fun updateProgressPanel(viewModel: LlmChatViewModel, model: Model, agentTools: AgentTools) {
  // Update status.
  val lastProgressPanelMessage =
    viewModel.getLastMessageWithType(
      model = model,
      type = ChatMessageType.COLLAPSABLE_PROGRESS_PANEL,
    )
  if (
    lastProgressPanelMessage != null &&
      lastProgressPanelMessage is ChatMessageCollapsableProgressPanel
  ) {
    if (lastProgressPanelMessage.title.startsWith("Loading")) {
      agentTools.sendToolAction(
        SkillProgressToolAction(
          label = lastProgressPanelMessage.title.replace("Loading", "Loaded"),
          inProgress = false,
        )
      )
    } else if (lastProgressPanelMessage.title.startsWith("Calling")) {
      agentTools.sendToolAction(
        SkillProgressToolAction(
          label = lastProgressPanelMessage.title.replace("Calling", "Called"),
          inProgress = false,
        )
      )
    } else if (lastProgressPanelMessage.title.startsWith("Executing")) {
      agentTools.sendToolAction(
        SkillProgressToolAction(
          label = lastProgressPanelMessage.title.replace("Executing", "Executed"),
          inProgress = false,
        )
      )
    } else {
      agentTools.sendToolAction(
        SkillProgressToolAction(label = lastProgressPanelMessage.title, inProgress = false)
      )
    }
  }
}

private fun resetSessionWithCurrentSkillsAndMcps(
  viewModel: LlmChatViewModel,
  modelManagerViewModel: ModelManagerViewModel,
  skillManagerViewModel: SkillManagerViewModel,
  task: Task,
  curSystemPrompt: String,
  agentTools: AgentTools,
  onDone: (Model) -> Unit = {},
  initialMessages: List<ChatMessage> = listOf(),
  clearHistory: Boolean = true,
) {
  val model = modelManagerViewModel.uiState.value.selectedModel
  val litertMessages = initialMessages.mapNotNull { convertToLitertMessage(it) }
  val toolsPrompt = agentTools.mcpManagerViewModel.getToolsPrompt()
  val actualSystemPrompt = getEffectiveBaseSystemPrompt(curSystemPrompt, toolsPrompt.isNotEmpty())

  val selectedSkills =
    runBlocking(Dispatchers.Default) { skillManagerViewModel.skillManager.getAvailableSkills() }
  val finalSystemPrompt =
    PromptExpander()
      .formatSystemInstructions(
        template = actualSystemPrompt,
        substitutions =
          mapOf(
            "___SKILLS___" to formatSelectedSkills(selectedSkills),
            "___TOOLS___" to toolsPrompt,
          ),
      )

  viewModel.resetSession(
    task = task,
    model = model,
    systemInstruction = finalSystemPrompt,
    actionChannel = agentTools.sendActionChannel,
    supportImage = model.supportImage,
    supportAudio = model.supportAudio,
    onDone = { onDone(model) },
    enableConversationConstrainedDecoding = true,
    initialMessages = litertMessages,
    clearHistory = clearHistory,
  )
}

class ChatWebViewJavascriptInterface {
  var onResultListener: ((String) -> Unit)? = null

  @JavascriptInterface
  fun onResultReady(result: String) {
    onResultListener?.invoke(result)
  }
}

class ChatWebViewClient(val context: Context) : BaseGalleryWebViewClient(context = context) {
  private var onPageLoaded: (() -> Unit)? = null

  fun setPageLoadListener(listener: (() -> Unit)?) {
    onPageLoaded = listener
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    Log.d(TAG, "page loaded")
    onPageLoaded?.invoke()
  }
}
