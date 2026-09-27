/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.common.chat

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatWelcomeTest {
  @get:Rule val composeRule = createComposeRule()
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun eachStarterSelectsItsEditableDraft() {
    val drafts = mutableListOf<String>()
    composeRule.setContent { MaterialTheme { ChatWelcome(onPromptSelected = drafts::add) } }

    val suggestions =
      listOf(
        R.string.chat_starter_learn to R.string.chat_starter_learn_prompt,
        R.string.chat_starter_write to R.string.chat_starter_write_prompt,
        R.string.chat_starter_plan to R.string.chat_starter_plan_prompt,
      )
    suggestions.forEach { (title, prompt) ->
      composeRule
        .onNodeWithText(context.getString(title))
        .performScrollTo()
        .assertHasClickAction()
        .performClick()
      composeRule.runOnIdle { assertEquals(context.getString(prompt), drafts.last()) }
    }
    composeRule.runOnIdle { assertEquals(3, drafts.size) }
  }

  @Test
  fun suggestionsAreUnavailableWhileTheComposerCannotAcceptInput() {
    val drafts = mutableListOf<String>()
    composeRule.setContent {
      MaterialTheme { ChatWelcome(onPromptSelected = drafts::add, enabled = false) }
    }

    listOf(R.string.chat_starter_learn, R.string.chat_starter_write, R.string.chat_starter_plan)
      .forEach { title ->
        composeRule
          .onNodeWithText(context.getString(title))
          .performScrollTo()
          .assertIsNotEnabled()
          .performClick()
      }
    composeRule.runOnIdle { assertTrue(drafts.isEmpty()) }
  }

  @Test
  fun allSuggestionsRemainReachableAtNarrowWidthAndLargeFont() {
    var draft = ""
    composeRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        MaterialTheme {
          Box(Modifier.size(width = 320.dp, height = 380.dp)) {
            ChatWelcome(onPromptSelected = { draft = it })
          }
        }
      }
    }

    composeRule
      .onNodeWithText(context.getString(R.string.chat_welcome_title))
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    listOf(R.string.chat_starter_learn, R.string.chat_starter_write, R.string.chat_starter_plan)
      .forEach { title ->
        composeRule
          .onNodeWithText(context.getString(title))
          .performScrollTo()
          .assertIsDisplayed()
          .assertHeightIsAtLeast(48.dp)
          .assertHasClickAction()
      }
    composeRule.onNodeWithText(context.getString(R.string.chat_starter_plan)).performClick()
    composeRule.runOnIdle {
      assertEquals(context.getString(R.string.chat_starter_plan_prompt), draft)
    }
  }

  @Test
  fun mediaWelcomeKeepsItsInstructionsReachableWithLargeFont() {
    val title = context.getString(R.string.askimage_emptystate_title)
    val description = context.getString(R.string.askimage_emptystate_content)
    composeRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        MaterialTheme {
          Box(Modifier.size(width = 320.dp, height = 380.dp)) {
            ChatMediaWelcome(title = title, description = description, icon = Icons.Rounded.Photo)
          }
        }
      }
    }

    composeRule
      .onNodeWithText(title)
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    composeRule.onNodeWithText(description).performScrollTo().assertIsDisplayed()
  }
}
