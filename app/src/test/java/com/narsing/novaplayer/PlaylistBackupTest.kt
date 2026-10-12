package com.narsing.novaplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
}
