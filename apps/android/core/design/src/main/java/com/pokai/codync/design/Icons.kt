package com.pokai.codync.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import com.pokai.codync.design.theme.Inter

/**
 * A Material Symbols Rounded glyph.
 *
 * The bundled font is subset to the glyphs below so the APK stays small. Use codepoints rather
 * than ligature text so subsetting keeps working.
 */
@Immutable
data class Symbol(val char: String, val label: String)

private fun glyph(code: Int) = code.toChar().toString()

object CodyncIcons {
    private fun s(code: Int, label: String) = Symbol(glyph(code), label)

    val Bots = s(0xF06C, "Bots")
    val Notifications = s(0xE7F5, "Notifications")
    val Settings = s(0xE8B8, "Settings")
    val Add = s(0xE145, "Add")
    val Search = s(0xEF7A, "Search")
    val More = s(0xE5D3, "More")
    val Back = s(0xE5C4, "Back")
    val Attach = s(0xE226, "Attach")
    val Mic = s(0xE31D, "Dictate")
    val MicOff = s(0xE02B, "Stop dictation")
    val Send = s(0xE163, "Send")
    val Close = s(0xE5CD, "Close")
    val Check = s(0xE668, "Check")
    val ChevronDown = s(0xE5CF, "Expand")
    val ChevronUp = s(0xE5CE, "Collapse")
    val ChevronRight = s(0xE5CC, "Open")
    val Computer = s(0xE31E, "Computer")
    val Cloud = s(0xF15C, "Cloud")
    val CloudOff = s(0xE2C1, "Offline")
    val CloudDone = s(0xE2BF, "Connected")
    val Groups = s(0xF233, "Groups")
    val Chat = s(0xE0CB, "Chat")
    val Lock = s(0xE899, "Secure")
    val Refresh = s(0xE5D5, "Refresh")
    val Error = s(0xF8B6, "Error")
    val Schedule = s(0xEFD6, "Schedule")
    val Download = s(0xF090, "Download")
    val Upload = s(0xF09B, "Upload")
    val Folder = s(0xE2C7, "Folder")
    val Image = s(0xE3F4, "Image")
    val File = s(0xE873, "File")
    val Play = s(0xE037, "Play")
    val Stop = s(0xE047, "Stop")
    val StopCircle = s(0xEF71, "Stop")
    val Tune = s(0xE429, "Tune")
    val Palette = s(0xE40A, "Appearance")
    val Person = s(0xF0D3, "Person")
    val PersonAdd = s(0xEA4D, "Add person")
    val GroupAdd = s(0xE7F0, "Add group")
    val Delete = s(0xE92E, "Delete")
    val Edit = s(0xF097, "Edit")
    val Pin = s(0xF10D, "Pin")
    val Hide = s(0xE8F5, "Hide")
    val Filter = s(0xE152, "Filter")
    val Wifi = s(0xE63E, "Direct")
    val WifiOff = s(0xE648, "No connection")
    val Bolt = s(0xEA0B, "Fast mode")
    val Memory = s(0xE322, "Memory")
    val Extension = s(0xE87B, "Extensions")
    val Terminal = s(0xEB8E, "Terminal")
    val Done = s(0xE876, "Done")
    val Cancel = s(0xE888, "Cancel")
    val Camera = s(0xE412, "Camera")
    val Share = s(0xE80D, "Share")
    val OpenInNew = s(0xE89E, "Open")
    val CheckCircle = s(0xF0BE, "Done")
    val Drag = s(0xE25D, "Reorder")
    val Link = s(0xE250, "Link")
    val QrCode = s(0xF206, "Scan QR code")
    val Speed = s(0xE9E4, "Performance")
    val Copy = s(0xE14D, "Copy")
    val ThumbUp = s(0xF577, "React")
    val Forum = s(0xE8AF, "Chats")
    val Inbox = s(0xE156, "Inbox")
    val History = s(0xE8B3, "History")
    val Undo = s(0xE166, "Rewind")
    val AddComment = s(0xE266, "New chat")
    val Hub = s(0xE9F4, "Team map")
    val Phone = s(0xE7BA, "Phone")
    val Router = s(0xE328, "Computer")
    val Build = s(0xF8CD, "Tools")
    val Science = s(0xEA4B, "Lab")
    val Rocket = s(0xEB9B, "Launch")
    val LightMode = s(0xE518, "Light")
    val DarkMode = s(0xE51C, "Dark")
    val Contrast = s(0xEB37, "System")
    val Volume = s(0xE050, "Sound")
    val VolumeOff = s(0xE04F, "Muted")
    val Info = s(0xE88E, "Info")
    val Help = s(0xE8FD, "Help")
    val Logout = s(0xE9BA, "Sign out")
    val Login = s(0xEA77, "Sign in")
    val Sync = s(0xE627, "Sync")
    val Warning = s(0xF083, "Warning")
    val Visible = s(0xE8F4, "Visible")
    val ArrowForward = s(0xE5C8, "Continue")
    val ArrowUp = s(0xE5D8, "Up")
    // arrow_downward (E5DB) is not in the subset font — KeyboardDown is.
    val ArrowDown = s(0xE313, "Jump to latest")
    val KeyboardDown = s(0xE313, "Show less")
    val KeyboardUp = s(0xE316, "Show more")
    val Menu = s(0xE5D2, "Menu")
    val Home = s(0xE9B2, "Home")
    val Dashboard = s(0xE66B, "Dashboard")
    val Workspaces = s(0xEA0F, "Workspaces")
    val Pause = s(0xE034, "Pause")
    val Radio = s(0xE837, "Selected")
    val Restart = s(0xF053, "Restart")
    val Fingerprint = s(0xE90D, "Approve")
    val Shield = s(0xE9E0, "Security")
    val Key = s(0xE73C, "Key")
    val VpnKey = s(0xE0DA, "Credentials")
    val Api = s(0xF1B7, "API")
    val Database = s(0xF20E, "Database")
    val Dns = s(0xE875, "Server")
    val Code = s(0xE86F, "Code")
    val DataObject = s(0xEAD3, "Data")
    val Web = s(0xE66A, "Web")
    val Globe = s(0xE64C, "Web")
    val Public = s(0xE80B, "Public")
    val DownloadOffline = s(0xF000, "Downloaded")
    val OfflineBolt = s(0xE932, "Offline")
    val NotificationsOff = s(0xE7F6, "Muted")
    val MailRead = s(0xF18C, "Read")
    val MailUnread = s(0xF18A, "Unread")
    val DoneAll = s(0xE877, "All done")
    val Remove = s(0xE15B, "Remove")
    val Circle = s(0xEF4A, "Status")
    val AddCircle = s(0xE990, "Add")
    val Pending = s(0xE909, "Pending")
    val CheckBox = s(0xE9DE, "Checked")
}

private val SymbolFont = FontFamily(Font(R.font.material_symbols_rounded))

@Composable
fun CodyncIcon(
    symbol: Symbol,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 22.dp,
    contentDescription: String? = null,
) {
    val density = LocalDensity.current
    val sp = with(density) { size.toSp() }
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription else invisibleToUser()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol.char,
            color = tint,
            style = TextStyle(
                fontFamily = SymbolFont,
                fontSize = sp,
                lineHeight = sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun CodyncIcon(
    symbol: Symbol,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 22.dp,
) = CodyncIcon(symbol, modifier, tint, size, contentDescription)

/** A symbol followed by a text label, used inside buttons and rows. */
@Composable
fun SymbolLabel(
    symbol: Symbol,
    text: String,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 18.dp,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
    ) {
        CodyncIcon(symbol, tint = tint, size = iconSize)
        Text(text = text, color = tint, style = com.pokai.codync.design.theme.CodyncType.subheadline)
    }
}
