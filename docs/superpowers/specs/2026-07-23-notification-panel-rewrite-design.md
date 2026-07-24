# Notification Panel Rewrite Design

**Date:** 2026-07-23

**Status:** Superseded in part by the shipped implementation. Kept as the record of the rewrite's
reasoning, not as a description of current behavior. Where the two disagree, the code and
`README.md` win.

**Published baseline:** `f9d127674ac87fbb9283b73c8b86babbca4134f6`

## Changes made after this document was approved

Three decisions were reversed during implementation. They are noted inline below, and summarized
here so nothing in this document is read as current:

1. **Matching uses wildcards, not RE2/J regular expressions.** Patterns are simple globs where `*`
   matches any run of characters and everything else is literal, matched case-insensitively by an
   in-repo two-pointer scan (`Wildcards`). This removes the third-party dependency the design
   assumed, so no Plugin Hub dependency verification is needed. Legacy regexes are translated where
   the translation is faithful and flagged or disabled where it is not.
2. **Visibility is a single global switch.** The per-rule `SHOW`/`HIDE`/`INHERIT` override is gone.
   The `visibility` config key now means "show a notification that matches no rule," and a
   notification matching an enabled rule is always shown. Rules can format and reveal, never hide.
3. **A rule needs no formatting attribute.** Because a matching rule reveals its notification, an
   override-free rule is a useful allowlist entry, so the "at least one attribute" constraint was
   dropped from both the editor and the stored schema.

## Purpose

Rewrite Notification Panel around an instance-owned, deterministic state core while preserving the published plugin's documented behavior and existing ordinary configuration values. The rewrite must eliminate per-notification threads, cross-thread UI mutation, static lifecycle state, stale render caches, unsafe regular-expression evaluation, and parallel-line rule configuration.

The new design keeps the RuneLite plugin entry point, overlay behavior, configuration group, and existing ordinary config keys. It replaces the internal implementation rather than incrementally adapting the current `Notification`, `FormatOption`, timer, and static-queue design.

## Goals

- Display RuneLite notifications in a movable, resizable overlay.
- Preserve existing ordinary settings and their config keys:
  - `expireTime`
  - `timeUnit`
  - `numToShow`
  - `showTime`
  - `fontType`
  - `bgColor`
  - `opacity`
  - `visibility`
- Preserve documented conditional-format behavior:
  - Rules use regular expressions.
  - Matching searches within the notification message.
  - A rule may override background color, opacity, and visibility.
  - When multiple rules match, the first matching rule that specifies an attribute wins that attribute.
- Replace the two parallel multiline rule fields with a structured rule editor.
- Migrate legacy `regexList` and `colorList` configuration exactly once without deleting the legacy source values.
- Make seconds-based lifetime track wall time and tick-based lifetime track game ticks.
- Keep active notifications unchanged when ordinary settings or rules change, except that lowering `numToShow` immediately removes the oldest excess entries.
- Keep balanced, TeX-style wrapping for normal messages while bounding worst-case formatting work.
- Make the domain behavior testable without starting RuneLite.

## Non-goals

- Reformatting or retiming notifications that are already active.
- Restoring the undocumented `duration` and `showTime` conditional-format tokens.
- Clearing a single notification from the overlay.
- Rule import/export beyond the automatic legacy migration.
- A generic event bus, repository framework, rendering framework, or interface around every class.
- Pixel-perfect compatibility with the old wrapper's premature line breaks or per-token truncation.

## Architecture

The rewrite uses a deep core with thin RuneLite adapters:

```text
RuneLite events ───────────────┐
                              v
Rule configuration ──> NotificationState ──> immutable snapshots ──> Overlay
                              ^
                              |
                  Clock and GameTick inputs

Legacy config ──> RuleConfigStore ──> RuleSet
                         ^
                         |
                  Rule editor panel
```

Implementation follows the current RuneLite example-plugin build, lifecycle, overlay, configuration, and sidebar conventions. The pure core is organized as a few deep classes with nested immutable result values; production interfaces are introduced only for a real runtime boundary, never solely to support mocks. JUnit tests and the conventional manual `PluginTest` launcher supplement thin adapter tests without adding a custom harness.

### Module boundaries

#### `NotificationState`

`NotificationState` is the deep core module. It owns:

- Current future-facing `NotificationState.Policy`
- Active notification ordering
- Maximum-count eviction
- Per-notification style and lifetime snapshots
- Tick sequence
- Expiration pruning
- Countdown and age calculation
- Immutable render snapshots

It owns no RuneLite overlays, Swing widgets, `PanelComponent` objects, timers, executors, or static state.

Its external interface is:

```java
final class NotificationState
{
    void accept(String message);
    void updatePolicy(Policy policy);
    void onGameTick();
    void clear();
    List<Snapshot> snapshot();
}
```

`NotificationState` receives a `java.time.Clock` in its constructor. Production uses `Clock.systemUTC()` and tests use a fixed or mutable clock.

`updatePolicy` changes only future notifications. If the new maximum count is smaller than the active count, it removes the oldest excess notifications immediately.

#### `RuleSet`

`RuleSet` validates immutable `NotificationRule` values, compiles their patterns, and resolves a message into a partial style. Compilation diagnostics and partial overrides are nested immutable result values instead of separate public modules.

Rules are evaluated in stored order. Matching is an unanchored substring search. For every matching rule, the resolver fills only style attributes that have not already been filled. Resolution may stop once background color and opacity are both resolved. Remaining attributes come from ordinary defaults.

Invalid rules never enter a usable `RuleSet`. The editor blocks saving newly invalid rules. Invalid legacy rules are migrated as disabled records with a visible migration note.

> **Superseded:** the approved design compiled patterns with RE2/J and matched with
> `Matcher.find()`. The shipped implementation wraps each pattern in leading and trailing stars and
> matches with `Wildcards`, a two-pointer scan whose backtracking is bounded by the most recent
> `*`. Worst-case cost is O(pattern x message) rather than linear, which measures at roughly 8 ms
> for a full 100-rule set at the documented pattern and message limits. Resolution also no longer
> tracks visibility, only whether any enabled rule matched — see changes 1 and 2 above.

#### `RuleConfigStore`

`RuleConfigStore` is the small RuneLite `ConfigManager` adapter. Serialization and migration logic live in pure collaborators so they can be tested without RuneLite.

Structured rules are stored atomically in one hidden config key:

```text
group: notificationpanel
key: rulesV1
```

The value is a JSON envelope:

```json
{
  "schemaVersion": 1,
  "migrationWarnings": [],
  "rules": [
    {
      "id": "7df65dc5-c46f-450e-9152-a1959767b65f",
      "name": "Rare drops",
      "enabled": true,
      "pattern": "dragon warhammer",
      "backgroundColor": "#BF616A",
      "opacityPercent": 90,
      "migrationNote": null
    }
  ]
}
```

Array order is rule priority. `backgroundColor` and `opacityPercent` may be `null`.

> **Superseded:** the approved design carried a per-rule `"visibility"` field of `INHERIT`,
> `SHOW`, or `HIDE`. The shipped schema has no such field — see change 2 above.

RuneLite already supplies Gson; the rewrite does not add another JSON dependency.

#### `NotificationText`

`NotificationText` is a pure layout module. It bounds accepted text and wraps it from a maximum pixel width and text-measurement function. Production adapts `FontMetrics`; tests use a deterministic fake measurer.

For up to 256 breakable tokens it uses dynamic programming to minimize the sum of squared unused width for every line except the last. This preserves the preferred TeX-style balanced output.

For more than 256 tokens it uses a linear greedy wrapper. Before either algorithm, accepted notification text is limited to 2,048 Unicode code points. Longer input retains its first 2,047 code points and appends one ellipsis as the final code point. Therefore:

- Dynamic-programming work is bounded by 65,536 candidate transitions per notification.
- Dense anomalous messages use linear wrapping.
- Matching and displayed content share the same bounded text.
- No individual token is silently shortened.

Break opportunities occur after Unicode whitespace, `/`, and `\`. A token wider than the available line width is split at Unicode code-point boundaries so resizing remains effective. The implementation must not split a surrogate pair.

#### RuneLite adapters

`NotificationPanelPlugin` translates RuneLite events into core operations and owns lifecycle setup and teardown.

`NotificationPanelOverlay` asks the state for a snapshot and creates the RuneLite component tree during each render. At most five notifications are rendered, so the design intentionally avoids a mutable cached component graph and dirty flag.

`RuleEditorPanel` is a standard RuneLite sidebar panel registered through `ClientToolbar`. Swing state remains on the event-dispatch thread. Saving rules writes one JSON value through `RuleConfigStore`; the resulting configuration change is transferred to RuneLite's client thread before updating `NotificationState`.

## Domain model

### `NotificationState.Policy`

An immutable value containing:

- Maximum shown, constrained to 1 through 5
- Default `NotificationState.Style`
- `NotificationState.Lifetime`
- Whether the time label is shown
- `RuleSet`

### `NotificationState.Style`

An immutable value containing:

- Background RGB color
- Opacity percent from 0 through 100
- Visibility
- Font type

Opacity remains a percentage throughout the core. The overlay converts it exactly once to an 8-bit alpha value using rounded integer arithmetic:

```text
alpha = (percent * 255 + 50) / 100
```

### `NotificationRule`

An immutable stored record containing:

- Stable UUID
- Display name, 1 through 64 Unicode code points
- Wildcard pattern, 1 through 512 Unicode code points
- Enabled flag
- Optional background RGB color
- Optional opacity percentage
- Optional migration note

New rule names default to `Rule N`; names do not have to be unique and do not affect matching.

The editor and store support at most 100 rules. This bounds matching to at most 100 searches over at most 2,048 code points.

> **Superseded:** the approved design also carried a visibility override and required at least one
> formatting attribute. Neither survives — an override-free rule is a valid allowlist entry. See
> changes 2 and 3 above.

### Active notification

An internal immutable record containing:

- Bounded message
- Resolved style
- Whether to show time
- Creation `Instant`
- Creation tick
- Optional expiration `Instant`
- Optional expiration tick

Duration zero produces no expiration. Hidden notifications are rejected before storage.

### `NotificationState.Snapshot`

An immutable render value containing:

- Message
- Resolved background color and opacity
- Font type
- Optional already-formatted time label

Time labels support hours and do not wrap at 60 minutes:

- Finite seconds: `1h 2m 3s`
- Infinite seconds age: `1h 2m 3s ago`
- Finite ticks: `3 ticks`
- Infinite tick age: `42 ticks ago`

Singular labels use `1 tick`.

## State and event flow

### Startup

1. Load ordinary configuration.
2. Load `rulesV1`.
3. If `rulesV1` is absent, run legacy migration and store the result.
4. Compile enabled valid rules.
5. Construct and install the future `NotificationState.Policy`.
6. Add the overlay.
7. Register the rule-editor navigation button.
8. Mark the plugin running.

If structured rule JSON is corrupt, startup continues with no conditional rules and ordinary defaults. The corrupt value is not overwritten. The editor shows a blocking error banner and allows the user to reset the structured rules.

### Notification fired

1. The plugin transfers handling to RuneLite's client thread.
2. If the plugin is no longer running, discard the delayed event.
3. Bound the message to 2,048 Unicode code points.
4. Resolve conditional attributes using the current future policy.
5. Fill unresolved attributes from ordinary defaults.
6. If final visibility is hidden, stop.
7. Snapshot the current lifetime and style.
8. Append the notification.
9. Remove oldest entries until the maximum count is satisfied.

### Render

1. `snapshot()` reads the injected clock and current tick sequence.
2. Remove all expired entries by identity.
3. Calculate time labels.
4. Return an immutable ordered list.
5. The overlay wraps and renders each entry using the current overlay width and configured font metrics.

Overlay height is the sum of rendered notification heights and gaps. Width remains controlled by RuneLite's resizable overlay behavior. Long unbreakable content hard-wraps rather than forcing a larger minimum width.

### Game tick

`onGameTick()` increments one state-owned tick sequence. Tick-based entries derive age and expiration from their stored creation and expiration ticks. No per-notification counter is mutated.

### Configuration change

For ordinary settings or `rulesV1`:

1. Load and validate a new `NotificationState.Policy`.
2. Transfer `updatePolicy` to the client thread.
3. Existing entries retain their snapshots.
4. A lower maximum immediately evicts the oldest excess entries.

Changing the time unit does not clear active notifications.

### Clear and shutdown

The clear overlay action verifies both the target overlay and menu option, then calls `NotificationState.clear()`.

Shutdown:

1. Mark the plugin not running.
2. Remove the navigation button.
3. Remove the overlay.
4. Clear state.

There are no scheduled tasks to cancel.

## Structured rule editor

The ordinary RuneLite config page continues to host duration, time unit, maximum shown, time label, font, default color, default opacity, and default visibility.

Conditional rules move to a dedicated RuneLite sidebar entry. RuneLite's native config panel supports only fixed annotated fields and cannot render an arbitrary number of composite rule rows.

### Rule list screen

The list screen shows rules in priority order. Each row displays:

- Enabled state
- Rule name
- Short pattern preview
- Short style summary
- Migration warning indicator when applicable

Actions:

- Add rule
- Edit selected rule
- Enable or disable
- Move up
- Move down
- Delete with confirmation

Reordering and enable/disable operations save immediately. Delete requires confirmation.

### Add/edit screen

Fields:

- Name
- Enabled
- Regex pattern
- Optional background color
- Optional opacity
- Visibility override

The editor validates continuously and shows an exact inline error. Save is disabled until:

- Name and pattern satisfy their length constraints.
- RE2/J compiles the pattern.
- Opacity is absent or between 0 and 100.
- At least one formatting attribute is specified.

Save writes the complete JSON envelope once. Cancel discards the draft. Successfully editing a migrated rule clears its migration note.

The first release does not include a regex test console, live notification preview, drag-and-drop ordering, or rule import/export.

## Legacy migration

Migration runs only when `rulesV1` is absent.

1. Read `regexList` and `colorList`.
2. Split both with `\\R` while preserving trailing and empty entries.
3. Iterate through the larger line count so no unmatched row disappears.
4. Skip a row only when both pattern and format line are empty.
5. Parse only documented legacy options:
   - `#RRGGBB`
   - `opacity=n`
   - `hide`
   - `show`
6. Create rules named `Imported rule N`.
7. Compile patterns with RE2/J.
8. Disable and annotate rows with:
   - Missing pattern
   - No recognized formatting attributes
   - Invalid color or opacity
   - Unsupported or invalid RE2/J syntax
9. Preserve successfully parsed attributes even when another token is invalid.
10. If more than 100 rows exist, migrate the first 100 and add an envelope warning.
11. Atomically write `rulesV1`.

The old `regexList` and `colorList` values remain stored but become hidden config items and are never read again while `rulesV1` exists. This provides rollback evidence without allowing two sources of truth.

Resetting plugin configuration unsets `rulesV1`, `regexList`, and `colorList` together, preventing an old legacy configuration from unexpectedly remigrating after reset.

Migration is idempotent: once `rulesV1` exists, no legacy input is parsed or written.

## Error handling and safety

- The in-repo `Wildcards` matcher replaces Java's backtracking regex engine. Its backtracking is
  bounded by the most recent `*`, so a pattern shaped like `*a*a*...*b` cannot hang the client
  thread against a long non-matching message.
- New invalid rules cannot be saved.
- Migrated invalid rules remain visible, disabled, and repairable.
- Corrupt structured JSON never gets overwritten automatically.
- Rule and message limits bound parsing, matching, and wrapping work.
- Structured JSON and each legacy rule-config value are rejected before parsing when longer than 262,144 UTF-16 code units.
- Null notification messages normalize to an empty string.
- Empty messages are valid and can render a time label.
- All core collections are instance-owned and client-thread-confined.
- Swing drafts are event-dispatch-thread-confined.
- RuneLite component collections are created and mutated only during rendering.
- No timer, executor, daemon thread, filesystem access, network access, reflection-based option lookup, or unchecked type cast is required by the runtime design.

> **Superseded:** the approved design added `com.google.re2j:re2j:1.8` and therefore required
> Plugin Hub third-party dependency verification, and kept `runelite-plugin.properties` in the
> Hub's `gradle` build mode to preserve the custom build. The shipped plugin adds no third-party
> dependency at all, so neither is needed: it uses `build=standard`, which the Hub's own tooling
> recommends "unless you have dependencies or other changes to your build.gradle". In that mode
> the Hub replaces `build.gradle` and `settings.gradle` and jars only `sourceSets.main.output`, so
> the test dependencies never participate in a Hub build, and the published version comes from
> `runelite-plugin.properties` rather than the Gradle project.

## Build design

- Base the build on RuneLite's current example-plugin structure.
- Resolve RuneLite as `latest.release`.
- Target Java 11 with `options.release.set(11)`.
- Use the official example plugin's Gradle 8.10 wrapper.
- Run CI on Java 11.
- Remove Lombok from production code; the new values use explicit constructors and accessors.
- Enable compiler lint output and eliminate unchecked warnings in plugin code.
- Add no dependencies beyond the test framework supplied by the build.

Pinning only the compiler's `--release` proved insufficient: it leaves the Gradle daemon and the
test JVM on whatever JDK is on the developer's `PATH`, and Mockito's inline mock maker cannot
instrument classes on a recent JDK. The build therefore pins the JVM in two more places, both
matching the Plugin Hub's Java 11 build environment:

- `gradle/gradle-daemon-jvm.properties` selects the daemon JVM, so the build script itself
  compiles on a JDK that Gradle 8.10 supports.
- A `java.toolchain` of 11 in `build.gradle` selects the compile and test JVM.

## Testing strategy

Tests use the same deep module interfaces as production callers.

### Rule tests

- Substring matching
- First matching rule per attribute
- Defaults filling unresolved attributes
- Disabled rules
- RE2/J-invalid patterns
- Rule, name, and pattern limits
- Opacity boundaries
- `SHOW` overriding hidden defaults
- `HIDE` overriding visible defaults

### Migration and persistence tests

- Aligned legacy rows
- Empty interior and trailing rows
- CRLF input
- Unequal list lengths
- Invalid tokens
- Java-only regex features disabled with a note
- More than 100 legacy rows
- Idempotent migration
- Corrupt JSON safe fallback
- Reset semantics
- JSON round trip and stable ordering

### State tests

- FIFO ordering
- Maximum-count eviction
- Immediate trimming when maximum decreases
- Future-only policy changes
- Wall-clock expiration
- Game-tick expiration
- Duration zero
- Clear
- Hidden-notification rejection
- Multiple staggered lifetimes
- Correct identity removal
- Hour-scale labels
- Singular and plural tick labels
- Shutdown/delayed-event guard behavior at the adapter level

### Wrapping tests

- Balanced expected line breaks with a deterministic measurer
- Last-line cost exclusion
- Spaces, `/`, and `\` break opportunities
- Unicode and surrogate-pair safety
- Long unbreakable token hard wrapping
- Exactly 256 tokens use dynamic programming
- More than 256 tokens use greedy wrapping
- Exactly 2,048 code points remain intact
- Longer input truncates once with an ellipsis
- Empty input
- Narrow and wide overlay widths

### Adapter and UI tests

- Startup and shutdown add/remove overlay and navigation button.
- Off-thread notifications are transferred to the client thread.
- Delayed events after shutdown are discarded.
- Clear menu events verify the target overlay.
- Editor save, cancel, reorder, enable/disable, delete confirmation, and validation state run on Swing's event-dispatch thread.
- The smoke launcher starts the rewritten plugin against current RuneLite.

`./gradlew test` must discover and execute tests; a successful zero-test report is a failure of the CI acceptance check.

## Delivery phases

### Phase 1: Build and pure rule foundation

- Modernize the build.
- Add executable tests.
- Implement rule values, JSON codec, RE2/J compilation, resolution, and legacy migration.
- Keep the published runtime active while the new pure modules are tested alongside it.

### Phase 2: State and wrapping core

- Implement `NotificationState`, policy/style/lifetime values, snapshots, clock handling, tick handling, and bounded balanced wrapping.
- Prove timer-free expiration, identity-safe removal, and formatting bounds through unit tests.

### Phase 3: Structured rule editor

- Add the navigation entry and list/add/edit screens.
- Wire atomic storage, validation, ordering, and migration diagnostics.
- Keep the editor independent of active notification instances.

### Phase 4: RuneLite adapter cutover

- Replace the published plugin and overlay internals with the new core.
- Preserve plugin class names, config group, ordinary config keys, metadata, overlay placement, resizing, and clear action.
- Remove timers, static queues, cached boxes, `FormatOption` subclasses, old parser classes, and unused injections.

### Phase 5: Verification and release preparation

- Run the complete automated suite.
- Run the smoke launcher against current RuneLite.
- Manually verify positioning, resizing, font choices, seconds and ticks, never-expire age, multi-client use, rule migration, editor behavior, clear, disable/re-enable, and profile reset.
- Confirm the Plugin Hub dependency-verification and build checks for RE2/J.
- Update README documentation and screenshots for the structured editor.

## Acceptance criteria

- The plugin contains no per-notification timer or static mutable runtime state.
- Expiration always removes the notification that expired.
- Clear, reconfiguration, eviction, disable, and re-enable cannot affect later notifications through orphaned work.
- Existing notifications do not change style or lifetime after configuration edits.
- Default 75% opacity renders as 75% alpha.
- Documented substring rules work.
- Malformed or unsupported patterns cannot break plugin startup.
- Multiple-rule per-attribute precedence matches the published README.
- Legacy rule configuration migrates once and remains recoverable.
- The editor never relies on matching rows across two text boxes.
- Normal notifications retain balanced wrapping.
- Formatting and matching work are bounded by the documented limits.
- Active notification text is never shortened per token.
- Java 11 build and test commands pass with nonzero executed tests.
- The source compiles against RuneLite `latest.release`.
- Tracked configuration and runtime state are cleanly removed on reset or shutdown as specified.
