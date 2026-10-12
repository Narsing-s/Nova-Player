package com.narsing.novaplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.json.JSONObject
import org.junit.Test

class PlaylistBackupTest {
    @Test
    fun roundTripsPlaylistNamesAndItems() {
        val original = listOf(
            PlaylistEntry(
                "Movie night",
                listOf(
                    HistoryEntry("content://media/video/1", "Movie One.mp4"),
                    HistoryEntry("content://media/video/2", "Movie Two.mp4")
                )
            ),
            PlaylistEntry("Empty list", emptyList())
        )

        val restored = parsePlaylistsJson(serializePlaylistsJson(original))

        assertEquals(original, restored)
    }

    @Test
    fun rejectsUnknownBackupFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePlaylistsJson("""{"format":"other","version":1,"playlists":[]}""")
        }
    }

    @Test
    fun rejectsUnsupportedBackupVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePlaylistsJson("""{"format":"nova-player-playlists","version":2,"playlists":[]}""")
        }
    }

    @Test
    fun ignoresMalformedEntriesAndTrimsNames() {
        val raw = """{
            "format":"nova-player-playlists",
            "version":1,
            "playlists":[
                {"name":"  Favorites  ","items":[
                    {"uri":"content://media/video/1","title":" Clip.mp4 "},
                    {"uri":"","title":"Invalid"}
                ]},
                {"name":"  ","items":[]},
                null
            ]
        }"""

        assertEquals(
            listOf(PlaylistEntry("Favorites", listOf(HistoryEntry("content://media/video/1", "Clip.mp4")))),
            parsePlaylistsJson(raw)
        )
    }
    @Test
    fun backupRoundTripsValidatedPlayerPreferences() {
        val backup = serializePlaylistsJson(
            listOf(PlaylistEntry("Favorites", listOf(HistoryEntry("content://video/1", "Clip")))),
            JSONObject()
                .put("themeMode", "AMOLED")
                .put("playbackSpeed", 1.5)
                .put("skipIntervalSeconds", 15)
                .put("orientationMode", "Landscape")
                .put("keepScreenOn", false)
                .put("gestureSeekEnabled", false)
                .put("subtitleDelayMs", 250)
        )
        val restored = parsePlayerPreferencesBackup(backup)
        assertEquals("AMOLED", restored?.getString("themeMode"))
        assertEquals(1.5, restored?.getDouble("playbackSpeed") ?: 0.0, 0.001)
        assertEquals(15, restored?.getInt("skipIntervalSeconds"))
        assertEquals("Landscape", restored?.getString("orientationMode"))
        assertEquals(false, restored?.getBoolean("keepScreenOn"))
        assertEquals(false, restored?.getBoolean("gestureSeekEnabled"))
        assertEquals(250, restored?.getInt("subtitleDelayMs"))
    }

    @Test
    fun invalidPreferencesAreClampedToSafeDefaults() {
        val backup = serializePlaylistsJson(
            emptyList(),
            JSONObject()
                .put("themeMode", "unknown")
                .put("playbackSpeed", 99.0)
                .put("skipIntervalSeconds", 999)
                .put("orientationMode", "sideways")
                .put("subtitleDelayMs", 999_999)
        )
        val restored = parsePlayerPreferencesBackup(backup)
        assertEquals("Midnight", restored?.getString("themeMode"))
        assertEquals(1.0, restored?.getDouble("playbackSpeed") ?: 0.0, 0.001)
        assertEquals(30, restored?.getInt("skipIntervalSeconds"))
        assertEquals("Auto", restored?.getString("orientationMode"))
        assertEquals(120_000, restored?.getInt("subtitleDelayMs"))
    }

    @Test
    fun oldPlaylistBackupsRemainImportableWithoutPreferences() {
        val oldBackup = """{"format":"nova-player-playlists","version":1,"playlists":[]}"""
        assertEquals(null, parsePlayerPreferencesBackup(oldBackup))
        assertEquals(emptyList<PlaylistEntry>(), parsePlaylistsJson(oldBackup))
    }

}
