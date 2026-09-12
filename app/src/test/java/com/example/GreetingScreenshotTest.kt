package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.battery.BatteryInfo
import com.example.ui.BatteryHeroCard
import com.example.ui.BatteryUiState
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        BatteryHeroCard(
          uiState = BatteryUiState(
            batteryInfo = BatteryInfo(
              level = 100,
              isPlugged = true,
              pluggedType = "AC Charger",
              isCharging = true,
              isFull = true,
              health = "Good",
              voltageMv = 4350,
              temperatureC = 29.5f,
              currentNowMa = 120,
              hardwareCycleCount = 20,
              isHardwareCycleSupported = true,
              isPixelDevice = true,
              deviceModel = "Google Pixel 8 Pro"
            ),
            effectiveCycleCount = 20,
            cyclesSinceLastCalibration = 10,
            isCalibrationDue = true
          )
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
