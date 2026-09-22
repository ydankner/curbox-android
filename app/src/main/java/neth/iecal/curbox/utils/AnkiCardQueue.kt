package neth.iecal.curbox.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import org.json.JSONArray

/**
 * Reads how many cards AnkiDroid still has waiting today through its public ContentProvider.
 * Everything stays on the device; no AnkiDroid data is stored or sent anywhere.
 */
object AnkiCardQueue {
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    const val ANKIDROID_PACKAGE = "com.ichi2.anki"

    private val DECKS_URI: Uri = Uri.parse("content://com.ichi2.anki.flashcards/decks")
    private const val DECK_COUNTS = "deck_count"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Cards left to study today across every deck, or null when AnkiDroid cannot be read because
     * it is missing, its API is turned off, or the permission was not granted.
     */
    fun remainingCards(context: Context): Int? {
        if (!hasPermission(context)) return null
        return runCatching {
            context.contentResolver.query(DECKS_URI, null, null, null, null)?.use { cursor ->
                val countsIndex = cursor.getColumnIndex(DECK_COUNTS)
                if (countsIndex == -1) return null
                var total = 0
                while (cursor.moveToNext()) {
                    // Each deck reports its counts as [learn, review, new], already capped by the
                    // deck's daily limits, so zero everywhere means nothing is left for today.
                    val counts = JSONArray(cursor.getString(countsIndex) ?: "[]")
                    for (i in 0 until counts.length()) total += counts.optInt(i)
                }
                total
            }
        }.getOrNull()
    }
}
