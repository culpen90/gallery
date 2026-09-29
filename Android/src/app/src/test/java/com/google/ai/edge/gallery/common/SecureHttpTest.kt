package com.google.ai.edge.gallery.common

import java.io.IOException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecureHttpTest {
  @Test
  fun rejectsCleartextAndEmbeddedCredentialsBeforeOpeningConnection() {
    var requests = 0
    val executor = HttpsRequestExecutor {
      requests++
      FakeConnection(it)
    }
    listOf(
      "http://huggingface.co/model",
      "file:///private/model",
      "https://user:secret@huggingface.co/model",
    ).forEach { unsafe -> expectFailure { executor.openConnection(unsafe) } }
    assertEquals(0, requests)
  }

  @Test
  fun rejectsDowngradeRedirectWithoutSendingAnotherRequest() {
    val initial = FakeConnection(URL("https://huggingface.co/model"), 302, "http://example.test/model")
    val executor = HttpsRequestExecutor { initial }
    expectFailure { executor.openConnection(initial.url.toString(), accessToken = "private-token") }
    assertTrue(initial.disconnected)
    assertEquals("Bearer private-token", initial.sentHeaders["Authorization"])
  }

  @Test
  fun keepsCredentialsOnHuggingFaceRedirectAndStripsThemAtCdnBoundary() {
    val requests = mutableListOf<FakeConnection>()
    val destinations = listOf("/renamed", "https://cdn.example.test/model", "https://huggingface.co/back")
    val executor = HttpsRequestExecutor {
      FakeConnection(it, if (requests.size < destinations.size) 302 else 200,
        destinations.getOrNull(requests.size)).also(requests::add)
    }
    val response = executor.openConnection(
      "https://huggingface.co/model", accessToken = "private-token",
      headers = mapOf("Range" to "bytes=123-"),
    )
    assertEquals(4, requests.size)
    assertEquals("Bearer private-token", requests[0].sentHeaders["Authorization"])
    assertEquals("Bearer private-token", requests[1].sentHeaders["Authorization"])
    assertNull(requests[2].sentHeaders["Authorization"])
    assertNull(requests[3].sentHeaders["Authorization"])
    requests.forEach {
      assertEquals("bytes=123-", it.sentHeaders["Range"])
      assertFalse(it.instanceFollowRedirects)
      assertFalse(it.useCaches)
      assertEquals(15_000, it.connectTimeout)
      assertEquals(30_000, it.readTimeout)
    }
    assertEquals(requests.last(), response)
    assertTrue(requests.dropLast(1).all { it.disconnected })
    assertFalse(requests.last().disconnected)
  }

  @Test
  fun neverSendsStoredTokenToOtherHostsSubdomainsOrPorts() {
    listOf(
      "https://example.test/model",
      "https://huggingface.co.example.test/model",
      "https://models.huggingface.co/model",
      "https://huggingface.co:8443/model",
    ).forEach { target ->
      val connection = FakeConnection(URL(target))
      HttpsRequestExecutor { connection }.openConnection(target, accessToken = "private-token")
      assertNull(connection.sentHeaders["Authorization"])
    }
  }

  @Test
  fun refusesUnscopedAuthorizationHeader() {
    val executor = HttpsRequestExecutor { fail("Must not open a connection"); FakeConnection(it) }
    expectFailure {
      executor.openConnection("https://example.test", headers = mapOf("authorization" to "secret"))
    }
  }

  @Test
  fun boundsRedirectsAndClosesConnectionOnFailure() {
    val requests = mutableListOf<FakeConnection>()
    val executor = HttpsRequestExecutor {
      FakeConnection(it, 302, "/loop").also(requests::add)
    }
    expectFailure { executor.openConnection("https://example.test/model") }
    assertEquals(6, requests.size)
    assertTrue(requests.all { it.disconnected })
  }

  private fun expectFailure(block: () -> Unit) {
    try {
      block()
      fail("Expected the unsafe request to fail")
    } catch (_: IOException) {
      // The network request fails closed.
    }
  }

  private class FakeConnection(
    url: URL,
    private val status: Int = 200,
    private val location: String? = null,
  ) : HttpsURLConnection(url) {
    val sentHeaders = mutableMapOf<String, String>()
    var disconnected = false

    override fun connect() {}
    override fun disconnect() { disconnected = true }
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getHeaderField(name: String) = if (name == "Location") location else null
    override fun setRequestProperty(key: String, value: String) { sentHeaders[key] = value }
    override fun getCipherSuite() = "TLS_AES_256_GCM_SHA384"
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getServerCertificates(): Array<Certificate> = emptyArray()
  }
}
