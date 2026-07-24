# Notification Panel

This is a RuneLite plugin that displays notifications in an overlay panel. It is much more useful than native
notifications when using multiple clients, and it's OS-agnostic.

![image demo](https://user-images.githubusercontent.com/87504405/180604834-a8cd83af-46b8-4095-abf9-74632a4aba24.png)

## Settings

These live in the normal plugin config panel.

* **Maximum shown**. How many notifications are visible at once, from 1 to 5. Lowering it trims the oldest active
  notifications right away; raising it only affects future notifications.
* **Show time**. Show or hide the age or countdown label on each notification.
* **Duration** and **Time unit**. How long each notification lasts, in seconds or ticks. A duration of 0 keeps a
  notification until newer ones push it out; with "show time" on, the label then counts up from when it arrived.
* **Font**. "Small," "regular," or "bold."
* **Show notifications by default**. Whether a notification that matches no rule is shown. Notifications matching an
  enabled rule are always shown, so turning this off makes your rules an allowlist.

The default **background color** and **opacity** are set in the sidebar instead, since a rule can override exactly
those two. **Show test notification** is a sidebar button for the same reason: it previews them.

Config changes apply to future notifications only, with one exception: lowering the maximum trims what is already on
screen. The underlying config keys are unchanged, so existing setups carry over.

You can reposition the panel and lock it to anchors like any other overlay, and adjust its width by alt-clicking a
border and dragging. Shift-right-clicking the panel shows a **Clear** option that removes all current notifications,
and the sidebar has a **Clear notifications** button that does the same.

Every notification spans the panel width, with text centred and wrapped to fit. The panel never resizes itself.

## Notification rules

Conditional formatting lives in the same sidebar panel. Look for the **Notification rules** button in the RuneLite
toolbar. A rule matches notifications by pattern and can override the background color or opacity.

The **Default formatting** row sits above the rule list and reads like a rule, because it behaves like one: it
carries the same **Style** summary, and a rule overriding the background or opacity is overriding exactly that. Its
**Edit** button opens the background and opacity, which apply as you change them, along with the test-notification
toggle so you can see them applied.

Each rule has a name, an enabled toggle, a pattern, and optional background-color and opacity overrides. A rule with no
overrides is still useful: because a matching enabled rule always shows its notification, an override-free rule acts as
an allowlist entry when "Show notifications by default" is off. Rules can only show and format notifications, never hide
them; to hide a notification, leave it unmatched with the default turned off.

Rules are an ordered list. When a notification arrives, each override attribute is taken from the **first** enabled rule
that matches and sets that attribute, so different attributes can come from different rules. If a notification reads
"You received (quantity) (item)," one rule can match the quantity to set the opacity and a later rule can match the item
to set the color.

### Editing rules

The sidebar list shows each rule's enabled state, name, a single-line pattern preview, and a summary of its overrides.
The buttons are Add, Edit, Enable/Disable, Up, Down, and Delete, and Delete asks for confirmation. The edit form
validates as you type: Save stays disabled until the name and pattern are valid. Every successful change
is saved immediately.

### Patterns

Patterns use simple wildcard syntax: `*` matches any run of characters (including none), and every other character is
literal. Matching ignores case.

A pattern has to describe the **whole** message, so `*` is how you match part of one. `dragon` matches only a message
that is exactly "dragon", while `*dragon*` matches any message containing it. `Your*thrall*grave.` matches "Your
lesser thrall returns to the grave."

> **Upgrading from a version before 2.0.0?** Those versions matched with regular expressions, also against the whole
> message, so most patterns translate exactly: `.*dragon.*` becomes `*dragon*` and `^dragon$` becomes `dragon`. A
> pattern relying on regex features with no wildcard equivalent is imported disabled and flagged.

## Migrating from the old Regex/Options lists

If you previously configured the two parallel lists (regex patterns and format strings), they are migrated once, the
first time the plugin loads after updating, into the new rule list. You no longer align two lists by hand.

* Each non-empty row becomes one rule named `Imported rule N`.
* A row is imported disabled and annotated, rather than dropped, when something about it is off: a missing pattern, a
  pattern that can't be expressed as a wildcard, a pattern that would match more than it used to, or an invalid color
  or opacity token.
* Only the first 100 rows are migrated. A warning notes if there were more.
* The old undocumented `duration` and `showTime` tokens are no longer recognized and are not migrated.
* Per-rule `hide`/`show` no longer exists. A migrated `hide` rule is imported disabled with a note, since rules can no
  longer suppress a notification; use the global "Show notifications by default" switch instead.

Both the old and new matching describe the **whole** message, so nearly every pattern translates exactly and is
imported switched on: `.*dragon.*` becomes `*dragon*`, `^dragon$` becomes `dragon`, and a leading or trailing `.*`
becomes a `*` in the same place.

Two things arrive **turned off**:

* **A lone `.`**, which matched exactly one character where `*` matches any run, so `level .` becomes `level *` and
  would match more than it did. These keep their **converted** text, so reviewing one and switching it on is all that
  is needed. A pattern built only from lone dots (`.`, `..`, `...`) collapses to a bare `*` matching everything, and
  is treated as needing a rewrite instead.
* **Patterns with no wildcard equivalent** — alternation, groups, character classes. These keep their **original**
  text, since you have to rewrite them by hand.

Matching now ignores case, which widens every imported rule slightly; that is not flagged per rule, since it applies
to all of them. Anything turned off is flagged in the sidebar with what it needs.

The original config values are kept (hidden), so migration never destroys your old data.

> **Upgrading note.** The opacity percentage is now scaled to an alpha value, where it used to be used as one
> directly. The default of 75 renders considerably more opaque than it did before 2.0; lower it if you preferred the
> old look.

## Limits and rendering

* Messages are capped at 2,048 Unicode code points. Longer messages are truncated with a single ellipsis.
* Lines wrap to the panel width. Messages up to 256 breakable tokens use a balanced (minimum-raggedness) algorithm, and
  longer ones fall back to greedy wrapping. Very narrow panels hard-wrap without splitting surrogate pairs.

## Reset and recovery

Resetting the plugin through RuneLite's normal config reset clears the rules along with the ordinary settings. If the
stored rule data is ever corrupted, the sidebar shows a banner, disables editing, and offers **Reset rules**, which
replaces the rule storage with a fresh empty list. Corrupt data is never silently overwritten — only that button
discards it, and only when you press it.

**Reset rules** leaves your original Regex/Options lists alone, so the pre-2.0 configuration those migrated from stays
recoverable. It does not re-run the import either: you get an empty list, not your old rules back.

## Building

The Plugin Hub builds submissions on **Java 11**, and this repository targets it: `gradle/gradle-daemon-jvm.properties`
pins the Gradle daemon and `build.gradle` pins a Java 11 toolchain, so `./gradlew test` behaves the same regardless of
which JDK is on your `PATH`. You need a JDK 11 installed somewhere Gradle can find it; if none is present, Gradle says
so directly instead of failing in confusing ways further along.

`runelite-plugin.properties` sets `build=standard`, the Hub's recommended mode: the plugin has no third-party
dependency, so the Hub replaces `build.gradle` and `settings.gradle` with its own at submission time and compiles only
`src/main`. That makes this build file purely a local convenience — it is what runs the tests, the manual launcher, and
CI, none of which the Hub uses. Because the Hub reads the published version from `runelite-plugin.properties` in this
mode, that file is the single source of truth for the version and `build.gradle` reads it from there.

## Video Demo

https://user-images.githubusercontent.com/87504405/180604701-3876d03f-e058-418c-a545-199b737b8293.mp4
