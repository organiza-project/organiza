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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.ui.AccountView
import org.organiza.ui.Space
import org.organiza.ui.Spinner
import androidx.compose.foundation.layout.padding
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

/** Chooses where the account and the shared device usage live (ADR-0015, ADR-0028). Blocking never depends on it. */
@Composable
fun BackendPanel(account: AccountView, actions: Actions, withAccount: Boolean = true) {
    val colors = Theme.colors
    var kind by rememberSaveable(account.kind) { mutableStateOf(account.kind) }
    var url by rememberSaveable(account.kind) { mutableStateOf(if (account.kind == BackendKind.SELF_HOSTED) account.url else "") }
    var key by remember { mutableStateOf("") }
    Panel {
        PanelHeader("Account and sync")
        Txt(
            "Habits, sessions, usage and overrides stay on this device whatever you choose. A backend only holds your " +
                "account and encrypted usage reports from your devices, which it cannot read.",
            Type.small,
            colors.muted,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceRow(
                "Only this device",
                "No account. Nothing leaves this device, and limits count this device only",
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
        account.deviceSync?.let { Txt(it, Type.small, colors.muted) }
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
    var showPassword by remember { mutableStateOf(false) }
    Panel {
        PanelHeader("Account") { Txt(account.url.removePrefix("https://"), Type.small, colors.muted, maxLines = 1) }
        if (account.signedInAs != null) {
            KeyValue("Signed in as", account.signedInAs)
            Txt("Usage shared between your devices goes through this account, sealed so the backend cannot read it.", Type.small, colors.muted)
            Spacer(Modifier.height(8.dp))
            Action("Sign out", ButtonKind.SECONDARY, enabled = !account.busy) { actions.signOut() }
            Rule()
            DevicesBlock(account, actions)
            Rule()
            Txt("Deleting removes your account and every usage report the backend holds, at once. Nothing on this device changes.", Type.small, colors.muted)
            Spacer(Modifier.height(8.dp))
            Action("Delete account & cloud data", ButtonKind.DANGER, enabled = !account.busy, modifier = Modifier.fillMaxWidth()) {
                actions.deleteAccount()
            }
            return@Panel
        }
        val host = account.url.substringAfter("://").substringBefore('/')
        Txt(if (mode == AuthMode.SIGN_IN) "Sign in to $host" else "Create your account", Type.section, modifier = Modifier.semantics { heading() })
        Txt(
            if (mode == AuthMode.SIGN_IN) "Use the same account on each of your devices." else "On $host. Then sign in with it on each of your devices.",
            Type.small,
            colors.muted,
            Modifier.padding(bottom = 12.dp),
        )
        Segmented(AuthMode.entries, mode, { it.label }) { mode = it }
        Spacer(Modifier.height(12.dp))
        Field(email, { email = it.trim() }, "Email")
        // Checked as it is typed, so a mistake shows before anything is sent.
        val badEmail = email.isNotEmpty() && !EMAIL.matches(email)
        if (badEmail) Problem("Enter an email address, like name@example.com")
        Row(verticalAlignment = Alignment.Bottom) {
            Field(password, { password = it }, "Password", Modifier.weight(1f), secret = !showPassword)
            Action(if (showPassword) "Hide" else "Show", ButtonKind.TEXT, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)) { showPassword = !showPassword }
        }
        val short = mode == AuthMode.SIGN_UP && password.length < MIN_PASSWORD
        if (mode == AuthMode.SIGN_UP) {
            if (short && password.isNotEmpty()) Problem("At least $MIN_PASSWORD characters: ${MIN_PASSWORD - password.length} more")
            else Txt("At least $MIN_PASSWORD characters. Your password goes to this backend only.", Type.small, colors.muted)
        }
        Spacer(Modifier.height(8.dp))
        if (account.busy) {
            Row(Modifier.fillMaxWidth().heightIn(min = Space.target), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Spinner()
                Txt(if (mode == AuthMode.SIGN_IN) "Signing in…" else "Creating your account…", Type.bodyStrong, modifier = Modifier.padding(start = 10.dp))
            }
        } else {
            val ready = email.isNotEmpty() && !badEmail && password.isNotEmpty() && !short
            Action(mode.label, ButtonKind.PRIMARY, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                if (mode == AuthMode.SIGN_IN) actions.signIn(email, password) else actions.signUp(email, password)
                password = ""
            }
        }
        account.authMessage?.let { message ->
            if (account.authFailed) Problem(message) else Txt(message, Type.body, colors.positive, Modifier.padding(top = 8.dp))
        }
    }
}

/** A problem with what was typed or sent, in the warning colour, right where it happened. */
@Composable
private fun Problem(text: String) {
    Txt(text, Type.small, Theme.colors.negative, Modifier.padding(top = 4.dp, bottom = 4.dp))
}

// Something on each side of one @; the server judges the rest.
private val EMAIL = Regex("[^@\\s]+@[^@\\s]+")
private const val MIN_PASSWORD = 8
