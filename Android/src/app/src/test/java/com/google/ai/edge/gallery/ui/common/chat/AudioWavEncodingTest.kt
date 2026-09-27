/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.common.chat

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioWavEncodingTest {
  @Test
  fun riffChunkEndsAtTheActualEndOfTheRecordedAudio() {
    val pcm = ByteArray(32000) { (it % 256).toByte() }
    val wav = ChatMessageAudioClip(pcm, 16000, ChatSide.USER).genByteArrayForWav()
    val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

    assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
    assertEquals(wav.size - 8, header.getInt(4))
    assertEquals(pcm.size, header.getInt(40))
    assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
  }

  @Test
  fun wavDecoderReadsTheCompleteMonoPcmRecordingAtItsSampleRate() {
    val pcm = byteArrayOf(0, 0, -1, 127, 0, -128, -1, -1)
    for (sampleRate in listOf(16000, 44100, 48000)) {
      val wav = ChatMessageAudioClip(pcm, sampleRate, ChatSide.USER).genByteArrayForWav()

      AudioSystem.getAudioInputStream(ByteArrayInputStream(wav)).use { decoded ->
        assertEquals(AudioFormat.Encoding.PCM_SIGNED, decoded.format.encoding)
        assertEquals(1, decoded.format.channels)
        assertEquals(16, decoded.format.sampleSizeInBits)
        assertEquals(sampleRate.toFloat(), decoded.format.sampleRate, 0f)
        assertEquals((pcm.size / 2).toLong(), decoded.frameLength)
        assertArrayEquals(pcm, decoded.readBytes())
      }
    }
  }
}
