/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
  @Test
  fun masksAuthorizationSchemesAndWholeCookieHeaders() {
    val cases =
      mapOf(
        "Authorization: Bearer abc-secret-123" to "abc-secret-123",
        "Authorization: Basic dXNlcjpwYXNz" to "dXNlcjpwYXNz",
        "{\"Authorization\":\"Bearer private-value\"}" to "private-value",
        "request Cookie: session=private-cookie; preference=private-preference" to "private-",
        "Set-Cookie: session=secret-cookie; Path=/; HttpOnly" to "secret-cookie",
      )
    for ((text, secret) in cases) {
      val result = DiagnosticRedactor.redact(text)
      assertFalse("Leaked a secret in $result", result.contains(secret))
      assertTrue(result.contains("[REDACTED]"))
    }
  }

  @Test
  fun masksJsonAndPropertySecretsWithCamelCaseSnakeCaseAndEscapedValues() {
    val keys =
      listOf(
        "access_token",
        "refreshToken",
        "idToken",
        "client_secret",
        "apiKey",
        "x-api-key",
        "password",
        "hf_token",
        "huggingFaceToken",
        "feedback_api_key",
        "authToken",
      )
    for (key in keys) {
      for (input in
        listOf(
          "{\"$key\": \"private-value\", \"duration\": 123}",
          "$key=private-value count=2",
          "'$key': 'private-value'",
        )) {
        val result = DiagnosticRedactor.redact(input)
        assertFalse("$key leaked: $result", result.contains("private-value"))
      }
    }
    assertFalse(DiagnosticRedactor.redact("{\"password\":\"hello\\\" secret\"}").contains("secret"))
  }

  @Test
  fun masksCredentialsAndSecretsInUrlsButKeepsUsefulParameters() {
    val result =
      DiagnosticRedactor.redact(
        "GET https://alice:myPassword@example.test/model?revision=main&token=private-token" +
          "&code=private-code&x-amz-signature=private-signature&count=2"
      )
    assertFalse(result.contains("alice"))
    assertFalse(result.contains("myPassword"))
    assertFalse(result.contains("private-"))
    assertTrue(result.contains("example.test/model?revision=main"))
    assertTrue(result.contains("count=2"))
  }

  @Test
  fun masksRecognizableTokensEvenWithoutAFieldName() {
    val secrets =
      listOf(
        "hf_abcdefghijklmn0123456789",
        "sk-proj-abcdefgh0123456789",
        "AIzaabcdefghijklmnopqrstuvwxyz0123456789",
        "AKIA0123456789ABCDEF",
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwcml2YXRlIn0.signature123",
      )
    for (secret in secrets) {
      assertEquals("failed: [REDACTED]", DiagnosticRedactor.redact("failed: $secret"))
    }
  }

  @Test
  fun leavesOrdinaryDiagnosticsIntact() {
    val message = "2026-09-27 Audio sampleRate=16000 token_count=48 model=Qwen durationMs=125"
    assertEquals(message, DiagnosticRedactor.redact(message))
  }

  @Test
  fun redactionIsStableWhenStoredRecordsAreRedactedAgainOnExport() {
    val inputs =
      listOf(
        "Authorization: Bearer private-token",
        "{\"apiKey\":\"private-key\",\"url\":\"https://example.test?token=private-token&revision=main\"}",
        "{\"Authorization\":\"Bearer private-token\"}",
        "Cookie: session=private-cookie; preference=value",
        "password=private-password",
      )
    for (input in inputs) {
      val once = DiagnosticRedactor.redact(input)
      assertEquals(once, DiagnosticRedactor.redact(once))
    }
    assertEquals(
      "{\"apiKey\":\"[REDACTED]\",\"url\":\"https://example.test?token=[REDACTED]&revision=main\"}",
      DiagnosticRedactor.redact(inputs[1]),
    )
  }
}
