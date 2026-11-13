package com.sdumsd11.easytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.core.ThemeSettings
import com.sdumsd11.easytracker.navigation.AppNavHost
import com.sdumsd11.easytracker.navigation.rememberEasyNavController
import com.sdumsd11.easytracker.ui.screens.SplashScreen
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** App-wide theme holder; Settings toggles the same singleton via ProfileViewModel. */
    @Inject
    lateinit var themeSettings: ThemeSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme by themeSettings.darkTheme.collectAsState()
            EasyTrackerTheme(darkTheme = darkTheme) {
                AppNavHost(rememberEasyNavController())
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EasyTrackerAppPreview() {
    EasyTrackerTheme {
        // NavHost needs Hilt (VM-scoped destinations); preview the Hilt-free splash instead.
        SplashScreen(onDone = {})
    }
}
