/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUnlockSessionTest {
  @Test
  fun backgroundingDropsAuthorizationAndRequiresFreshAuthentication() {
    val session = AppUnlockSession()
    session.resume()
    val attempt = session.beginAuthentication()!!
    assertTrue(session.authenticationSucceeded(attempt))
    assertTrue(session.isUnlocked)

    session.pause()
    assertFalse(session.isUnlocked)
    session.resume()
    assertFalse(session.isUnlocked)
    assertNotNull(session.beginAuthentication())
  }

  @Test
  fun canceledOrStaleCallbacksCannotUnlockANewerAttempt() {
    val session = AppUnlockSession()
    session.resume()
    val canceledAttempt = session.beginAuthentication()!!
    assertTrue(session.authenticationFailed(canceledAttempt))
    val nextAttempt = session.beginAuthentication()!!
    assertNotEquals(canceledAttempt, nextAttempt)

    assertFalse(session.authenticationSucceeded(canceledAttempt))
    assertFalse(session.authenticationFailed(canceledAttempt))
    assertFalse(session.isUnlocked)
    assertTrue(session.authenticationSucceeded(nextAttempt))
    assertTrue(session.isUnlocked)
  }

  @Test
  fun deviceCredentialHandoffCannotShowPrivateContentWhilePaused() {
    val session = AppUnlockSession()
    session.resume()
    val attempt = session.beginAuthentication()!!
    session.pause()
    assertTrue(session.authenticationSucceeded(attempt))
    assertFalse(session.isUnlocked)
    assertNull(session.beginAuthentication())

    session.resume()
    assertTrue(session.isUnlocked)
    session.pause()
    session.resume()
    assertFalse(session.isUnlocked)
  }

  @Test
  fun lockInvalidatesOutstandingCallbacksAndPendingCredentialSuccess() {
    val session = AppUnlockSession()
    session.resume()
    val attempt = session.beginAuthentication()!!
    session.pause()
    session.lock()
    assertFalse(session.authenticationSucceeded(attempt))
    session.resume()
    assertFalse(session.isUnlocked)

    val nextAttempt = session.beginAuthentication()!!
    session.pause()
    assertTrue(session.authenticationSucceeded(nextAttempt))
    session.lock()
    session.resume()
    assertFalse(session.isUnlocked)
  }

  @Test
  fun recreatedSessionNeverInheritsAuthorization() {
    val previous = AppUnlockSession()
    previous.resume()
    previous.authenticationSucceeded(previous.beginAuthentication()!!)
    assertTrue(previous.isUnlocked)

    val recreated = AppUnlockSession()
    assertFalse(recreated.isUnlocked)
    assertNull(recreated.beginAuthentication())
    recreated.resume()
    assertFalse(recreated.isUnlocked)
    assertNotNull(recreated.beginAuthentication())
  }
}
