/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.intents

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileActionParamsTest {
  private val moshi = Moshi.Builder().build()

  @Test
  fun contactOnlyRequiresANameAndPreservesOptionalFields() {
    val adapter = moshi.adapter(CreateContactParams::class.java)

    assertEquals(CreateContactParams(name = "Ada"), adapter.fromJson("""{"name":"Ada"}"""))
    assertEquals(
      CreateContactParams(name = "Ada", phone_number = "+15555550123", email = "ada@example.com"),
      adapter.fromJson(
        """{"name":"Ada","phone_number":"+15555550123","email":"ada@example.com"}"""
      ),
    )
  }

  @Test(expected = JsonDataException::class)
  fun contactWithoutANameIsRejected() {
    moshi.adapter(CreateContactParams::class.java).fromJson("""{"phone_number":"555"}""")
  }

  @Test
  fun mapLocationPreservesSpacesAndSpecialCharactersForUriEncoding() {
    assertEquals(
      ShowLocationOnMapParams("Café & Park, Boston"),
      moshi.adapter(ShowLocationOnMapParams::class.java)
        .fromJson("""{"location":"Café & Park, Boston"}"""),
    )
  }

  @Test(expected = JsonDataException::class)
  fun mapRequestWithoutALocationIsRejected() {
    moshi.adapter(ShowLocationOnMapParams::class.java).fromJson("{}")
  }
}
