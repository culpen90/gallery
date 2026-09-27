/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.diagnostics

/** Best-effort secret filtering; diagnostic text can still contain personal information. */
object DiagnosticRedactor {
  private const val MASK = "[REDACTED]"
  private const val SECRET_KEY =
    "authorization|proxy[-_]?authorization|(?:set[-_]?)?cookie|" +
      "password|passwd|client[-_]?secret|secret|" +
      "(?:access|refresh|id|auth|bearer|session|hf|hugging[-_]?face)[-_]?token|token|" +
      "(?:x[-_]?)?api[-_]?key|feedback[-_]?api[-_]?key|private[-_]?key"

  private val urlCredentials = Regex("(?i)(https?://)[^/\\s:@]+(?::[^@\\s/]*)?@")
  private val urlSecrets =
    Regex(
      "(?i)([?&](?:$SECRET_KEY|key|code|auth|session[-_]?id|sig|signature|" +
        "x-amz-(?:signature|credential|security-token)|x-goog-(?:signature|credential))=)[^&#\\s\"'<>]*"
    )
  private val cookieHeader = Regex("(?im)(\\b(?:set-cookie|cookie)\\s*:\\s*)[^\\r\\n]+")
  private val authorizationScheme =
    Regex("(?i)\\b(Bearer|Basic)\\s+(?:\\[REDACTED\\]|[^\\s\"'<>;,}\\]]+)")
  private val assignedSecret =
    Regex(
      "(?i)(?<![?&\\w-])([\"']?(?:$SECRET_KEY)[\"']?\\s*[:=]\\s*)" +
        "(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\[REDACTED\\]|[^\\s,;&}\\]\"']+)"
    )
  private val recognizableToken =
    Regex(
      "\\b(?:hf_[A-Za-z0-9]{6,}|sk-[A-Za-z0-9_-]{8,}|AIza[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16})\\b"
    )
  private val jwt = Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b")

  fun redact(text: String): String {
    var result = urlCredentials.replace(text) { "${it.groupValues[1]}$MASK@" }
    result = urlSecrets.replace(result) { "${it.groupValues[1]}$MASK" }
    result = cookieHeader.replace(result) { "${it.groupValues[1]}$MASK" }
    result = authorizationScheme.replace(result) { "${it.groupValues[1]} $MASK" }
    result =
      assignedSecret.replace(result) {
        val quote =
          it.groupValues[2].first().takeIf { character -> character == '\"' || character == '\'' }
        "${it.groupValues[1]}${quote ?: ""}$MASK${quote ?: ""}"
      }
    result = recognizableToken.replace(result, MASK)
    return jwt.replace(result, MASK)
  }
}
