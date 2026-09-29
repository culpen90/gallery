// Modified for the Gallery Android fork (Beta 5).
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

package com.google.ai.edge.gallery.skills

import com.google.ai.edge.gallery.common.LOCAL_URL_BASE
import com.google.ai.edge.gallery.common.SecureHttp
import com.google.ai.edge.gallery.proto.Skill
import android.net.Uri

const val SKILL_INSTRUCTIONS_TEMPLATE = "---\nname: %s\ndescription: %s\n---\n\n%s"

fun Skill.getSkillContent(): String {
  return SKILL_INSTRUCTIONS_TEMPLATE.format(name, description, instructions)
}

/** Formats descriptions of selected skills for inclusion in prompt placeholders. */
fun formatSelectedSkills(skills: List<Skill>): String {
  return skills
    .filter { it.selected }
    .joinToString("\n\n") { skill ->
      "- Skill name: \"${skill.name}\"\n- Description: ${skill.description}"
    }
}

fun Skill.getJsSkillUrl(scriptName: String): String? {
  val baseUrl = getSecureSkillBaseUrl() ?: return null
  val path = encodeSkillRelativePath(scriptName) ?: return null
  return "$baseUrl/scripts/$path"
}

fun Skill.getJsSkillWebviewUrl(url: String): String {
  val baseUrl = getSecureSkillBaseUrl() ?: return ""
  if (url.contains("://")) {
    if (runCatching { SecureHttp.requireHttpsUrl(url) }.isFailure) return ""
    val target = Uri.parse(url)
    if (target.host == "appassets.androidplatform.net") {
      val base = Uri.parse(baseUrl)
      if (base.host != target.host ||
          target.pathSegments.take(base.pathSegments.size) != base.pathSegments ||
          target.pathSegments.any { it == "." || it == ".." }) return ""
    }
    return url
  }
  val path = encodeSkillRelativePath(url) ?: return ""
  return "$baseUrl/assets/$path"
}

private fun Skill.getSecureSkillBaseUrl(): String? {
  if (importDirName.isNotEmpty()) {
    val path = encodeSkillRelativePath(importDirName) ?: return null
    if ((!builtIn && !importDirName.startsWith("skills/")) ||
        (builtIn && !importDirName.startsWith("assets/skills/"))) return null
    return "$LOCAL_URL_BASE/$path"
  }
  if (skillUrl.isEmpty()) return null
  return runCatching { SecureHttp.requireHttpsUrl(skillUrl).toExternalForm().trimEnd('/') }.getOrNull()
}

private fun encodeSkillRelativePath(value: String): String? {
  val parts = value.split('/')
  if (parts.any { it.isBlank() || it == "." || it == ".." ||
      it.any { char -> char == '\\' || char == '?' || char == '#' || char == ':' ||
        char.code < 32 || char.code == 127 } }) return null
  return parts.joinToString("/") { Uri.encode(it) }
}
