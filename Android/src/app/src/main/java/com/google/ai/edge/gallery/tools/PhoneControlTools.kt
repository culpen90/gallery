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

package com.google.ai.edge.gallery.tools

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import org.json.JSONObject

/** Native Mobile Actions exposed directly to the same model that handles conversation and skills. */
class PhoneControlTools(private val intentTool: RunIntentTool) : ToolDefinition {
  override val alwaysAllow = true

  // Share the per-turn permission context, including the transcription-only tool restriction.
  override var executionContext: ToolExecutionContext?
    get() = intentTool.executionContext
    set(value) { intentTool.executionContext = value }

  private fun run(action: String, parameters: Map<String, String> = emptyMap()): Map<String, String> {
    toolExecutionDeniedResult()?.let { return it }
    return intentTool.runIntent(action, JSONObject(parameters).toString())
  }

  @Tool(description = "Turn the phone flashlight on when the user asks. May request camera permission.")
  fun turnOnFlashlight(): Map<String, String> = run("turn_on_flashlight")

  @Tool(description = "Turn the phone flashlight off when the user asks.")
  fun turnOffFlashlight(): Map<String, String> = run("turn_off_flashlight")

  @Tool(description = "Open Wi-Fi settings on the phone. This opens settings; it does not toggle Wi-Fi.")
  fun openWifiSettings(): Map<String, String> = run("open_wifi_settings")

  @Tool(description = "Open a location, business, or address in the phone's map app.")
  fun showLocationOnMap(
    @ToolParam(description = "The place or address requested by the user.") location: String,
  ): Map<String, String> = run("show_location_on_map", mapOf("location" to location))

  @Tool(description = "Open a new contact draft for the user to review and save. Do not claim it has been saved.")
  fun createContact(
    @ToolParam(description = "The contact's first name.") firstName: String,
    @ToolParam(description = "The contact's last name, or an empty string if not provided.") lastName: String,
    @ToolParam(description = "The phone number, or an empty string if not provided.") phoneNumber: String,
    @ToolParam(description = "The email address, or an empty string if not provided.") email: String,
  ): Map<String, String> =
    run(
      "create_contact",
      mapOf(
        "name" to listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
        "phone_number" to phoneNumber,
        "email" to email,
      ),
    )

  @Tool(description = "Open an email draft in the phone's email app for the user to review and send. Does not send automatically.")
  fun sendEmail(
    @ToolParam(description = "The recipient's email address.") to: String,
    @ToolParam(description = "The email subject.") subject: String,
    @ToolParam(description = "The email body.") body: String,
  ): Map<String, String> =
    run("send_email", mapOf("extra_email" to to, "extra_subject" to subject, "extra_text" to body))

  @Tool(description = "Get the phone's current local date, time and weekday to resolve relative dates before creating an event.")
  fun getCurrentDateAndTime(): Map<String, String> = run("get_current_date_and_time")

  @Tool(description = "Open a calendar event draft for the user to review and save. Resolve relative dates with getCurrentDateAndTime first. Does not save automatically.")
  fun createCalendarEvent(
    @ToolParam(description = "The event title.") title: String,
    @ToolParam(description = "The event description, or an empty string if none.") description: String,
    @ToolParam(description = "Local start date and time in YYYY-MM-DDTHH:MM:SS format.") beginTime: String,
    @ToolParam(description = "Local end date and time in YYYY-MM-DDTHH:MM:SS format.") endTime: String,
  ): Map<String, String> =
    run(
      "create_calendar_event",
      mapOf("title" to title, "description" to description, "begin_time" to beginTime, "end_time" to endTime),
    )
}
