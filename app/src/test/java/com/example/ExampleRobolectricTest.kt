package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.RacingCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Turbo Roar", appName)
  }

  @Test
  fun `racing catalog has cars and tracks`() {
    assertTrue(RacingCatalog.cars.size >= 3)
    assertTrue(RacingCatalog.tracks.size >= 3)
    assertEquals("APEX VIPER", RacingCatalog.carById("apex_viper").name)
  }
}
