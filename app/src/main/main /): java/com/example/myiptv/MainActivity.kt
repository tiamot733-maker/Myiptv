package com.example.myiptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class Channel(val name: String, val group: String, val logo: String, val url: String)

fun parseM3u(text: String): List<Channel> {
    val list = mutableListOf<Channel>()
    var name = ""; var group = ""; var logo = ""
    for (line in text.lineSequence()) {
        val l = line.trim()
        when {
            l.startsWith("#EXTINF") -> {
                name = l.substringAfterLast(",").trim()
                group = Regex("group-title=\"(.*?)\"").find(l)?.groupValues?.get(1) ?: ""
                logo = Regex("tvg-logo=\"(.*?)\"").find(l)?.groupValues?.get(1) ?: ""
            }
            l.isNotEmpty() && !l.startsWith("#") -> list += Channel(name, group, logo, l)
        }
    }
    return list
}

object Repo {
    private val client = OkHttpClient()
    suspend fun load(url: String): List<Channel> = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute()
            .use { parseM3u(it.body!!.string()) }
    }
}

class MainVM : ViewModel() {
    var channels by mutableStateOf<List<Channel>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selected by mutableStateOf<Channel?>(null)

    fun load(url: String) = viewModelScope.launch {
        loading = true; error = null
        try { channels = Repo.load(url) } catch (e: Exception) { error = e.message }
        loading = false
    }
}

@Composable
fun Player(url: String) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    LaunchedEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare(); player.playWhenReady = true
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f)
    )
}

@Composable
fun App(vm: MainVM = viewModel()) {
    var url by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        vm.selected?.let { Player(it.url) }
        Row(Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                modifier = Modifier.weight(1f),
                label = { Text("M3U URL") }, singleLine = true
            )
            Button(onClick = { vm.load(url) }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Load")
            }
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(8.dp)) }
        LazyColumn {
            items(vm.channels) { ch ->
                ListItem(
                    headlineContent = { Text(ch.name) },
                    supportingContent = { Text(ch.group) },
                    leadingContent = { AsyncImage(ch.logo, null, Modifier.size(40.dp)) },
                    modifier = Modifier.clickable { vm.selected = ch }
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { App() } } }
    }
}
