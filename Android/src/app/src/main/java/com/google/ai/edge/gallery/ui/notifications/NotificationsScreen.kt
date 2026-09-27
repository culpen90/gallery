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

package com.google.ai.edge.gallery.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.notifications.NotificationScheduleManager
import com.google.ai.edge.gallery.proto.ScheduledNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(val scheduleManager: NotificationScheduleManager) :
  ViewModel() {
  val notifications = scheduleManager.scheduledNotifications

  fun removeNotification(id: String) {
    scheduleManager.removeNotification(id)
  }
}

/** Scheduled reminders grouped by their source, with a quiet and useful empty state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: NotificationsViewModel = hiltViewModel(),
) {
  val notifications by viewModel.notifications.collectAsState()
  var notificationToDelete by remember { mutableStateOf<ScheduledNotification?>(null) }
  val groupedNotifications = remember(notifications) { notifications.groupBy { it.channelName } }
  val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.notifications_title)) },
        navigationIcon = {
          IconButton(onClick = navigateUp) {
            Icon(
              Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.support_back),
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
    ) {
      item(key = "intro") {
        Text(
          stringResource(R.string.support_notifications_description),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 12.dp),
        )
      }
      if (notifications.isEmpty()) {
        item(key = "empty") {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  Icons.Rounded.Notifications,
                  contentDescription = null,
                  modifier = Modifier.size(32.dp),
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
              Text(
                stringResource(R.string.notifications_empty_state),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
              )
              Text(
                stringResource(R.string.support_notifications_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
              TextButton(onClick = navigateUp, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.support_return_to_chat))
              }
            }
          }
        }
      } else {
        for ((channelName, list) in groupedNotifications) {
          item(key = "header_$channelName") {
            val isExpanded = expandedStates.getOrDefault(channelName, true)
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .clickable(role = Role.Button) { expandedStates[channelName] = !isExpanded }
                  .heightIn(min = 56.dp)
                  .padding(horizontal = 4.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = channelName.ifEmpty { stringResource(R.string.support_reminders) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
              )
              Text(
                list.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Icon(
                if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription =
                  stringResource(
                    if (isExpanded) R.string.cd_collapse_icon else R.string.cd_expand_icon
                  ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (expandedStates.getOrDefault(channelName, true)) {
            items(list, key = { it.id }) { notification ->
              NotificationItem(
                notification,
                onDeleteClick = { notificationToDelete = notification },
              )
            }
          }
        }
      }
    }
  }

  notificationToDelete?.let { notification ->
    AlertDialog(
      onDismissRequest = { notificationToDelete = null },
      icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
      title = { Text(stringResource(R.string.notifications_delete_dialog_title)) },
      text = {
        Text(stringResource(R.string.notifications_delete_dialog_content, notification.title))
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.removeNotification(notification.id)
            notificationToDelete = null
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
          Text(stringResource(R.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { notificationToDelete = null }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}

@Composable
fun NotificationItem(notification: ScheduledNotification, onDeleteClick: () -> Unit) {
  val context = LocalContext.current
  val scheduledTime =
    Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, notification.hour)
      set(Calendar.MINUTE, notification.minute)
      set(Calendar.SECOND, 0)
    }
  val timeLabel = android.text.format.DateFormat.getTimeFormat(context).format(scheduledTime.time)
  val dateLabel =
    if (
      !notification.repeatDaily &&
        notification.hasYear() &&
        notification.hasMonth() &&
        notification.hasDay()
    ) {
      val date =
        Calendar.getInstance().apply {
          set(notification.year, notification.month - 1, notification.day)
        }
      android.text.format.DateFormat.getMediumDateFormat(context).format(date.time)
    } else null

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            notification.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            notification.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onDeleteClick) {
          Icon(
            Icons.Outlined.Delete,
            contentDescription =
              stringResource(R.string.support_delete_reminder, notification.title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(timeLabel, style = MaterialTheme.typography.titleMedium)
            dateLabel?.let {
              Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Text(
            stringResource(
              if (notification.repeatDaily) R.string.notifications_repeat_daily
              else R.string.notifications_repeat_one_time
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}
