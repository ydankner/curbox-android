package neth.iecal.curbox.data.models

/**
 * Something that has to be done today before the apps or websites of a group open at all.
 * Groups point at a requirement by [id], so one requirement can guard several groups.
 */
data class AccessRequirement(
    val id: String = "",
    val name: String = "",
    /** True when every condition has to be met, false when any single one is enough. */
    val isAllRequired: Boolean = false,
    val conditions: List<AccessCondition> = emptyList(),
)

data class AccessCondition(
    val type: String = TYPE_ANKI_CLEARED,
    /** App to use, only for [TYPE_APP_USAGE]. */
    val packageName: String = "",
    /** Minutes of use today, only for [TYPE_APP_USAGE]. */
    val minutes: Int = 0,
) {
    companion object {
        /** AnkiDroid has no cards left to study today. */
        const val TYPE_ANKI_CLEARED = "anki_cleared"
        /** An app was in the foreground for at least [minutes] today. */
        const val TYPE_APP_USAGE = "app_usage"
    }
}
