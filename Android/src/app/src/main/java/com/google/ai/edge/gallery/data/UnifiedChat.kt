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

package com.google.ai.edge.gallery.data

private val chatTaskPriority =
  listOf(BuiltInTaskId.LLM_AGENT_CHAT, BuiltInTaskId.LLM_CHAT, BuiltInTaskId.LLM_TEST)

/** Keep the allowlist's runtime support boundary while preferring chat with skills. */
fun preferredChatTask(tasks: List<Task>, model: Model): Task? =
  chatTaskPriority.firstNotNullOfOrNull { id ->
    tasks.firstOrNull { task ->
      task.id == id &&
        (id != BuiltInTaskId.LLM_AGENT_CHAT || !model.isAiCore) &&
        task.models.any { it.name == model.name }
    }
  }

/** Merge compatible chat models without making the user pick a task. */
fun unifiedChatModels(tasks: List<Task>): List<Model> =
  chatTaskPriority
    .flatMap { id -> tasks.firstOrNull { it.id == id }?.models.orEmpty() }
    .distinctBy { it.name }
    .filter { preferredChatTask(tasks, it) != null }
