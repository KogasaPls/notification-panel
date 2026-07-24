# Notification Panel

This is a RuneLite plugin that displays notifications in an overlay panel. It is much more useful than native
notifications when using multiple clients, and it's OS-agnostic.

![image demo](https://user-images.githubusercontent.com/87504405/180604834-a8cd83af-46b8-4095-abf9-74632a4aba24.png)

## Settings

These live in the normal plugin config panel and behave as they always have.

* **Maximum shown**. How many notifications are visible at once, from 1 to 5. Lowering it trims the oldest active
  notifications right away; raising it only affects future notifications.
* **Show time**. Show or hide the age or countdown label on each notification.
* **Duration** and **Time unit**. How long each notification lasts, in seconds or ticks. A duration of 0 keeps a
  notification until newer ones push it out; with "show time" on, the label then counts up from when it arrived.
* **Font**. "Small," "regular," or "bold."
* **Background color**, **Opacity**, and **Visibility**. The defaults for notifications that no rule overrides.

Config changes apply to future notifications only, with one exception: lowering the maximum trims what is already on
screen. The underlying config keys are unchanged, so existing setups carry over.

You can reposition the panel and lock it to anchors like any other overlay, and adjust its width by alt-clicking a
border and dragging. Shift-right-clicking the panel shows a **Clear** option that removes all current notifications.

## Notification rules

Conditional formatting now lives in its own sidebar panel. Look for the **Notification rules** button in the RuneLite
toolbar. A rule matches notifications by pattern and overrides the background color, opacity, or visibility.

Each rule has a name, an enabled toggle, a pattern, and at least one override (background color, opacity, or visibility,
where visibility is inherit, show, or hide).

Rules are an ordered list. When a notification arrives, each override attribute is taken from the **first** enabled rule
that matches and sets that attribute, so different attributes can come from different rules. If a notification reads
"You received (quantity) (item)," one rule can match the quantity to set the opacity and a later rule can match the item
to set the color.

### Editing rules

The sidebar list shows each rule's enabled state, name, a single-line pattern preview, and a summary of its overrides.
The buttons are Add, Edit, Enable/Disable, Up, Down, and Delete, and Delete asks for confirmation. The edit form
validates as you type: Save stays disabled until the name, pattern, and overrides are all valid. Every successful change
is saved immediately.

### Patterns

Patterns use simple wildcard syntax: `*` matches any run of characters (including none), and every other character is
literal. Matching ignores case.

A pattern matches if it occurs anywhere in the message, so `dragon` and `*dragon*` both match any message containing
"dragon", and `Your*thrall*grave` matches "Your lesser thrall returns to the grave."

> **Upgrading from a version before 1.1.0?** Those versions matched with regular expressions. Existing rules are
> migrated to wildcards automatically where the translation is unambiguous. A pattern that relies on regex features with
> no wildcard equivalent is imported disabled and flagged, so you may need to rewrite the more complex ones by hand.

## Migrating from the old Regex/Options lists

If you previously configured the two parallel lists (regex patterns and format strings), they are migrated once, the
first time the plugin loads after updating, into the new rule list. You no longer align two lists by hand.

* Each non-empty row becomes one rule named `Imported rule N`.
* A row is imported disabled and annotated, rather than dropped, when something about it is off: a missing pattern, a
  pattern that can't be expressed as a wildcard, an invalid color or opacity, or no recognized override.
* Only the first 100 rows are migrated. A warning notes if there were more.
* The old undocumented `duration` and `showTime` tokens are no longer recognized and are not migrated.

Older versions matched a pattern against the whole notification, while wildcards match anywhere in it. A migrated
pattern can therefore fire on more messages than it used to (for example, a plain `Congratulations` now matches any
message containing that word). Look over your imported rules after upgrading.

The original config values are kept (hidden), so migration never destroys your old data.

## Limits and rendering

* Messages are capped at 2,048 Unicode code points. Longer messages are truncated with a single ellipsis.
* Lines wrap to the panel width. Messages up to 256 breakable tokens use a balanced (minimum-raggedness) algorithm, and
  longer ones fall back to greedy wrapping. Very narrow panels hard-wrap without splitting surrogate pairs.

## Reset and recovery

Resetting the plugin through RuneLite's normal config reset clears the rules along with the ordinary settings. If the
stored rule data is ever corrupted, the sidebar shows a banner, disables editing, and offers **Reset rules**, which
clears the rule storage and gives you a fresh empty list. Corrupt data is never silently overwritten.

## Video Demo

https://user-images.githubusercontent.com/87504405/180604701-3876d03f-e058-418c-a545-199b737b8293.mp4
