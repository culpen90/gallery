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

package com.google.ai.edge.gallery.intents

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.net.toUri
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class CreateContactParams(
  val name: String,
  val phone_number: String = "",
  val email: String = "",
)

@JsonClass(generateAdapter = true)
data class ShowLocationOnMapParams(val location: String)

/** The same native phone actions are available through the conversational skill runtime. */
internal object MobileActionsIntentHandler {
  suspend fun setFlashlight(
    context: Context,
    enabled: Boolean,
    requestPermission: suspend (String) -> Boolean,
  ): String {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
      return "failed: this device has no flashlight"
    }
    if (
      checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
        !requestPermission(Manifest.permission.CAMERA)
    ) {
      return "failed: camera permission was denied; the flashlight was not changed"
    }
    return try {
      val manager = context.getSystemService(CameraManager::class.java)
        ?: return "failed: camera service is unavailable"
      val flashCameras = manager.cameraIdList.filter {
        manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
      }
      val cameraId = flashCameras.firstOrNull {
        manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
          CameraCharacteristics.LENS_FACING_BACK
      } ?: flashCameras.firstOrNull() ?: return "failed: no flashlight is available"
      manager.setTorchMode(cameraId, enabled)
      if (enabled) "succeeded: flashlight turned on" else "succeeded: flashlight turned off"
    } catch (e: Exception) {
      "failed: the flashlight could not be changed (${e.message ?: "camera unavailable"})"
    }
  }

  suspend fun createContact(context: Context, parameters: String): String {
    val params = try {
      Moshi.Builder().build().adapter(CreateContactParams::class.java).fromJson(parameters)
    } catch (_: Exception) {
      null
    } ?: return "failed: create_contact requires a name and optional phone_number and email"
    if (params.name.isBlank()) return "failed: the contact name is missing"
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
      type = ContactsContract.RawContacts.CONTENT_TYPE
      putExtra(ContactsContract.Intents.Insert.NAME, params.name)
      if (params.phone_number.isNotBlank()) {
        putExtra(ContactsContract.Intents.Insert.PHONE, params.phone_number)
      }
      if (params.email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, params.email)
    }
    return openActivity(context, intent, "opened: contact draft; the user must review and save it")
  }

  suspend fun showLocationOnMap(context: Context, parameters: String): String {
    val params = try {
      Moshi.Builder().build().adapter(ShowLocationOnMapParams::class.java).fromJson(parameters)
    } catch (_: Exception) {
      null
    } ?: return "failed: show_location_on_map requires a location"
    if (params.location.isBlank()) return "failed: the map location is missing"
    val intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(params.location)}".toUri())
    return openActivity(context, intent, "opened: map search for ${params.location}")
  }

  suspend fun openWifiSettings(context: Context): String {
    return openActivity(context, Intent(Settings.ACTION_WIFI_SETTINGS), "opened: Wi-Fi settings")
  }

  suspend fun openActivity(context: Context, intent: Intent, result: String): String {
    return try {
      withContext(Dispatchers.Main) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
      result
    } catch (e: CancellationException) {
      throw e
    } catch (_: ActivityNotFoundException) {
      "failed: no installed app can handle this action"
    } catch (_: SecurityException) {
      "failed: Android did not allow this action"
    }
  }
}
