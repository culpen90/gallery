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

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.proto.Theme
import com.google.ai.edge.gallery.ui.common.ClickableLink
import com.google.ai.edge.gallery.ui.common.tos.AppTosDialog
import com.google.ai.edge.gallery.ui.diagnostics.DiagnosticsSettings
import com.google.ai.edge.gallery.ui.diagnostics.DiagnosticsViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

private val THEME_OPTIONS = listOf(Theme.THEME_AUTO, Theme.THEME_LIGHT, Theme.THEME_DARK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
  curThemeOverride: Theme,
  curFirebaseAnalytics: Boolean,
  modelManagerViewModel: ModelManagerViewModel,
  onDismissed: () -> Unit,
  diagnosticsViewModel: DiagnosticsViewModel = hiltViewModel(),
) {
  val writingDiagnostics by diagnosticsViewModel.writing.collectAsState()
  val pickingDiagnosticsFile by diagnosticsViewModel.pickerPending.collectAsState()
  val diagnosticsBusy = writingDiagnostics || pickingDiagnosticsFile
  var selectedTheme by remember { mutableStateOf(curThemeOverride) }
  var selectedFirebaseAnalytics by remember { mutableStateOf(curFirebaseAnalytics) }
  var hfToken by remember { mutableStateOf(modelManagerViewModel.getTokenStatusAndData().data) }
  var customHfToken by remember { mutableStateOf("") }
  var showTos by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val saveToken = {
    if (customHfToken.isNotBlank()) {
      modelManagerViewModel.saveAccessToken(
        accessToken = customHfToken.trim(),
        refreshToken = "",
        expiresAt = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10,
      )
      hfToken = modelManagerViewModel.getTokenStatusAndData().data
      customHfToken = ""
      focusManager.clearFocus()
    }
  }

  ModalBottomSheet(
    onDismissRequest = { if (!diagnosticsBusy) onDismissed() },
    sheetState = rememberModalBottomSheetState(
      skipPartiallyExpanded = true,
      confirmValueChange = { !diagnosticsBusy || it != SheetValue.Hidden },
    ),
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(R.string.drawer_settings_label),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismissed, enabled = !diagnosticsBusy) {
          Icon(Icons.Rounded.Close, stringResource(R.string.cd_close_icon))
        }
      }
      Text(
        stringResource(R.string.redesign_settings_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
      )
      Column(
        modifier =
          Modifier.weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        SettingsSection(stringResource(R.string.diagnostics_title)) {
          DiagnosticsSettings(viewModel = diagnosticsViewModel)
        }
        SettingsSection(stringResource(R.string.redesign_appearance)) {
          Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleSmall)
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            THEME_OPTIONS.forEachIndexed { index, theme ->
              SegmentedButton(
                selected = theme == selectedTheme,
                shape =
                  SegmentedButtonDefaults.itemShape(index = index, count = THEME_OPTIONS.size),
                onClick = {
                  selectedTheme = theme
                  ThemeSettings.themeOverride.value = theme
                  modelManagerViewModel.saveThemeOverride(theme)
                  val uiModeManager =
                    context.applicationContext.getSystemService(Context.UI_MODE_SERVICE)
                      as UiModeManager
                  uiModeManager.setApplicationNightMode(
                    when (theme) {
                      Theme.THEME_LIGHT -> UiModeManager.MODE_NIGHT_NO
                      Theme.THEME_DARK -> UiModeManager.MODE_NIGHT_YES
                      else -> UiModeManager.MODE_NIGHT_AUTO
                    }
                  )
                },
                label = { Text(stringResource(themeLabelRes(theme))) },
              )
            }
          }
        }
        SettingsSection(stringResource(R.string.redesign_privacy)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                stringResource(R.string.settings_dialog_firebase_analytics_title),
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                stringResource(R.string.settings_dialog_firebase_analytics_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            val analyticsLabel = stringResource(R.string.settings_dialog_firebase_analytics_title)
            Switch(
              modifier = Modifier.semantics { contentDescription = analyticsLabel },
              checked = selectedFirebaseAnalytics,
              onCheckedChange = {
                selectedFirebaseAnalytics = it
                modelManagerViewModel.saveFirebaseAnalytics(it)
              },
            )
          }
          Text(
            stringResource(R.string.redesign_local_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        SettingsSection(stringResource(R.string.redesign_connections)) {
          Text(
            stringResource(R.string.redesign_token_title),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            stringResource(R.string.redesign_token_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          val hasToken = hfToken?.accessToken?.isNotEmpty() == true
          Text(
            stringResource(
              if (hasToken) R.string.redesign_token_connected else R.string.redesign_token_none
            ),
            style = MaterialTheme.typography.labelLarge,
            color =
              if (hasToken) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = customHfToken,
            onValueChange = { customHfToken = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.redesign_token_input)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { saveToken() }),
            shape = RoundedCornerShape(16.dp),
          )
          Button(
            onClick = saveToken,
            enabled = customHfToken.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
          ) {
            Text(stringResource(R.string.redesign_save_token))
          }
          if (hasToken) {
            TextButton(
              onClick = {
                modelManagerViewModel.clearAccessToken()
                hfToken = null
              }
            ) {
              Text(stringResource(R.string.redesign_remove_token))
            }
          }
        }
        SettingsSection(stringResource(R.string.redesign_about)) {
          Text("Gallery ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
          TextButton(
            onClick = {
              context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
            }
          ) {
            Text(stringResource(R.string.view_licenses))
          }
          TextButton(onClick = { showTos = true }) {
            Text(stringResource(R.string.settings_dialog_view_app_terms_of_service))
          }
          ClickableLink(
            url = "https://ai.google.dev/gemma/terms",
            linkText = stringResource(R.string.tos_dialog_title_gemma),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          )
          ClickableLink(
            url = "https://ai.google.dev/gemma/prohibited_use_policy",
            linkText = stringResource(R.string.settings_dialog_gemma_prohibited_use_policy),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          )
        }
      }
    }
  }
  if (showTos) {
    AppTosDialog(onTosAccepted = { showTos = false }, viewingMode = true)
  }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      title,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 4.dp),
    )
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
      )
    }
  }
}

@StringRes
private fun themeLabelRes(theme: Theme): Int =
  when (theme) {
    Theme.THEME_AUTO -> R.string.theme_auto
    Theme.THEME_LIGHT -> R.string.theme_light
    Theme.THEME_DARK -> R.string.theme_dark
    else -> R.string.unknown
  }
