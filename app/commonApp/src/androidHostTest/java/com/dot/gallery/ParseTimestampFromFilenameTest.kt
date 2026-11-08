package com.dot.gallery

import com.dot.gallery.domain.date.parseTimestampFromFilename
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import com.dot.gallery.feature.editor.adjustments.filters.None



/**
 * Regression tests for [parseTimestampFromFilename].
 *
 * Facebook export photos (long numeric IDs joined by underscores)
 * were being parsed as future dates because the date regex latched onto digits
 * embedded inside the IDs.
 */
class ParseTimestampFromFilenameTest {

    @Test
    fun parsesStandardCameraFilenames() {
        assertNotNull("IMG_20180508_213737.jpg".parseTimestampFromFilename())
        assertNotNull("VID_20180508_213737.mp4".parseTimestampFromFilename())
        assertNotNull("PXL_20180508_213737.jpg".parseTimestampFromFilename())
        assertNotNull("Screenshot_20210101-120000.png".parseTimestampFromFilename())
    }

    @Test
    fun ignoresDigitsEmbeddedInLongerNumericRuns() {
        // Facebook export style filenames: long numeric IDs separated by underscores.
        // None of these should yield a (future) date.
        assertNull("462051234_101601234567890_1234567890123456789_n.jpg".parseTimestampFromFilename())
        assertNull("123456789_10157012345678901_o.jpg".parseTimestampFromFilename())
        assertNull("FB_IMG_1623456789.jpg".parseTimestampFromFilename())
    }

    @Test
    fun rejectsFutureDates() {
        val futureYear = Calendar.getInstance().get(Calendar.YEAR) + 5
        assertNull("IMG_${futureYear}1201_120000.jpg".parseTimestampFromFilename())
    }

    @Test
    fun rejectsInvalidCalendarValues() {
        assertNull("IMG_20181308_213737.jpg".parseTimestampFromFilename()) // month 13
        assertNull("IMG_20180532_213737.jpg".parseTimestampFromFilename()) // day 32
        assertNull("IMG_18000508_213737.jpg".parseTimestampFromFilename()) // year < 1970
    }
}
