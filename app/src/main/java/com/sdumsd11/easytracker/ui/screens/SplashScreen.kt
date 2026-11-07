package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(modifier: Modifier = Modifier, onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(800) // stub auth/bootstrap check
        onDone()
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "EasyTracker",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.Center),
        )
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center)
                .semantics { contentDescription = "Loading" },
        )
    }
}
