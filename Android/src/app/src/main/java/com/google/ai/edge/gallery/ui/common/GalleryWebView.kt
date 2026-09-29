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

package com.google.ai.edge.gallery.ui.common

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.google.ai.edge.gallery.common.SecureHttp
import com.google.ai.edge.gallery.skills.PrivateSkillFiles
import java.io.ByteArrayInputStream

private val iframeWrapper =
  """
  <html>
    <body style="margin:0;padding:0;">
      <iframe
          width="100%"
          height="100%"
          src="___"
          frameborder="0"
          style="border:0;">
      </iframe>
    </body>
  </html>
  """
    .trimIndent()

/**
 * A base [WebViewClient] for [GalleryWebView] that handles local asset loading and logs page
 * finishing.
 */
open class BaseGalleryWebViewClient(context: Context) : WebViewClient() {
  private val privateSkillFiles = PrivateSkillFiles(context.filesDir)
  @Volatile private var allowedPrivateSkill: String? = null
  private val localFileAssetsLoader =
    WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
      .build()

  /** Set by app code before loading a skill; scripts cannot grant themselves another directory. */
  fun allowPrivateSkillUrl(url: String) {
    val uri = android.net.Uri.parse(url)
    allowedPrivateSkill = if (
      uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
        uri.pathSegments.firstOrNull() == "skills"
    ) {
      uri.pathSegments.getOrNull(1)?.also {
        privateSkillFiles.importedDirectory("skills/$it")
      }
    } else null
  }

  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    if (url != null && url != "about:blank") {
      val uri = android.net.Uri.parse(url)
      if (uri.host != "appassets.androidplatform.net" ||
          uri.pathSegments.take(2) != listOf("skills", allowedPrivateSkill)) {
        allowedPrivateSkill = null
      }
    }
    super.onPageStarted(view, url, favicon)
  }

  override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?,
  ): WebResourceResponse? {
    val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
    if (uri.scheme != "https") return blockedResource()
    if (uri.host == "appassets.androidplatform.net") {
      if (runCatching { SecureHttp.requireHttpsUrl(uri.toString()) }.isFailure) return blockedResource()
      if (uri.pathSegments.firstOrNull() == "assets") {
        return localFileAssetsLoader.shouldInterceptRequest(uri) ?: blockedResource()
      }
      val parts = uri.pathSegments
      if (parts.size < 3 || parts[0] != "skills" || parts[1] != allowedPrivateSkill ||
          parts.last() == "SKILL.md") return blockedResource()
      return try {
        var logicalFile = privateSkillFiles.importedDirectory("skills/${parts[1]}")
        parts.drop(2).forEach { logicalFile = privateSkillFiles.child(logicalFile, it) }
        if (!privateSkillFiles.exists(logicalFile)) return blockedResource()
        val plaintext = privateSkillFiles.read(logicalFile)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(logicalFile.extension.lowercase())
          ?: "application/octet-stream"
        val stream = object : ByteArrayInputStream(plaintext) {
          override fun close() {
            plaintext.fill(0)
            super.close()
          }
        }
        WebResourceResponse(mimeType, "UTF-8", stream)
      } catch (_: Exception) {
        blockedResource()
      }
    }
    return super.shouldInterceptRequest(view, request)
  }

  private fun blockedResource() = WebResourceResponse(
    "text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0))
  )
}

/**
 * A reusable Composable that wraps an Android WebView, providing common configurations and handling
 * for permissions, local asset loading, and JavaScript interfaces.
 */
@Composable
fun GalleryWebView(
  modifier: Modifier = Modifier,
  initialUrl: String? = null,
  useIframeWrapper: Boolean = false,
  preventParentScrolling: Boolean = false,
  allowRequestPermission: Boolean = false,
  onWebViewCreated: ((WebView) -> Unit)? = null,
  onConsoleMessage: ((ConsoleMessage?) -> Unit)? = null,
  onPermissionRequest: ((PermissionRequest?) -> Unit)? = null,
  customWebViewClient: WebViewClient? = null,
) {
  val context = LocalContext.current

  val curWebViewClient = remember {
    customWebViewClient ?: BaseGalleryWebViewClient(context = context)
  }
  var pendingCameraPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
  var pendingAudioPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
      isGranted: Boolean ->
      pendingCameraPermissionRequest?.let { request ->
        if (isGranted) {
          request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        } else {
          // If camera is denied, we don't call request.deny() on the whole request,
          // as it might contain other resources. The WebView will handle the denial
          // of the specific camera resource.
        }
        pendingCameraPermissionRequest = null
      }
    }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
      isGranted: Boolean ->
      pendingAudioPermissionRequest?.let { request ->
        if (isGranted) {
          request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        } else {
          // Similar to camera, don't call request.deny() on the whole request.
        }
        pendingAudioPermissionRequest = null
      }
    }

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      WebView(ctx).apply {
        WebView.setWebContentsDebuggingEnabled(false)
        val privateWebView = this
        CookieManager.getInstance().apply {
          setAcceptCookie(false)
          setAcceptThirdPartyCookies(privateWebView, false)
          removeAllCookies(null)
        }
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )

        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = false
          databaseEnabled = false
          allowFileAccess = false
          allowContentAccess = false
          cacheMode = WebSettings.LOAD_NO_CACHE
          mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
          safeBrowsingEnabled = true
          saveFormData = false
          mediaPlaybackRequiresUserGesture = false
        }

        if (preventParentScrolling) {
          setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
          }
        }

        webChromeClient =
          object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
              onConsoleMessage?.invoke(consoleMessage)
              return super.onConsoleMessage(consoleMessage)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
              if (!allowRequestPermission) {
                request?.deny()
                return
              }

              if (request == null) return
              onPermissionRequest?.invoke(request)
                ?: run {
                  val resources = request.resources
                  val isCameraRequest = resources.any {
                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                  }
                  val isAudioRequest = resources.any {
                    it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                  }

                  if (isCameraRequest) {
                    pendingCameraPermissionRequest = request
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                  }

                  if (isAudioRequest) {
                    pendingAudioPermissionRequest = request
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  }

                  val otherResources =
                    resources
                      .filter {
                        it != PermissionRequest.RESOURCE_VIDEO_CAPTURE &&
                          it != PermissionRequest.RESOURCE_AUDIO_CAPTURE
                      }
                      .toTypedArray()
                  if (otherResources.isNotEmpty()) {
                    request.grant(otherResources)
                  }
                }
            }
          }

        webViewClient = curWebViewClient

        initialUrl?.let { url ->
          if (runCatching { SecureHttp.requireHttpsUrl(url) }.isFailure) return@let
          (curWebViewClient as? BaseGalleryWebViewClient)?.allowPrivateSkillUrl(url)
          if (useIframeWrapper) {
            loadDataWithBaseURL(null, iframeWrapper.replace("___", url), "text/html", "UTF-8", null)
          } else {
            loadUrl(url)
          }
        }
        onWebViewCreated?.invoke(this)
      }
    },
    onRelease = { webView ->
      webView.stopLoading()
      webView.clearCache(true)
      webView.clearHistory()
      webView.clearFormData()
      webView.destroy()
    },
  )
}
