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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.ai.edge.gallery.security.SecureActivity
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : SecureActivity() {

  private val modelManagerViewModel: ModelManagerViewModel by viewModels()
  private var modelsLoaded = false
  override val offersDiagnosticsRecovery: Boolean = true

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    // Restore result registrations, but never restore the app's authorization state.
    super.onCreate(savedInstanceState)
    setSecureContent {
      GalleryTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          GalleryApp(modelManagerViewModel = modelManagerViewModel)
        }
      }
    }
    splashScreen.setOnExitAnimationListener { it.remove() }

    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Fix for three-button nav not properly going edge-to-edge.
      // See: https://issuetracker.google.com/issues/298296168
      window.isNavigationBarContrastEnforced = false
    }
    // Let Android's normal screen-lock timeout protect an unattended phone.
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)

    if (isAccessUnlocked) handleDeferredDeepLink()
  }

  override fun onAccessUnlocked() {
    super.onAccessUnlocked()
    if (!modelsLoaded) {
      @OptIn(ExperimentalApi::class)
      ExperimentalFlags.enableBenchmark = false
      modelManagerViewModel.loadModelAllowlist()
      modelsLoaded = true
    }
    modelManagerViewModel.setAppInForeground(foreground = true)
    handleDeferredDeepLink()
  }

  override fun onAccessLocked() {
    if (modelsLoaded) modelManagerViewModel.setAppInForeground(foreground = false)
  }

  private fun handleDeferredDeepLink() {
    intent.getStringExtra("deeplink")?.let { link ->
      intent.removeExtra("deeplink")
      val uri = link.toUri()
      when (uri.scheme) {
        "https" -> startActivity(Intent(Intent.ACTION_VIEW, uri))
        "com.google.ai.edge.gallery" -> intent.data = uri
      }
    }
  }
}
