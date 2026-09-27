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

package com.google.ai.edge.gallery.ui.common.chat

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputHistorySheet(
  history: List<String>,
  onHistoryItemClicked: (String) -> Unit,
  onHistoryItemDeleted: (String) -> Unit,
  onHistoryItemsDeleteAll: () -> Unit,
  onDismissed: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()

  ModalBottomSheet(
    onDismissRequest = onDismissed,
    sheetState = sheetState,
    modifier = Modifier.wrapContentHeight(),
    containerColor = MaterialTheme.colorScheme.background,
  ) {
    SheetContent(
      history = history,
      onHistoryItemClicked = { item ->
        scope.launch {
          sheetState.hide()
          delay(100)
          onHistoryItemClicked(item)
          onDismissed()
        }
      },
      onHistoryItemDeleted = onHistoryItemDeleted,
      onHistoryItemsDeleteAll = {
        scope.launch {
          sheetState.hide()
          onDismissed()
          onHistoryItemsDeleteAll()
        }
      },
      onDismissed = {
        scope.launch {
          sheetState.hide()
          onDismissed()
        }
      },
    )
  }
}

@Composable
private fun SheetContent(
  history: List<String>,
  onHistoryItemClicked: (String) -> Unit,
  onHistoryItemDeleted: (String) -> Unit,
  onHistoryItemsDeleteAll: () -> Unit,
  onDismissed: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var showConfirmDeleteDialog by remember { mutableStateOf(false) }

  Column {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.text_input_history_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.weight(1f),
      )
      if (history.isNotEmpty()) {
        IconButton(onClick = { showConfirmDeleteDialog = true }) {
          Icon(
            Icons.Rounded.DeleteSweep,
            contentDescription = stringResource(R.string.cd_clear_input_history_icon),
          )
        }
      }
      IconButton(onClick = onDismissed) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
      }
    }
    Text(
      stringResource(R.string.support_prompt_history_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
    )
    LazyColumn(
      modifier = Modifier.weight(1f, fill = false),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
    ) {
      if (history.isEmpty()) {
        item {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              Icons.Rounded.History,
              contentDescription = null,
              modifier = Modifier.size(36.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              stringResource(R.string.support_prompt_history_empty),
              style = MaterialTheme.typography.titleMedium,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
      items(history, key = { it }) { item ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(24.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLow)
              .clickable { onHistoryItemClicked(item) }
              .heightIn(min = 72.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            item,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 16.dp).padding(start = 16.dp).weight(1f),
          )
          IconButton(
            modifier = Modifier.padding(end = 8.dp),
            onClick = {
              scope.launch {
                delay(400)
                onHistoryItemDeleted(item)
              }
            },
          ) {
            Icon(
              Icons.Rounded.Delete,
              contentDescription = stringResource(R.string.cd_delete_input_history_entry_icon),
            )
          }
        }
      }
    }
  }

  if (showConfirmDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmDeleteDialog = false },
      title = { Text(stringResource(R.string.clear_history_title)) },
      text = { Text(stringResource(R.string.clear_history_message)) },
      confirmButton = {
        Button(
          onClick = {
            showConfirmDeleteDialog = false
            onHistoryItemsDeleteAll()
          }
        ) {
          Text(stringResource(R.string.ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { showConfirmDeleteDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}

// @Preview(showBackground = true)
// @Composable
// fun TextInputHistorySheetContentPreview() {
//   GalleryTheme {
//     SheetContent(
//       history =
//         listOf(
//           "Analyze the sentiment of the following Tweets and classify them as POSITIVE, NEGATIVE,
// or NEUTRAL. \"It's so beautiful today!\"",
//           "I have the ingredients above. Not sure what to cook for lunch. Show me a list of foods
// with the recipes.",
//           "You are Santa Claus, write a letter back for this kid.",
//           "Generate a list of cookie recipes. Make the outputs in JSON format.",
//         ),
//       onHistoryItemClicked = {},
//       onHistoryItemDeleted = {},
//       onHistoryItemsDeleteAll = {},
//       onDismissed = {},
//     )
//   }
// }
