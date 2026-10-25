package com.ytube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ytube.app.data.VideoItem
import com.ytube.app.ui.PlayerScreen
import com.ytube.app.ui.SearchScreen
import com.ytube.app.ui.SettingsScreen
import com.ytube.app.ui.YTubeTheme

sealed interface Screen {
    data object Search : Screen
    data class Player(val video: VideoItem) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YTubeTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Search) }
                BackHandler(enabled = screen != Screen.Search) {
                    screen = Screen.Search
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("YTube") },
                            navigationIcon = {
                                if (screen != Screen.Search) {
                                    IconButton(onClick = { screen = Screen.Search }) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                                    }
                                }
                            },
                            actions = {
                                IconButton(onClick = { screen = Screen.Settings }) {
                                    Icon(Icons.Filled.AccountCircle, contentDescription = "账号")
                                }
                            }
                        )
                    }
                ) { padding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.padding(padding)
                    ) {
                        when (val s = screen) {
                            is Screen.Search -> SearchScreen(
                                onVideoClick = { screen = Screen.Player(it) }
                            )
                            is Screen.Player -> PlayerScreen(video = s.video)
                            is Screen.Settings -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
