# Task Plan: JARVIS Repo Bug Sweep

## Goal
Find and repair concrete user-impacting defects and meaningful quality issues across the JARVIS Android repo, using focused regression checks and available build verification.

## Next Step
Deliver the verified repo sweep and note the device-only checks that could not run.

## Current Phase
Phase 5

## Phases

### Phase 1: Requirements & Discovery
- [x] Confirm repo-wide coverage and include meaningful reliability, security, test-gap, performance, and dead-code issues
- [x] Map production and test footprint, build setup, docs, and device availability
- **Status:** complete

### Phase 2: Planning & Structure
- [x] Prioritize concrete defects in high-impact execution paths
- [x] Establish repair and regression-check workflow
- **Status:** complete

### Phase 3: Implementation
- [x] Fix approval bypass and require an explicit positive reply
- [x] Fix immediate conversation history, ambiguous intent routing, shared auto-reply state, exact platform filtering, and date presence in adaptive briefings
- [x] Correct two stale test expectations for removed recovery strategies and the added gym routine
- [x] Add focused approval-reply regression coverage
- **Status:** complete

### Phase 4: Testing & Verification
- [x] Run focused regressions for approval, history, intent routing, auto-reply state, WhatsApp filtering, and briefing output
- [x] Run full unit suite and debug APK build
- [x] Document hardware checks that could not run
- **Status:** complete

### Phase 5: Delivery
- [x] Review diff and report fixes, verification, and remaining risks
- **Status:** complete

## Decisions Made
| Decision | Rationale |
|----------|-----------|
| Fix concrete user-impacting issues and meaningful quality defects; skip style-only and speculative cleanup. | User chose all quality issues; this keeps the sweep evidence-based. |
| Work in small batches and add regression tests for non-trivial bug fixes. | Minimizes regressions and follows the approved plan. |

## Errors Encountered
| Error | Resolution |
|-------|------------|
| Initial Gradle test attempt had no Java in PATH. | Found repo JDK at `/home/shanu/jdk/jdk-17.0.13+11` and reran with JAVA_HOME. |
