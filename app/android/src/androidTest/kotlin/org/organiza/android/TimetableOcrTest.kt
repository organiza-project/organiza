package org.organiza.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.organiza.android.feature.schedule.TimetableOcr

@RunWith(AndroidJUnit4::class)
class TimetableOcrTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun anArabicTimetableIsReadWithItsSubjectsAndTimes() {
        // A photo stands in as a file, the way a picked image arrives as a URI.
        val photo = File(instrumentation.targetContext.cacheDir, "arabic-timetable.png")
        instrumentation.context.assets.open("arabic-timetable.png").use { input -> photo.outputStream().use(input::copyTo) }
        val text = TimetableOcr(instrumentation.targetContext).recognise(Uri.fromFile(photo)).orEmpty()
        assertTrue(text, "08:00" in text && "10:30" in text)
        assertTrue(text, "رياضيات" in text)
        assertTrue(text, "الأحد" in text || "الاحد" in text)
    }
}
