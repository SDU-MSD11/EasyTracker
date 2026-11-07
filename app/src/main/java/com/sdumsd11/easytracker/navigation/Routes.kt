package com.sdumsd11.easytracker.navigation

/**
 * Route contract — LAW from the nav diagram. String routes (no kotlinx-serialization dep in
 * this slice); each destination has a pattern + a builder so arg keys live in exactly one place.
 *
 * Enforcement notes for implementers:
 * - Owner-gate: [EditGroup] is admin-only. Its VM checks admin and redirects non-admins to
 *   [GroupDetail]; the graph does not gate (deep links must still land safely).
 * - Point-of-no-return: after payment, navigate to [paymentSuccess] with
 *   `popUpTo(makePayment(..)) { inclusive = true }` so back lands on [GroupDetail], never a resubmit.
 */
object GroupsTab {
    const val JOINED = "joined"
    const val FREE = "free"
}

object Routes {
    const val Splash = "splash"

    const val ArgTab = "tab"
    const val Groups = "groups?$ArgTab={$ArgTab}"
    fun groups(tab: String = GroupsTab.JOINED) = "groups?$ArgTab=$tab"

    const val CreateGroup = "create-group"

    const val ArgGroupId = "groupId"

    /** Group Debt Overview; shows EmptyState when the group has no debts (ui-ux). */
    const val GroupDetail = "groups/{$ArgGroupId}"
    fun groupDetail(groupId: String) = "groups/$groupId"

    /** Owner-only, see enforcement note above. */
    const val EditGroup = "groups/{$ArgGroupId}/edit"
    fun editGroup(groupId: String) = "groups/$groupId/edit"

    const val ArgDebtId = "debtId"
    const val SetupDebt = "groups/{$ArgGroupId}/debts/setup?$ArgDebtId={$ArgDebtId}"
    fun setupDebt(groupId: String, debtId: String? = null) = buildString {
        append("groups/$groupId/debts/setup")
        if (debtId != null) append("?$ArgDebtId=$debtId")
    }

    const val MakePayment = "groups/{$ArgGroupId}/pay"
    fun makePayment(groupId: String) = "groups/$groupId/pay"

    const val ArgTxId = "txId"
    const val PaymentSuccess = "payments/success/{$ArgTxId}"
    fun paymentSuccess(txId: String) = "payments/success/$txId"

    const val ArgError = "error"
    const val PaymentFailed = "payments/failed?$ArgError={$ArgError}"
    fun paymentFailed(error: String? = null) = buildString {
        append("payments/failed")
        if (error != null) append("?$ArgError=$error")
    }

    const val Account = "account"
    const val PaymentHistory = "account/history"
    const val Settings = "settings"
    const val EditProfile = "account/edit"

    const val Receipt = "receipts?$ArgTxId={$ArgTxId}"
    fun receipt(txId: String? = null) = buildString {
        append("receipts")
        if (txId != null) append("?$ArgTxId=$txId")
    }
}
