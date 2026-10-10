package com.narsing.novaplayer

import android.net.Uri
import android.os.Bundle
import android.app.PictureInPictureParams
import android.content.Context
import android.app.Activity
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.TrackSelectionDialogBuilder
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

private val Midnight = Color(0xFF0B1020)
private val Panel = Color(0xFF151D31)
private val Mint = Color(0xFF75F0D1)
private val Lavender = Color(0xFFB9A4FF)
private const val PREFS = "nova_player_state"
private const val KEY_LAST_URI = "last_uri"
private const val KEY_LAST_TITLE = "last_title"
private const val KEY_LAST_POSITION = "last_position"
private const val KEY_HISTORY = "playback_history"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Mint, secondary = Lavender, background = Midnight, surface = Panel)) {
                NovaHome()
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NovaHome() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
    var streamUrl by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTitle by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Choose a video or enter a secure HTTPS stream URL.") }
    var isPlaying by remember { mutableStateOf(false) }
    var isSeekingGesture by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableStateOf(0f) }
    var recentItems by remember { mutableStateOf(readHistory(prefs)) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var aspectMode by remember { mutableStateOf("Fit") }

    val player = remember {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (!playing) currentMediaItem?.localConfiguration?.uri?.let { uri ->
                        prefs.edit().putString(KEY_LAST_URI, uri.toString()).putLong(KEY_LAST_POSITION, currentPosition.coerceAtLeast(0L)).apply()
                    }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) prefs.edit().putLong(KEY_LAST_POSITION, 0L).apply()
                }
            })
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.currentMediaItem?.localConfiguration?.uri?.let { uri ->
                prefs.edit().putString(KEY_LAST_URI, uri.toString()).putLong(KEY_LAST_POSITION, player.currentPosition.coerceAtLeast(0L)).apply()
            }
            player.release()
        }
    }

    fun playMedia(uri: Uri, title: String, subtitle: Uri? = null) {
        selectedUri = uri
        selectedTitle = title
        val builder = MediaItem.Builder().setUri(uri).setMediaId(uri.toString())
        if (subtitle != null) {
            val name = subtitle.lastPathSegment.orEmpty().lowercase()
            val mime = if (name.endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(subtitle).setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
        }
        val resume = if (prefs.getString(KEY_LAST_URI, null) == uri.toString()) prefs.getLong(KEY_LAST_POSITION, 0L) else 0L
        player.setMediaItem(builder.build(), resume)
        player.prepare()
        player.play()
        prefs.edit().putString(KEY_LAST_URI, uri.toString()).putString(KEY_LAST_TITLE, title).apply()
        recentItems = (listOf(HistoryEntry(uri.toString(), title)) + recentItems.filterNot { it.uri == uri.toString() }).take(8)
        saveHistory(prefs, recentItems)
        status = if (resume > 5_000L) "Resuming from " + formatTime(resume) else "Now playing: $title"
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            playMedia(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "Local video")
        }
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selectedUri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle"
            if (name.endsWith(".srt", true) || name.endsWith(".vtt", true)) {
                playMedia(selectedUri!!, selectedTitle, uri)
                status = "Subtitle loaded: $name"
            } else status = "Choose an .srt or .vtt subtitle file."
        } else status = "Start a video before loading external subtitles."
    }

    Surface(Modifier.fillMaxSize(), color = Midnight) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Brush.verticalGradient(listOf(Color(0xFF101A31), Midnight, Color(0xFF090D18)))).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Mint, Lavender)), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Midnight, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NOVA PLAYER", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Your video space", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text(if (isPlaying) "PLAYING" else "READY", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoLibrary, null, tint = Mint); Spacer(Modifier.width(8.dp))
                        Text("Your library", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    }
                    Text("Open a local video with Android's secure file picker.", color = Color(0xFFB6C1D8), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { videoPicker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Browse videos")
                    }
                    val lastTitle = prefs.getString(KEY_LAST_TITLE, null)
                    val lastUri = prefs.getString(KEY_LAST_URI, null)
                    if (!lastTitle.isNullOrBlank() && !lastUri.isNullOrBlank()) {
                        TextButton(onClick = {
                            try { playMedia(Uri.parse(lastUri), lastTitle) }
                            catch (_: Exception) { status = "Last video unavailable. Browse and select it again." }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Resume recent • $lastTitle", color = Lavender, maxLines = 1) }
                    }
                    if (recentItems.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Recently played", color = Color(0xFFB6C1D8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        recentItems.take(5).forEach { item ->
                            TextButton(onClick = {
                                try { playMedia(Uri.parse(item.uri), item.title) }
                                catch (_: Exception) { status = "This media is unavailable. Browse and select it again." }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.History, null, tint = Lavender)
                                Spacer(Modifier.width(8.dp))
                                Text(item.title, color = Color.White, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null, tint = Lavender); Spacer(Modifier.width(8.dp)); Text("Secure streaming", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = streamUrl, onValueChange = { streamUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("HTTPS video / HLS / DASH URL") }, placeholder = { Text("https://example.com/video.m3u8") })
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val parsed = Uri.parse(streamUrl.trim())
                        if (parsed.scheme == "https" && !parsed.host.isNullOrBlank()) {
                            playMedia(parsed, parsed.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Online stream" } ?: "Online stream")
                        } else status = "Use a valid HTTPS media URL. Plain HTTP is disabled for network security."
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play stream") }
                }
            }

            if (selectedUri != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(20.dp)) {
                    Column {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(220.dp).pointerInput(selectedUri) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragTotal = 0f; isSeekingGesture = true },
                                    onHorizontalDrag = { change, amount -> dragTotal += amount; change.consume() },
                                    onDragEnd = {
                                        val delta = (dragTotal / 8f).toLong() * 1_000L
                                        if (abs(delta) >= 1_000L) {
                                            player.seekTo((player.currentPosition + delta).coerceAtLeast(0L))
                                            status = if (delta > 0) "Skipped forward " + (delta / 1000) + "s" else "Skipped back " + (abs(delta) / 1000) + "s"
                                        }
                                        isSeekingGesture = false
                                    },
                                    onDragCancel = { isSeekingGesture = false }
                                )
                            },
                            factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = true; setShowSubtitleButton(true); resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                            update = { view -> view.player = player; view.resizeMode = when (aspectMode) { "Fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL; "Zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT } }
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }) { Text("−10 sec") }
                            TextButton(onClick = { player.seekTo(player.currentPosition + 10_000L) }) { Text("+10 sec") }
                            TextButton(onClick = {
                                val nextSpeed = when (playbackSpeed) { 0.5f -> 0.75f; 0.75f -> 1.0f; 1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2.0f; else -> 0.5f }
                                playbackSpeed = nextSpeed
                                player.setPlaybackSpeed(nextSpeed)
                                status = "Playback speed: " + nextSpeed + "×"
                            }) { Text(playbackSpeed.toString() + "×") }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = {
                                aspectMode = when (aspectMode) { "Fit" -> "Fill"; "Fill" -> "Zoom"; else -> "Fit" }
                                status = "Screen mode: " + aspectMode
                            }) { Text(aspectMode) }
                            TextButton(onClick = {
                                try { TrackSelectionDialogBuilder(context, "Audio tracks", player, C.TRACK_TYPE_AUDIO).build().show() }
                                catch (_: Exception) { status = "Audio track selection is unavailable for this media." }
                            }) { Icon(Icons.Default.Audiotrack, null, tint = Lavender); Text("Audio") }
                            TextButton(onClick = { subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain")) }) {
                                Icon(Icons.Default.Subtitles, null, tint = Lavender); Text("Subtitles")
                            }
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val activity = context.findActivity()
                                    activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                                } else status = "Picture-in-Picture requires Android 8.0 or newer."
                            }) { Icon(Icons.Default.PictureInPictureAlt, null, tint = Mint); Text("PiP") }
                        }
                    }
                }
                Text(if (isSeekingGesture) "Swipe horizontally to seek" else selectedTitle, color = Lavender, fontSize = 12.sp, maxLines = 1)
            }

            Text(status, color = if (status.startsWith("Use") || status.startsWith("Choose an") || status.startsWith("Start a")) Color(0xFFFFB4AB) else Color(0xFFB6C1D8), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("PRIVATE BY DESIGN  •  NOVA PLAYER", color = Mint, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds / 1_000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}


private fun Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}


private data class HistoryEntry(val uri: String, val title: String)

private fun readHistory(prefs: android.content.SharedPreferences): List<HistoryEntry> {
    return try {
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = item.optString("uri")
            val title = item.optString("title")
            if (uri.isBlank() || title.isBlank()) null else HistoryEntry(uri, title)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveHistory(prefs: android.content.SharedPreferences, items: List<HistoryEntry>) {
    val array = JSONArray()
    items.take(8).forEach { item ->
        array.put(JSONObject().put("uri", item.uri).put("title", item.title))
    }
    prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
}
