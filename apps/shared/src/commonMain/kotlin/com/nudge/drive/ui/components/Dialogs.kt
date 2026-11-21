package com.nudge.drive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nudge.drive.ui.theme.Nd
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.launch

/** Lavender bottom sheet with a grab handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NudgeSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Nd.Sheet,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 6.dp).size(44.dp, 5.dp).clip(CircleShape).background(Nd.Lilac)) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), content = content)
    }
}

/** One action row in an options sheet. */
@Composable
fun SheetAction(icon: ImageVector, label: String, tint: Color = Nd.Ink, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).bounceClick(onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (tint == Nd.Danger) Nd.DangerSoft else Nd.Mist), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (tint == Nd.Ink) Nd.IrisDeep else tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = tint)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Nd.Muted)
        }
    }
}

/**
 * Permanently deletes the signed-in account. Confirmed with the password, or with a code emailed to
 * [email] (people who sign in with Google don't know a password). [onDeleted] runs after the
 * session is cleared.
 */
@Composable
fun DeleteAccountDialog(repo: com.nudge.drive.data.Repository, email: String, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var useCode by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun fail(t: Throwable) { error = (t as? com.nudge.drive.data.ApiException)?.friendly ?: "Couldn't reach the server. Check your connection and try again."; busy = false }
    val ready = if (useCode) codeSent && code.length == 6 else password.isNotEmpty()

    NudgeDialog(onDismiss = { if (!busy) onDismiss() }) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Nd.DangerSoft), contentAlignment = Alignment.Center) {
            Icon(com.composables.icons.lucide.Lucide.TriangleAlert, null, tint = Nd.Danger, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Delete your account?", style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "This permanently deletes the Nudge Drive account $email: your folders, share links, drive connections, API keys and history. " +
                "Your files stay in your own Google Drive or storage. This can't be undone.",
            style = MaterialTheme.typography.bodyMedium, color = Nd.Muted,
        )
        Spacer(Modifier.height(16.dp))
        if (!useCode) {
            PillField(
                password, { password = it; error = null }, "Your password",
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = ImeAction.Done),
            )
            Text("Signed up with Google? Email me a code instead", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).bounceClick { useCode = true; error = null }.padding(vertical = 4.dp))
        } else {
            if (codeSent) {
                PillField(code, { v -> code = v.filter { it.isDigit() }.take(6); error = null }, "6-digit code from the email",
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword, imeAction = ImeAction.Done))
                Text("Sent to $email. It expires in 15 minutes.", style = MaterialTheme.typography.bodySmall, color = Nd.Muted, modifier = Modifier.padding(top = 6.dp))
            } else {
                SecondaryButton(if (busy) "Sending…" else "Email me a code", Modifier.fillMaxWidth()) {
                    if (busy) return@SecondaryButton
                    busy = true; error = null
                    scope.launch { runCatching { repo.requestDeleteCode() }.onSuccess { codeSent = true; busy = false }.onFailure(::fail) }
                }
            }
            Text("Use my password instead", style = MaterialTheme.typography.labelMedium, color = Nd.IrisDeep,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).bounceClick { useCode = false; error = null }.padding(vertical = 4.dp))
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Nd.Danger, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", Modifier.weight(1f)) { if (!busy) onDismiss() }
            PrimaryButton("Delete", Modifier.weight(1f), enabled = ready && !busy, loading = busy && ready, container = Nd.Danger) {
                busy = true; error = null
                scope.launch {
                    runCatching { if (useCode) repo.deleteAccount(code = code) else repo.deleteAccount(password = password) }
                        .onSuccess { onDeleted() }.onFailure(::fail)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NudgeDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(30.dp)).background(Nd.Surface).padding(22.dp),
            content = content,
        )
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    placeholder: String = "",
    confirm: String = "Save",
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    NudgeDialog(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
        Spacer(Modifier.height(16.dp))
        PillField(
            text, { text = it }, placeholder, Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text.trim()) }),
        )
        extra?.invoke(this)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", Modifier.weight(1f), onClick = onDismiss)
            PrimaryButton(confirm, Modifier.weight(1f), enabled = text.isNotBlank()) { onConfirm(text.trim()) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, danger: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    NudgeDialog(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Nd.Ink)
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Nd.Muted)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", Modifier.weight(1f), onClick = onDismiss)
            PrimaryButton(
                confirm, Modifier.weight(1f),
                container = if (danger) Nd.Danger else Nd.Primary, content = if (danger) Nd.OnInverse else Nd.OnPrimary,
            ) { onConfirm() }
        }
    }
}
