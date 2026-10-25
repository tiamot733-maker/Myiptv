package com.example.myiptv

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.json.JSONArray
import org.json.JSONObject

val Navy = Color(0xFF0B1230)
val Card = Color(0xFF16224F)
val Teal = Color(0xFF1FB5A8)
val Orange = Color(0xFFFF9F1C)
val Red = Color(0xFFFF6B6B)

data class Cat(val id: String, val name: String)
data class Item(val id: String, val name: String, val icon: String, val ext: String)

class Api(val server: String, val user: String, val pass: String) {
    private val client = OkHttpClient()
    private fun base() = server.trimEnd('/')

    private fun get(extra: String): String {
        val url = "${base()}/player_api.php?username=$user&password=$pass$extra"
        return client.newCall(Request.Builder().url(url).build()).execute()
            .use { it.body!!.string() }
    }

    suspend fun login(): Boolean = withContext(Dispatchers.IO) {
        JSONObject(get("")).optJSONObject("user_info")?.optInt("auth", 0) == 1
    }

    suspend fun cats(type: String): List<Cat> = withContext(Dispatchers.IO) {
        val a = JSONArray(get("&action=get_${type}_categories"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Cat(o.optString("category_id"), o.optString("category_name"))
        }
    }

    suspend fun items(type: String, catId: String): List<Item> = withContext(Dispatchers.IO) {
        val a = JSONArray(get("&action=get_${type}_streams&category_id=$catId"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Item(
                o.optString("stream_id"),
                o.optString("name"),
                o.optString("stream_icon"),
                o.optString("container_extension", "mp4")
            )
        }
    }

    fun liveUrl(i: Item) = "${base()}/live/$user/$pass/${i.id}.m3u8"
    fun movieUrl(i: Item) = "${base()}/movie/$user/$pass/${i.id}.${i.ext}"
}

class VM : ViewModel() {
    var api by mutableStateOf<Api?>(null)
    var screen by mutableStateOf("login")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var cats by mutableStateOf<List<Cat>>(emptyList())
    var items by mutableStateOf<List<Item>>(emptyList())
    var selCat by mutableStateOf<String?>(null)
    var playing by mutableStateOf<Item?>(null)
    var playUrl by mutableStateOf<String?>(null)
    var tried = false

    fun login(ctx: Context, server: String, user: String, pass: String) = viewModelScope.launch {
        loading = true; error = null
        try {
            val s = server.trim().let { if (it.startsWith("http")) it else "http://$it" }
            val a = Api(s, user.trim(), pass.trim())
            if (a.login()) {
                api = a
                screen = "home"
                ctx.getSharedPreferences("login", 0).edit()
                    .putString("s", s).putString("u", user.trim()).putString("p", pass.trim())
                    .apply()
            } else error = "بيانات الدخول غلط"
        } catch (e: Exception) {
            error = "تعذر الاتصال: ${e.message}"
        }
        loading = false
    }

    fun logout(ctx: Context) {
        ctx.getSharedPreferences("login", 0).edit().clear().apply()
        api = null; playing = null; playUrl = null; screen = "login"
    }

    fun open(type: String) = viewModelScope.launch {
        screen = type
        cats = emptyList(); items = emptyList(); selCat = null
        playing = null; playUrl = null; error = null; loading = true
        try { cats = api!!.cats(type) } catch (e: Exception) { error = e.message }
        loading = false
    }

    fun pickCat(type: String, id: String) = viewModelScope.launch {
        selCat = id; loading = true; error = null
        try { items = api!!.items(type, id) } catch (e: Exception) { error = e.message }
        loading = false
    }

    fun play(type: String, i: Item) {
        playing = i
        playUrl = if (type == "live") api!!.liveUrl(i) else api!!.movieUrl(i)
    }

    fun back() {
        if (playUrl != null) { playing = null; playUrl = null } else screen = "home"
    }
}

@Composable
fun Login(vm: VM) {
    val ctx = LocalContext.current
    val sp = ctx.getSharedPreferences("login", 0)
    var s by remember { mutableStateOf(sp.getString("s", "") ?: "") }
    var u by remember { mutableStateOf(sp.getString("u", "") ?: "") }
    var p by remember { mutableStateOf(sp.getString("p", "") ?: "") }

    LaunchedEffect(Unit) {
        if (!vm.tried) {
            vm.tried = true
            if (s.isNotBlank() && u.isNotBlank() && p.isNotBlank()) vm.login(ctx, s, u, p)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("MY IPTV", color = Teal, fontSize = 30.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            s, { s = it }, Modifier.fillMaxWidth(),
            label = { Text("http://server_url:port") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            u, { u = it }, Modifier.fillMaxWidth(),
            label = { Text("Username") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            p, { p = it }, Modifier.fillMaxWidth(),
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.login(ctx, s, u, p) },
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (vm.loading) "..." else "CONNECT") }
        vm.error?.let { Text(it, color = Red, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
fun Tile(icon: String, label: String, m: Modifier, onClick: () -> Unit) {
    Column(
        m.height(110.dp).clip(RoundedCornerShape(16.dp)).background(Card).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 32.sp)
        Text(label, fontSize = 14.sp)
    }
}

@Composable
fun Home(vm: VM) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MY IPTV", fontSize = 22.sp, color = Teal)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("📺", "Live TV", Modifier.weight(1f)) { vm.open("live") }
            Tile("🎬", "Movies", Modifier.weight(1f)) { vm.open("vod") }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("🎞", "Series (soon)", Modifier.weight(1f)) { }
            Tile("🔄", "Switch user", Modifier.weight(1f)) { vm.logout(ctx) }
        }
    }
}

@Composable
fun Player(url: String) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    LaunchedEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun PlayerPane(vm: VM, m: Modifier) {
    val url = vm.playUrl
    Box(m.aspectRatio(16 / 9f).background(Color.Black), contentAlignment = Alignment.Center) {
        if (url == null) Text("Select an item", fontSize = 13.sp) else Player(url)
    }
}

@Composable
fun CatsPane(vm: VM, type: String, m: Modifier) {
    LazyColumn(m) {
        items(vm.cats) { c ->
            Text(
                c.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                color = if (c.id == vm.selCat) Orange else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().clickable { vm.pickCat(type, c.id) }.padding(10.dp)
            )
        }
    }
}

@Composable
fun ItemsPane(vm: VM, type: String, m: Modifier) {
    if (type == "live") {
        LazyColumn(m) {
            items(vm.items) { i ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.play(type, i) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(i.icon, null, Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        i.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                        color = if (vm.playing?.id == i.id) Orange else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        LazyVerticalGrid(GridCells.Adaptive(96.dp), m, contentPadding = PaddingValues(6.dp)) {
            items(vm.items) { i ->
                Column(Modifier.padding(4.dp).clickable { vm.play(type, i) }) {
                    AsyncImage(
                        i.icon, null,
                        Modifier.fillMaxWidth().aspectRatio(2 / 3f)
                            .clip(RoundedCornerShape(8.dp)).background(Card),
                        contentScale = ContentScale.Crop
                    )
                    Text(i.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun Browse(vm: VM, type: String) {
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxSize()) {
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { Text(it, color = Red, modifier = Modifier.padding(8.dp)) }
        if (land) {
            Row(Modifier.fillMaxSize()) {
                CatsPane(vm, type, Modifier.weight(0.22f).fillMaxHeight())
                ItemsPane(vm, type, Modifier.weight(0.38f).fillMaxHeight())
                PlayerPane(vm, Modifier.weight(0.40f))
            }
        } else {
            if (vm.playUrl != null) PlayerPane(vm, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxSize()) {
                CatsPane(vm, type, Modifier.weight(0.33f).fillMaxHeight())
                ItemsPane(vm, type, Modifier.weight(0.67f).fillMaxHeight())
            }
        }
    }
}

@Composable
fun App(vm: VM = viewModel()) {
    BackHandler(enabled = vm.screen == "live" || vm.screen == "vod") { vm.back() }
    when (vm.screen) {
        "login" -> Login(vm)
        "home" -> Home(vm)
        else -> Browse(vm, vm.screen)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Teal, background = Navy, surface = Navy)) {
                Surface(Modifier.fillMaxSize().statusBarsPadding(), color = Navy) { App() }
            }
        }
    }
}
