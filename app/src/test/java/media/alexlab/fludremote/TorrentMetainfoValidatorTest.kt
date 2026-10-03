package media.alexlab.fludremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentMetainfoValidatorTest {
    @Test
    fun acceptsCompleteTorrentMetainfoWithInfoDictionary() {
        val bytes = "d4:infod4:name4:testee".toByteArray(Charsets.US_ASCII)
        assertTrue(TorrentMetainfoValidator.isValid(bytes))
    }

    @Test
    fun rejectsDictionaryWithoutInfoKey() {
        val bytes = "d4:name4:teste".toByteArray(Charsets.US_ASCII)
        assertFalse(TorrentMetainfoValidator.isValid(bytes))
    }

    @Test
    fun rejectsTruncatedMetainfo() {
        val bytes = "d4:infod4:name4:test".toByteArray(Charsets.US_ASCII)
        assertFalse(TorrentMetainfoValidator.isValid(bytes))
    }

    @Test
    fun sanitizesTorrentFilenameAndKeepsExtension() {
        assertEquals(
            "Movie_Name_.torrent",
            TorrentFileSupport.sanitizeName("Movie<Name>?.torrent")
        )
        assertEquals(
            "download.torrent",
            TorrentFileSupport.sanitizeName("")
        )
        assertEquals(
            "ubuntu.torrent",
            TorrentFileSupport.sanitizeName("ubuntu")
        )
    }
}
