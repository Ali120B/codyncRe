package com.pokai.codync.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Entry
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
fun ConnectionRequestCard(
    entry: Entry,
    session: SessionStore,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val colors = CodyncTheme.colors
    val request = entry.data.connectionRequest ?: return
    val title = request["title"]?.jsonPrimitive?.contentOrNull ?: entry.data.title ?: "Connection request"
    val subtitle = entry.data.text
    val kind = request["kind"]?.jsonPrimitive?.contentOrNull ?: "connector"
    val status = request["status"]?.jsonPrimitive?.contentOrNull ?: entry.data.status ?: "pending"
    val pending = status == "pending"
    val connectorId = request["connectorId"]?.jsonPrimitive?.contentOrNull
    val registryName = request["registryName"]?.jsonPrimitive?.contentOrNull
    val field = request["field"]?.jsonPrimitive?.contentOrNull
    val location = request["location"]?.jsonPrimitive?.contentOrNull
    val isLogin = kind == "login"
    val isSecret = kind == "secret"

    var value by remember(entry.id) { mutableStateOf("") }
    var username by remember(entry.id) { mutableStateOf("") }
    var busy by remember(entry.id) { mutableStateOf(false) }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }

    fun finish(opts: Map<String, String?>) {
        scope.launch {
            busy = true
            error = null
            try {
                session.call("connectorRequestFinish", buildJsonObject {
                    put("entryId", entry.id)
                    opts["connectorId"]?.let { put("connectorId", it) }
                    opts["value"]?.let { put("value", it) }
                    opts["username"]?.let { put("username", it) }
                }).getOrThrow()
            } catch (t: Throwable) {
                error = t.message ?: "Couldn't finish the request"
            } finally {
                busy = false
            }
        }
    }

    SectionCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = colors.text, style = CodyncType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, color = colors.secondary, style = CodyncType.footnote)
                    }
                }
                if (pending) {
                    if (busy) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    } else if (status != "pending") {
                        Text(status.replaceFirstChar { it.uppercase() }, color = colors.tertiary, style = CodyncType.caption)
                    }
                } else {
                    Text(if (status == "ready") "Connected" else status.replaceFirstChar { it.uppercase() }, color = colors.tertiary, style = CodyncType.caption)
                }
            }
            if (pending) {
                Spacer(Modifier.height(10.dp))
                if (isLogin) {
                    BasicTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(10.dp),
                        textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.text),
                        decorationBox = { inner -> if (username.isEmpty()) Text("Username or email", color = colors.tertiary, style = CodyncType.footnote) else inner() ; inner() },
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(10.dp),
                        textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.text),
                        visualTransformation = PasswordVisualTransformation(),
                        decorationBox = { inner -> if (value.isEmpty()) Text("Password or op:// reference", color = colors.tertiary, style = CodyncType.footnote) else inner() ; inner() },
                    )
                } else if (isSecret) {
                    if (field != null || location != null) {
                        Text("${field ?: "Credential"} · ${location ?: ""}", color = colors.tertiary, style = CodyncType.caption)
                        Spacer(Modifier.height(4.dp))
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(10.dp),
                        textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.text),
                        visualTransformation = PasswordVisualTransformation(),
                        decorationBox = { inner -> if (value.isEmpty()) Text("Credential or op:// reference", color = colors.tertiary, style = CodyncType.footnote) else inner() ; inner() },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isLogin || isSecret) {
                        PrimaryButton(
                            text = if (isLogin) "Save" else "Save",
                            enabled = !busy && value.isNotBlank(),
                            onClick = { finish(mapOf("connectorId" to connectorId, "value" to value, "username" to (if (isLogin) username else null))) },
                        )
                    } else if (connectorId != null) {
                        PrimaryButton(
                            text = "Connect",
                            enabled = !busy,
                            onClick = { finish(mapOf("connectorId" to connectorId, "value" to null, "username" to null)) },
                        )
                    } else {
                        SecondaryButton(text = "Open Extensions", onClick = { /* user navigates */ })
                    }
                }
                error?.let { Text(it, color = colors.danger, style = CodyncType.caption, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
    }
}