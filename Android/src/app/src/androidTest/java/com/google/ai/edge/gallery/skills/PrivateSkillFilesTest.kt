/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.skills

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.gallery.ui.common.BaseGalleryWebViewClient
import com.google.ai.edge.gallery.proto.Skill
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic sources only; each test cleans up its own isolated directory. */
@RunWith(AndroidJUnit4::class)
class PrivateSkillFilesTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun sourcesStayEncryptedAndEditorKeepsTheirLogicalNames() = withIsolatedFiles { files, base ->
    val directory = files.destinationDirectory("synthetic skill")
    val scriptDirectory = files.child(directory, "scripts").apply { mkdirs() }
    val script = files.child(scriptDirectory, "example.js")
    val content = "synthetic private API credential ${UUID.randomUUID()}".toByteArray()
    files.write(script, content)
    assertFalse(script.exists())
    val envelope = File(script.parentFile, "${script.name}.enc")
    assertTrue(envelope.exists())
    assertFalse(envelope.readBytes().toString(Charsets.UTF_8).contains(content.toString(Charsets.UTF_8)))
    assertArrayEquals(content, files.read(script))
    assertEquals(listOf("example.js"), files.listFiles(scriptDirectory).map { it.name })
    assertTrue(envelope.canonicalPath.startsWith(base.canonicalPath + File.separator))
  }

  @Test
  fun legacyInstructionsAndBinaryAssetsMigrateWithoutPlaintextFallback() = withIsolatedFiles { files, _ ->
    val directory = files.destinationDirectory("legacy").apply { mkdirs() }
    val instructions = files.child(directory, "SKILL.md")
    val asset = files.child(files.child(directory, "assets").apply { mkdirs() }, "image.bin")
    val instructionsContent = "synthetic confidential instructions".toByteArray()
    val assetContent = byteArrayOf(0, 1, 2, 3, 127, -1)
    instructions.writeBytes(instructionsContent)
    asset.writeBytes(assetContent)
    files.migrateAll()
    assertFalse(instructions.exists())
    assertFalse(asset.exists())
    assertArrayEquals(instructionsContent, files.read(instructions))
    assertArrayEquals(assetContent, files.read(asset))
    val envelope = File(asset.parentFile, "${asset.name}.enc")
    val damaged = envelope.readBytes()
    damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
    envelope.writeBytes(damaged)
    asset.writeText("must never be selected")
    assertThrows(GeneralSecurityException::class.java) { files.read(asset) }
    assertTrue(asset.exists())
  }

  @Test
  fun renameReencryptsForDestinationAndCiphertextCannotBeMovedBetweenSkills() = withIsolatedFiles { files, _ ->
    val original = files.destinationDirectory("original").apply { mkdirs() }
    val script = files.child(files.child(original, "scripts").apply { mkdirs() }, "index.js")
    val content = "synthetic renamed script".toByteArray()
    files.write(script, content)
    val renamed = files.destinationDirectory("renamed")
    files.copyDirectory(original, renamed)
    val copied = files.child(files.child(renamed, "scripts"), "index.js")
    assertArrayEquals(content, files.read(copied))
    assertArrayEquals(content, files.read(script))
    File(script.parentFile, "index.js.enc").copyTo(File(copied.parentFile, "index.js.enc"), overwrite = true)
    assertThrows(GeneralSecurityException::class.java) { files.read(copied) }
    assertArrayEquals(content, files.read(script))
  }

  @Test
  fun pathsCannotEscapeThroughTraversalOrSymlinks() = withIsolatedFiles { files, base ->
    val directory = files.destinationDirectory("safe").apply { mkdirs() }
    listOf("../escape", "/absolute", "..", ".", "source.js.enc", "nested/name").forEach { name ->
      assertThrows(IOException::class.java) { files.child(directory, name) }
    }
    assertThrows(IOException::class.java) { files.destinationDirectory("../escape") }
    assertThrows(IOException::class.java) { files.importedDirectory("skills/../../escape") }
    val link = File(directory, "outside")
    Files.createSymbolicLink(link.toPath(), base.toPath())
    try {
      assertThrows(IOException::class.java) { files.child(link, "source.js") }
    } finally {
      Files.delete(link.toPath())
    }
  }

  @Test
  fun webviewDecryptsOnlyItsExplicitlySelectedSkill() {
    val files = PrivateSkillFiles(context.filesDir)
    val directory = files.destinationDirectory("security-test-${UUID.randomUUID()}")
    try {
      val script = files.child(files.child(directory, "scripts").apply { mkdirs() }, "index.js")
      val content = "synthetic in-memory script".toByteArray()
      files.write(script, content)
      val url = "https://appassets.androidplatform.net/skills/${directory.name}/scripts/index.js"
      val client = BaseGalleryWebViewClient(context)
      assertEquals(403, client.shouldInterceptRequest(null, request(url))!!.statusCode)
      client.allowPrivateSkillUrl(url)
      val response = client.shouldInterceptRequest(null, request(url))!!
      assertArrayEquals(content, response.data.use { it.readBytes() })
      assertEquals(403, client.shouldInterceptRequest(null,
        request("https://appassets.androidplatform.net/datastore/user.pb.enc"))!!.statusCode)
      assertEquals(403, client.shouldInterceptRequest(null,
        request(url.replace(directory.name, "other-skill")))!!.statusCode)
      assertEquals(403, client.shouldInterceptRequest(null,
        request(url.replace("index.js", "index.js.enc")))!!.statusCode)
      assertFalse(script.exists())
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun scriptAndResultUrlsCannotSelectAnotherPrivateSkill() {
    val skill = Skill.newBuilder().setName("synthetic").setImportDirName("skills/synthetic").build()
    assertNull(skill.getJsSkillUrl("../../other/scripts/index.js"))
    assertNull(skill.getJsSkillUrl("/absolute.js"))
    assertEquals("", skill.getJsSkillWebviewUrl(
      "https://appassets.androidplatform.net/skills/other/assets/index.html"
    ))
    assertEquals("", skill.getJsSkillWebviewUrl("http://example.test/page"))
    assertEquals(
      "https://appassets.androidplatform.net/skills/synthetic/scripts/index.js",
      skill.getJsSkillUrl("index.js"),
    )
    assertEquals(
      "https://appassets.androidplatform.net/skills/synthetic/assets/chart.html",
      skill.getJsSkillWebviewUrl("chart.html"),
    )
  }

  private fun withIsolatedFiles(block: (PrivateSkillFiles, File) -> Unit) {
    val base = File(context.cacheDir, "private-skill-test-${UUID.randomUUID()}").apply { mkdirs() }
    try { block(PrivateSkillFiles(base), base) } finally { base.deleteRecursively() }
  }

  private fun request(url: String) = object : WebResourceRequest {
    override fun getUrl() = Uri.parse(url)
    override fun isForMainFrame() = true
    override fun isRedirect() = false
    override fun hasGesture() = false
    override fun getMethod() = "GET"
    override fun getRequestHeaders(): Map<String, String> = emptyMap()
  }
}
