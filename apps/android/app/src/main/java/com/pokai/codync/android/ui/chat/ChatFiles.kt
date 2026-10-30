package com.pokai.codync.android.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A save request for either a message attachment or a file shared by a bot. */
data class ChatDownload(
    val name: String,
    val size: Long,
    val uploadId: String? = null,
    val entryId: String? = null,
    val fileId: String? = null,
)

private sealed interface ChatImageSource {
    data class Upload(val botId: String, val uploadId: String, val size: Long) : ChatImageSource
    data class Shared(val entryId: String, val fileId: String, val size: Long) : ChatImageSource
}

private fun isImage(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf(
        "png", "jpg", "jpeg", "heic", "gif", "webp", "tiff", "bmp",
    )

/** Images from either side of the conversation, plus save controls for every other file. */
@Composable
fun ChatFiles(
    session: SessionStore,
    entry: Entry,
    botId: String,
    onDownload: (ChatDownload) -> Unit,
) {
    val attachments = entry.data.attachments.orEmpty()
    val files = entry.data.files.orEmpty()
    if (attachments.isEmpty() && files.isEmpty()) return
    val colors = CodyncTheme.colors
    Column(
        Modifier.fillMaxWidth().background(Color.Transparent),
        horizontalAlignment = if (entry.kind == "user") Alignment.End else Alignment.Start,
    ) {
        attachments.forEach { attachment ->
            if (isImage(attachment.name)) {
                ChatImage(
                    session = session,
                    source = ChatImageSource.Upload(botId, attachment.id, attachment.size),
                    name = attachment.name,
                )
            } else {
                SecondaryButton("Save ${attachment.name}", onClick = {
                    onDownload(ChatDownload(attachment.name, attachment.size, uploadId = attachment.id))
                })
            }
        }
        files.forEach { file ->
            if (isImage(file.name)) {
                ChatImage(
                    session = session,
                    source = ChatImageSource.Shared(entry.id, file.id, file.size),
                    name = file.name,
                )
            } else {
                Text(file.name, color = colors.secondary, style = CodyncType.caption)
                SecondaryButton("Save ${file.name}", onClick = {
                    onDownload(ChatDownload(file.name, file.size, entryId = entry.id, fileId = file.id))
                })
            }
        }
    }
}

@Composable
private fun ChatImage(session: SessionStore, source: ChatImageSource, name: String) {
    val colors = CodyncTheme.colors
    var bitmap by remember(source) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loading by remember(source) { mutableStateOf(true) }
    var failed by remember(source) { mutableStateOf(false) }
    var viewing by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source) {
        loading = true
        failed = false
        val size = when (source) {
            is ChatImageSource.Upload -> source.size
            is ChatImageSource.Shared -> source.size
        }
        val bytes = if (size > MAX_IMAGE_BYTES) {
            null
        } else {
            runCatching { withContext(Dispatchers.IO) { readImageBytes(session, source) } }.getOrNull()
        }
        val decoded = bytes?.let { withContext(Dispatchers.IO) { decodeBitmap(it) } }
        bitmap = decoded
        failed = decoded == null
        loading = false
    }

    Box(
        Modifier
            .widthIn(min = 120.dp, max = 240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(enabled = bitmap != null) { viewing = true }
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.padding(24.dp), color = colors.secondary, strokeWidth = 2.dp)
            bitmap != null -> Image(
                bitmap = bitmap!!,
                contentDescription = name,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .widthIn(max = 234.dp)
                    .heightIn(max = 280.dp),
                contentScale = ContentScale.Fit,
            )
            else -> Text(
                if (failed) "Couldn't load $name" else "Loading $name",
                modifier = Modifier.padding(14.dp),
                color = colors.tertiary,
                style = CodyncType.caption,
            )
        }
    }

    if (viewing && bitmap != null) {
        Dialog(
            onDismissRequest = { viewing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black).clickable { viewing = false },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private suspend fun readImageBytes(session: SessionStore, source: ChatImageSource): ByteArray {
    val chunks = ArrayList<ByteArray>()
    var offset = 0L
    var expected: Long? = null
    while (expected == null || offset < expected) {
        val response = when (source) {
            is ChatImageSource.Upload -> session.call("readUpload", buildJsonObject {
                put("botId", source.botId)
                put("uploadId", source.uploadId)
                put("offset", offset)
            }).getOrThrow().jsonObject
            is ChatImageSource.Shared -> session.call("readFile", buildJsonObject {
                put("entryId", source.entryId)
                put("fileId", source.fileId)
                put("offset", offset)
            }).getOrThrow().jsonObject
        }
        val size = response["size"]?.jsonPrimitive?.longOrNull ?: error("Host returned an invalid file size")
        expected = expected ?: size
        require(expected == size) { "The host changed the file size while loading it" }
        val encoded = response["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val bytes = if (encoded.isEmpty()) ByteArray(0) else Base64.decode(encoded, Base64.DEFAULT)
        if (bytes.isEmpty()) break
        chunks += bytes
        offset += bytes.size
        require(offset <= expected) { "The host returned more data than expected" }
    }
    require(offset == (expected ?: 0L)) { "The image ended early" }
    val total = chunks.sumOf(ByteArray::size)
    val joined = ByteArray(total)
    var position = 0
    chunks.forEach { chunk ->
        chunk.copyInto(joined, position)
        position += chunk.size
    }
    return joined
}

/** Decode at a bounded size so a large camera photo cannot exhaust the chat's heap. */
private fun decodeBitmap(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
