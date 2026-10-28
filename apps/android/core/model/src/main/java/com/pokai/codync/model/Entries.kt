package com.pokai.codync.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Entry(
    val id: String,
    val seq: Long = 0,
    val botId: String,
    val threadId: String? = null,
    val rev: Long = 0,
    /** user | agent | thought | tool | plan | permission | notice */
    val kind: String,
    val turn: Long = 0,
    val data: EntryData = EntryData(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class EntryData(
    val chat: Boolean? = null,
    val botMessage: BotMessage? = null,
    val heading: String? = null,
    val delegationId: String? = null,
    val sourceBotId: String? = null,
    val targetBotId: String? = null,
    val routineId: String? = null,
    val runId: String? = null,
    val text: String? = null,
    val final: Boolean? = null,
    val status: String? = null,
    val clientNonce: String? = null,
    val title: String? = null,
    val toolKind: String? = null,
    val output: String? = null,
    val diffs: List<FileDiff>? = null,
    val locations: List<FileLocation>? = null,
    val options: List<PermissionOption>? = null,
    val selected: String? = null,
    val command: String? = null,
    val detail: String? = null,
    val cwd: String? = null,
    val entries: List<PlanItem>? = null,
    val style: String? = null,
    val author: String? = null,
    val thread: ThreadSummary? = null,
    val reactions: List<String>? = null,
    val connectionRequest: JsonObject? = null,
    val attachments: List<Attachment>? = null,
    val files: List<SharedFile>? = null,
    val callSeconds: Int? = null,
)

@Serializable
data class BotMessage(
    val sourceBotId: String,
    val targetBotId: String,
    val text: String,
    val reply: String? = null,
    val detail: String? = null,
)

@Serializable
data class FileDiff(
    val path: String,
    val added: Int = 0,
    val removed: Int = 0,
    val isNew: Boolean = false,
    val startLine: Int = 0,
    val patch: String = "",
)

@Serializable
data class FileLocation(
    val path: String,
    val line: Int? = null,
)

@Serializable
data class PermissionOption(
    val optionId: String,
    val name: String,
    /** allow_once | allow_always | reject_once | reject_always */
    val kind: String,
)

@Serializable
data class PlanItem(
    val content: String,
    val status: String,
    val priority: String? = null,
)

@Serializable
data class ThreadSummary(
    val count: Int = 0,
    val lastAt: Long = 0,
    val authors: List<String> = emptyList(),
    val unread: Int? = null,
)

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val size: Long = 0,
)

@Serializable
data class SharedFile(
    val id: String,
    val name: String,
    val size: Long = 0,
    val sha256: String = "",
)

/** Chat-visible rows: user, final agent reply, permission, notice. */
fun Entry.isChatVisible(): Boolean = when (kind) {
    "user", "permission", "notice" -> true
    // Match desktop: streamed narration, thoughts, plans, tool status and intermediate
    // assistant rows are trace. The transcript shows only the completed assistant reply.
    "agent" -> data.final == true
    else -> false
}
