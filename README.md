# Notification Panel

This is a RuneLite plugin that displays notifications in an overlay panel. It is much more useful than native
notifications when using multiple clients, and it's OS-agnostic.

![image demo](https://user-images.githubusercontent.com/87504405/180604834-a8cd83af-46b8-4095-abf9-74632a4aba24.png)

## Settings

These live in the normal plugin config panel and behave as they always have:

* **Maximum shown** — how many notifications are visible at once, from 1 to 5. Lowering it trims the oldest active
  notifications immediately; raising it only affects future notifications.
* **Show time** — show or hide the age/countdown label on each notification.
* **Duration** and **Time unit** — how long each notification lasts, in seconds or ticks. A duration of 0 keeps
  notifications until they are pushed out by newer ones; with "show time" on, the label then shows the notification's
  age instead of a countdown.
* **Font** — "small," "regular," or "bold."
* **Background color**, **Opacity**, and **Visibility** — the defaults used for notifications that no rule overrides.

Config changes apply to future notifications only, except that lowering the maximum trims what's already on screen. The
underlying config keys are unchanged, so existing setups carry over.

The panel can be repositioned and locked to anchors like any other overlay, its width adjusted by alt-clicking a border
and dragging. Shift-right-clicking the panel shows a **Clear** option that removes all current notifications.

## Notification rules

Conditional formatting now lives in its own sidebar panel — look for the **Notification rules** button in the RuneLite
toolbar. A rule matches notifications by pattern and overrides one or more of background color, opacity, and visibility.

Each rule has:

* a **name** (for your reference),
* an **enabled** toggle,
* a **pattern**,
* and at least one override: **background color**, **opacity**, or **visibility** (inherit / show / hide).

Rules are an ordered list. When a notification arrives, each override attribute is taken from the **first** enabled rule
that matches and specifies that attribute. Different attributes can therefore come from different rules. For example, if
a notification reads "You received (quantity) (item)," one rule can match on the quantity to set opacity and a later rule
can match on the item to set the color.

### Editing rules

The sidebar list shows each rule's enabled state, name, a single-line pattern preview, and a summary of its overrides.
The buttons are **Add**, **Edit**, **Enable/Disable**, **Up**, **Down**, and **Delete** (Delete asks for confirmation).
The edit form validates as you type: **Save** stays disabled until the name, pattern, and overrides are all valid.
Every successful change is saved immediately.

### Patterns

Patterns use [regex](https://github.com/google/regex) syntax and are matched as a **substring search** (an unanchored
`find`), so `dragon` matches anywhere in the message. regex guarantees linear-time matching, which is why constructs
that require backtracking — most notably backreferences like `(a)\1` — are not supported; such a pattern is rejected in
the editor with an error rather than silently misbehaving.

## Migrating from the old Regex/Options lists

If you previously configured the two parallel lists (regex patterns and format strings), they are migrated **once**, the
first time the plugin loads after updating, into the new rule list. You no longer align two lists by hand.

* Each non-empty row becomes one rule named `Imported rule N`.
* Rows whose format could not be fully understood — an invalid color or opacity, a pattern regex can't compile, a row
  with no recognized override, or a missing pattern — are imported **disabled** and annotated so you can see what needs
  attention, rather than being dropped.
* Only the first 100 rows are migrated; a warning notes if there were more.
* The old undocumented `duration` and `showTime` tokens are no longer recognized and are not migrated.

The original config values are kept (hidden) so migration never destroys your old data.

## Limits and rendering

* Messages are capped at 2,048 Unicode code points; longer messages are truncated with a single ellipsis.
* Lines are wrapped to the panel width. Up to 256 breakable tokens are wrapped with a balanced (minimum-raggedness)
  algorithm; longer messages fall back to greedy wrapping. Very narrow panels hard-wrap without splitting surrogate
  pairs.

## Reset and recovery

Resetting the plugin through RuneLite's normal config reset clears the rules along with the ordinary settings. If the
stored rule data is ever corrupted, the sidebar shows a banner, disables editing, and offers **Reset rules**, which
clears the rule storage and starts you over with an empty list. Corrupt data is never silently overwritten.

## Video Demo

https://user-images.githubusercontent.com/87504405/180604701-3876d03f-e058-418c-a545-199b737b8293.mp4
