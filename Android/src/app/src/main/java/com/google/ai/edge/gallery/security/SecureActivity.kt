/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.security

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.gallery.GalleryApplication
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.ui.diagnostics.DiagnosticsActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Protects private screens with Android's strong biometric or device credential authentication.
 * A native hidden container closes the drawing/accessibility window synchronously on pause.
 * Private composition is disposed too, closing its dialogs. Saveable UI/launcher keys are kept
 * only in memory for the next authenticated foreground session, never in an Activity Bundle.
 */
abstract class SecureActivity : ComponentActivity() {
  private val unlockSession = AppUnlockSession()
  private lateinit var privateView: ComposeView
  private lateinit var lockView: LinearLayout
  private lateinit var lockMessage: TextView
  private lateinit var unlockButton: Button
  private lateinit var setupButton: Button
  private var privateContent: (@Composable () -> Unit)? = null
  private var privateContentCreated = false
  private var privateSavedState: Map<String, List<Any?>>? = null
  private var privateStateRegistry: SaveableStateRegistry? = null
  private var preparation: Job? = null
  private var preparationGeneration = 0L
  private var authenticationCancellation: CancellationSignal? = null
  private var promptOnResume = true

  protected val isAccessUnlocked: Boolean
    get() = unlockSession.isUnlocked

  protected open val offersDiagnosticsRecovery: Boolean = false

  private val keyguardManager: KeyguardManager
    get() = getSystemService(KeyguardManager::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    // Third-party overlay windows must not cover authentication or private controls.
    window.setHideOverlayWindows(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      setRecentsScreenshotEnabled(false)
    }
    super.onCreate(savedInstanceState)

    privateView = ComposeView(this).apply {
      id = View.generateViewId()
      visibility = View.GONE
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
      importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
      filterTouchesWhenObscured = true
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
    lockView = createLockView()
    setContentView(
      FrameLayout(this).apply {
        addView(privateView, matchParentLayout())
        addView(lockView, matchParentLayout())
      }
    )
    showLockedScreen()
  }

  protected fun setSecureContent(content: @Composable () -> Unit) {
    privateContent = content
    if (unlockSession.isUnlocked) showPrivateScreen()
  }

  /** Called after authentication, before initializing or exposing private content. */
  protected open fun onAccessUnlocked() {}

  /** Runs on IO after authentication. Recovery screens can prepare their storage independently. */
  protected open fun preparePrivateAccess() {
    (application as GalleryApplication).initializePrivateSettings()
  }

  /** Called on every pause, before private content can enter the background. */
  protected open fun onAccessLocked() {}

  override fun onResume() {
    super.onResume()
    unlockSession.resume()
    if (!keyguardManager.isDeviceSecure || keyguardManager.isDeviceLocked) {
      unlockSession.lock()
    }
    if (unlockSession.isUnlocked) {
      showPrivateScreen()
    } else {
      showLockedScreen()
      if (promptOnResume) requestUnlock()
    }
  }

  override fun onPause() {
    promptOnResume = unlockSession.isUnlocked || promptOnResume
    unlockSession.pause()
    preparationGeneration++
    preparation?.cancel()
    preparation = null
    showLockedScreen()
    onAccessLocked()
    super.onPause()
  }

  override fun onDestroy() {
    unlockSession.lock()
    authenticationCancellation?.cancel()
    authenticationCancellation = null
    privateSavedState = null
    privateStateRegistry = null
    super.onDestroy()
  }

  private fun requestUnlock() {
    if (!keyguardManager.isDeviceSecure) {
      showLockedScreen()
      return
    }
    val attempt = unlockSession.beginAuthentication() ?: return
    promptOnResume = false
    val cancellation = CancellationSignal()
    authenticationCancellation = cancellation
    unlockButton.isEnabled = false
    try {
      BiometricPrompt.Builder(this)
        .setTitle(getString(R.string.security_unlock_title))
        .setSubtitle(getString(R.string.security_unlock_subtitle))
        .setAllowedAuthenticators(
          BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .setConfirmationRequired(true)
        .build()
        .authenticate(
          cancellation,
          mainExecutor,
          object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
              if (!unlockSession.authenticationSucceeded(attempt)) return
              authenticationCancellation = null
              if (unlockSession.isResumed) {
                if (keyguardManager.isDeviceSecure && !keyguardManager.isDeviceLocked) {
                  showPrivateScreen()
                } else {
                  unlockSession.lock()
                  showLockedScreen()
                }
              }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
              if (!unlockSession.authenticationFailed(attempt)) return
              authenticationCancellation = null
              showLockedScreen()
              // Never expose platform/internal error details on the lock screen.
              lockMessage.setText(R.string.security_unlock_retry_message)
            }
          },
        )
    } catch (_: RuntimeException) {
      unlockSession.authenticationFailed(attempt)
      authenticationCancellation = null
      showLockedScreen()
      lockMessage.setText(R.string.security_unlock_retry_message)
    }
  }

  private fun showPrivateScreen() {
    if (!unlockSession.isUnlocked || !unlockSession.isResumed || isFinishing || isDestroyed) return
    if (preparation != null) return
    val generation = ++preparationGeneration
    lockMessage.setText(R.string.security_preparing_message)
    unlockButton.isEnabled = false
    preparation = lifecycleScope.launch {
      try {
        withContext(Dispatchers.IO) {
          preparePrivateAccess()
        }
        if (!mayRevealPrivateScreen(generation)) return@launch
        onAccessUnlocked()
        if (!mayRevealPrivateScreen(generation)) return@launch
        if (!privateContentCreated) {
          val content = privateContent ?: return@launch
          privateView.setContent {
            val parentRegistry = LocalSaveableStateRegistry.current
            val registry = remember {
              SaveableStateRegistry(privateSavedState) { value ->
                parentRegistry?.canBeSaved(value) == true
              }
            }
            privateStateRegistry = registry
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { content() }
          }
          privateContentCreated = true
        }
        privateView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        privateView.visibility = View.VISIBLE
        lockView.visibility = View.GONE
      } catch (canceled: CancellationException) {
        throw canceled
      } catch (_: Exception) {
        if (generation == preparationGeneration) {
          unlockSession.lock()
          showLockedScreen()
          lockMessage.setText(R.string.security_storage_retry_message)
        }
      } finally {
        if (generation == preparationGeneration) preparation = null
      }
    }
  }

  private fun mayRevealPrivateScreen(generation: Long): Boolean =
    generation == preparationGeneration && unlockSession.isUnlocked && unlockSession.isResumed &&
      !isFinishing && !isDestroyed && keyguardManager.isDeviceSecure && !keyguardManager.isDeviceLocked

  private fun showLockedScreen() {
    if (!::privateView.isInitialized) return
    privateView.visibility = View.GONE
    privateView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    lockView.visibility = View.VISIBLE
    if (privateContentCreated) {
      privateSavedState = try {
        privateStateRegistry?.performSave()
      } catch (_: RuntimeException) {
        null
      }
      // ComposeView may recreate a disposed composition when reattached. Replace its retained
      // content first so that recreation while locked cannot restore private dialogs or data.
      privateView.setContent {}
      privateView.disposeComposition()
      privateStateRegistry = null
      privateContentCreated = false
    }
    val secureDevice = keyguardManager.isDeviceSecure
    lockMessage.setText(
      if (secureDevice) R.string.security_locked_message else R.string.security_setup_message
    )
    unlockButton.visibility = if (secureDevice) View.VISIBLE else View.GONE
    unlockButton.isEnabled = unlockSession.activeAttempt == null
    setupButton.visibility = if (secureDevice) View.GONE else View.VISIBLE
  }

  private fun createLockView(): LinearLayout =
    LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(48, 48, 48, 48)
      setBackgroundColor(Color.rgb(18, 18, 18))
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      addView(
        TextView(this@SecureActivity).apply {
          setText(R.string.security_unlock_title)
          setTextColor(Color.WHITE)
          textSize = 24f
          gravity = Gravity.CENTER
        }
      )
      lockMessage = TextView(this@SecureActivity).apply {
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(0, 32, 0, 32)
      }
      addView(lockMessage)
      unlockButton = Button(this@SecureActivity).apply {
        setText(R.string.security_unlock_button)
        filterTouchesWhenObscured = true
        setOnClickListener { requestUnlock() }
      }
      addView(unlockButton)
      setupButton = Button(this@SecureActivity).apply {
        setText(R.string.security_setup_button)
        filterTouchesWhenObscured = true
        setOnClickListener {
          try {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
          } catch (_: RuntimeException) {
            lockMessage.setText(R.string.security_setup_manual_message)
          }
        }
      }
      addView(setupButton)
      if (offersDiagnosticsRecovery) {
        addView(
          Button(this@SecureActivity).apply {
            setText(R.string.diagnostics_title)
            filterTouchesWhenObscured = true
            setOnClickListener {
              startActivity(Intent(this@SecureActivity, DiagnosticsActivity::class.java))
            }
          }
        )
      }
    }

  private fun matchParentLayout() =
    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}
