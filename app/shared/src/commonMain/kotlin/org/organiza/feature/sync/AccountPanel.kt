package org.organiza.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.ui.AccountView
import org.organiza.ui.Action
import org.organiza.ui.Actions
import org.organiza.ui.ButtonKind
import org.organiza.ui.ChoiceRow
import org.organiza.ui.Field
import org.organiza.ui.KeyValue
import org.organiza.ui.Panel
import org.organiza.ui.PanelHeader
import org.organiza.ui.Rule
import org.organiza.ui.Segmented
import org.organiza.ui.Theme
import org.organiza.ui.Txt
import org.organiza.ui.Type
import org.organiza.sync.BackendKind
import org.organiza.presentation.Format

/** Chooses where Arena results and the account live (ADR-0015). Blocking never depends on it. */
@Composable
fun BackendPanel(account: AccountView, actions: Actions, withAccount: Boolean = true) {
    val colors = Theme.colors
    var kind by rememberSaveable(account.kind) { mutableStateOf(account.kind) }
    var url by rememberSaveable(account.kind) { mutableStateOf(if (account.kind == BackendKind.SELF_HOSTED) account.url else "") }
    var key by remember { mutableStateOf("") }
    Panel {
        PanelHeader("Where Arena syncs")
        Txt(
            "Habits, sessions, usage and overrides stay on this device whatever you choose. A backend only holds your " +
                "account and encrypted challenge results it cannot read.",
            Type.small,
            colors.muted,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceRow(
                "Only this device",
                "No account. To take on challenges with friends, enter a relay address when you start one: a small server that passes your encrypted results along and cannot read them",
                kind == null,
            ) { kind = null }
            ChoiceRow(
                "Organiza hosted",
                if (account.hostedAvailable) "A managed Supabase backend. Sign in with email" else "Not available in this build",
                kind == BackendKind.HOSTED,
                enabled = account.hostedAvailable,
            ) { kind = BackendKind.HOSTED }
            ChoiceRow("My own Supabase", "Enter your project URL and its anon or publishable key", kind == BackendKind.SELF_HOSTED) {
                // Coming from another backend, the fields start empty rather than carrying its address.
                if (kind != BackendKind.SELF_HOSTED && account.kind != BackendKind.SELF_HOSTED) {
                    url = ""
                    key = ""
                }
                kind = BackendKind.SELF_HOSTED
            }
        }
        if (kind == BackendKind.SELF_HOSTED) {
            Spacer(Modifier.height(12.dp))
            Field(url, { url = it }, "Project URL (https)")
            Field(key, { key = it }, "Anon or publishable key", secret = true)
            Txt("Never use a service role or secret key here: it bypasses row-level security for every user.", Type.small, colors.muted)
        }
        Spacer(Modifier.height(8.dp))
        val changed = kind != account.kind || (kind == BackendKind.SELF_HOSTED && (url != account.url || key.isNotBlank()))
        Action("Use this", ButtonKind.SECONDARY, enabled = changed && !account.busy) {
            actions.chooseBackend(kind, url, key)
            key = ""
        }
    }
    // The account below belongs to the backend in use; while another is chosen but not saved, it would mislead.
    if (withAccount && account.kind != null && kind == account.kind) AccountPanel(account, actions)
}

private enum class AuthMode(val label: String) { SIGN_IN("Sign in"), SIGN_UP("Create account") }

/** Limits that count every device, through reports only this person's devices can open (ADR-0028). */
@Composable
private fun DevicesBlock(account: AccountView, actions: Actions) {
    val colors = Theme.colors
    // The code is a secret, so it is never saved with the screen's state.
    var code by remember { mutableStateOf("") }
    Txt("Usage across devices", Type.bodyStrong)
    if (account.devicesLinked) {
        Txt(
            if (account.otherDevices == 0) "On. No other device has reported today yet."
            else "On. Daily limits count ${Format.count(account.otherDevices, "other device", "other devices")} today.",
            Type.small,
            colors.muted,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Action("Copy device code", ButtonKind.SECONDARY) { actions.shareDeviceCode() }
            Spacer(Modifier.width(8.dp))
            Action("Stop sharing", ButtonKind.TEXT) { actions.unlinkDevices() }
        }
        return
    }
    Txt(
        "Daily limits can count your time on your other devices. Each one seals today's minutes per app so only your devices can open them; the backend sees padded ciphertext.",
        Type.small,
        colors.muted,
    )
    Spacer(Modifier.height(8.dp))
    Action("Start sharing on this device", ButtonKind.SECONDARY) { actions.linkDevices("") }
    Field(code, { code = it.trim() }, "Or paste a code from your other device", secret = true)
    Action("Join", ButtonKind.SECONDARY, enabled = code.isNotEmpty()) {
        actions.linkDevices(code)
        code = ""
    }
}

/** Email sign-in and sign-up for the chosen backend, then the account and its deletion (ADR-0015, ADR-0020). */
@Composable
fun AccountPanel(account: AccountView, actions: Actions) {
    val colors = Theme.colors
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    // Secrets use remember, not rememberSaveable, so they never reach saved instance state.
    var password by remember { mutableStateOf("") }
    Panel {
        PanelHeader("Account") { Txt(account.url.removePrefix("https://"), Type.small, colors.muted, maxLines = 1) }
        if (account.signedInAs != null) {
            KeyValue("Signed in as", account.signedInAs)
            Txt("Challenge results sync through this account, encrypted so the backend cannot read them.", Type.small, colors.muted)
            Spacer(Modifier.height(8.dp))
            Action("Sign out", ButtonKind.SECONDARY, enabled = !account.busy) { actions.signOut() }
            Rule()
            DevicesBlock(account, actions)
            Rule()
            Txt("Deleting removes your account and every challenge result the backend holds, at once. Nothing on this device changes.", Type.small, colors.muted)
            Spacer(Modifier.height(8.dp))
            Action("Delete account & cloud data", ButtonKind.DANGER, enabled = !account.busy, modifier = Modifier.fillMaxWidth()) {
                actions.deleteAccount()
            }
            return@Panel
        }
        Segmented(AuthMode.entries, mode, { it.label }) { mode = it }
        Spacer(Modifier.height(12.dp))
        Field(email, { email = it.trim() }, "Email")
        Field(password, { password = it }, "Password", secret = true)
        if (mode == AuthMode.SIGN_UP) Txt("At least 8 characters. Your password goes to this backend only.", Type.small, colors.muted)
        Spacer(Modifier.height(8.dp))
        val ready = "@" in email && password.isNotEmpty() && !account.busy
        Action(if (account.busy) "Working" else mode.label, ButtonKind.PRIMARY, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            if (mode == AuthMode.SIGN_IN) actions.signIn(email, password) else actions.signUp(email, password)
            password = ""
        }
    }
}
