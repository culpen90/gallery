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

package com.google.ai.edge.gallery

import android.app.Application
import com.google.ai.edge.gallery.diagnostics.DiagnosticsRecorder
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.notifications.NotificationScheduleManager
import com.google.ai.edge.gallery.security.EncryptedDataStore
import com.google.ai.edge.gallery.security.LegacyDownloadCleanup
import com.google.ai.edge.gallery.security.PrivateMediaMigration
import com.google.ai.edge.gallery.security.ProtectedImageSharing
import com.google.ai.edge.gallery.skills.PrivateSkillFiles
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GalleryApplication : Application() {
  private var privateSettingsInitialized = false

  @Inject lateinit var dataStoreRepository: DataStoreRepository
  @Inject lateinit var notificationScheduleManager: NotificationScheduleManager

  override fun onCreate() {
    super.onCreate()
    DiagnosticsRecorder.initialize(this)
    FirebaseApp.initializeApp(this)
    // Encrypted settings may be unavailable while the phone is locked.
    firebaseAnalytics?.setAnalyticsCollectionEnabled(false)
  }
  @Synchronized
  fun initializePrivateSettings() {
    if (privateSettingsInitialized) return
    EncryptedDataStore.migrateAll(this)
    PrivateMediaMigration.migrate(this)
    ProtectedImageSharing.migrateLegacyCache(this)
    PrivateSkillFiles(filesDir).migrateAll()
    LegacyDownloadCleanup.migrate(this)
    DiagnosticsRecorder.onUserUnlocked(this)
    notificationScheduleManager.initialize()
    ThemeSettings.themeOverride.value = dataStoreRepository.readTheme()
    firebaseAnalytics?.setAnalyticsCollectionEnabled(dataStoreRepository.readFirebaseAnalytics())
    privateSettingsInitialized = true
  }
}
