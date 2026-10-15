package com.example.splitwsie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.presentation.navigation.SetupNavGraph
import com.example.splitwsie.presentation.theme.SettlePrimary
import com.example.splitwsie.presentation.theme.SplitwsieTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitwsieTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettleApp()
                }
            }
        }
    }
}

@Composable
private fun SettleApp(viewModel: AppEntryViewModel = hiltViewModel()) {
    val startDestination by viewModel.startDestination.collectAsState()

    val nextDestination by viewModel.nextDestination.collectAsState()

    if (startDestination == null || nextDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SettlePrimary)
        }
        return
    }

    SetupNavGraph(startDestination = startDestination!!, nextDestination = nextDestination!!)
}
