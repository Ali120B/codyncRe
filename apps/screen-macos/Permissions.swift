import AppKit
import ApplicationServices
import CoreGraphics

/// Prompts follow explicit actions: enabling Remote screen (which launches the helper) or a
/// request from the computer's setup screen.
@MainActor
enum ScreenPermissions {
    static func request(_ permission: String) throws {
        switch permission {
        case "capture":
            if !CGPreflightScreenCaptureAccess() {
                NSApp.activate(ignoringOtherApps: true)
                _ = CGRequestScreenCaptureAccess()
            }
        case "input":
            if !AXIsProcessTrusted() {
                // The C global for this option is not available under Swift 6 concurrency.
                _ = AXIsProcessTrustedWithOptions(["AXTrustedCheckOptionPrompt": true] as CFDictionary)
            }
        default:
            throw HelperError("Unknown computer access permission.")
        }
    }
}
