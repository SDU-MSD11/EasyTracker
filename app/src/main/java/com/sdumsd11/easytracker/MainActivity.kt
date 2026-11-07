package com.sdumsd11.easytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sdumsd11.easytracker.navigation.AppNavHost
import com.sdumsd11.easytracker.navigation.rememberEasyNavController
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyTrackerTheme {
                AppNavHost(rememberEasyNavController())
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EasyTrackerAppPreview() {
    EasyTrackerTheme {
        AppNavHost(rememberEasyNavController())
    }
}
