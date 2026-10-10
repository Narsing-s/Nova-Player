package com.narsing.novaplayer

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.content.ContentUris
import android.os.Bundle
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.app.Activity
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
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

private val Midnight = Color(0xFF101010)
private val Panel = Color(0xFF171717)
private val Mint = Color(0xFFFF6D00)
private val Lavender = Color(0xFFFF8A33)
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
    var localVideos by remember { mutableStateOf(emptyList<LocalVideo>()) }
    var librarySearch by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(false) }\n    var activeTab by remember { mutableStateOf("Videos") }

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

    val libraryPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    val libraryPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            localVideos = loadLocalVideos(context)
            status = "Found " + localVideos.size + " videos on this device."
        } else status = "Allow video access to show your device library. You can still use Browse videos."
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF101010)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF101010))) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).background(Color(0xFFFF6D00), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("NOVA PLAYER", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("LOCAL VIDEO PLAYER", color = Color(0xFFAAAAAA), fontSize = 9.sp, letterSpacing = 1.4.sp)
                }
                IconButton(onClick = {
                    if (context.checkSelfPermission(libraryPermission) == PackageManager.PERMISSION_GRANTED) {
                        localVideos = loadLocalVideos(context)
                        status = "Found " + localVideos.size + " videos on this device."
                    } else libraryPermissionLauncher.launch(libraryPermission)
                }) { Icon(Icons.Default.Refresh, "Refresh library", tint = Color.White) }
                IconButton(onClick = { sortByName = !sortByName }) { Icon(Icons.Default.Sort, "Sort videos", tint = Color.White) }
            }

            Row(Modifier.fillMaxWidth().background(Color(0xFF171717)), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Videos", "Recent", "Online").forEach { tab ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { activeTab = tab }, modifier = Modifier.fillMaxWidth()) {
                            Text(tab, color = if (activeTab == tab) Color(0xFFFF6D00) else Color(0xFFBDBDBD), fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal)
                        }
                        Box(Modifier.fillMaxWidth().height(2.dp).background(if (activeTab == tab) Color(0xFFFF6D00) else Color.Transparent))
                    }
                }
            }

            if (activeTab == "Videos") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = librarySearch, onValueChange = { librarySearch = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        label = { Text("Search videos") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6D00),
                            unfocusedBorderColor = Color(0xFF454545),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFFFF6D00), unfocusedLabelColor = Color(0xFFAAAAAA)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                        Icon(Icons.Default.FolderOpen, "Browse files", tint = Color(0xFFFF6D00))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (localVideos.isEmpty()) "DEVICE VIDEOS" else "${localVideos.size} VIDEOS", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (sortByName) "Name A–Z" else "Recently added", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                }
                val visibleVideos = localVideos.filter { it.title.contains(librarySearch, ignoreCase = true) }
                    .let { list -> if (sortByName) list.sortedBy { it.title.lowercase() } else list.sortedByDescending { it.dateAdded } }
                if (visibleVideos.isEmpty()) {
                    Column(Modifier.fillMaxWidth().weight(1f).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.VideoLibrary, null, tint = Color(0xFFFF6D00), modifier = Modifier.size(58.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Your videos appear here", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Scan your device to build your local library, or browse and open a single video.", color = Color(0xFFAAAAAA), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = {
                            if (context.checkSelfPermission(libraryPermission) == PackageManager.PERMISSION_GRANTED) {
                                localVideos = loadLocalVideos(context)
                                status = "Found " + localVideos.size + " videos on this device."
                            } else libraryPermissionLauncher.launch(libraryPermission)
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00))) {
                            Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Scan device videos")
                        }
                        TextButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) { Text("Browse files instead", color = Color(0xFFFF8A33)) }
                    }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        visibleVideos.take(200).forEach { video ->
                            Row(
                                Modifier.fillMaxWidth().background(Color(0xFF101010)).padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(width = 100.dp, height = 66.dp).background(Color(0xFF292929), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Movie, null, tint = Color(0xFFFF6D00), modifier = Modifier.size(30.dp))
                                    Box(Modifier.align(Alignment.BottomEnd).background(Color(0xCC000000), RoundedCornerShape(topStart = 4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text(formatTime(video.duration), color = Color.White, fontSize = 10.sp)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(video.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatBytes(video.size) + "  •  Local video", color = Color(0xFF9E9E9E), fontSize = 11.sp)
                                }
                                IconButton(onClick = { playMedia(video.uri, video.title) }) {
                                    Icon(Icons.Default.PlayCircle, "Play ${video.title}", tint = Color(0xFFFF6D00), modifier = Modifier.size(30.dp))
                                }
                            }
                            Divider(color = Color(0xFF292929), thickness = 1.dp)
                        }
                    }
                }
            } else if (activeTab == "Recent") {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("PLAYBACK HISTORY", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    if (recentItems.isEmpty()) {
                        Text("Videos you play will appear here.", color = Color(0xFFAAAAAA), modifier = Modifier.padding(vertical = 24.dp))
                    } else recentItems.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = Color(0xFFFF6D00), modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(item.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2)
                            IconButton(onClick = {
                                try { playMedia(Uri.parse(item.uri), item.title) }
                                catch (_: Exception) { status = "This media is unavailable. Browse and select it again." }
                            }) { Icon(Icons.Default.PlayArrow, "Play recent video", tint = Color(0xFFFF6D00)) }
                        }
                        Divider(color = Color(0xFF292929))
                    }
                }
            } else {
                Column(Modifier.weight(1f).padding(16.dp)) {
                    Text("Play a network video", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Paste a direct HTTPS video, HLS or DASH URL.", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(value = streamUrl, onValueChange = { streamUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("HTTPS video / HLS / DASH URL") }, placeholder = { Text("https://example.com/video.m3u8") })
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val parsed = Uri.parse(streamUrl.trim())
                        if (parsed.scheme == "https" && !parsed.host.isNullOrBlank()) {
                            playMedia(parsed, parsed.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Online stream" } ?: "Online stream")
                        } else status = "Use a valid HTTPS media URL. Plain HTTP is disabled for network security."
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00))) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play stream")
                    }
                }
            }

            if (selectedUri != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(20.dp)) {
                    Column {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(220.dp).pointerInput(selectedUri) {
                                var totalX = 0f
                                var totalY = 0f
                                var startX = 0f
                                var startVolume = 0
                                var maxVolume = 1
                                var startBrightness = 0.5f
                                var gestureAxis = "none"
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        totalX = 0f
                                        totalY = 0f
                                        startX = offset.x
                                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        startBrightness = context.findActivity()?.window?.attributes?.screenBrightness
                                            ?.takeIf { it >= 0f } ?: 0.5f
                                        gestureAxis = "none"
                                        isSeekingGesture = false
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        totalX += amount.x
                                        totalY += amount.y
                                        if (gestureAxis == "none" && (abs(totalX) > 12f || abs(totalY) > 12f)) {
                                            gestureAxis = if (abs(totalX) >= abs(totalY)) "horizontal" else "vertical"
                                        }
                                        when (gestureAxis) {
                                            "horizontal" -> isSeekingGesture = true
                                            "vertical" -> {
                                                val adjustment = -totalY / size.height.coerceAtLeast(1).toFloat()
                                                if (startX < size.width / 2f) {
                                                    val window = context.findActivity()?.window
                                                    if (window != null) {
                                                        val attributes = window.attributes
                                                        attributes.screenBrightness = (startBrightness + adjustment).coerceIn(0.05f, 1f)
                                                        window.attributes = attributes
                                                    }
                                                    status = "Brightness " + ((startBrightness + adjustment).coerceIn(0.05f, 1f) * 100).toInt() + "%"
                                                } else {
                                                    val volume = (startVolume + adjustment * maxVolume).toInt().coerceIn(0, maxVolume)
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                                    status = "Volume " + (volume * 100 / maxVolume) + "%"
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (gestureAxis == "horizontal") {
                                            val delta = (totalX / 8f).toLong() * 1_000L
                                            if (abs(delta) >= 1_000L) {
                                                player.seekTo((player.currentPosition + delta).coerceAtLeast(0L))
                                                status = if (delta > 0) "Skipped forward " + (delta / 1000) + "s" else "Skipped back " + (abs(delta) / 1000) + "s"
                                            }
                                        }
                                        isSeekingGesture = false
                                        gestureAxis = "none"
                                    },
                                    onDragCancel = { isSeekingGesture = false; gestureAxis = "none" }
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

            Text(status, color = if (status.startsWith("Use") || status.startsWith("Choose an") || status.startsWith("Start a")) Color(0xFFFFB4AB) else Color(0xFFBDBDBD), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 14.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Icon(Icons.Default.VideoLibrary, null, tint = if (activeTab == "Videos") Color(0xFFFF6D00) else Color(0xFF777777))
                Icon(Icons.Default.History, null, tint = if (activeTab == "Recent") Color(0xFFFF6D00) else Color(0xFF777777))
                Icon(Icons.Default.Link, null, tint = if (activeTab == "Online") Color(0xFFFF6D00) else Color(0xFF777777))
            }
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


private data class LocalVideo(val uri: Uri, val title: String, val duration: Long, val size: Long, val dateAdded: Long)

private fun loadLocalVideos(context: Context): List<LocalVideo> {
    val videos = mutableListOf<LocalVideo>()
    val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED)
    context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn) ?: "Video"
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            videos.add(LocalVideo(uri, name, cursor.getLong(durationColumn), cursor.getLong(sizeColumn), cursor.getLong(dateColumn)))
        }
    }
    return videos
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return bytes.toString() + " B"
    if (bytes < 1024L * 1024L) return "%.1f KB".format(bytes / 1024.0)
    if (bytes < 1024L * 1024L * 1024L) return "%.1f MB".format(bytes / (1024.0 * 1024.0))
    return "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
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
