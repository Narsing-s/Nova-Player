package com.narsing.novaplayer

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.ComponentName
import android.content.Intent
import android.provider.MediaStore
import android.content.ContentUris
import android.os.Bundle
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
private const val KEY_RESUME_PREFIX = "resume_position_"

private fun resumePositionKey(uri: String): String = KEY_RESUME_PREFIX + android.util.Base64.encodeToString(
    uri.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
)

private fun shiftedSubtitleUri(context: Context, source: Uri, offsetMs: Long): Uri? {
    if (offsetMs == 0L) return source
    return try {
        val original = context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() } ?: return null
        val adjusted = shiftSubtitleTimestamps(original, offsetMs)
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

@UnstableApi
@Composable
private fun NovaHome(player: Player) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val prefs = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTitle by remember { mutableStateOf("") }
    var lastSavedPosition by remember { mutableLongStateOf(prefs.getLong(KEY_LAST_POSITION, 0L)) }
    var resumePositions by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var status by remember { mutableStateOf("Loading videos on this device…") }
    var isPlaying by remember { mutableStateOf(false) }
    var isSeekingGesture by remember { mutableStateOf(false) }
    var seekPreviewDeltaMs by remember { mutableStateOf(0L) }
    val seekPreviewBitmap by produceState<Bitmap?>(initialValue = null, key1 = selectedUri, key2 = isSeekingGesture, key3 = seekPreviewDeltaMs) {
        val previewUri = selectedUri
        if (isSeekingGesture && previewUri != null) {
            delay(180)
            val previewPositionMs = clampSeekPosition(player.currentPosition, seekPreviewDeltaMs, player.duration)
            value = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, previewUri)
                    retriever.getFrameAtTime(
                        previewPositionMs.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                } catch (_: Exception) {
                    null
                } finally {
                    try { retriever.release() } catch (_: Exception) { }
                }
            }
        } else {
            value = null
        }
    }
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
    var orientationMode by remember { mutableStateOf(prefs.getString("player_orientation", "Auto") ?: "Auto") }
    var controlsLocked by remember { mutableStateOf(false) }
    var showLandscapeDock by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keep_screen_on", true)) }
    var abLoopStartMs by remember { mutableStateOf<Long?>(null) }
    var abLoopEndMs by remember { mutableStateOf<Long?>(null) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
    var showSkipIntervalDialog by remember { mutableStateOf(false) }
    var skipIntervalSeconds by remember { mutableIntStateOf(prefs.getInt("skip_interval_seconds", 10).coerceIn(5, 30)) }
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
    var aspectMode by remember { mutableStateOf(prefs.getString("player_aspect_mode", "Auto") ?: "Auto") }
    var videoAspectRatio by remember { mutableFloatStateOf(0f) }
    var playerZoomScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(selectedUri) { playerZoomScale = 1f }
    var localVideos by remember { mutableStateOf(emptyList<LocalVideo>()) }
    var isScanningLibrary by remember { mutableStateOf(false) }
    fun refreshLibrary() {
        if (isScanningLibrary) return
        isScanningLibrary = true
        status = "Scanning videos on this device…"
        uiScope.launch {
            try {
                val videos = withContext(Dispatchers.IO) { loadLocalVideos(context) }
                localVideos = videos
                resumePositions = videos.mapNotNull { video ->
                    val uri = video.uri.toString()
                    val saved = prefs.getLong(resumePositionKey(uri), if (prefs.getString(KEY_LAST_URI, null) == uri) prefs.getLong(KEY_LAST_POSITION, 0L) else 0L)
                    if (saved > 0L) uri to saved else null
                }.toMap()
                status = "Library refreshed · " + videos.size + " videos found"
            } catch (_: Exception) {
                status = "Could not refresh the video library. Check storage permission and try again."
            } finally {
                isScanningLibrary = false
            }
        }
    }
    var librarySearch by remember { mutableStateOf("") }
    var librarySort by remember { mutableStateOf(prefs.getString("library_sort", "Recently added") ?: "Recently added") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var durationFilter by remember { mutableStateOf(prefs.getString("library_duration_filter", "All") ?: "All") }
    var watchFilter by remember { mutableStateOf(prefs.getString("library_watch_filter", "All videos") ?: "All videos") }
    var watchedUris by remember { mutableStateOf(prefs.getStringSet("watched_video_uris", emptySet())?.toSet() ?: emptySet()) }
    var gridView by remember { mutableStateOf(prefs.getBoolean("library_grid_view", true)) }
    var activeTab by remember { mutableStateOf("Videos") }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "Midnight") ?: "Midnight") }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedVideoInfo by remember { mutableStateOf<LocalVideo?>(null) }

    LaunchedEffect(queueItems) {
        saveQueue(prefs, queueItems)
    }

    LaunchedEffect(selectedUri) {
        abLoopStartMs = null
        abLoopEndMs = null
    }

    // Periodically checkpoint playback so a force-close or process death loses less progress.
    LaunchedEffect(selectedUri, selectedTitle) {
        while (selectedUri != null) {
            delay(5_000L)
            val currentUri = player.currentMediaItem?.localConfiguration?.uri
            if (currentUri != null && currentUri == selectedUri) {
                val position = player.currentPosition.coerceAtLeast(0L)
                val uri = currentUri.toString()
                prefs.edit()
                    .putString(KEY_LAST_URI, uri)
                    .putString(KEY_LAST_TITLE, selectedTitle)
                    .putLong(KEY_LAST_POSITION, position)
                    .putLong(resumePositionKey(uri), position)
                    .apply()
                lastSavedPosition = position
                resumePositions = if (position > 0L) resumePositions + (uri to position) else resumePositions - uri
            }
        }
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
    LaunchedEffect(playbackSpeed, skipIntervalSeconds, shuffleEnabled, repeatMode, subtitleScale, subtitleBackgroundEnabled, gestureSeekEnabled, gestureBrightnessEnabled, gestureVolumeEnabled, keepScreenOn) {
        prefs.edit()
            .putFloat("playback_speed", playbackSpeed)
            .putInt("skip_interval_seconds", skipIntervalSeconds)
            .putBoolean("shuffle_enabled", shuffleEnabled)
            .putInt("repeat_mode", repeatMode)
            .putFloat("subtitle_scale", subtitleScale)
            .putBoolean("subtitle_background", subtitleBackgroundEnabled)
            .putBoolean("gesture_seek_enabled", gestureSeekEnabled)
            .putBoolean("gesture_brightness_enabled", gestureBrightnessEnabled)
            .putBoolean("gesture_volume_enabled", gestureVolumeEnabled)
            .putBoolean("keep_screen_on", keepScreenOn)
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
                    val position = player.currentPosition.coerceAtLeast(0L)
                    val uriString = uri.toString()
                    prefs.edit().putString(KEY_LAST_URI, uriString)
                        .putLong(KEY_LAST_POSITION, position)
                        .putLong(resumePositionKey(uriString), position).apply()
                    lastSavedPosition = position
                    resumePositions = if (position > 0L) resumePositions + (uriString to position) else resumePositions - uriString
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    prefs.edit().putLong(KEY_LAST_POSITION, 0L).apply()
                    lastSavedPosition = 0L
                    // Clear only this video's resume checkpoint; other videos keep their own progress.
                    player.currentMediaItem?.localConfiguration?.uri?.toString()?.let { completedUri ->
                        prefs.edit().putLong(resumePositionKey(completedUri), 0L).apply()
                        resumePositions = resumePositions - completedUri
                        val updatedWatched = watchedUris + completedUri
                        watchedUris = updatedWatched
                        prefs.edit().putStringSet("watched_video_uris", updatedWatched).apply()
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoAspectRatio = if (videoSize.height > 0) videoSize.width.toFloat() / videoSize.height.toFloat() else 0f
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
        player.videoSize.let { size -> videoAspectRatio = if (size.height > 0) size.width.toFloat() / size.height.toFloat() else 0f }
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
                val position = player.currentPosition.coerceAtLeast(0L)
                prefs.edit().putString(KEY_LAST_URI, uri.toString())
                    .putLong(KEY_LAST_POSITION, position)
                    .putLong(resumePositionKey(uri.toString()), position).apply()
            }
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player.removeListener(listener)
            // The service owns the player; releasing it here would stop background playback.
        }
    }

    LaunchedEffect(selectedUri, keepScreenOn) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        if (selectedUri != null && keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(orientationMode, selectedUri) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        activity.requestedOrientation = requestedOrientationForMode(orientationMode)
        prefs.edit().putString("player_orientation", orientationMode).apply()
    }

    val immersivePlayback = shouldUseImmersivePlayback(fullScreen, selectedUri != null, isLandscape)

    LaunchedEffect(immersivePlayback) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (immersivePlayback) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun playMedia(uri: Uri, title: String, subtitle: Uri? = null) {
        fullScreen = false
        // Keep the user's saved Up Next list when opening a standalone file or reloading subtitles.
        selectedUri = uri
        selectedTitle = title
        val builder = MediaItem.Builder().setUri(uri).setMediaId(uri.toString())
        if (subtitle != null) {
            val name = subtitle.lastPathSegment.orEmpty().lowercase()
            val mime = if (name.endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(subtitle).setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
        }
        val resume = prefs.getLong(
            resumePositionKey(uri.toString()),
            if (prefs.getString(KEY_LAST_URI, null) == uri.toString()) prefs.getLong(KEY_LAST_POSITION, 0L) else 0L
        ).coerceAtLeast(0L)
        lastSavedPosition = resume
        resumePositions = if (resume > 0L) resumePositions + (uri.toString() to resume) else resumePositions - uri.toString()
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
        val playerAlreadyMirrorsQueue = playerMatchesQueue(player, queueItems)
        queueItems = updated
        if (player.mediaItemCount == 0) {
            playQueue(updated.lastIndex)
        } else if (queueItems.size == 2 && updated.firstOrNull()?.uri == player.currentMediaItem?.localConfiguration?.uri?.toString() && player.mediaItemCount == 1) {
            // Seed the actual playback timeline with the currently playing item plus the new item.
            player.addMediaItem(MediaItem.Builder().setUri(video.uri).setMediaId(entry.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).build()).build())
            status = "Added to queue: ${video.title}"
        } else if (playerAlreadyMirrorsQueue) {
            player.addMediaItem(MediaItem.Builder().setUri(video.uri).setMediaId(entry.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).build()).build())
            status = "Added to queue: ${video.title}"
        } else {
            status = "Saved to Up Next for the next queue playback: ${video.title}"
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

    val exportPlaylistsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(serializePlaylistsJson(playlists, JSONObject()
                        .put("themeMode", themeMode)
                        .put("playbackSpeed", playbackSpeed)
                        .put("skipIntervalSeconds", skipIntervalSeconds)
                        .put("orientationMode", orientationMode)
                        .put("keepScreenOn", keepScreenOn)
                        .put("gestureSeekEnabled", gestureSeekEnabled)
                        .put("gestureBrightnessEnabled", gestureBrightnessEnabled)
                        .put("gestureVolumeEnabled", gestureVolumeEnabled)
                        .put("subtitleScale", subtitleScale)
                        .put("subtitleBackground", subtitleBackgroundEnabled)
                        .put("subtitleDelayMs", subtitleDelayMs)
                        .put("shuffleEnabled", shuffleEnabled)
                        .put("repeatMode", repeatMode)))
                } ?: throw java.io.IOException("Could not open the selected destination.")
                status = "Exported ${playlists.size} playlists and player preferences"
            } catch (_: Exception) {
                status = "Playlist export failed. Choose another destination and try again."
            }
        }
    }

    val importPlaylistsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            uiScope.launch {
                try {
                    val raw = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: throw java.io.IOException("Could not read the selected file.")
                    }
                    val imported = parsePlaylistsJson(raw)
                    if (imported.isEmpty()) {
                        status = "No valid playlists were found in that backup."
                    } else {
                        val merged = playlists.toMutableList()
                        imported.forEach { incoming ->
                            val existingIndex = merged.indexOfFirst { it.name.equals(incoming.name, ignoreCase = true) }
                            if (existingIndex >= 0) merged[existingIndex] = incoming else merged.add(incoming)
                        }
                        playlists = merged
                        savePlaylists(prefs, merged)
                        val settings = parsePlayerPreferencesBackup(raw)
                        if (settings != null) {
                            themeMode = settings.optString("themeMode", themeMode)
                            playbackSpeed = settings.optDouble("playbackSpeed", playbackSpeed.toDouble()).toFloat()
                            skipIntervalSeconds = settings.optInt("skipIntervalSeconds", skipIntervalSeconds)
                            orientationMode = settings.optString("orientationMode", orientationMode)
                            keepScreenOn = settings.optBoolean("keepScreenOn", keepScreenOn)
                            gestureSeekEnabled = settings.optBoolean("gestureSeekEnabled", gestureSeekEnabled)
                            gestureBrightnessEnabled = settings.optBoolean("gestureBrightnessEnabled", gestureBrightnessEnabled)
                            gestureVolumeEnabled = settings.optBoolean("gestureVolumeEnabled", gestureVolumeEnabled)
                            subtitleScale = settings.optDouble("subtitleScale", subtitleScale.toDouble()).toFloat()
                            subtitleBackgroundEnabled = settings.optBoolean("subtitleBackground", subtitleBackgroundEnabled)
                            subtitleDelayMs = settings.optInt("subtitleDelayMs", subtitleDelayMs)
                            shuffleEnabled = settings.optBoolean("shuffleEnabled", shuffleEnabled)
                            repeatMode = settings.optInt("repeatMode", repeatMode)
                            prefs.edit()
                                .putString("theme_mode", themeMode)
                                .putFloat("playback_speed", playbackSpeed)
                                .putInt("skip_interval_seconds", skipIntervalSeconds)
                                .putString("player_orientation", orientationMode)
                                .putBoolean("keep_screen_on", keepScreenOn)
                                .putBoolean("gesture_seek_enabled", gestureSeekEnabled)
                                .putBoolean("gesture_brightness_enabled", gestureBrightnessEnabled)
                                .putBoolean("gesture_volume_enabled", gestureVolumeEnabled)
                                .putFloat("subtitle_scale", subtitleScale)
                                .putBoolean("subtitle_background", subtitleBackgroundEnabled)
                                .putInt("subtitle_delay_ms", subtitleDelayMs)
                                .putBoolean("shuffle_enabled", shuffleEnabled)
                                .putInt("repeat_mode", repeatMode)
                                .apply()
                        }
                        status = if (settings != null) {
                            "Restored ${imported.size} playlists and player preferences"
                        } else {
                            "Imported ${imported.size} playlists; no preferences found in this older backup"
                        }
                    }
                } catch (_: Exception) {
                    status = "Import failed. Choose a valid Nova Player playlist JSON backup."
                }
            }
        }
    }

    val libraryPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    val libraryPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            refreshLibrary()
        } else status = "Video access is needed to show all device videos. You can still browse and open a video."
    }

    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(libraryPermission) == PackageManager.PERMISSION_GRANTED) {
            refreshLibrary()
        } else {
            libraryPermissionLauncher.launch(libraryPermission)
        }
    }

    val canvasColor = if (themeMode == "AMOLED") Color.Black else Color(0xFF0A0E1A)
    Surface(Modifier.fillMaxSize(), color = canvasColor) {
        Column(Modifier.fillMaxSize().background(canvasColor)) {
            if (!fullScreen && !(selectedUri != null && isLandscape)) Row(
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
                    IconButton(onClick = { selectedUri = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to library", tint = Color.White) }
                } else {
                    IconButton(
                        enabled = !isScanningLibrary,
                        onClick = {
                            if (context.checkSelfPermission(libraryPermission) == PackageManager.PERMISSION_GRANTED) {
                                refreshLibrary()
                            } else libraryPermissionLauncher.launch(libraryPermission)
                        }
                    ) {
                        if (isScanningLibrary) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp).semantics { contentDescription = "Scanning library" }, color = Mint, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh library", tint = Color.White)
                        }
                    }
                    if (activeTab == "Videos") {
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort videos", tint = Color.White) }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }, modifier = Modifier.background(Panel)) {
                                listOf("Recently added", "Recently played", "Name A–Z", "Name Z–A", "Longest first", "Largest first").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = if (librarySort == option) Mint else Color.White) },
                                        leadingIcon = { if (librarySort == option) Icon(Icons.Default.Check, null, tint = Mint) else Icon(Icons.AutoMirrored.Filled.Sort, null, tint = Color(0xFF9BA8C7)) },
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
                        }) { Icon(if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, if (gridView) "Switch to list view" else "Switch to grid view", tint = Mint) }
                    }
                }
            }

            if (selectedUri == null && activeTab == "Videos") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = librarySearch, onValueChange = { librarySearch = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        label = { Text("Search your videos") },
                        placeholder = { Text("Title or filename", color = Color(0xFF6F7D9C)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Mint) },
                        trailingIcon = {
                            if (librarySearch.isNotEmpty()) {
                                IconButton(onClick = { librarySearch = "" }) {
                                    Icon(Icons.Default.Close, "Clear search", tint = Color(0xFF9BA8C7))
                                }
                            }
                        },
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
                val visibleVideos = remember(localVideos, librarySearch, selectedFolder, durationFilter, watchFilter, librarySort, recentItems, watchedUris) {
                    localVideos.filter { video ->
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
                        (video.title.contains(librarySearch, ignoreCase = true) || video.folder.contains(librarySearch, ignoreCase = true)) &&
                            (selectedFolder == null || video.folder == selectedFolder) &&
                            matchesDuration && matchesWatch
                    }.let { list ->
                        // Index playback history once; Compose remembers this result until
                        // a library/filter/history input actually changes.
                        val recentRank = recentItems.mapIndexed { index, entry -> entry.uri to index }.toMap()
                        when (librarySort) {
                            "Name A–Z" -> list.sortedBy { it.title.lowercase() }
                            "Name Z–A" -> list.sortedByDescending { it.title.lowercase() }
                            "Longest first" -> list.sortedByDescending { it.duration }
                            "Largest first" -> list.sortedByDescending { it.size }
                            "Recently played" -> list.sortedWith(compareBy<LocalVideo> { recentRank[it.uri.toString()] ?: Int.MAX_VALUE }.thenByDescending { it.dateAdded })
                            else -> list.sortedByDescending { it.dateAdded }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (visibleVideos.size == localVideos.size) "${visibleVideos.size} videos" else "${visibleVideos.size} of ${localVideos.size} videos",
                        color = Color(0xFF9BA8C7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    if (librarySearch.isNotBlank()) {
                        Text("SEARCHED", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    } else if (selectedFolder != null) {
                        Text("FOLDER FILTER", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    } else if (durationFilter != "All" || watchFilter != "All videos") {
                        Text("FILTERED", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                if (visibleVideos.isEmpty()) {
                    val hasActiveLibraryFilters = librarySearch.isNotBlank() || selectedFolder != null || durationFilter != "All" || watchFilter != "All videos"
                    Column(Modifier.fillMaxWidth().weight(1f).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(if (hasActiveLibraryFilters) Icons.Default.Search else Icons.Default.VideoLibrary, null, tint = Color(0xFF39E7D5), modifier = Modifier.size(58.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when {
                                hasActiveLibraryFilters -> "No videos match"
                                localVideos.isEmpty() -> "Your videos appear here"
                                else -> "No videos to show"
                            },
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                hasActiveLibraryFilters -> "Try another title or folder, or clear your filters to see more videos."
                                localVideos.isEmpty() -> "Scan your device to build your local library, or browse and open a single video."
                                else -> "Try changing the duration or watched filter."
                            },
                            color = Color(0xFF9BA8C7), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        if (hasActiveLibraryFilters) {
                            Button(onClick = {
                                librarySearch = ""
                                selectedFolder = null
                                durationFilter = "All"
                                watchFilter = "All videos"
                                prefs.edit().putString("library_duration_filter", "All").putString("library_watch_filter", "All videos").apply()
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39E7D5))) {
                                Icon(Icons.Default.Clear, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Clear search & filters")
                            }
                        } else {
                            Button(onClick = {
                                if (context.checkSelfPermission(libraryPermission) == PackageManager.PERMISSION_GRANTED) {
                                    refreshLibrary()
                                } else libraryPermissionLauncher.launch(libraryPermission)
                            }, enabled = !isScanningLibrary, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39E7D5))) {
                                if (isScanningLibrary) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Midnight, strokeWidth = 2.dp)
                                else Icon(Icons.Default.FolderOpen, null)
                                Spacer(Modifier.width(8.dp)); Text(if (isScanningLibrary) "Scanning…" else "Scan device videos")
                            }
                            TextButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) { Text("Browse files instead", color = Color(0xFF8A70FF)) }
                        }
                    }
                } else if (gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visibleVideos, key = { it.uri.toString() }) { video ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Panel),
                                onClick = { playMedia(video.uri, video.title) }
                            ) {
                                Column {
                                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF202A40)), contentAlignment = Alignment.Center) {
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
                                    val videoResumePosition = resumePositions[video.uri.toString()]
                                        ?: if (prefs.getString(KEY_LAST_URI, null) == video.uri.toString()) lastSavedPosition else 0L
                                    if (video.duration > 0L && videoResumePosition > 0L) {
                                        val resumeProgress = (videoResumePosition.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f)
                                        LinearProgressIndicator(
                                            progress = { resumeProgress },
                                            modifier = Modifier.fillMaxWidth().height(3.dp),
                                            color = Mint,
                                            trackColor = Color(0xFF29344A)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("RESUME ${formatTime(videoResumePosition)}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Spacer(Modifier.weight(1f))
                                            Text("${(resumeProgress * 100).toInt()}% watched", color = Color(0xFF9BA8C7), fontSize = 9.sp, maxLines = 1)
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
                                        IconButton(onClick = { enqueueMedia(video) }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue", tint = Lavender, modifier = Modifier.size(20.dp)) }
                                        IconButton(onClick = { selectedVideoInfo = video }) { Icon(Icons.Default.Info, "Video details", tint = Color(0xFF9BA8C7), modifier = Modifier.size(19.dp)) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        visibleVideos.forEach { video ->
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
                                    val videoResumePosition = resumePositions[video.uri.toString()]
                                        ?: if (prefs.getString(KEY_LAST_URI, null) == video.uri.toString()) lastSavedPosition else 0L
                                    if (video.duration > 0L && videoResumePosition > 0L) {
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { (videoResumePosition.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(3.dp),
                                            color = Mint,
                                            trackColor = Color(0xFF29344A)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("RESUME ${formatTime(videoResumePosition)}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Spacer(Modifier.weight(1f))
                                            Text("${((lastSavedPosition.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f) * 100).toInt()}% watched", color = Color(0xFF9BA8C7), fontSize = 9.sp, maxLines = 1)
                                        }
                                    }
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
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add ${video.title} to queue", tint = Color(0xFF8A70FF), modifier = Modifier.size(26.dp))
                            }
                            IconButton(onClick = { playMedia(video.uri, video.title) }) {
                                    Icon(Icons.Default.PlayCircle, "Play ${video.title}", tint = Color(0xFF39E7D5), modifier = Modifier.size(30.dp))
                                }
                            }
                            HorizontalDivider(color = Color(0xFF202A40), thickness = 1.dp)
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
                        HorizontalDivider(color = Color(0xFF202A40))
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
                        IconButton(onClick = { importPlaylistsLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }) {
                            Icon(Icons.Default.FileDownload, "Import playlist backup", tint = Mint)
                        }
                        IconButton(onClick = { exportPlaylistsLauncher.launch("nova-playlists-backup.json") }, enabled = playlists.isNotEmpty()) {
                            Icon(Icons.Default.FileUpload, "Export playlist backup", tint = if (playlists.isNotEmpty()) Mint else Color(0xFF46516A))
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
                                        val existingUris = queueItems.map { it.uri }.toSet()
                                        val additions = playlist.items.filterNot { it.uri in existingUris }
                                        val wasMirrored = playerMatchesQueue(player, queueItems)
                                        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                                        val currentTitle = selectedTitle.ifBlank { player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty() }
                                        val seedCurrent = queueItems.isEmpty() && currentUri != null
                                        val updated = if (seedCurrent) {
                                            listOf(HistoryEntry(currentUri!!, currentTitle.ifBlank { Uri.parse(currentUri!!).lastPathSegment ?: "Now playing" })) +
                                                additions.filterNot { it.uri == currentUri }
                                        } else queueItems + additions
                                        queueItems = updated
                                        if (player.mediaItemCount == 0 && updated.isNotEmpty()) {
                                            playQueue(0)
                                        } else if (wasMirrored || seedCurrent) {
                                            additions.filterNot { it.uri == currentUri }.forEach { entry ->
                                                player.addMediaItem(
                                                    MediaItem.Builder().setUri(Uri.parse(entry.uri)).setMediaId(entry.uri)
                                                        .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).build()).build()
                                                )
                                            }
                                            status = "Added ${additions.size} new playlist videos to the active queue"
                                        } else {
                                            status = "Saved ${additions.size} new playlist videos for the next queue playback"
                                        }
                                    }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(Modifier.width(5.dp)); Text("Add to queue")
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
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Mint)
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
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Lavender, modifier = Modifier.size(46.dp))
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
                                    val mirrorsPlayerQueue = playerMatchesQueue(player, queueItems)
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
                                val mirrorsPlayerQueue = playerMatchesQueue(player, queueItems)
                                queueItems = queueItems.filterIndexed { i, _ -> i != index }
                                // Never remove an unrelated timeline item when a standalone video is playing.
                                if (mirrorsPlayerQueue && index < player.mediaItemCount) {
                                    player.removeMediaItem(index)
                                }
                                if (wasCurrent && queueItems.isEmpty() && mirrorsPlayerQueue) {
                                    selectedUri = null
                                    selectedTitle = ""
                                }
                                status = if (mirrorsPlayerQueue) "Removed from queue" else "Removed from saved Up Next list"
                            }) { Icon(Icons.Default.Close, "Remove ${item.title} from queue", tint = Color(0xFF9BA8C7)) }
                        }
                        HorizontalDivider(color = Color(0xFF202A40))
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
                        HorizontalDivider(color = Color(0xFF202A40))
                    }
                }
            }

            if (selectedUri == null && activeTab == "Settings") {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("PERSONALIZE NOVA", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Preferences stay on this device. Existing playback controls remain unchanged.", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Appearance", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Choose the player canvas", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Midnight", "AMOLED").forEach { option ->
                                    FilterChip(
                                        selected = themeMode == option,
                                        onClick = {
                                            themeMode = option
                                            prefs.edit().putString("theme_mode", option).apply()
                                            status = "$option appearance saved"
                                        },
                                        label = { Text(option) },
                                        leadingIcon = { Icon(if (option == "AMOLED") Icons.Default.DarkMode else Icons.Default.Palette, null) }
                                    )
                                }
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Playback", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Keep screen awake", color = Color.White)
                                    Text("Prevent display timeout while watching", color = Color(0xFF9BA8C7), fontSize = 11.sp)
                                }
                                Switch(checked = keepScreenOn, onCheckedChange = {
                                    keepScreenOn = it
                                    prefs.edit().putBoolean("keep_screen_on", it).apply()
                                })
                            }
                            Text("Default orientation", color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Auto", "Landscape", "Portrait").forEach { option ->
                                    FilterChip(
                                        selected = orientationMode == option,
                                        onClick = {
                                            orientationMode = option
                                            prefs.edit().putString("player_orientation", option).apply()
                                            status = "Default orientation: $option"
                                        },
                                        label = { Text(option) }
                                    )
                                }
                            }
                            Text("Double-tap seek interval", color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(5, 10, 15, 30).forEach { seconds ->
                                    FilterChip(
                                        selected = skipIntervalSeconds == seconds,
                                        onClick = {
                                            skipIntervalSeconds = seconds
                                            prefs.edit().putInt("skip_interval_seconds", seconds).apply()
                                        },
                                        label = { Text("${seconds}s") }
                                    )
                                }
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Gesture controls", color = Color.White, fontWeight = FontWeight.SemiBold)
                            GestureSettingRow("Swipe to seek", "Horizontal swipes move through the video", gestureSeekEnabled) {
                                gestureSeekEnabled = it
                                prefs.edit().putBoolean("gesture_seek_enabled", it).apply()
                            }
                            GestureSettingRow("Brightness gesture", "Vertical swipe on the left side", gestureBrightnessEnabled) {
                                gestureBrightnessEnabled = it
                                prefs.edit().putBoolean("gesture_brightness_enabled", it).apply()
                            }
                            GestureSettingRow("Volume gesture", "Vertical swipe on the right side", gestureVolumeEnabled) {
                                gestureVolumeEnabled = it
                                prefs.edit().putBoolean("gesture_volume_enabled", it).apply()
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Troubleshooting", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Inspect the current playback state, active video format, resolution and track codecs.", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                            Button(onClick = { showDiagnosticsDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.MonitorHeart, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open playback diagnostics")
                            }
                        }
                    }
                }
            }

            if (selectedUri != null) {
                Card(
                    modifier = (if (fullScreen || isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth().weight(1f))
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || selectedUri == null) {
                                false
                            } else {
                                when (event.key) {
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                                        if (player.isPlaying) player.pause() else player.play()
                                        status = if (player.isPlaying) "Playing" else "Paused"
                                        true
                                    }
                                    Key.DirectionLeft, Key.MediaRewind -> {
                                        player.seekTo(clampSeekPosition(player.currentPosition, -skipIntervalSeconds * 1_000L, player.duration))
                                        status = "Rewound $skipIntervalSeconds seconds"
                                        true
                                    }
                                    Key.DirectionRight, Key.MediaFastForward -> {
                                        player.seekTo(clampSeekPosition(player.currentPosition, skipIntervalSeconds * 1_000L, player.duration))
                                        status = "Skipped forward $skipIntervalSeconds seconds"
                                        true
                                    }
                                    else -> false
                                }
                            }
                        }
                        .focusable(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = if (fullScreen || isLandscape) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = playerZoomScale
                                    scaleY = playerZoomScale
                                }
                                .pointerInput(controlsLocked) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        var previousDistance = 0f
                                        do {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.size >= 2) {
                                                val distance = (pressed[0].position - pressed[1].position).getDistance()
                                                if (previousDistance > 0f && !controlsLocked && distance > 0f) {
                                                    val factor = distance / previousDistance
                                                    playerZoomScale = (playerZoomScale * factor).coerceIn(1f, 2.5f)
                                                    if (playerZoomScale > 1.01f) {
                                                        status = "Pinch zoom " + "%.1f".format(playerZoomScale) + "×"
                                                    }
                                                    event.changes.forEach { it.consume() }
                                                }
                                                previousDistance = distance
                                            } else {
                                                previousDistance = 0f
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                                .pointerInput(selectedUri, controlsLocked) {
                                    detectTapGestures(
                                        onDoubleTap = { offset ->
                                            if (!controlsLocked && selectedUri != null) {
                                                val forward = offset.x >= size.width / 2f
                                                val duration = player.duration
                                                val target = clampSeekPosition(
                                                    player.currentPosition,
                                                    if (forward) skipIntervalSeconds * 1_000L else -skipIntervalSeconds * 1_000L,
                                                    duration
                                                )
                                                player.seekTo(target)
                                                status = (if (forward) "Forward " else "Rewind ") + "$skipIntervalSeconds seconds"
                                            }
                                        }
                                    )
                                }
                                .pointerInput(selectedUri, controlsLocked, gestureSeekEnabled, gestureBrightnessEnabled, gestureVolumeEnabled) {
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
                                        seekPreviewDeltaMs = 0L
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
                                            "horizontal" -> {
                                                isSeekingGesture = true
                                                seekPreviewDeltaMs = (totalX / 8f).toLong() * 1_000L
                                            }
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
                                                val target = (player.currentPosition + delta)
                                                    .coerceAtLeast(0L)
                                                    .let { if (player.duration > 0L) it.coerceAtMost(player.duration) else it }
                                                player.seekTo(target)
                                                status = (if (delta > 0) "Skipped forward " else "Skipped back ") +
                                                    (abs(delta) / 1000) + "s · " + formatTime(target)
                                            }
                                        }
                                        isSeekingGesture = false
                                        seekPreviewDeltaMs = 0L
                                        gestureAxis = "none"
                                    },
                                    onDragCancel = { isSeekingGesture = false; seekPreviewDeltaMs = 0L; gestureAxis = "none" }
                                )
                            },
                            factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = !controlsLocked; setShowSubtitleButton(true); resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                            update = { view ->
                                view.player = player
                                view.useController = !controlsLocked
                                view.resizeMode = when (aspectMode) {
                                    "Fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    "Zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    "Auto" -> if (isLandscape && videoAspectRatio > 1.0f) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
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
                        // Landscape quick controls: keep advanced playback actions reachable
                        // without leaving the immersive video surface.
                        if (isLandscape && !controlsLocked && showLandscapeDock) {
                            Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                color = Color(0xE6101829),
                                shape = RoundedCornerShape(22.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Mint.copy(alpha = 0.32f))
                            ) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    TextButton(onClick = { showPlaybackSpeedDialog = true }) {
                                        Icon(Icons.Default.Speed, contentDescription = null, tint = Mint)
                                        Spacer(Modifier.width(4.dp))
                                        Text("${playbackSpeed}×", color = Color.White)
                                    }
                                    TextButton(onClick = {
                                        aspectMode = when (aspectMode) { "Auto" -> "Fit"; "Fit" -> "Fill"; "Fill" -> "Zoom"; else -> "Auto" }
                                        prefs.edit().putString("player_aspect_mode", aspectMode).apply()
                                        status = "Screen mode: " + aspectMode
                                    }) {
                                        Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Mint)
                                        Spacer(Modifier.width(4.dp))
                                        Text(aspectMode, color = Color.White)
                                    }
                                    TextButton(onClick = { subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain")) }) {
                                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = Mint)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Subtitles", color = Color.White)
                                    }
                                    TextButton(onClick = {
                                        try { TrackSelectionDialogBuilder(context, "Audio tracks", player, C.TRACK_TYPE_AUDIO).build().show() }
                                        catch (_: Exception) { status = "Audio track selection is unavailable for this media." }
                                    }) {
                                        Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Mint)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Audio", color = Color.White)
                                    }
                                    TextButton(onClick = {
                                        val uri = selectedUri?.toString()
                                        if (uri == null) {
                                            status = "Play a video before changing favorites."
                                        } else {
                                            val alreadyFavorite = favoriteItems.any { it.uri == uri }
                                            favoriteItems = if (alreadyFavorite) favoriteItems.filterNot { it.uri == uri }
                                                else listOf(HistoryEntry(uri, selectedTitle.ifBlank { "Local video" })) + favoriteItems
                                            saveFavorites(prefs, favoriteItems)
                                            status = if (alreadyFavorite) "Removed from favorites" else "Added to favorites"
                                        }
                                    }) {
                                        Icon(
                                            if (favoriteItems.any { it.uri == selectedUri?.toString() }) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Toggle favorite",
                                            tint = if (favoriteItems.any { it.uri == selectedUri?.toString() }) Color(0xFFFF6B9D) else Color.White
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Favorite", color = if (favoriteItems.any { it.uri == selectedUri?.toString() }) Color(0xFFFF6B9D) else Color.White)
                                    }
                                    TextButton(onClick = {
                                        val start = abLoopStartMs
                                        val end = abLoopEndMs
                                        if (start == null) {
                                            abLoopStartMs = player.currentPosition.coerceAtLeast(0L)
                                            abLoopEndMs = null
                                            status = "A–B loop point A set at " + formatTime(player.currentPosition)
                                        } else if (end == null) {
                                            val current = player.currentPosition.coerceAtLeast(0L)
                                            if (current > start) {
                                                abLoopEndMs = current
                                                status = "A–B loop enabled: " + formatTime(start) + " – " + formatTime(current)
                                            } else {
                                                status = "Point B must be after point A"
                                            }
                                        } else {
                                            abLoopStartMs = null
                                            abLoopEndMs = null
                                            status = "A–B loop cleared"
                                        }
                                    }) {
                                        Icon(Icons.Default.RepeatOne, contentDescription = "Set or clear A-B loop", tint = if (abLoopStartMs != null) Lavender else Color.White)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            when {
                                                abLoopStartMs == null -> "Set A"
                                                abLoopEndMs == null -> "Set B"
                                                else -> "Clear A–B"
                                            },
                                            color = if (abLoopStartMs != null) Lavender else Color.White
                                        )
                                    }
                                    TextButton(onClick = {
                                        shuffleEnabled = !shuffleEnabled
                                        player.shuffleModeEnabled = shuffleEnabled
                                        prefs.edit().putBoolean("shuffle_enabled", shuffleEnabled).apply()
                                        status = if (shuffleEnabled) "Shuffle enabled" else "Shuffle disabled"
                                    }) {
                                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = if (shuffleEnabled) Mint else Color.White)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Shuffle", color = if (shuffleEnabled) Mint else Color.White)
                                    }
                                    TextButton(onClick = {
                                        repeatMode = when (repeatMode) {
                                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                            else -> Player.REPEAT_MODE_OFF
                                        }
                                        player.repeatMode = repeatMode
                                        prefs.edit().putInt("repeat_mode", repeatMode).apply()
                                        status = when (repeatMode) {
                                            Player.REPEAT_MODE_ALL -> "Repeat all enabled"
                                            Player.REPEAT_MODE_ONE -> "Repeat current video enabled"
                                            else -> "Repeat disabled"
                                        }
                                    }) {
                                        Icon(
                                            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                            contentDescription = null,
                                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) Mint else Color.White
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            when (repeatMode) {
                                                Player.REPEAT_MODE_ALL -> "Repeat all"
                                                Player.REPEAT_MODE_ONE -> "Repeat one"
                                                else -> "Repeat off"
                                            },
                                            color = if (repeatMode != Player.REPEAT_MODE_OFF) Mint else Color.White
                                        )
                                    }
                                    TextButton(onClick = { showSleepTimerDialog = true }) {
                                        Icon(Icons.Default.Bedtime, contentDescription = null, tint = if (sleepTimerActive) Mint else Color.White)
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (sleepTimerActive) "Sleep on" else "Sleep timer", color = if (sleepTimerActive) Mint else Color.White)
                                    }
                                    TextButton(onClick = {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.findActivity()?.enterPictureInPictureMode(
                                                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                                                )
                                            } else status = "Picture-in-Picture requires Android 8.0 or newer."
                                        } catch (_: Exception) { status = "Picture-in-Picture could not be started on this device." }
                                    }) {
                                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, tint = Mint)
                                        Spacer(Modifier.width(4.dp))
                                        Text("PiP", color = Color.White)
                                    }
                                    IconButton(onClick = {
                                        controlsLocked = true
                                        status = "Player controls locked · Tap Unlock controls to restore"
                                    }) {
                                        Icon(Icons.Default.Lock, contentDescription = "Lock player controls", tint = Mint)
                                    }
                                    IconButton(onClick = {
                                        showLandscapeDock = false
                                        status = "Playback dock hidden · Tap the controls icon to show it"
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Hide playback dock", tint = Color.White)
                                    }
                                    IconButton(onClick = {
                                        fullScreen = !fullScreen
                                        status = if (fullScreen) "Immersive full-screen enabled" else "Immersive full-screen disabled"
                                    }) {
                                        Icon(
                                            if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = if (fullScreen) "Exit immersive mode" else "Enter immersive mode",
                                            tint = Mint
                                        )
                                    }
                                }
                            }
                        }
                        if (isLandscape && !controlsLocked && !showLandscapeDock) {
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                color = Color(0xE6101829),
                                shape = RoundedCornerShape(50),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Mint.copy(alpha = 0.5f))
                            ) {
                                IconButton(onClick = {
                                    showLandscapeDock = true
                                    status = "Playback dock shown"
                                }) {
                                    Icon(Icons.Default.Tune, contentDescription = "Show playback controls", tint = Mint)
                                }
                            }
                        }
                        if (playerZoomScale > 1.01f && !controlsLocked) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                                color = Color(0xE6101829),
                                shape = RoundedCornerShape(50),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Lavender.copy(alpha = 0.7f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Zoom " + "%.1f".format(playerZoomScale) + "×", color = Color.White, fontSize = 12.sp)
                                    TextButton(onClick = { playerZoomScale = 1f; status = "Pinch zoom reset" }) {
                                        Text("Reset", color = Mint)
                                    }
                                }
                            }
                        }
                        if (isSeekingGesture && !controlsLocked) {
                            val previewPosition = clampSeekPosition(player.currentPosition, seekPreviewDeltaMs, player.duration)
                            Surface(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color(0xE6101829),
                                shape = RoundedCornerShape(18.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Mint.copy(alpha = 0.55f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (seekPreviewBitmap != null) {
                                        Image(
                                            bitmap = seekPreviewBitmap!!.asImageBitmap(),
                                            contentDescription = "Video frame preview",
                                            modifier = Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            if (seekPreviewDeltaMs >= 0L) Icons.Default.FastForward else Icons.Default.FastRewind,
                                            contentDescription = null,
                                            tint = Mint,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            (if (seekPreviewDeltaMs >= 0L) "+" else "−") + formatTime(abs(seekPreviewDeltaMs)),
                                            color = Mint,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                        Text(
                                            formatTime(previewPosition) + if (player.duration > 0L) " / " + formatTime(player.duration) else "",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
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
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentUri = selectedUri?.toString()
                            val isFavorite = currentUri != null && favoriteItems.any { it.uri == currentUri }
                            IconButton(
                                onClick = {
                                    if (currentUri != null) {
                                        favoriteItems = if (isFavorite) {
                                            favoriteItems.filterNot { it.uri == currentUri }
                                        } else {
                                            listOf(HistoryEntry(currentUri, selectedTitle)) + favoriteItems
                                        }
                                        saveFavorites(prefs, favoriteItems)
                                        status = if (isFavorite) "Removed from favorites" else "Added to favorites"
                                    }
                                },
                                modifier = Modifier.background(Panel.copy(alpha = 0.85f), RoundedCornerShape(50))
                            ) {
                                Icon(
                                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) Color(0xFFFF6B8A) else Color.White
                                )
                            }
                            val isQueued = currentUri != null && queueItems.any { it.uri == currentUri }
                            IconButton(
                                onClick = {
                                    if (currentUri != null && !isQueued) {
                                        queueItems = queueItems + HistoryEntry(currentUri, selectedTitle)
                                        status = "Added to play queue"
                                    }
                                },
                                enabled = currentUri != null && !isQueued,
                                modifier = Modifier.background(Panel.copy(alpha = 0.85f), RoundedCornerShape(50))
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = if (isQueued) "Already in queue" else "Add to play queue",
                                    tint = if (isQueued) Mint else Color.White
                                )
                            }
                            if (fullScreen || isLandscape) {
                                IconButton(
                                    onClick = {
                                        if (fullScreen) {
                                            fullScreen = false
                                            status = "Immersive full-screen disabled"
                                        } else {
                                            orientationMode = "Portrait"
                                            status = "Returned to portrait playback"
                                        }
                                    },
                                    modifier = Modifier.background(Panel.copy(alpha = 0.85f), RoundedCornerShape(50))
                                ) {
                                    Icon(Icons.Default.FullscreenExit, "Exit full screen", tint = Mint)
                                }
                            }
                        }
                        }
                        if (!fullScreen && !isLandscape && !controlsLocked) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    player.seekToPreviousMediaItem()
                                    status = "Previous video"
                                },
                                enabled = player.hasPreviousMediaItem(),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF202A40), contentColor = Color.White, disabledContentColor = Color(0xFF65718B)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Previous")
                            }
                            FilledTonalButton(
                                onClick = {
                                    player.seekToNextMediaItem()
                                    status = "Next video"
                                },
                                enabled = player.hasNextMediaItem(),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF202A40), contentColor = Color.White, disabledContentColor = Color(0xFF65718B)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Next")
                            }
                            FilledTonalButton(
                                onClick = {
                                    player.seekTo((player.currentPosition - skipIntervalSeconds * 1_000L).coerceAtLeast(0L))
                                    status = "Rewind $skipIntervalSeconds seconds"
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF202A40), contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text("↶  ${skipIntervalSeconds}s", fontWeight = FontWeight.SemiBold) }
                            FilledTonalButton(
                                onClick = {
                                    val target = player.currentPosition + skipIntervalSeconds * 1_000L
                                    player.seekTo(if (player.duration > 0L) target.coerceAtMost(player.duration) else target)
                                    status = "Forward $skipIntervalSeconds seconds"
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF202A40), contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text("${skipIntervalSeconds}s  ↷", fontWeight = FontWeight.SemiBold) }
                            FilledTonalButton(
                                onClick = { showSkipIntervalDialog = true },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF202A40), contentColor = Mint),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) { Text("Skip ${skipIntervalSeconds}s", fontWeight = FontWeight.SemiBold) }
                            FilledTonalButton(
                                onClick = { showPlaybackSpeedDialog = true },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF202A40), contentColor = Color(0xFF8A70FF)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text("Speed " + playbackSpeed.toString() + "×", fontWeight = FontWeight.Bold) }
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
                                keepScreenOn = !keepScreenOn
                                prefs.edit().putBoolean("keep_screen_on", keepScreenOn).apply()
                                status = if (keepScreenOn) "Screen stays awake during playback" else "Screen may sleep during playback"
                            }) {
                                Icon(if (keepScreenOn) Icons.Default.BrightnessHigh else Icons.Default.BrightnessLow, null, tint = if (keepScreenOn) Mint else Lavender)
                                Text(if (keepScreenOn) "Keep awake" else "Allow sleep", color = if (keepScreenOn) Mint else Color.White)
                            }
                            TextButton(onClick = {
                                aspectMode = when (aspectMode) { "Auto" -> "Fit"; "Fit" -> "Fill"; "Fill" -> "Zoom"; else -> "Auto" }
                                prefs.edit().putString("player_aspect_mode", aspectMode).apply()
                                status = "Screen mode: " + aspectMode
                            }) { Icon(Icons.Default.AspectRatio, null, tint = Lavender); Text(aspectMode) }
                            TextButton(onClick = {
                                orientationMode = when (orientationMode) {
                                    "Auto" -> "Landscape"
                                    "Landscape" -> "Portrait"
                                    else -> "Auto"
                                }
                                status = "Player orientation: " + orientationMode
                            }) {
                                Icon(Icons.Default.ScreenRotation, null, tint = if (orientationMode == "Auto") Lavender else Mint)
                                Text(orientationMode, color = if (orientationMode == "Auto") Color.White else Mint)
                            }
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
                if (!fullScreen && !isLandscape && !controlsLocked) {
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

            if (selectedUri == null) Text(status, color = if (status.startsWith("Use") || status.startsWith("Choose an") || status.startsWith("Start a")) Color(0xFFFFB4AB) else Color(0xFFBAC6DF), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF12182A)).semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 14.dp, vertical = 8.dp))
            if (selectedUri == null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFF12182A)).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Videos", "Folders", "Recent", "Favorites", "Queue", "Playlists", "Settings").forEach { tab ->
                    TextButton(onClick = { activeTab = tab }, modifier = Modifier.weight(1f).semantics { stateDescription = if (activeTab == tab) "Selected" else "Not selected" }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(when (tab) { "Videos" -> Icons.Default.VideoLibrary; "Folders" -> Icons.Default.Folder; "Favorites" -> Icons.Default.Favorite; "Queue" -> Icons.AutoMirrored.Filled.QueueMusic; "Playlists" -> Icons.Default.LibraryMusic; "Settings" -> Icons.Default.Settings; "Recent" -> Icons.Default.History; else -> Icons.Default.History }, null, tint = if (activeTab == tab) Color(0xFF39E7D5) else Color(0xFF6F7D9C))
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
        if (showSkipIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showSkipIntervalDialog = false },
            containerColor = Panel,
            title = { Text("Seek interval", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose how far the player skips when you tap the rewind or forward controls, or double-tap the video.", color = Color(0xFFBAC6DF), fontSize = 13.sp)
                    listOf(5, 10, 15, 30).forEach { seconds ->
                        Row(
                            Modifier.fillMaxWidth().background(
                                if (skipIntervalSeconds == seconds) Mint.copy(alpha = 0.14f) else Color(0xFF202A40),
                                RoundedCornerShape(10.dp)
                            ).padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    skipIntervalSeconds = seconds
                                    prefs.edit().putInt("skip_interval_seconds", seconds).apply()
                                    showSkipIntervalDialog = false
                                    status = "Seek interval set to ${seconds} seconds"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${seconds} seconds", color = if (skipIntervalSeconds == seconds) Mint else Color.White, fontWeight = FontWeight.SemiBold)
                            }
                            if (skipIntervalSeconds == seconds) Icon(Icons.Default.CheckCircle, null, tint = Mint, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSkipIntervalDialog = false }) { Text("Done", color = Mint) }
            }
        )
    }

    if (showPlaybackSpeedDialog) {
            val speedPresets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
            AlertDialog(
                onDismissRequest = { showPlaybackSpeedDialog = false },
                title = { Text("Playback speed") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose a precise speed for movies, lectures or language practice.", color = Color(0xFF9BA8C7), fontSize = 12.sp)
                        speedPresets.chunked(3).forEach { rowSpeeds ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowSpeeds.forEach { speed ->
                                    FilterChip(
                                        selected = playbackSpeed == speed,
                                        onClick = {
                                            playbackSpeed = speed
                                            player.setPlaybackSpeed(speed)
                                            showPlaybackSpeedDialog = false
                                            status = "Playback speed: ${speed}×"
                                        },
                                        label = { Text(if (speed == 1.0f) "1×" else "${speed}×", fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Mint.copy(alpha = 0.18f),
                                            selectedLabelColor = Mint,
                                            labelColor = Color.White
                                        )
                                    )
                                }
                                repeat(3 - rowSpeeds.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Text("Current speed: ${playbackSpeed}×", color = Mint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaybackSpeedDialog = false }) { Text("Done", color = Mint) }
                },
                containerColor = Panel,
                titleContentColor = Color.White,
                textContentColor = Color.White
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
        if (showDiagnosticsDialog) {
            val activeItem = player.currentMediaItem
            val formats = player.currentTracks.groups.flatMap { group ->
                (0 until group.length)
                    .filter { index -> group.isTrackSelected(index) }
                    .map { index -> group.getTrackFormat(index) }
            }
            val videoFormats = formats.filter { it.sampleMimeType?.startsWith("video/") == true }
            val audioFormats = formats.filter { it.sampleMimeType?.startsWith("audio/") == true }
            AlertDialog(
                onDismissRequest = { showDiagnosticsDialog = false },
                title = { Text("Playback diagnostics", fontWeight = FontWeight.Bold) },
                text = {
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(activeItem?.mediaMetadata?.title?.toString() ?: selectedTitle.ifBlank { "No media selected" }, color = Mint, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = Color(0xFF35415B))
                        listOf(
                            "State" to playbackStateLabel(player.playbackState),
                            "Playing" to player.isPlaying.toString(),
                            "Play when ready" to player.playWhenReady.toString(),
                            "Position" to formatTime(player.currentPosition),
                            "Duration" to if (player.duration > 0L) formatTime(player.duration) else "Unknown / live",
                            "Video format" to (videoFormats.firstOrNull()?.let { it.sampleMimeType ?: it.codecs ?: "Unknown codec" } ?: "No active video track"),
                            "Resolution" to (videoFormats.firstOrNull()?.let { if (it.width > 0 && it.height > 0) "${it.width} × ${it.height}" else "Not reported by decoder" } ?: "Not available"),
                            "Frame rate" to (videoFormats.firstOrNull()?.let { if (it.frameRate > 0f) "%.2f fps".format(it.frameRate) else "Not reported" } ?: "Not available"),
                            "Video codecs" to (videoFormats.mapNotNull { it.codecs ?: it.sampleMimeType }.distinct().joinToString().ifBlank { "Not reported" }),
                            "Audio codecs" to (audioFormats.mapNotNull { it.codecs ?: it.sampleMimeType }.distinct().joinToString().ifBlank { "No active audio track" }),
                            "Tracks detected" to formats.size.toString(),
                            "Last status" to status
                        ).forEach { (label, value) ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(label, color = Color(0xFF9BA8C7), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(value, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDiagnosticsDialog = false }) { Text("Close", color = Mint) } },
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
                        Text("Quick presets", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("Compact" to 0.045f, "Standard" to 0.065f, "Large" to 0.085f).forEach { (label, size) ->
                                FilterChip(
                                    selected = kotlin.math.abs(subtitleScale - size) < 0.001f,
                                    onClick = { subtitleScale = size },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Lavender.copy(alpha = 0.25f),
                                        selectedLabelColor = Mint,
                                        labelColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
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
private fun GestureSettingRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White)
            Text(description, color = Color(0xFF9BA8C7), fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
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

private fun playerMatchesQueue(player: Player, entries: List<HistoryEntry>): Boolean {
    if (player.mediaItemCount != entries.size) return false
    return entries.indices.all { index ->
        player.getMediaItemAt(index).localConfiguration?.uri?.toString() == entries[index].uri
    }
}

internal fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val seconds = totalSeconds % 60L
    val totalMinutes = totalSeconds / 60L
    val minutes = totalMinutes % 60L
    val hours = totalSeconds / 3_600L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(totalMinutes, seconds)
    }
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

internal data class HistoryEntry(val uri: String, val title: String)
internal data class PlaylistEntry(val name: String, val items: List<HistoryEntry>)

internal fun serializePlaylistsJson(playlists: List<PlaylistEntry>, preferences: JSONObject = JSONObject()): String {
    val root = JSONObject()
        .put("format", "nova-player-playlists")
        .put("version", 1)
        .put("preferences", sanitizePlayerPreferences(preferences))
    val array = JSONArray()
    playlists.forEach { playlist ->
        val items = JSONArray()
        playlist.items.forEach { item ->
            items.put(JSONObject().put("uri", item.uri).put("title", item.title))
        }
        array.put(JSONObject().put("name", playlist.name).put("items", items))
    }
    root.put("playlists", array)
    return root.toString(2)
}

internal fun sanitizePlayerPreferences(source: JSONObject): JSONObject {
    val result = JSONObject()
    val theme = source.optString("themeMode", "Midnight")
    result.put("themeMode", if (theme == "AMOLED") "AMOLED" else "Midnight")
    val speed = source.optDouble("playbackSpeed", 1.0).takeIf { it.isFinite() && it in 0.25..4.0 } ?: 1.0
    result.put("playbackSpeed", speed)
    result.put("skipIntervalSeconds", source.optInt("skipIntervalSeconds", 10).coerceIn(5, 30))
    val orientation = source.optString("orientationMode", "Auto")
    result.put("orientationMode", if (orientation in setOf("Auto", "Landscape", "Portrait")) orientation else "Auto")
    listOf("keepScreenOn", "gestureSeekEnabled", "gestureBrightnessEnabled", "gestureVolumeEnabled", "subtitleBackground", "shuffleEnabled").forEach { key ->
        result.put(key, source.optBoolean(key, key != "shuffleEnabled"))
    }
    result.put("subtitleScale", source.optDouble("subtitleScale", 0.065).takeIf { it.isFinite() && it in 0.03..0.12 } ?: 0.065)
    result.put("subtitleDelayMs", source.optInt("subtitleDelayMs", 0).coerceIn(-120_000, 120_000))
    result.put("repeatMode", source.optInt("repeatMode", Player.REPEAT_MODE_OFF).coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL))
    return result
}

internal fun parsePlayerPreferencesBackup(raw: String): JSONObject? {
    return try {
        require(raw.length <= 5_000_000) { "Backup file is too large" }
        val root = JSONObject(raw)
        require(root.optString("format") == "nova-player-playlists") { "Unknown backup format" }
        require(root.optInt("version", -1) == 1) { "Unsupported backup version" }
        val settings = root.optJSONObject("preferences") ?: return null
        sanitizePlayerPreferences(settings)
    } catch (_: Exception) {
        null
    }
}

internal fun parsePlaylistsJson(raw: String): List<PlaylistEntry> {
    require(raw.length <= 5_000_000) { "Backup file is too large" }
    val root = JSONObject(raw)
    require(root.optString("format") == "nova-player-playlists") { "Unknown backup format" }
    require(root.optInt("version", -1) == 1) { "Unsupported backup version" }
    val array = root.optJSONArray("playlists") ?: throw IllegalArgumentException("Missing playlists")
    require(array.length() <= 2_000) { "Too many playlists" }
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val name = item.optString("name").trim().take(120)
        val sourceItems = item.optJSONArray("items") ?: JSONArray()
        if (name.isBlank() || sourceItems.length() > 20_000) return@mapNotNull null
        val entries = (0 until sourceItems.length()).mapNotNull { itemIndex ->
            val entry = sourceItems.optJSONObject(itemIndex) ?: return@mapNotNull null
            val uri = entry.optString("uri").trim()
            val title = entry.optString("title").trim().take(500)
            if (uri.isBlank() || title.isBlank()) null else HistoryEntry(uri, title)
        }
        PlaylistEntry(name, entries)
    }
}

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
