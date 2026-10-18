# Voice Command Aliases Design

## Goal

Let users configure a short spoken phrase that invokes an existing JARVIS command.

## Design

- Add a “Voice Command Aliases” section to Settings with phrase and command fields, a list of saved pairs, and a delete action.
- Persist aliases in `UiPreferencesStore` as a dedicated preference map. Include that map in settings export/import so a user's configuration survives backup and restore.
- When `AssistantRuntime` receives a command, normalize the utterance and alias keys by trimming, lowercasing with `Locale.ROOT`, and collapsing repeated whitespace. Only an exact whole-utterance match expands; substitute the target phrase once.
- Send the expanded phrase through the existing `IntentResolver` and execution path. Aliases do not name tools or bypass existing confirmation, approval, or permission checks.
- Reject blank phrases, identical normalized alias keys, and duplicate keys. Replacing an existing alias key updates its target. Keep the feature deterministic and avoid recursive alias expansion.
- Keep old settings files compatible: absent alias data means no aliases.

## Scope and behavior

Aliases work for typed and recognized voice commands because both enter `AssistantRuntime.executeCommand`. They take effect on the next command without restarting the foreground service. Existing commands continue to work when no alias matches. This change does not add new underlying command capabilities or fuzzy matching.

## Validation

- Unit coverage for exact normalized matching, no partial matching, one-step expansion, and unmatched utterances.
- Store coverage for save/load and settings export/import compatibility.
- Build and install on the connected Android device, then verify an alias reaches the same existing command handling path.

## Alternatives considered

- Mapping aliases directly to tool names would duplicate command routing and could skip current policy checks.
- Hard-coded phrases would not satisfy user configurability.
- Fuzzy matching could trigger an unintended action from a similar utterance.

## Review notes

The alias is only a phrase rewrite; authorization and execution remain owned by the current runtime. Export/import uses the existing user-configuration backup path, which excludes credentials and runtime state.
