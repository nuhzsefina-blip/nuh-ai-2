package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiJudicialService
import com.example.sync.GoogleDriveApiClient
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ረዳት ዳኛ", appName)
  }

  @Test
  fun `default_web_client_id is provisioned`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val webClientId = context.getString(R.string.default_web_client_id)
    assertTrue(webClientId.isNotBlank())
  }

  @Test
  fun `driveApiClient parses Drive files JSON`() {
    val sampleJson = """
      {
        "files": [
          {
            "id": "1AbCdEfG",
            "name": "redat_dagna_cloud_1700000000.rdenc",
            "modifiedTime": "2026-11-14T12:30:00.000Z",
            "size": "4096"
          }
        ]
      }
    """.trimIndent()
    val parsed = GoogleDriveApiClient.parseBackupListJson(sampleJson)
    assertEquals(1, parsed.size)
    assertEquals("1AbCdEfG", parsed[0].id)
    assertEquals("redat_dagna_cloud_1700000000.rdenc", parsed[0].name)
    assertEquals(4096L, parsed[0].sizeBytes)
  }

  @Test
  fun `friendlyError returns localized Amharic guidance for quota timeout and network`() {
    val quotaMsg = GeminiJudicialService.friendlyError(
      RuntimeException("You exceeded your current quota https://ai.dev/rate-limit limit: 20 Please retry in 12h13m39.32s")
    )
    assertTrue(quotaMsg.contains("12h13m"))
    assertTrue(quotaMsg.contains("gemini-3.5-flash-lite"))
    assertFalse(quotaMsg.contains("https://"))

    val timeoutMsg = GeminiJudicialService.friendlyError(SocketTimeoutException("timeout"))
    assertTrue(timeoutMsg.contains("ጊዜው አልፏል"))

    val netMsg = GeminiJudicialService.friendlyError(UnknownHostException("generativelanguage.googleapis.com"))
    assertTrue(netMsg.contains("ኢንተርኔት"))
  }

  @Test(expected = CancellationException::class)
  fun `friendlyError rethrows coroutine CancellationException`() {
    GeminiJudicialService.friendlyError(CancellationException("user cancelled"))
  }
}
