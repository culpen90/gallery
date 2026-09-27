/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class UnifiedChatTest {
  @Test
  fun supportedModelPrefersSkillsRegardlessOfTaskOrder() {
    val model = Model(name = "assistant")
    val chat = task(BuiltInTaskId.LLM_CHAT, model)
    val skills = task(BuiltInTaskId.LLM_AGENT_CHAT, model)

    assertSame(skills, preferredChatTask(listOf(chat, skills), model))
  }

  @Test
  fun selectionMatchesModelIdentityRatherThanObjectInstance() {
    val supportedModel = Model(name = "assistant")
    val selectedModel = supportedModel.copy(displayName = "My assistant")
    val skills = task(BuiltInTaskId.LLM_AGENT_CHAT, supportedModel)

    assertSame(skills, preferredChatTask(listOf(skills), selectedModel))
  }

  @Test
  fun aiCoreFallsBackToSupportedChatEvenWhenListedForSkills() {
    val model = Model(name = "nano", backendSpec = BackendSpec(runtimeType = RuntimeType.AICORE))
    val skills = task(BuiltInTaskId.LLM_AGENT_CHAT, model)
    val chat = task(BuiltInTaskId.LLM_CHAT, model)

    assertSame(chat, preferredChatTask(listOf(skills, chat), model))
    assertNull(preferredChatTask(listOf(skills), model))
    assertEquals(emptyList<Model>(), unifiedChatModels(listOf(skills)))
  }

  @Test
  fun unsupportedSkillsModelUsesItsAvailableFallback() {
    val model = Model(name = "imported")
    val skills = task(BuiltInTaskId.LLM_AGENT_CHAT, Model(name = "other"))
    val testChat = task(BuiltInTaskId.LLM_TEST, model)

    assertSame(testChat, preferredChatTask(listOf(skills, testChat), model))
  }

  @Test
  fun modelWithoutAChatRuntimeHasNoRoute() {
    val model = Model(name = "audio-only")
    val audioTask = task(BuiltInTaskId.LLM_ASK_AUDIO, model)

    assertNull(preferredChatTask(listOf(audioTask), model))
    assertNull(preferredChatTask(emptyList(), model))
  }

  @Test
  fun modelListUnifiesChatTasksInPriorityOrderWithoutDuplicateModels() {
    val shared = Model(name = "shared")
    val skillsModel = Model(name = "skills")
    val fallbackModel = Model(name = "imported")
    val excludedModel = Model(name = "image-only")
    val tasks =
      listOf(
        task(BuiltInTaskId.LLM_TEST, shared.copy(), fallbackModel),
        task(BuiltInTaskId.LLM_ASK_IMAGE, excludedModel),
        task(BuiltInTaskId.LLM_CHAT, shared.copy()),
        task(BuiltInTaskId.LLM_AGENT_CHAT, skillsModel, shared),
      )

    val models = unifiedChatModels(tasks)

    assertEquals(listOf("skills", "shared", "imported"), models.map { it.name })
    assertSame(shared, models[1])
    assertEquals(emptyList<Model>(), unifiedChatModels(emptyList()))
  }

  private fun task(id: String, vararg models: Model) =
    Task(
      id = id,
      label = id,
      category = Category.LLM,
      description = "",
      models = models.toMutableList(),
    )
}
