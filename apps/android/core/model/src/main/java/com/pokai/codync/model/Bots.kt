package com.pokai.codync.model

import kotlinx.serialization.Serializable

@Serializable
data class Bot(
    val id: String,
    val kind: String = "agent",
    val members: List<String> = emptyList(),
    val name: String = "Bot",
    val description: String = "",
    val avatarColor: String = "blue",
    val avatarShape: String = "blob",
    val backend: String = "custom",
    val command: String? = null,
    val cwd: String = "",
    val managedWorkspace: Boolean = false,
    val permission: String = "auto",
    val allowlist: List<String> = emptyList(),
    val model: String? = null,
    val modelOptions: Map<String, String> = emptyMap(),
    val fastMode: Boolean = false,
    val fastModeSupport: String = "unknown",
    val fastModeIssue: String? = null,
    val pinned: Boolean = false,
    val position: Int = 0,
    val hidden: Boolean = false,
    val notify: Boolean? = null,
    val connectors: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val leader: String = "",
    val instructions: String = "",
    val workspace: String = "",
    val memberSettings: Map<String, MemberAccess>? = null,
    val computer: Boolean = false,
    val browser: Boolean = false,
    val browserEngine: String = "",
    val autoName: Boolean = false,
    val createdAt: Long = 0,
    val rev: Long = 0,
    val status: String = "idle",
    val activity: String = "",
    val startedAt: Long? = null,
    val workingChat: String? = null,
    val workingThread: String? = null,
    val unread: Int = 0,
    val lastMessage: String? = null,
    val lastAt: Long = 0,
)

@Serializable
data class MemberAccess(
    val browser: Boolean = false,
    val computer: Boolean = false,
)

fun Bot.isGroup(): Boolean = kind == "group"
fun Bot.isWorking(): Boolean = status == "working" || status == "needsInput"
fun Bot.needsInput(): Boolean = status == "needsInput"

@Serializable
data class BotDraft(
    val id: String? = null,
    val name: String,
    val description: String = "",
    val avatarColor: String = "blue",
    val avatarShape: String = "blob",
    val backend: String = "custom",
    val command: String? = null,
    val cwd: String = "",
    val permission: String = "auto",
    val allowlist: List<String> = emptyList(),
    val model: String? = null,
    val modelOptions: Map<String, String> = emptyMap(),
    val fastMode: Boolean = false,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val notify: Boolean? = null,
    val connectors: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val computer: Boolean = false,
    val browser: Boolean = false,
    val browserEngine: String = "",
)

fun Bot.toDraft(): BotDraft = BotDraft(
    id = id,
    name = name,
    description = description,
    avatarColor = avatarColor,
    avatarShape = avatarShape,
    backend = backend,
    command = command,
    cwd = if (managedWorkspace) "" else cwd,
    permission = permission,
    allowlist = allowlist,
    model = model,
    modelOptions = modelOptions,
    fastMode = fastMode,
    pinned = pinned,
    hidden = hidden,
    notify = notify,
    connectors = connectors,
    skills = skills,
    computer = computer,
    browser = browser,
    browserEngine = browserEngine,
)

@Serializable
data class GroupDraft(
    val id: String? = null,
    val kind: String = "group",
    val name: String,
    val description: String = "",
    val members: List<String> = emptyList(),
    val leader: String? = null,
    val instructions: String? = null,
    val workspace: String? = null,
    val memberSettings: Map<String, MemberAccess>? = null,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val notify: Boolean? = null,
)
