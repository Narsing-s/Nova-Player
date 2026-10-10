package com.narsing.novaplayer

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Intent
import android.provider.MediaStore
import android.content.ContentUris
import android.os.Bundle
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.app.Activity
import android.os.Build
import android.util.Rational
import android.util.Size
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.TrackSelectionDialogBuilder
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Midnight = Color(0xFF0A0E1A)
private val Panel = Color(0xFF12182A)
private val Mint = Color(0xFF39E7D5)
private val Lavender = Color(0xFF8A70FF)
private const val PREFS = "nova_player_state"
private const val KEY_LAST_URI = "last_uri"
private const val KEY_LAST_TITLE = "last_title"
private const val KEY_LAST_POSITION = "last_position"
private const val KEY_HISTORY = "playback_history"
private const val KEY_FAVORITES = "favorite_videos"
private const val KEY_PLAYLISTS = "saved_playlists"
private const val KEY_QUEUE = "play_queue"

private fun shiftedSubtitleUri(context: Context, source: Uri, offsetMs: Long): Uri? {
    if (offsetMs == 0L) return source
    return try {
        val original = context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() } ?: return null
        // Handles standard SRT timestamps and both WEBVTT timestamp forms (MM:SS.mmm / HH:MM:SS.mmm).
        val timestamp = Regex("""(?:\d{2}:)?\d{2}:\d{2}[,.]\d{3}""")
        fun shift(value: String): String {
            val separator = if (value.contains(',')) ',' else '.'
            val clock = value.substringBeforeLast(separator)
            val fraction = value.substringAfterLast(separator).toLong()
            val parts = clock.split(':').map { it.toLong() }
            val baseMs = if (parts.size == 3) ((parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000) + fraction
                else ((parts[0] * 60 + parts[1]) * 1000) + fraction
            val shifted = (baseMs + offsetMs).coerceAtLeast(0L)
            val hours = shifted / 3600000
            val minutes = (shifted % 3600000) / 60000
            val seconds = (shifted % 60000) / 1000
            val millis = shifted % 1000
            return if (parts.size == 2 && shifted < 3600000) {
                "%02d:%02d%c%03d".format(shifted / 60000, seconds, separator, millis)
            } else {
                "%02d:%02d:%02d%c%03d".format(hours, minutes, seconds, separator, millis)
            }
        }
        val adjusted = timestamp.replace(original) { match -> shift(match.value) }
        val suffix = if (source.lastPathSegment?.endsWith(".vtt", true) == true) ".vtt" else ".srt"
        val output = java.io.File(context.cacheDir, "subtitle-${source.lastPathSegment.hashCode()}-${offsetMs}$suffix")
        output.writeText(adjusted)
        Uri.fromFile(output)
    } catch (_: Exception) {
        null
    }
}

class MainActivity : ComponentActivity() {
    private val playbackPlayer = mutableStateOf<Player?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Mint, secondary = Lavender, background = Midnight, surface = Panel)) {
                val player = playbackPlayer.value
                if (player == null) {
                    Surface(Modifier.fillMaxSize(), color = Midnight) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Mint)
                        }
                    }
                } else {
                    NovaHome(player)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, NovaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                try {
                    playbackPlayer.value = future.get()
                } catch (_: Exception) {
                    playbackPlayer.value = null
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(this))
        }
    }

    override fun onStop() {
        playbackPlayer.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NovaHome(player: Player) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTitle by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Loading videos on this device…") }
    var isPlaying by remember { mutableStateOf(false) }
    var isSeekingGesture by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableStateOf(0f) }
    var recentItems by remember { mutableStateOf(readHistory(prefs)) }
    var favoriteItems by remember { mutableStateOf(readFavorites(prefs)) }
    var playbackSpeed by remember { mutableStateOf(prefs.getFloat("playback_speed", 1.0f)) }
    var shuffleEnabled by remember { mutableStateOf(prefs.getBoolean("shuffle_enabled", false)) }
    var repeatMode by remember { mutableIntStateOf(prefs.getInt("repeat_mode", Player.REPEAT_MODE_OFF)) }
    var queueItems by remember { mutableStateOf(readQueue(prefs)) }
    var playlists by remember { mutableStateOf(readPlaylists(prefs)) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var fullScreen by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var abLoopStartMs by remember { mutableStateOf<Long?>(null) }
    var abLoopEndMs by remember { mutableStateOf<Long?>(null) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerActive by remember { mutableStateOf(false) }
    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }
    val uiScope = rememberCoroutineScope()
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var showSubtitleSyncDialog by remember { mutableStateOf(false) }
    var subtitleDelayMs by remember { mutableIntStateOf(prefs.getInt("subtitle_delay_ms", 0)) }
    var loadedSubtitleSource by remember { mutableStateOf<Uri?>(null) }
    var showGestureSettings by remember { mutableStateOf(false) }
    var gestureSeekEnabled by remember { mutableStateOf(prefs.getBoolean("gesture_seek_enabled", true)) }
    var gestureBrightnessEnabled by remember { mutableStateOf(prefs.getBoolean("gesture_brightness_enabled", true)) }
    var gestureVolumeEnabled by remember { mutableStateOf(prefs.getBoolean("gesture_volume_enabled", true)) }
    var subtitleScale by remember { mutableFloatStateOf(prefs.getFloat("subtitle_scale", 0.065f)) }
    var subtitleBackgroundEnabled by remember { mutableStateOf(prefs.getBoolean("subtitle_background", true)) }
    var aspectMode by remember { mutableStateOf("Fit") }
    var localVideos by remember { mutableStateOf(emptyList<LocalVideo>()) }
    var librarySearch by remember { mutableStateOf("") }
    var librarySort by remember { mutableStateOf(prefs.getString("library_sort", "Recently added") ?: "Recently added") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var durationFilter by remember { mutableStateOf(prefs.getString("library_duration_filter", "All") ?: "All") }
    var watchFilter by remember { mutableStateOf(prefs.getString("library_watch_filter", "All videos") ?: "All videos") }
    var watchedUris by remember { mutableStateOf(prefs.getStringSet("watched_video_uris", emptySet())?.toSet() ?: emptySet()) }
    var gridView by remember { mutableStateOf(prefs.getBoolean("library_grid_view", true)) }
    var activeTab by remember { mutableStateOf("Videos") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedVideoInfo by remember { mutableStateOf<LocalVideo?>(null) }

    LaunchedEffect(queueItems) {
        saveQueue(prefs, queueItems)
    }

    LaunchedEffect(selectedUri) {
        abLoopStartMs = null
        abLoopEndMs = null
    }

    LaunchedEffect(selectedUri, abLoopStartMs, abLoopEndMs) {
        while (true) {
            val start = abLoopStartMs
            val end = abLoopEndMs
            if (selectedUri != null && start != null && end != null && end > start && player.currentPosition >= end) {
                player.seekTo(start)
            }
            delay(150L)
        }
    }
    LaunchedEffect(playbackSpeed, shuffleEnabled, repeatMode, subtitleScale, subtitleBackgroundEnabled, gestureSeekEnabled, gestureBrightnessEnabled, gestureVolumeEnabled) {
        prefs.edit()
            .putFloat("playback_speed", playbackSpeed)
            .putBoolean("shuffle_enabled", shuffleEnabled)
            .putInt("repeat_mode", repeatMode)
            .putFloat("subtitle_scale", subtitleScale)
            .putBoolean("subtitle_background", subtitleBackgroundEnabled)
            .putBoolean("gesture_seek_enabled", gestureSeekEnabled)
            .putBoolean("gesture_brightness_enabled", gestureBrightnessEnabled)
            .putBoolean("gesture_volume_enabled", gestureVolumeEnabled)
            .putInt("subtitle_delay_ms", subtitleDelayMs)
            .apply()
        player.shuffleModeEnabled = shuffleEnabled
        player.repeatMode = repeatMode
        player.setPlaybackSpeed(playbackSpeed)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) player.currentMediaItem?.localConfiguration?.uri?.let { uri ->
                    prefs.edit().putString(KEY_LAST_URI, uri.toString())
                        .putLong(KEY_LAST_POSITION, player.currentPosition.coerceAtLeast(0L)).apply()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) prefs.edit().putLong(KEY_LAST_POSITION, 0L).apply()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uri = mediaItem?.localConfiguration?.uri ?: return
                selectedUri = uri
                selectedTitle = mediaItem.mediaMetadata.title?.toString() ?: uri.lastPathSegment ?: "Local video"
                prefs.edit().putString(KEY_LAST_URI, uri.toString()).putString(KEY_LAST_TITLE, selectedTitle).apply()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                status = "Playback failed (${error.errorCodeName}). Try reopening the file or choosing another video."
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        player.currentMediaItem?.let { item ->
            selectedUri = item.localConfiguration?.uri
            selectedTitle = item.mediaMetadata.title?.toString() ?: item.localConfiguration?.uri?.lastPathSegment.orEmpty()
        }
        onDispose {
            val activity = context.findActivity()
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            player.currentMediaItem?.localConfiguration?.uri?.let { uri ->
                prefs.edit().putString(KEY_LAST_URI, uri.toString())
                    .putLong(KEY_LAST_POSITION, player.currentPosition.coerceAtLeast(0L)).apply()
            }
            player.removeListener(listener)
            // The service owns the player; releasing it here would stop background playback.
        }
    }

    LaunchedEffect(fullScreen) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullScreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun playMedia(uri: Uri, title: String, subtitle: Uri? = null) {
        fullScreen = false
        queueItems = emptyList()
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

    fun playQueueEntries(entries: List<HistoryEntry>, startIndex: Int = 0) {
        if (entries.isEmpty()) {
            status = "Your play queue is empty. Add videos using the queue icon."
            return
        }
        val safeIndex = startIndex.coerceIn(entries.indices)
        val mediaItems = entries.map { entry ->
            MediaItem.Builder()
                .setUri(Uri.parse(entry.uri))
                .setMediaId(entry.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).build())
                .build()
        }
        fullScreen = false
        player.setMediaItems(mediaItems, safeIndex, 0L)
        player.prepare()
        player.play()
        selectedUri = Uri.parse(entries[safeIndex].uri)
        selectedTitle = entries[safeIndex].title
        status = "Playing queue: $selectedTitle"
    }

    fun playQueue(startIndex: Int = 0) = playQueueEntries(queueItems, startIndex)

    fun enqueueMedia(video: LocalVideo) {
        val entry = HistoryEntry(video.uri.toString(), video.title)
        if (queueItems.any { it.uri == entry.uri }) {
            status = "Already in queue: ${video.title}"
            return
        }
        val currentPlayingUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (queueItems.isEmpty() && currentPlayingUri == entry.uri) {
            queueItems = listOf(entry)
            status = "Current video added to queue"
            return
        }
        val updated = if (queueItems.isEmpty() && player.mediaItemCount > 0) {
            val current = player.currentMediaItem
            val currentUri = current?.localConfiguration?.uri
            val currentTitle = selectedTitle.ifBlank { current?.mediaMetadata?.title?.toString().orEmpty() }
            if (currentUri != null && currentUri.toString() != entry.uri) {
                listOf(HistoryEntry(currentUri.toString(), currentTitle.ifBlank { currentUri.lastPathSegment ?: "Now playing" })) + entry
            } else listOf(entry)
        } else queueItems + entry
        queueItems = updated
        if (player.mediaItemCount == 0) {
            playQueue(updated.lastIndex)
        } else {
            player.addMediaItem(MediaItem.Builder().setUri(video.uri).setMediaId(entry.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).build()).build())
            status = "Added to queue: ${video.title}"
        }
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
                loadedSubtitleSource = uri
                val adjustedSubtitle = shiftedSubtitleUri(context, uri, subtitleDelayMs.toLong())
                if (adjustedSubtitle == null) {
                    status = "Could not read the subtitle file. Try selecting it again."
                } else {
                    playMedia(selectedUri!!, selectedTitle, adjustedSubtitle)
                    status = "Subtitle loaded: $name ($subtitleDelayMs ms offset)"
                }
            } else status = "Choose an .srt or .vtt subtitle file."
        } else status = "Start a video before loading external subtitles."
    }

    val libraryPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    val libraryPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            localVideos = loadLocalVideos(context)
            status = "Found " + localVideos.size + " videos on this device."
        } else status = "Video access is needed to show all device videos. You can still browse and open a video."
    }

    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(libraryPermission) == PackageManager.PERMISSION_GRANTED) {
            localVideos = loadLocalVideos(context)
            status = "Found " + localVideos.size + " videos on this device."
        } else {
            libraryPermissionLauncher.launch(libraryPermission)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF0A0E1A)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0A0E1A))) {
            if (!fullScreen) Row(
                Modifier.fillMaxWidth().background(Color(0xFF12182A)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).background(Color(0xFF39E7D5), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("NOVA PLAYER", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("LOCAL VIDEO PLAYER", color = Color(0xFF9BA8C7), fontSize = 9.sp, letterSpacing = 1.4.sp)
                }
                if (selectedUri != null) {
                    IconButton(onClick = { selectedUri = null }) { Icon(Icons.Default.ArrowBack, "Back to library", tint = Color.White) }
                } else {
                    IconButton(onClick = {
                        if (context.checkSelfPermission(libraryPermission) == PackageManager.PERMISSION_GRANTED) {
                            localVideos = loadLocalVideos(context)
                            status = "Found " + localVideos.size + " videos on this device."
                        } else libraryPermissionLauncher.launch(libraryPermission)
                    }) { Icon(Icons.Default.Refresh, "Refresh library", tint = Color.White) }
                    if (activeTab == "Videos") {
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) { Icon(Icons.Default.Sort, "Sort videos", tint = Color.White) }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }, modifier = Modifier.background(Panel)) {
                                listOf("Recently added", "Name A–Z", "Name Z–A", "Longest first", "Largest first").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = if (librarySort == option) Mint else Color.White) },
                                        leadingIcon = { if (librarySort == option) Icon(Icons.Default.Check, null, tint = Mint) else Icon(Icons.Default.Sort, null, tint = Color(0xFF9BA8C7)) },
                                        onClick = {
                                            librarySort = option
                                            prefs.edit().putString("library_sort", option).apply()
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            gridView = !gridView
                            prefs.edit().putBoolean("library_grid_view", gridView).apply()
                        }) { Icon(if (gridView) Icons.Default.ViewList else Icons.Default.GridView, if (gridView) "Switch to list view" else "Switch to grid view", tint = Mint) }
                    }
                }
            }

            if (selectedUri == null && activeTab == "Videos") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = librarySearch, onValueChange = { librarySearch = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        label = { Text("Search videos") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF39E7D5),
                            unfocusedBorderColor = Color(0xFF35415B),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF39E7D5), unfocusedLabelColor = Color(0xFF9BA8C7)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                        Icon(Icons.Default.FolderOpen, "Browse files", tint = Color(0xFF39E7D5))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("All videos", "Unwatched", "Watched").forEach { filter ->
                        FilterChip(
                            selected = watchFilter == filter,
                            onClick = {
                                watchFilter = filter
                                prefs.edit().putString("library_watch_filter", filter).apply()
                            },
                            label = { Text(filter, fontSize = 11.sp) },
                            leadingIcon = if (watchFilter == filter) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp)) }) else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Lavender.copy(alpha = 0.18f),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Mint,
                                labelColor = Color(0xFFBAC6DF)
                            )
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("All", "Under 10 min", "10–60 min", "1 hour+").forEach { filter ->
                        FilterChip(
                            selected = durationFilter == filter,
                            onClick = {
                                durationFilter = filter
                                prefs.edit().putString("library_duration_filter", filter).apply()
                            },
                            label = { Text(filter, fontSize = 11.sp) },
                            leadingIcon = if (durationFilter == filter) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp)) }) else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Mint.copy(alpha = 0.16f),
                                selectedLabelColor = Mint,
                                selectedLeadingIconColor = Mint,
                                labelColor = Color(0xFFBAC6DF)
                            )
                        )
                    }
                }
                recentItems.firstOrNull()?.let { recent ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF152438)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(Color(0xFF20374A), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayCircle, null, tint = Mint, modifier = Modifier.size(27.dp))
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text("CONTINUE WATCHING", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(recent.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                val savedPosition = if (prefs.getString(KEY_LAST_URI, null) == recent.uri) prefs.getLong(KEY_LAST_POSITION, 0L) else 0L
                                Text(if (savedPosition > 5000L) "Resume at ${formatTime(savedPosition)}" else "Recently played · On this device", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    try { playMedia(Uri.parse(recent.uri), recent.title) }
                                    catch (_: Exception) { status = "This media is unavailable. Browse and select it again." }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Mint, contentColor = Midnight),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text("Resume", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectedFolder != null) "FOLDER: $selectedFolder" else if (localVideos.isEmpty()) "DEVICE VIDEOS" else "${localVideos.size} VIDEOS", color = Color(0xFF9BA8C7), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    if (selectedFolder != null) TextButton(onClick = { selectedFolder = null }) { Text("Clear", color = Color(0xFF8A70FF), fontSize = 11.sp) }
                    Spacer(Modifier.weight(1f))
                    Text(librarySort, color = Color(0xFF9BA8C7), fontSize = 11.sp)
                }
                val visibleVideos = localVideos.filter { video ->
                    val matchesDuration = when (durationFilter) {
                        "Under 10 min" -> video.duration in 1L..599_999L
                        "10–60 min" -> video.duration in 600_000L..3_599_999L
                        "1 hour+" -> video.duration >= 3_600_000L
                        else -> true
                    }
                    val isWatched = watchedUris.contains(video.uri.toString())
                    val matchesWatch = when (watchFilter) {
                        "Watched" -> isWatched
                        "Unwatched" -> !isWatched
                        else -> true
                    }
                    video.title.contains(librarySearch, ignoreCase = true) &&
                        (selectedFolder == null || video.folder == selectedFolder) &&
                        matchesDuration && matchesWatch
                }.let { list ->
                    when (librarySort) {
                        "Name A–Z" -> list.sortedBy { it.title.lowercase() }
                        "Name Z–A" -> list.sortedByDescending { it.title.lowercase() }
                        "Longest first" -> list.sortedByDescending { it.duration }
                        "Largest first" -> list.sortedByDescending { it.size }
                        else -> list.sortedByDescending { it.dateAdded }
                    }
                }
                if (visibleVideos.isEmpty()) {
                    Column(Modifier.fillMaxWidth().weight(1f).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.VideoLibrary, null, tint = Color(0xFF39E7D5), modifier = Modifier.size(58.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Your videos appear here", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Scan your device to build your local library, or browse and open a single video.", color = Color(0xFF9BA8C7), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = {
                            if (context.checkSelfPermission(libraryPermission) == PackageManager.PERMISSION_GRANTED) {
                                localVideos = loadLocalVideos(context)
                                status = "Found " + localVideos.size + " videos on this device."
                            } else libraryPermissionLauncher.launch(libraryPermission)
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39E7D5))) {
                            Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Scan device videos")
                        }
                        TextButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) { Text("Browse files instead", color = Color(0xFF8A70FF)) }
                    }
                } else if (gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visibleVideos.take(200), key = { it.uri.toString() }) { video ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Panel),
                                onClick = { playMedia(video.uri, video.title) }
                            ) {
                                Column {
                                    Box(Modifier.fillMaxWidth().height(116.dp).background(Color(0xFF202A40)), contentAlignment = Alignment.Center) {
                                        VideoThumbnail(video)
                                        Box(Modifier.align(Alignment.BottomEnd).background(Color(0xCC000000), RoundedCornerShape(topStart = 8.dp)).padding(horizontal = 7.dp, vertical = 4.dp)) {
                                            Text(formatTime(video.duration), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        if (watchedUris.contains(video.uri.toString())) {
                                            Row(Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xE6192438), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, null, tint = Mint, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(3.dp))
                                                Text("WATCHED", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Box(Modifier.align(Alignment.Center).size(42.dp).background(Color(0xB31A263B), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.PlayArrow, "Play video", tint = Mint, modifier = Modifier.size(30.dp))
                                        }
                                    }
                                    Text(video.title, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2)
                                    Text(formatBytes(video.size) + " · " + video.folder.substringAfterLast('/').ifBlank { "Device" }, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), color = Color(0xFF9BA8C7), fontSize = 10.sp, maxLines = 1)
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = {
                                            val uri = video.uri.toString()
                                            favoriteItems = if (favoriteItems.any { it.uri == uri }) favoriteItems.filterNot { it.uri == uri } else listOf(HistoryEntry(uri, video.title)) + favoriteItems
                                            saveFavorites(prefs, favoriteItems)
                                            status = if (favoriteItems.any { it.uri == uri }) "Added to favorites" else "Removed from favorites"
                                        }) {
                                            Icon(if (favoriteItems.any { it.uri == video.uri.toString() }) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Toggle favorite", tint = if (favoriteItems.any { it.uri == video.uri.toString() }) Color(0xFFFF6B9D) else Color(0xFF9BA8C7), modifier = Modifier.size(19.dp))
                                        }
                                        IconButton(onClick = {
                                            val uri = video.uri.toString()
                                            watchedUris = if (uri in watchedUris) watchedUris - uri else watchedUris + uri
                                            prefs.edit().putStringSet("watched_video_uris", watchedUris).apply()
                                            status = if (uri in watchedUris) "Marked as watched" else "Marked as unwatched"
                                        }) { Icon(if (video.uri.toString() in watchedUris) Icons.Default.CheckCircle else Icons.Default.Visibility, "Toggle watched status", tint = if (video.uri.toString() in watchedUris) Mint else Color(0xFF9BA8C7), modifier = Modifier.size(19.dp)) }
                                        IconButton(onClick = { enqueueMedia(video) }) { Icon(Icons.Default.PlaylistAdd, "Add to queue", tint = Lavender, modifier = Modifier.size(20.dp)) }
                                        IconButton(onClick = { selectedVideoInfo = video }) { Icon(Icons.Default.Info, "Video details", tint = Color(0xFF9BA8C7), modifier = Modifier.size(19.dp)) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        visibleVideos.take(200).forEach { video ->
                            Row(
                                Modifier.fillMaxWidth().background(Color(0xFF0A0E1A)).padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(width = 100.dp, height = 66.dp).background(Color(0xFF202A40), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                                    VideoThumbnail(video)
                                    Box(Modifier.align(Alignment.BottomEnd).background(Color(0xCC000000), RoundedCornerShape(topStart = 4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text(formatTime(video.duration), color = Color.White, fontSize = 10.sp)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(video.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatBytes(video.size) + "  •  " + video.folder.substringAfterLast('/').ifBlank { "Device storage" }, color = Color(0xFF9E9E9E), fontSize = 11.sp, maxLines = 1)
                                }
                                IconButton(onClick = {
                                    val uri = video.uri.toString()
                                    favoriteItems = if (favoriteItems.any { it.uri == uri }) {
                                        favoriteItems.filterNot { it.uri == uri }
                                    } else {
                                        listOf(HistoryEntry(uri, video.title)) + favoriteItems
                                    }
                                    saveFavorites(prefs, favoriteItems)
                                    status = if (favoriteItems.any { it.uri == uri }) "Added to favorites" else "Removed from favorites"
                                }) {
                                    Icon(
                                        if (favoriteItems.any { it.uri == video.uri.toString() }) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        "Toggle favorite",
                                        tint = Color(0xFF39E7D5)
                                    )
                                }
                                IconButton(onClick = {
                                    val uri = video.uri.toString()
                                    watchedUris = if (uri in watchedUris) watchedUris - uri else watchedUris + uri
                                    prefs.edit().putStringSet("watched_video_uris", watchedUris).apply()
                                    status = if (uri in watchedUris) "Marked as watched" else "Marked as unwatched"
                                }) {
                                    Icon(if (video.uri.toString() in watchedUris) Icons.Default.CheckCircle else Icons.Default.Visibility, "Toggle watched status", tint = if (video.uri.toString() in watchedUris) Mint else Color(0xFF9BA8C7), modifier = Modifier.size(22.dp))
                                }
                                IconButton(onClick = { enqueueMedia(video) }) {
                                Icon(Icons.Default.PlaylistAdd, "Add ${video.title} to queue", tint = Color(0xFF8A70FF), modifier = Modifier.size(26.dp))
                            }
                            IconButton(onClick = { playMedia(video.uri, video.title) }) {
                                    Icon(Icons.Default.PlayCircle, "Play ${video.title}", tint = Color(0xFF39E7D5), modifier = Modifier.size(30.dp))
                                }
                            }
                            Divider(color = Color(0xFF202A40), thickness = 1.dp)
                        }
                    }
                }
            } else if (selectedUri == null && activeTab == "Folders") {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("BROWSE BY FOLDER", color = Color(0xFF9BA8C7), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    val folders = localVideos.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
                    if (folders.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF39E7D5), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("No indexed video folders yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Scan device videos or use Browse files.", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                            TextButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) { Text("Browse files", color = Color(0xFF8A70FF)) }
                        }
                    } else folders.forEach { (folder, videos) ->
                        Row(Modifier.fillMaxWidth().background(Color(0xFF12182A), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = Color(0xFF8A70FF), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(folder.substringAfterLast('/').ifBlank { "Device storage" }, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${videos.size} video${if (videos.size == 1) "" else "s"}  •  ${formatBytes(videos.sumOf { it.size })}", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                                Text(folder, color = Color(0xFF6F7D9C), fontSize = 10.sp, maxLines = 1)
                            }
                            IconButton(onClick = { selectedFolder = folder; activeTab = "Videos" }) { Icon(Icons.Default.ChevronRight, "Open folder", tint = Color(0xFF39E7D5)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else if (selectedUri == null && activeTab == "Recent") {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("PLAYBACK HISTORY", color = Color(0xFF9BA8C7), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    if (recentItems.isEmpty()) {
                        Text("Videos you play will appear here.", color = Color(0xFF9BA8C7), modifier = Modifier.padding(vertical = 24.dp))
                    } else recentItems.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = Color(0xFF39E7D5), modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(item.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2)
                            IconButton(onClick = {
                                try { playMedia(Uri.parse(item.uri), item.title) }
                                catch (_: Exception) { status = "This media is unavailable. Browse and select it again." }
                            }) { Icon(Icons.Default.PlayArrow, "Play recent video", tint = Color(0xFF39E7D5)) }
                        }
                        Divider(color = Color(0xFF202A40))
                    }
                }
            }

            if (selectedUri == null && activeTab == "Playlists") {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SAVED PLAYLISTS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("Stored on this device · Works offline", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                        }
                        Button(onClick = { playlistName = ""; showCreatePlaylistDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Save queue")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (playlists.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LibraryMusic, null, tint = Lavender, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Build your own playlists", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Add videos to the queue, then save it as a playlist.", color = Color(0xFF9BA8C7), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    } else playlists.forEach { playlist ->
                        Card(Modifier.fillMaxWidth().padding(bottom = 9.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(13.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(42.dp).background(Color(0xFF202A40), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.LibraryMusic, null, tint = Mint, modifier = Modifier.size(25.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(playlist.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                        Text("${playlist.items.size} video${if (playlist.items.size == 1) "" else "s"} · Saved on this device", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                                    }
                                    IconButton(onClick = {
                                        playlists = playlists.filterNot { it.name == playlist.name }
                                        savePlaylists(prefs, playlists)
                                        status = "Deleted playlist: ${playlist.name}"
                                    }) { Icon(Icons.Default.DeleteOutline, "Delete ${playlist.name}", tint = Color(0xFF9BA8C7)) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { queueItems = playlist.items; playQueueEntries(playlist.items, 0) }, enabled = playlist.items.isNotEmpty(), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Play playlist")
                                    }
                                    OutlinedButton(onClick = {
                                        queueItems = (queueItems + playlist.items).distinctBy { it.uri }
                                        status = "Added ${playlist.items.size} playlist videos to queue"
                                    }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.PlaylistAdd, null); Spacer(Modifier.width(5.dp)); Text("Add to queue")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedUri == null && activeTab == "Queue") {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QueueMusic, null, tint = Mint)
                        Spacer(Modifier.width(8.dp))
                        Text("PLAY QUEUE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${queueItems.size} items", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { playQueue(0) }, enabled = queueItems.isNotEmpty(), modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play all")
                        }
                        OutlinedButton(onClick = {
                            queueItems = emptyList()
                            player.clearMediaItems()
                            selectedUri = null
                            selectedTitle = ""
                            status = "Play queue cleared"
                        }, enabled = queueItems.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Clear queue")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (queueItems.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QueueMusic, null, tint = Lavender, modifier = Modifier.size(46.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Your queue is empty", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Tap the playlist-plus icon beside any video to add it.", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                        }
                    } else queueItems.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth().background(if (selectedUri?.toString() == item.uri) Color(0xFF172A39) else Color.Transparent, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = Mint, modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(item.title, color = Color.White, maxLines = 2, fontSize = 13.sp)
                                Text(if (selectedUri?.toString() == item.uri) "Now playing" else "Queued locally", color = Color(0xFF9BA8C7), fontSize = 10.sp)
                            }
                            IconButton(onClick = {
                                if (index > 0) {
                                    val reordered = queueItems.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    val mirrorsPlayerQueue = player.mediaItemCount == queueItems.size
                                    if (mirrorsPlayerQueue) player.moveMediaItem(index, index - 1)
                                    queueItems = reordered
                                    status = if (mirrorsPlayerQueue) "Queue order updated" else "Order saved for the next queue playback"
                                }
                            }, enabled = index > 0) {
                                Icon(Icons.Default.ArrowUpward, "Move ${item.title} up", tint = if (index > 0) Lavender else Color(0xFF46516A), modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                if (index < queueItems.lastIndex) {
                                    val reordered = queueItems.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    val mirrorsPlayerQueue = player.mediaItemCount == queueItems.size
                                    if (mirrorsPlayerQueue) player.moveMediaItem(index, index + 1)
                                    queueItems = reordered
                                    status = if (mirrorsPlayerQueue) "Queue order updated" else "Order saved for the next queue playback"
                                }
                            }, enabled = index < queueItems.lastIndex) {
                                Icon(Icons.Default.ArrowDownward, "Move ${item.title} down", tint = if (index < queueItems.lastIndex) Lavender else Color(0xFF46516A), modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { playQueue(index) }) { Icon(Icons.Default.PlayArrow, "Play ${item.title}", tint = Mint) }
                            IconButton(onClick = {
                                val wasCurrent = selectedUri?.toString() == item.uri
                                queueItems = queueItems.filterIndexed { i, _ -> i != index }
                                if (player.mediaItemCount > 0) {
                                    val playerIndex = player.currentMediaItemIndex
                                    player.removeMediaItem(index.coerceAtMost(player.mediaItemCount - 1))
                                    if (wasCurrent && queueItems.isEmpty()) {
                                        selectedUri = null
                                        selectedTitle = ""
                                    }
                                }
                                status = "Removed from queue"
                            }) { Icon(Icons.Default.Close, "Remove ${item.title} from queue", tint = Color(0xFF9BA8C7)) }
                        }
                        Divider(color = Color(0xFF202A40))
                    }
                }
            }

            if (selectedUri == null && activeTab == "Favorites") {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, null, tint = Color(0xFF39E7D5))
                        Spacer(Modifier.width(8.dp))
                        Text("YOUR FAVORITES", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${favoriteItems.size} saved", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (favoriteItems.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FavoriteBorder, null, tint = Color(0xFF6F7D9C), modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("No favorites yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Tap the heart beside a video to save it here.", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                        }
                    } else favoriteItems.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Movie, null, tint = Color(0xFF8A70FF), modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = Color.White, maxLines = 2, fontSize = 14.sp)
                                Text("Saved on this device", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                            }
                            IconButton(onClick = {
                                try { playMedia(Uri.parse(item.uri), item.title) }
                                catch (_: Exception) { status = "This media is unavailable. Browse and select it again." }
                            }) { Icon(Icons.Default.PlayCircle, "Play favorite", tint = Color(0xFF39E7D5)) }
                            IconButton(onClick = {
                                favoriteItems = favoriteItems.filterNot { it.uri == item.uri }
                                saveFavorites(prefs, favoriteItems)
                                status = "Removed from favorites"
                            }) { Icon(Icons.Default.DeleteOutline, "Remove favorite", tint = Color(0xFF9BA8C7)) }
                        }
                        Divider(color = Color(0xFF202A40))
                    }
                }
            }

            if (selectedUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize().pointerInput(selectedUri, controlsLocked, gestureSeekEnabled, gestureBrightnessEnabled, gestureVolumeEnabled) {
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
                                        gestureAxis = if (controlsLocked) "locked" else "none"
                                        isSeekingGesture = false
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        totalX += amount.x
                                        totalY += amount.y
                                        if (gestureAxis == "none" && (abs(totalX) > 12f || abs(totalY) > 12f)) {
                                            gestureAxis = if (abs(totalX) >= abs(totalY)) {
                                                if (gestureSeekEnabled) "horizontal" else "none"
                                            } else {
                                                if ((startX < size.width / 2f && gestureBrightnessEnabled) || (startX >= size.width / 2f && gestureVolumeEnabled)) "vertical" else "none"
                                            }
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
                            factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = !controlsLocked; setShowSubtitleButton(true); resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                            update = { view ->
                                view.player = player
                                view.useController = !controlsLocked
                                view.resizeMode = when (aspectMode) { "Fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL; "Zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
                                view.subtitleView?.setFractionalTextSize(subtitleScale)
                                view.subtitleView?.setStyle(CaptionStyleCompat(
                                    android.graphics.Color.WHITE,
                                    if (subtitleBackgroundEnabled) android.graphics.Color.argb(190, 0, 0, 0) else android.graphics.Color.TRANSPARENT,
                                    android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    null
                                ))
                            }
                        )
                        if (controlsLocked) {
                            FilledTonalButton(
                                onClick = {
                                    controlsLocked = false
                                    status = "Player controls unlocked"
                                },
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Mint, contentColor = Midnight)
                            ) {
                                Icon(Icons.Default.LockOpen, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Unlock controls")
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    controlsLocked = true
                                    status = "Player controls locked · Tap Unlock controls to restore"
                                },
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Panel.copy(alpha = 0.85f), RoundedCornerShape(50))
                            ) {
                                Icon(Icons.Default.Lock, "Lock player controls", tint = Mint)
                            }
                        }
                        if (fullScreen) {
                            IconButton(
                                onClick = {
                                    fullScreen = false
                                    status = "Immersive full-screen disabled"
                                },
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Panel.copy(alpha = 0.85f), RoundedCornerShape(50))
                            ) {
                                Icon(Icons.Default.FullscreenExit, "Exit full screen", tint = Mint)
                            }
                        }
                        }
                        if (!fullScreen && !controlsLocked) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                    status = "Rewind 10 seconds"
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF202A40), contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text("↶  10s", fontWeight = FontWeight.SemiBold) }
                            FilledTonalButton(
                                onClick = {
                                    val target = player.currentPosition + 10_000L
                                    player.seekTo(if (player.duration > 0L) target.coerceAtMost(player.duration) else target)
                                    status = "Forward 10 seconds"
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF202A40), contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text("10s  ↷", fontWeight = FontWeight.SemiBold) }
                            FilledTonalButton(
                                onClick = {
                                    val nextSpeed = when (playbackSpeed) { 0.5f -> 0.75f; 0.75f -> 1.0f; 1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2.0f; else -> 0.5f }
                                    playbackSpeed = nextSpeed
                                    player.setPlaybackSpeed(nextSpeed)
                                    status = "Playback speed: " + nextSpeed + "×"
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF202A40), contentColor = Color(0xFF8A70FF)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text(playbackSpeed.toString() + "×", fontWeight = FontWeight.Bold) }
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = {
                                abLoopStartMs = player.currentPosition.coerceAtLeast(0L)
                                abLoopEndMs = null
                                status = "A–B loop point A set at " + formatTime(player.currentPosition)
                            }) {
                                Text(if (abLoopStartMs == null) "Set A" else "A: " + formatTime(abLoopStartMs!!), color = if (abLoopStartMs != null) Mint else Color.White, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = {
                                val start = abLoopStartMs
                                val end = player.currentPosition.coerceAtLeast(0L)
                                if (start != null && end > start) {
                                    abLoopEndMs = end
                                    status = "A–B loop enabled: " + formatTime(start) + " – " + formatTime(end)
                                } else {
                                    status = "Set point A first, then set point B later in the video"
                                }
                            }) {
                                Text(if (abLoopEndMs == null) "Set B" else "B: " + formatTime(abLoopEndMs!!), color = if (abLoopEndMs != null) Mint else Color.White, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = {
                                abLoopStartMs = null
                                abLoopEndMs = null
                                status = "A–B loop cleared"
                            }) { Text("Clear A–B", color = Lavender) }
                            TextButton(onClick = {
                                shuffleEnabled = !shuffleEnabled
                                player.shuffleModeEnabled = shuffleEnabled
                                status = if (shuffleEnabled) "Shuffle enabled" else "Shuffle disabled"
                            }) {
                                Icon(Icons.Default.Shuffle, null, tint = if (shuffleEnabled) Mint else Lavender)
                                Text("Shuffle", color = if (shuffleEnabled) Mint else Color.White)
                            }
                            TextButton(onClick = {
                                repeatMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                player.repeatMode = repeatMode
                                status = when (repeatMode) {
                                    Player.REPEAT_MODE_ALL -> "Repeat all enabled"
                                    Player.REPEAT_MODE_ONE -> "Repeat current video enabled"
                                    else -> "Repeat disabled"
                                }
                            }) {
                                Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if (repeatMode != Player.REPEAT_MODE_OFF) Mint else Lavender)
                                Text(when (repeatMode) {
                                    Player.REPEAT_MODE_ALL -> "Repeat all"
                                    Player.REPEAT_MODE_ONE -> "Repeat one"
                                    else -> "Repeat off"
                                }, color = if (repeatMode != Player.REPEAT_MODE_OFF) Mint else Color.White)
                            }
                            TextButton(onClick = {
                                aspectMode = when (aspectMode) { "Fit" -> "Fill"; "Fill" -> "Zoom"; else -> "Fit" }
                                status = "Screen mode: " + aspectMode
                            }) { Icon(Icons.Default.AspectRatio, null, tint = Lavender); Text(aspectMode) }
                            TextButton(onClick = {
                                try { TrackSelectionDialogBuilder(context, "Video quality / tracks", player, C.TRACK_TYPE_VIDEO).build().show() }
                                catch (_: Exception) { status = "Video quality selection is unavailable for this file. The source resolution is unchanged." }
                            }) { Icon(Icons.Default.HighQuality, null, tint = Lavender); Text("Quality") }
                            TextButton(onClick = {
                                try { TrackSelectionDialogBuilder(context, "Audio tracks", player, C.TRACK_TYPE_AUDIO).build().show() }
                                catch (_: Exception) { status = "Audio track selection is unavailable for this media." }
                            }) { Icon(Icons.Default.Audiotrack, null, tint = Lavender); Text("Audio") }
                            TextButton(onClick = { subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain")) }) {
                                Icon(Icons.Default.Subtitles, null, tint = Lavender); Text("Subtitles")
                            }
                            TextButton(onClick = { showSubtitleSettings = true }) {
                                Icon(Icons.Default.TextFields, null, tint = Lavender); Text("Text")
                            }
                            TextButton(onClick = { showSubtitleSyncDialog = true }) {
                                Icon(Icons.Default.Sync, null, tint = Lavender); Text("Sync")
                            }
                            TextButton(onClick = { showGestureSettings = true }) {
                                Icon(Icons.Default.Tune, null, tint = Lavender)
                                Text("Gestures", color = Color.White)
                            }
                            TextButton(onClick = { showSleepTimerDialog = true }) {
                                Icon(Icons.Default.Bedtime, null, tint = if (sleepTimerActive) Mint else Lavender)
                                Text(if (sleepTimerActive) "Sleep on" else "Sleep timer", color = if (sleepTimerActive) Mint else Color.White)
                            }
                            TextButton(onClick = {
                                fullScreen = !fullScreen
                                status = if (fullScreen) "Immersive full-screen enabled. Swipe from an edge to reveal system bars." else "Immersive full-screen disabled"
                            }) {
                                Icon(if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, null, tint = Mint)
                                Text(if (fullScreen) "Exit full" else "Full screen")
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
                }
                if (!fullScreen && !controlsLocked) {
                    Text(
                        if (isSeekingGesture) "Swipe horizontally to seek" else if (status.startsWith("Playback failed")) status else selectedTitle,
                        color = if (status.startsWith("Playback failed")) Color(0xFFFFB4AB) else Lavender,
                        fontSize = 12.sp,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    if (status.startsWith("Playback failed")) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    try {
                                        player.prepare()
                                        player.play()
                                        status = "Retrying playback…"
                                    } catch (_: Exception) {
                                        status = "Could not retry this file. Reopen it from the library or choose another video."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                            ) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Retry playback") }
                            OutlinedButton(onClick = { selectedUri = null; fullScreen = false; status = "Choose a video to continue." }) {
                                Text("Back to library")
                            }
                        }
                    }
                }
            }

            if (selectedUri == null) Text(status, color = if (status.startsWith("Use") || status.startsWith("Choose an") || status.startsWith("Start a")) Color(0xFFFFB4AB) else Color(0xFFBAC6DF), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF12182A)).padding(horizontal = 14.dp, vertical = 8.dp))
            if (selectedUri == null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFF12182A)).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Videos", "Folders", "Recent", "Favorites", "Queue", "Playlists").forEach { tab ->
                    TextButton(onClick = { activeTab = tab }, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(when (tab) { "Videos" -> Icons.Default.VideoLibrary; "Folders" -> Icons.Default.Folder; "Favorites" -> Icons.Default.Favorite; "Queue" -> Icons.Default.QueueMusic; "Playlists" -> Icons.Default.LibraryMusic; else -> Icons.Default.History }, null, tint = if (activeTab == tab) Color(0xFF39E7D5) else Color(0xFF6F7D9C))
                            Text(tab, fontSize = 10.sp, color = if (activeTab == tab) Color(0xFF39E7D5) else Color(0xFF9BA8C7))
                        }
                    }
                }
            }
        }
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = { Text("Save playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Save the current queue for later. It stays on this device and is available offline.", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                        OutlinedTextField(value = playlistName, onValueChange = { playlistName = it.take(50) }, singleLine = true, label = { Text("Playlist name") }, placeholder = { Text("e.g. Weekend movies") })
                        Text("${queueItems.size} videos in current queue", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = playlistName.trim()
                        if (name.isNotBlank() && queueItems.isNotEmpty()) {
                            val saved = PlaylistEntry(name, queueItems.toList())
                            playlists = listOf(saved) + playlists.filterNot { it.name.equals(name, ignoreCase = true) }
                            savePlaylists(prefs, playlists)
                            showCreatePlaylistDialog = false
                            status = "Saved playlist: $name"
                        } else status = if (queueItems.isEmpty()) "Add videos to the queue before saving a playlist" else "Enter a playlist name"
                    }, enabled = playlistName.isNotBlank() && queueItems.isNotEmpty()) { Text("Save", color = Mint) }
                },
                dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") } },
                containerColor = Panel, titleContentColor = Color.White, textContentColor = Color.White
            )
        }
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = { Text("Sleep timer") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (sleepTimerActive) "A sleep timer is active. Choose a new duration to replace it, or cancel it below."
                            else "Pause playback automatically after the selected time.",
                            color = Color(0xFF9BA8C7),
                            fontSize = 12.sp
                        )
                        listOf(15, 30, 45, 60, 90).forEach { minutes ->
                            OutlinedButton(
                                onClick = {
                                    sleepTimerJob?.cancel()
                                    sleepTimerActive = true
                                    showSleepTimerDialog = false
                                    status = "Sleep timer set for $minutes minutes"
                                    sleepTimerJob = uiScope.launch {
                                        delay(minutes * 60_000L)
                                        player.pause()
                                        sleepTimerActive = false
                                        sleepTimerJob = null
                                        status = "Sleep timer finished — playback paused"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Bedtime, null, tint = Mint)
                                Spacer(Modifier.width(8.dp))
                                Text("$minutes minutes")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        sleepTimerJob?.cancel()
                        sleepTimerJob = null
                        sleepTimerActive = false
                        showSleepTimerDialog = false
                        status = "Sleep timer cancelled"
                    }) { Text(if (sleepTimerActive) "Cancel timer" else "Close", color = Mint) }
                },
                dismissButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) { Text("Done") }
                },
                containerColor = Panel,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
        if (showGestureSettings) {
            AlertDialog(
                onDismissRequest = { showGestureSettings = false },
                title = { Text("Gesture controls") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Choose which swipe gestures are active while watching a video.", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Horizontal swipe to seek", color = Color.White)
                                Text("Swipe left or right to jump through a video", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                            }
                            Switch(checked = gestureSeekEnabled, onCheckedChange = { gestureSeekEnabled = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Left-side brightness", color = Color.White)
                                Text("Swipe vertically on the left half", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                            }
                            Switch(checked = gestureBrightnessEnabled, onCheckedChange = { gestureBrightnessEnabled = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Right-side volume", color = Color.White)
                                Text("Swipe vertically on the right half", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                            }
                            Switch(checked = gestureVolumeEnabled, onCheckedChange = { gestureVolumeEnabled = it })
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showGestureSettings = false
                        status = "Gesture preferences saved"
                    }) { Text("Done", color = Mint) }
                },
                containerColor = Panel,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
        if (showSubtitleSyncDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleSyncDialog = false },
                title = { Text("Subtitle timing") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Adjust when subtitles appear relative to the video.", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                        Text(
                            if (subtitleDelayMs == 0) "No delay" else if (subtitleDelayMs < 0) "Earlier by ${-subtitleDelayMs} ms" else "Later by ${subtitleDelayMs} ms",
                            color = Mint,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(value = subtitleDelayMs.toFloat(), onValueChange = { subtitleDelayMs = it.toInt() }, valueRange = -5000f..5000f, steps = 99)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { subtitleDelayMs = (subtitleDelayMs - 250).coerceAtLeast(-5000) }) { Text("−250 ms") }
                            OutlinedButton(onClick = { subtitleDelayMs = (subtitleDelayMs + 250).coerceAtMost(5000) }) { Text("+250 ms") }
                        }
                        Text("Offset is applied to the selected .srt or .vtt file when you tap Apply.", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putInt("subtitle_delay_ms", subtitleDelayMs).apply()
                        val source = loadedSubtitleSource
                        val video = selectedUri
                        if (source != null && video != null) {
                            val adjusted = shiftedSubtitleUri(context, source, subtitleDelayMs.toLong())
                            if (adjusted != null) {
                                playMedia(video, selectedTitle, adjusted)
                                status = "Subtitle timing applied: ${subtitleDelayMs} ms"
                            } else status = "Could not apply subtitle timing. Re-select the subtitle file."
                        } else {
                            status = "Timing saved. Load an external subtitle file to apply it."
                        }
                        showSubtitleSyncDialog = false
                    }) { Text("Apply", color = Mint) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        subtitleDelayMs = 0
                        prefs.edit().putInt("subtitle_delay_ms", 0).apply()
                    }) { Text("Reset") }
                },
                containerColor = Panel,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
        selectedVideoInfo?.let { video ->
            AlertDialog(
                onDismissRequest = { selectedVideoInfo = null },
                title = { Text("Video details", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(video.title, color = Mint, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = Color(0xFF35415B))
                        listOf(
                            "Duration" to formatTime(video.duration),
                            "File size" to formatBytes(video.size),
                            "Folder" to video.folder,
                            "Added" to java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(video.dateAdded * 1000L)),
                            "Media URI" to video.uri.toString()
                        ).forEach { (label, value) ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(label, color = Color(0xFF9BA8C7), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(value, color = Color.White, fontSize = 13.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = {
                                selectedVideoInfo = null
                                playMedia(video.uri, video.title)
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
                            }
                            OutlinedButton(onClick = {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/*"
                                        putExtra(Intent.EXTRA_STREAM, video.uri)
                                        putExtra(Intent.EXTRA_TITLE, video.title)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                                    status = "Choose an app to share ${video.title}"
                                } catch (_: Exception) {
                                    status = "No compatible app is available to share this video."
                                }
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share")
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { selectedVideoInfo = null }) { Text("Close", color = Mint) } },
                containerColor = Panel,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
        if (showSubtitleSettings) {
            AlertDialog(
                onDismissRequest = { showSubtitleSettings = false },
                title = { Text("Subtitle appearance") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Text size", color = Color.White)
                        Slider(
                            value = subtitleScale,
                            onValueChange = { subtitleScale = it },
                            valueRange = 0.035f..0.11f,
                            steps = 14
                        )
                        Text("Preview size: ${(subtitleScale * 100).toInt()}%", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Dark subtitle background", color = Color.White)
                                Text("Improves readability on bright scenes", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                            }
                            Switch(checked = subtitleBackgroundEnabled, onCheckedChange = { subtitleBackgroundEnabled = it })
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleSettings = false }) { Text("Done", color = Mint) }
                },
                containerColor = Panel,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}

@Composable
private fun VideoThumbnail(video: LocalVideo) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = video.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(video.uri, Size(320, 180), null)
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, video.uri)
                        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = video.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Icon(Icons.Default.Movie, null, tint = Color(0xFF39E7D5), modifier = Modifier.size(30.dp))
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


private data class LocalVideo(val uri: Uri, val title: String, val duration: Long, val size: Long, val dateAdded: Long, val folder: String)

private fun loadLocalVideos(context: Context): List<LocalVideo> {
    val videos = mutableListOf<LocalVideo>()
    val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.RELATIVE_PATH)
    } else {
        arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED)
    }
    context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn) ?: "Video"
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            val folder = if (pathColumn >= 0) cursor.getString(pathColumn)?.trimEnd('/').orEmpty().ifBlank { "Device storage" } else "Device storage"
            videos.add(LocalVideo(uri, name, cursor.getLong(durationColumn), cursor.getLong(sizeColumn), cursor.getLong(dateColumn), folder))
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
private data class PlaylistEntry(val name: String, val items: List<HistoryEntry>)

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

private fun readFavorites(prefs: android.content.SharedPreferences): List<HistoryEntry> {
    return try {
        val array = JSONArray(prefs.getString(KEY_FAVORITES, "[]"))
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

private fun saveFavorites(prefs: android.content.SharedPreferences, items: List<HistoryEntry>) {
    val array = JSONArray()
    items.forEach { item ->
        array.put(JSONObject().put("uri", item.uri).put("title", item.title))
    }
    prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
}

private fun readQueue(prefs: android.content.SharedPreferences): List<HistoryEntry> = readHistoryFromKey(prefs, KEY_QUEUE)

private fun saveQueue(prefs: android.content.SharedPreferences, items: List<HistoryEntry>) {
    val array = JSONArray()
    items.forEach { item -> array.put(JSONObject().put("uri", item.uri).put("title", item.title)) }
    prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
}

private fun readHistoryFromKey(prefs: android.content.SharedPreferences, key: String): List<HistoryEntry> {
    return try {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = item.optString("uri")
            val title = item.optString("title")
            if (uri.isBlank() || title.isBlank()) null else HistoryEntry(uri, title)
        }
    } catch (_: Exception) { emptyList() }
}

private fun readPlaylists(prefs: android.content.SharedPreferences): List<PlaylistEntry> {
    return try {
        val array = JSONArray(prefs.getString(KEY_PLAYLISTS, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            val videos = item.optJSONArray("items") ?: JSONArray()
            val entries = (0 until videos.length()).mapNotNull { videoIndex ->
                val video = videos.optJSONObject(videoIndex) ?: return@mapNotNull null
                val uri = video.optString("uri")
                val title = video.optString("title")
                if (uri.isBlank() || title.isBlank()) null else HistoryEntry(uri, title)
            }
            if (name.isBlank()) null else PlaylistEntry(name, entries)
        }
    } catch (_: Exception) { emptyList() }
}

private fun savePlaylists(prefs: android.content.SharedPreferences, playlists: List<PlaylistEntry>) {
    val array = JSONArray()
    playlists.forEach { playlist ->
        val items = JSONArray()
        playlist.items.forEach { item -> items.put(JSONObject().put("uri", item.uri).put("title", item.title)) }
        array.put(JSONObject().put("name", playlist.name).put("items", items))
    }
    prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
}

private fun saveHistory(prefs: android.content.SharedPreferences, items: List<HistoryEntry>) {
    val array = JSONArray()
    items.take(8).forEach { item ->
        array.put(JSONObject().put("uri", item.uri).put("title", item.title))
    }
    prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
}
