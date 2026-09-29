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

package com.google.ai.edge.gallery.common

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Platform-verified TLS, with explicit redirect and Hugging Face credential boundaries. */
object SecureHttp {
  /** Returns a connected response. The caller must close its streams and disconnect it. */
  fun openConnection(
    url: String,
    method: String = "GET",
    accessToken: String? = null,
    headers: Map<String, String> = emptyMap(),
    connectTimeoutMs: Int = 15_000,
    readTimeoutMs: Int = 30_000,
  ): HttpURLConnection =
    HttpsRequestExecutor().openConnection(
      url, method, accessToken, headers, connectTimeoutMs, readTimeoutMs
    )

  fun requireHttpsUrl(value: String): URL {
    val url = try {
      URL(value)
    } catch (_: Exception) {
      throw IOException("Invalid HTTPS URL")
    }
    if (
      !url.protocol.equals("https", ignoreCase = true) ||
        url.host.isNullOrBlank() ||
        url.userInfo != null
    ) {
      throw IOException("Only HTTPS URLs without embedded credentials are allowed")
    }
    return url
  }

  /** Stored Hugging Face tokens are never credentials for custom servers or CDN hosts. */
  fun isHuggingFaceOrigin(url: URL): Boolean =
    url.protocol.equals("https", ignoreCase = true) &&
      url.host.equals("huggingface.co", ignoreCase = true) &&
      (url.port == -1 || url.port == 443) &&
      url.userInfo == null
}

internal class HttpsRequestExecutor(
  private val connectionFactory: (URL) -> HttpsURLConnection = {
    it.openConnection() as HttpsURLConnection
  }
) {
  fun openConnection(
    url: String,
    method: String = "GET",
    accessToken: String? = null,
    headers: Map<String, String> = emptyMap(),
    connectTimeoutMs: Int = 15_000,
    readTimeoutMs: Int = 30_000,
  ): HttpURLConnection {
    if (method != "GET" && method != "HEAD") {
      throw IOException("Unsupported HTTPS request method")
    }
    if (headers.keys.any { it.equals("Authorization", ignoreCase = true) }) {
      throw IOException("Authorization must use the scoped token parameter")
    }
    var target = SecureHttp.requireHttpsUrl(url)
    var sendToken = !accessToken.isNullOrEmpty() && SecureHttp.isHuggingFaceOrigin(target)
    var redirects = 0
    while (true) {
      val connection = connectionFactory(target)
      try {
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.useCaches = false
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (sendToken) {
          connection.setRequestProperty("Authorization", "Bearer $accessToken")
        }
        connection.connect()
        val responseCode = connection.responseCode
        if (responseCode !in setOf(301, 302, 303, 307, 308)) return connection
        if (++redirects > 5) throw IOException("Too many HTTPS redirects")
        val location = connection.getHeaderField("Location")
          ?: throw IOException("HTTPS redirect has no destination")
        val next = try {
          SecureHttp.requireHttpsUrl(URL(target, location).toExternalForm())
        } catch (_: Exception) {
          throw IOException("Unsafe HTTPS redirect destination")
        }
        // Once a redirect leaves the token's origin, no later hop receives the token.
        sendToken = sendToken && SecureHttp.isHuggingFaceOrigin(next)
        target = next
        connection.disconnect()
      } catch (error: Exception) {
        connection.disconnect()
        throw error
      }
    }
  }
}
