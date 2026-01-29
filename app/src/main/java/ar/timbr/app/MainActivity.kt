package ar.timbr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.timbr.app.ui.MainViewModel
import ar.timbr.app.ui.auth.AuthScreen
import ar.timbr.app.ui.home.HomeScreen
import ar.timbr.app.ui.theme.TimbrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val isAuthenticated by mainViewModel.isAuthenticated.collectAsStateWithLifecycle()
            val isPreview = LocalInspectionMode.current

            TimbrTheme(darkTheme = false) {
                if (isAuthenticated || isPreview) {
                    HomeScreen()
                } else {
                    AuthScreen()
                }
            }
        }
    }
}
