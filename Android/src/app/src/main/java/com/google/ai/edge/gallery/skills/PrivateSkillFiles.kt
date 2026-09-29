/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.skills

import com.google.ai.edge.gallery.security.EncryptedPrivateFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/** Keeps custom skill sources encrypted while preserving their logical script and asset paths. */
class PrivateSkillFiles(private val filesDir: File) {
  private val root = File(filesDir, "skills")

  fun destinationDirectory(name: String): File {
    val normalized = name.replace("\\s+".toRegex(), "-")
    requireComponent(normalized)
    return checked(File(root, normalized))
  }

  fun importedDirectory(relativePath: String): File {
    val parts = relativePath.split('/')
    if (parts.size != 2 || parts[0] != "skills") throw IOException("Invalid private skill directory")
    requireComponent(parts[1])
    return checked(File(filesDir, relativePath))
  }

  fun child(directory: File, name: String): File {
    requireLogicalFileName(name)
    return checked(File(checked(directory), name))
  }

  fun write(logicalFile: File, content: ByteArray) {
    val logical = checked(logicalFile)
    requireLogicalFileName(logical.name)
    val encrypted = encryptedFile(logical)
    EncryptedPrivateFile.write(encrypted, content, purpose(logical))
    EncryptedPrivateFile.read(encrypted, purpose(logical)).fill(0)
    if (logical.exists() && !logical.delete()) throw IOException("Cannot remove legacy skill file")
  }

  fun writeStream(logicalFile: File, input: InputStream) {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    try {
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size().toLong() + count > 64L * 1024 * 1024) {
          throw IOException("Private skill file exceeds its size limit")
        }
        output.write(buffer, 0, count)
      }
      val content = output.toByteArray()
      try {
        write(logicalFile, content)
      } finally {
        content.fill(0)
      }
    } finally {
      buffer.fill(0)
      output.reset()
    }
  }

  fun read(logicalFile: File): ByteArray {
    val logical = checked(logicalFile)
    requireLogicalFileName(logical.name)
    val encrypted = encryptedFile(logical)
    EncryptedPrivateFile.migrate(logical, encrypted, purpose(logical))
    return EncryptedPrivateFile.read(encrypted, purpose(logical))
  }

  fun exists(logicalFile: File): Boolean {
    val logical = checked(logicalFile)
    requireLogicalFileName(logical.name)
    return encryptedFile(logical).isFile || logical.isFile
  }

  fun delete(logicalFile: File) {
    val logical = checked(logicalFile)
    requireLogicalFileName(logical.name)
    listOf(encryptedFile(logical), logical).forEach {
      if (it.exists() && !it.delete()) throw IOException("Cannot remove private skill file")
    }
  }

  fun deleteDirectory(directory: File) {
    val safeDirectory = checked(directory)
    if (!safeDirectory.exists()) return
    val paths = Files.walk(safeDirectory.toPath()).use { stream ->
      stream.iterator().asSequence().map { checked(it.toFile()).toPath() }.toList()
    }
    paths.sortedByDescending { it.nameCount }.forEach { Files.delete(it) }
  }

  /** Logical names let the editor and WebView consume decrypted sources only in memory. */
  fun listFiles(directory: File): List<File> =
    checked(directory).listFiles().orEmpty()
      .filter { checked(it).isFile }
      .map {
        if (it.name.endsWith(".enc")) child(directory, it.name.removeSuffix(".enc")) else it
      }
      .distinctBy { it.absolutePath }

  /** Re-encrypts under the destination path's authenticated purpose before updating the index. */
  fun copyDirectory(source: File, destination: File) {
    val from = checked(source)
    val to = checked(destination)
    if (to.exists()) throw IOException("Private skill destination already exists")
    try {
      fun copy(fromDir: File, toDir: File) {
        if (!toDir.mkdirs() && !toDir.isDirectory) throw IOException("Cannot create skill directory")
        val children = checked(fromDir).listFiles() ?: throw IOException("Cannot read skill directory")
        children.filter { checked(it).isDirectory }.forEach {
          copy(it, child(toDir, it.name))
        }
        listFiles(fromDir).forEach { original ->
          val plaintext = read(original)
          try {
            write(child(toDir, original.name), plaintext)
          } finally {
            plaintext.fill(0)
          }
        }
      }
      copy(from, to)
    } catch (error: Exception) {
      deleteDirectory(to)
      throw error
    }
  }

  /** Eagerly removes legacy app-owned plaintext before showing the unlocked app. */
  fun migrateAll() {
    if (!root.exists()) return
    fun migrateDirectory(directory: File) {
      val children = checked(directory, allowRoot = true).listFiles()
        ?: throw IOException("Cannot read private skill directory")
      children.forEach { file ->
        checked(file)
        if (file.isDirectory) {
          migrateDirectory(file)
        } else if (file.isFile) {
          if (file.name.endsWith(".enc")) {
            val logical = child(file.parentFile, file.name.removeSuffix(".enc"))
            EncryptedPrivateFile.read(file, purpose(logical)).fill(0)
          } else {
            val encrypted = encryptedFile(file)
            EncryptedPrivateFile.migrate(file, encrypted, purpose(file))
          }
        }
      }
    }
    migrateDirectory(root)
  }

  private fun encryptedFile(logical: File) = checked(File(logical.parentFile, "${logical.name}.enc"))

  private fun purpose(logical: File) = "skill-file/${logical.relativeTo(root).invariantSeparatorsPath}"

  private fun checked(file: File, allowRoot: Boolean = false): File {
    val absoluteRoot = root.absoluteFile.normalize()
    val absolute = file.absoluteFile.normalize()
    if (!(absolute.path.startsWith(absoluteRoot.path + File.separator) ||
        (allowRoot && absolute == absoluteRoot))) {
      throw IOException("Private skill path escapes its directory")
    }
    var current: File? = absolute
    while (current != null && current.path.startsWith(absoluteRoot.path)) {
      if (Files.isSymbolicLink(current.toPath())) throw IOException("Private skill paths cannot be symlinks")
      current = current.parentFile
    }
    return absolute
  }

  private fun requireComponent(name: String) {
    if (name.isBlank() || name == "." || name == ".." ||
        name.any { it == '/' || it == '\\' || it.code < 32 || it.code == 127 }) {
      throw IOException("Invalid private skill file name")
    }
  }

  private fun requireLogicalFileName(name: String) {
    requireComponent(name)
    if (name.endsWith(".enc")) throw IOException("Reserved private skill file extension")
  }
}
