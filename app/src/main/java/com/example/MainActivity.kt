package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mylock.MyLockApplication
import com.example.mylock.core.localization.LanguageRegistry
import com.example.mylock.core.localization.LocalStrings
import com.example.mylock.core.model.ThemePreference
import com.example.mylock.domain.MyLockViewModel
import com.example.mylock.feature.navigation.MyLockRootHost
import com.example.ui.theme.MyLockTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MyLockApplication
        val factory = MyLockViewModel.Factory(app.container)

        setContent {
            val myLockViewModel: MyLockViewModel = viewModel(factory = factory)
            val uiState by myLockViewModel.uiState.collectAsStateWithLifecycle()

            val darkTheme = when (uiState.settings.themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
            }

            val strings = LanguageRegistry.stringsFor(uiState.settings.language)

            CompositionLocalProvider(LocalStrings provides strings) {
                MyLockTheme(darkTheme = darkTheme) {
                    MyLockRootHost(
                        state = uiState,
                        viewModel = myLockViewModel
                    )
                }
            }
        }
    }
}
