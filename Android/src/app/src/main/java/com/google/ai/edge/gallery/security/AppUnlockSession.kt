/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.security

/** In-memory authorization only. An unlocked state must never survive Activity recreation. */
internal class AppUnlockSession {
  var isResumed = false
    private set
  var isUnlocked = false
    private set
  var activeAttempt: Long? = null
    private set
  private var nextAttempt = 0L
  private var authenticatedWhilePaused = false

  fun resume() {
    isResumed = true
    isUnlocked = authenticatedWhilePaused
    authenticatedWhilePaused = false
  }

  fun pause() {
    isResumed = false
    isUnlocked = false
    authenticatedWhilePaused = false
    // The system PIN/password screen may pause its caller. Keep only its outstanding attempt,
    // never the previous authorization, until the system reports success or cancellation.
  }

  fun beginAuthentication(): Long? {
    if (!isResumed || isUnlocked || activeAttempt != null) return null
    return (++nextAttempt).also { activeAttempt = it }
  }

  fun authenticationSucceeded(attempt: Long): Boolean {
    if (activeAttempt != attempt) return false
    activeAttempt = null
    isUnlocked = isResumed
    authenticatedWhilePaused = !isResumed
    return true
  }

  fun authenticationFailed(attempt: Long): Boolean {
    if (activeAttempt != attempt) return false
    activeAttempt = null
    isUnlocked = false
    authenticatedWhilePaused = false
    return true
  }

  fun lock() {
    activeAttempt = null
    isUnlocked = false
    authenticatedWhilePaused = false
  }
}
