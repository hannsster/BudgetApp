package com.mipresupuestocr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipresupuestocr.app.ui.MainViewModel
import com.mipresupuestocr.app.ui.navigation.MiPresupuestoApp
import com.mipresupuestocr.app.ui.theme.MiPresupuestoTheme
import com.mipresupuestocr.app.worker.RefreshScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var refreshScheduler: RefreshScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshScheduler.schedulePeriodic()

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            MiPresupuestoTheme(themeMode = settings.themeMode) {
                MiPresupuestoApp()
            }
        }
    }
}
