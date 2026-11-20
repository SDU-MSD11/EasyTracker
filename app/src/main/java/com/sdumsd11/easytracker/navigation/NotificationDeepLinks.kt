package com.sdumsd11.easytracker.navigation

/**
 * Notification tap-to-destination contract (ui-ux owns the mapping; api-integration
 * owns the poster). Notification content intents arrive as VIEW intents with data
 * `easytracker://<route>`, where route is a full [Routes] string. Missing/malformed
 * Uris fall back to Splash routing (the nav host ignores them).
 *
 * Kind -> route (poster builds the matching content intent one-to-one):
 * - `new-expense`/`summary` -> GroupDetail (`groups/<groupId>`), else Groups root.
 * - `debt-update`/`retry-success` -> PaymentHistory (`account/history`).
 * - anything else (e.g. `reminder`) -> Groups root.
 */
object NotificationDeepLinks {
    const val Scheme = "easytracker"

    /** Full deep-link Uri string for a [Routes] string. */
    fun uri(route: String): String = "$Scheme://$route"

    fun groups(): String = uri(Routes.groups())

    fun groupDetail(groupId: String): String = uri(Routes.groupDetail(groupId))

    fun paymentHistory(): String = uri(Routes.PaymentHistory)

    fun settings(): String = uri(Routes.Settings)

    fun receipt(txId: String? = null, amountCents: Long? = null, groupId: String? = null): String =
        uri(Routes.receipt(txId, amountCents, groupId))

    /** Route a notification kind opens; unknown kinds land on the Groups root. */
    fun routeForKind(kind: String, groupId: String? = null): String = when (kind) {
        "new-expense", "summary" -> groupId?.takeIf { it.isNotBlank() }?.let(Routes::groupDetail)
            ?: Routes.groups()
        "debt-update", "retry-success" -> Routes.PaymentHistory
        else -> Routes.groups()
    }

    /**
     * Validates a VIEW intent data string; returns the [Routes] string to navigate
     * to, or null when missing/malformed/unknown (caller falls back to Splash routing).
     * Pure string ops so the contract is JVM-unit-testable.
     */
    fun routeFromDeepLink(dataString: String?): String? {
        val route = dataString?.takeIf { it.startsWith("$Scheme://") }
            ?.removePrefix("$Scheme://")?.takeIf { it.isNotBlank() } ?: return null
        val path = route.substringBefore('?').substringBefore('#')
        // Blank group id (`groups/`) matches nothing in the graph — fall back.
        val known = path == "groups" || path == "account/history" || path == "settings" ||
            path == "receipts" ||
            (path.startsWith("groups/") && path.removePrefix("groups/").isNotBlank())
        return route.takeIf { known }
    }
}
