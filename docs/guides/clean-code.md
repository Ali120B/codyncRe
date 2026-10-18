# Clean code

Rules every change follows, in every part of the repository (host, iOS, desktop, cloud, relay). Language-specific rules stay in [CLAUDE.md](../../CLAUDE.md) (Swift 6, Rust lints, TypeScript); where to put a file is in [file structure](../architecture/file-structure.md).

## Before writing code

- **Reuse first.** Search for an existing helper, type, component or pattern before adding one (`LockExt`, `Motion`, `Controls.swift`, `components/`, store methods). A second copy of logic that already exists is a bug waiting to diverge.
- **Build only what is asked.** No speculative options, config for values that never change, or scaffolding "for later". No protocol/trait/interface with one implementation unless it is the test seam.
- **Fix the root cause** in the shared function every caller goes through, not a guard at the one call site the bug report names.

## Modules

- **One responsibility per file and per type.** If describing a file needs "and", it is two files. Name it after that responsibility, using the shared terms from file structure.
- **Don't grow a large file.** New behavior in a file past ~500 lines goes into a new sibling module (Rust submodule, Swift extension file, React component/hook), not appended to it. When a change already touches a large file, move the part you are changing out first, as its own `refactor` commit.
- **Layers stay in their lane.** Views render state and send intents; stores/models own state and logic; clients/transports own I/O. No networking, persistence or parsing in a SwiftUI view or React component. In the host, keep protocol parsing, decisions and side effects (db, process, network) apart so the decision part is a pure, unit-testable function. Across the apps, product logic lives in the host, not in any client: [host owns the logic](../architecture/overview.md#host-owns-the-logic).
- **Small public surface.** Default to `private`/`fileprivate`, `pub(crate)` or no export; widen only when another module needs it.
- **Dependencies point one way.** `CodyncKit` never imports `CodyncUI`; renderer code reaches the host only through `window.codync`; shared code never knows about a specific screen.

## Functions and types

- **Short functions at one level of abstraction.** A function either orchestrates (calls named steps) or does one step, not both. Past ~40 lines or 3 levels of nesting, extract named helpers.
- **Early return** over nested `if`/`else`; `guard` in Swift, `?`/`let … else` in Rust.
- **Make illegal states unrepresentable.** Enums with associated data over flags, optional pairs and string states; newtypes/IDs over bare `String`/`Int` where mixing them up is possible.
- **No magic values.** Name constants once, next to the code that owns them; reuse theme tokens instead of literal colors, spacing and durations.
- **Pure where possible.** Pass data in and return results; keep mutation and global state at the edges.

## Readability

- **Names say intent**, not type or mechanics (`pendingApprovals`, not `list2` or `dataArray`). Booleans read as questions (`isConnected`, `hasUnread`).
- **Comments explain why**, never restate what the code does. A comment that explains *what* means the code needs a better name or an extracted function.
- **Delete dead code.** No commented-out code, unused parameters, stale feature flags or `TODO` without a reason. Git keeps the history.
- **Errors are never swallowed.** Handle, propagate with context, or log with `tracing`/`Logger`; an empty `catch` or `let _ =` on a `Result` needs a comment saying why it is safe.

## Changing existing code

- Leave the code you touch a bit cleaner than you found it, but keep cleanups inside your change's area and in separate `refactor` commits from behavior changes.
- Prefer deleting and simplifying over adding. The smallest diff that fixes the problem in the right place wins.
- Every changed piece of logic gets a test that would fail without the change; test behavior, not private details.
