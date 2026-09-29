// Modified for the Gallery Android fork (Beta 5).
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

package com.google.ai.edge.gallery.customtasks.agentchat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import com.google.ai.edge.gallery.security.EncryptedDataStore
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.agent.AgentChatExecutor
import com.google.ai.edge.gallery.agent.AgentRuntimeConfig
import com.google.ai.edge.gallery.agent.AgentRuntimeExecutor
import com.google.ai.edge.gallery.agent.DefaultAgentRuntimeExecutor
import com.google.ai.edge.gallery.agent.PromptExpander
import com.google.ai.edge.gallery.agent.sessions.LlmSessionManager
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.proto.McpServers
import com.google.ai.edge.gallery.skills.SkillManager
import com.google.ai.edge.gallery.skills.SkillsProvider
import com.google.ai.edge.gallery.skills.formatSelectedSkills
import com.google.ai.edge.gallery.tools.RuntimeToolDispatcher
import com.google.ai.edge.litertlm.Contents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AGAgentChatTask"

class AgentChatTask
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val skillsProvider: SkillsProvider,
  private val agentTools: AgentTools,
  @AgentChatExecutor private val executor: AgentRuntimeExecutor,
) : CustomTask {
  override val task: Task by lazy {
    Task(
      id = BuiltInTaskId.LLM_AGENT_CHAT,
      label = context.getString(R.string.unified_chat_label),
      category = Category.LLM,
      iconVectorResourceId = R.drawable.chat_spark,
      models = mutableListOf(),
      description = context.getString(R.string.unified_chat_description),
      shortDescription = context.getString(R.string.unified_chat_description),
      docUrl = "https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/README.md",
      sourceCodeUrl =
        "https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/",
      textInputPlaceHolderRes = R.string.text_input_placeholder_llm_chat,
      defaultSystemPrompt = DEFAULT_SYSTEM_PROMPT_TRIMMED,
    )
  }

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (String) -> Unit,
  ) {
    val initialSystemPrompt = systemInstruction?.toString() ?: task.defaultSystemPrompt
    coroutineScope.launch(Dispatchers.Default) {
      val skillsJob = launch {
        agentTools.skillsProvider.loadSkills(SkillManagerViewModel.getDefaultDisabledSkills(model))
      }
      val mcpJob = launch { agentTools.mcpManagerViewModel.loadMcpServers() }
      skillsJob.join()
      mcpJob.join()

      // Determine base system prompt based on whether MCP tools are enabled.
      val toolsPrompt = agentTools.mcpManagerViewModel.getToolsPrompt()
      val baseSystemPrompt =
        getEffectiveBaseSystemPrompt(initialSystemPrompt, toolsPrompt.isNotEmpty())

      // TODO: inject prompt expander as a dependency.
      val finalSystemPrompt =
        PromptExpander()
          .formatSystemInstructions(
            template = baseSystemPrompt,
            substitutions =
              mapOf(
                "___SKILLS___" to formatSelectedSkills(skillsProvider.getAvailableSkills()),
                "___TOOLS___" to toolsPrompt,
              ),
          )

      val config =
        AgentRuntimeConfig(
          model = model,
          taskId = task.id,
          actionChannel = agentTools.sendActionChannel,
          supportImage = model.supportImage,
          supportAudio = model.supportAudio,
          enableConversationConstrainedDecoding = true,
          systemInstruction = finalSystemPrompt,
        )

      executor.initialize(context = context, config = config, onDone = onDone)
    }
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    executor.cleanUp(onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val myData = data as CustomTaskDataForBuiltinTask
    AgentChatScreen(
      task = task,
      modelManagerViewModel = myData.modelManagerViewModel,
      navigateUp = myData.onNavUp,
      agentTools = agentTools,
      initialQuery = myData.initialQuery,
      unifiedInterface = myData.unifiedInterface,
    )
  }
}

@Module
@InstallIn(SingletonComponent::class)
internal object AgentChatTaskModule {
  @Provides
  @Singleton
  fun provideAgentTools(skillManager: SkillManager): AgentTools {
    return AgentToolsImpl().apply { skillsProvider = skillManager }
  }

  @Provides
  @Singleton
  @AgentChatExecutor
  fun provideAgentChatExecutor(
    skillManager: SkillManager,
    agentTools: AgentTools,
    llmSessionManager: LlmSessionManager,
  ): AgentRuntimeExecutor {
    return DefaultAgentRuntimeExecutor(
      skillsProvider = skillManager,
      toolsProvider = agentTools,
      toolDispatcher = RuntimeToolDispatcher(),
      llmSessionManager = llmSessionManager,
    )
  }

  @Provides
  @IntoSet
  fun provideTask(
    @ApplicationContext context: Context,
    skillManager: SkillManager,
    agentTools: AgentTools,
    @AgentChatExecutor executor: AgentRuntimeExecutor,
  ): CustomTask {
    return AgentChatTask(context, skillManager, agentTools, executor)
  }

  @Provides
  @Singleton
  fun provideMcpServersDataStore(@ApplicationContext context: Context): DataStore<McpServers> {
    return EncryptedDataStore.create(context, context.dataStoreFile("mcp_servers.pb"), McpServersSerializer)
  }
}
