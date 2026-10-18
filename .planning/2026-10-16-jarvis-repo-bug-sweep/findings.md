# Findings & Decisions

## Requirements
- Repo-wide sweep; include meaningful performance, test gap, duplication, and dead-code issues, not style-only cleanup.
- Concrete defects only; no promise that inspection proves the absence of undiscovered bugs.
- No Android device attached; hardware behavior cannot be directly checked in this session.

## Research Findings
- Repository has 342 production source files, 109 test files, and 664 test annotations; current branch is clean and 21 commits ahead of origin/main.
- Device test report is historical (version 1.0, dated 2026-09-19), so it is not evidence of current version 1.1 device behavior.
- Confirmed approval bypass: `AgentKernel.executeLoop` retries `ToolResult.NeedsConfirmation` with `userApprovalGranted = true`. `AssistantRuntime.executeCommand` defaults approval to false; `ToolExecutor` explicitly returns `NeedsConfirmation` for high-risk tools in manual mode. Existing state checkpoints already support `pendingApproval`, and `resumeTask` already replays it after consent, so the fix can route the action into that path instead of retrying automatically.
- Approval reply handling also needs review: `resumeTask` currently treats any reply containing a token from `yes/yep/.../send/do/karo` as consent. A reply such as “don't send it” contains `send` and could approve the pending action.
- `AssistantRuntime.executeCommand` routes utterances to pending clarification tasks; its caller passes the normal approval argument, default false. `resumePendingTask` itself defaults approval to true, so confirm all callsites before adjusting that default.
- Baseline `testDebugUnitTest`: 555 tests, 9 failures. Confirmed likely defects: async `ConversationManager.addMessage` makes immediate `getHistory()` return empty; camera and screen intents overlap; screen mode detects `ai` inside `explain`; WhatsApp standard filter uses package substring and includes WhatsApp Business; tool and engine keep separate auto-reply state. Other failures may be stale expectations: 5 vs 4 default routines, obsolete recovery strategies in a test, and time-dependent daily briefing assertions.
- Daily briefings include a date only in morning mode; the test can run in evening/bedtime and fails its unconditional date assertion. The adaptive bedtime template is intentionally different, so test expectations must account for style or the date should be a common field by contract.
- `WhatsAppTool` writes auto-reply mode to `AutoReplyPolicyManager`, which incoming-message handlers read; `UniversalMessagingEngine.autoReplyMode` is a separate field used only by its own setters and tests, so tool configuration is invisible through the engine API.
- For WhatsApp filtering, `NotificationStore.getRecentNotifications(appFilter)` uses substring matching; `com.whatsapp.w4b` matches `com.whatsapp`, causing standard-only reads to include business messages.
- First Gradle attempt failed because Java was not on PATH. JDK 17 is available at `/home/shanu/jdk/jdk-17.0.13+11`; baseline suite is now running with that JDK.
- Focused regressions confirmed and fixed the failures; one additional run caught that screen mode fell back to `driving` after preventing the substring false match (`ai` inside `explain`). Mode selection now recognizes error, extract, summarize, explain, describe, and read requests before ambient modes.
- WhatsApp package filtering now runs inside `NotificationStore` before applying the result limit. The regression adds 24 newer business notifications and confirms an older standard notification remains visible to a standard-only read.
- Final verification: `testDebugUnitTest` passes all 556 tests; `assembleDebug` succeeds; no Android device is connected for hardware verification.

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| Review shared high-impact execution paths first, then broaden through storage, voice, and service flows. | Bugs there can affect many features or cause unauthorized side effects. |
| Reuse existing pending-approval checkpoint and resume flow for approval-gated tool actions. | The code already persists and replays approved actions; adding another flow is unnecessary. |
| Treat 5 of 9 baseline failures as probable code defects, and inspect the remaining 4 before changing expectations. | Several assertions conflict with explicit adaptive behavior or newer feature additions. |

## Issues Encountered
| Issue | Resolution |
|-------|------------|

## Resources
-
