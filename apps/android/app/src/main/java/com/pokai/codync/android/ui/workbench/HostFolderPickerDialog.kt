package com.pokai.codync.android.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.ui.SearchField
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
internal fun HostFolderPickerDialog(
    session: SessionStore,
    initialPath: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    computerId: String? = null,
) {
    val colors = CodyncTheme.colors
    var path by remember(initialPath) { mutableStateOf(initialPath) }
    var listing by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(path, retry) {
        loading = true
        error = null
        listing = null
        val request = if (computerId.isNullOrBlank()) session.call("listDirs", buildJsonObject { path?.let { put("path", it) } })
        else session.callOn(computerId, "listDirs", buildJsonObject { path?.let { put("path", it) } })
        request
            .onSuccess { response -> listing = runCatching { response.jsonObject }.getOrNull() }
            .onFailure { error = it.message ?: "Couldn't browse folders on the computer." }
        loading = false
    }

    val dirs: List<JsonObject> = listing?.folderArray("dirs")?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        .orEmpty().filter { query.isBlank() || it.folderText("name").orEmpty().contains(query.trim(), ignoreCase = true) }
    val currentPath = listing?.folderText("path").orEmpty()
    val parent = listing?.folderText("parent")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.78f)
                .clip(RoundedCornerShape(26.dp)).background(colors.surface).padding(vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    symbol = if (parent == null) CodyncIcons.Close else CodyncIcons.Back,
                    contentDescription = if (parent == null) "Close folder browser" else "Parent folder",
                    onClick = { if (parent == null) onDismiss() else { path = parent; query = "" } },
                )
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text("Choose a host folder", color = colors.text, style = CodyncType.headline)
                    Text(currentPath.ifBlank { "Loading folders…" }, color = colors.tertiary, style = CodyncType.caption, maxLines = 1)
                }
            }
            SearchField(query, { query = it }, placeholder = "Filter folders", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            SectionCard(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                SettingsRow(
                    title = "Use this folder",
                    subtitle = currentPath,
                    icon = CodyncIcons.Folder,
                    showChevron = false,
                    onClick = { if (currentPath.isNotBlank()) onSelect(currentPath) },
                )
            }
            if (loading) {
                Text("Loading folders…", modifier = Modifier.padding(18.dp), color = colors.tertiary, style = CodyncType.footnote)
            } else if (error != null) {
                Text(error.orEmpty(), modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = colors.warning, style = CodyncType.footnote)
                PrimaryButton("Retry", onClick = { retry += 1 }, modifier = Modifier.padding(horizontal = 16.dp))
            } else if (dirs.isEmpty()) {
                Text(
                    if (query.isBlank()) "There are no child folders here." else "No folders match that search.",
                    modifier = Modifier.padding(18.dp),
                    color = colors.tertiary,
                    style = CodyncType.footnote,
                )
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(dirs, key = { it.folderText("path").orEmpty() }) { folder ->
                        SectionCard {
                            SettingsRow(
                                title = folder.folderText("name") ?: "Folder",
                                subtitle = folder.folderText("path"),
                                icon = CodyncIcons.Folder,
                                onClick = {
                                    path = folder.folderText("path")
                                    query = ""
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun JsonObject.folderText(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.folderArray(key: String): JsonArray? = this[key]?.let { runCatching { it.jsonArray }.getOrNull() }
