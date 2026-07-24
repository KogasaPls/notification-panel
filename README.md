# Notification Panel

A RuneLite plugin that shows game notifications in an overlay panel instead of your OS notification
tray. Useful if you run several clients at once, and it behaves the same on every platform.

![image demo](https://user-images.githubusercontent.com/87504405/180604834-a8cd83af-46b8-4095-abf9-74632a4aba24.png)

## Settings

Most settings are self-explanatory.

* A duration of 0 keeps a notification until newer ones push it out. With show time on, its label
  counts up from when it arrived.
* "Show notifications by default" decides what happens to a notification matching no rule. A
  notification matching an enabled rule is always shown, so turning this off makes your rules an
  allowlist.
* The default background color and opacity live in the sidebar rather than here, next to the Show
  test notification button that previews them.

Alt-click a panel border to drag its width like any other overlay. Shift-right-click will clear all
notifications, except the pinned test notification: it is a setting rather than a notification, so
turn it off with the same button that turned it on.

## Notification Panel Rules

The Notification Panel Rules button in the RuneLite toolbar opens the sidebar. A rule matches
notifications by wildcard pattern and can override the background color or opacity.
The Default formatting row above the list sets what every notification starts with. When "Show notifications by default" is
disabled, these rules determine when a notification will be displayed.

Color and opacity resolve separately, each taken from the topmost enabled rule that matches and sets
it. Given "You received (quantity) (item)," one rule can match the quantity to set the opacity and a
later one can match the item to set the color.

### Wildcard Patterns

`*` matches any run of characters including none, every other character is literal, and matching
ignores case.

A pattern has to describe the whole message, so wrapping with `*` is how you match substrings. `dragon` matches
only the message "dragon", while `*dragon*` matches any message containing "dragon". `Your*thrall*grave.`
matches "Your lesser thrall returns to the grave."

## Upgrading from before 2.0.0

Older versions matched with regular expressions, so nearly every pattern translates exactly and is
imported switched on: `.*dragon.*` becomes `*dragon*`, `^dragon$` becomes `dragon`, and a leading or
trailing `.*` becomes a `*` in the same place.

Your two parallel lists of regex patterns and format strings are migrated into the rule list once,
the first time the plugin loads after updating. Each non-empty row becomes one rule, up to a limit
of 100. The original values are kept, hidden, so nothing is destroyed.

A row that doesn't translate cleanly arrives disabled rather than dropped, flagged with what it
needs:

* A lone `.` matched exactly one character, where `*` matches any run, so `level .` becomes
  `level *` and would match more than it used to. Review it and switch it on. A pattern built only
  from dots collapses to a bare `*` and needs rewriting instead.
* Alternation, groups, and character classes have no wildcard equivalent, so these keep their
  original text for you to rewrite by hand.
* A `hide` rule, since rules can no longer hide anything. Use show notifications by default instead.
* A per-rule `duration=` or `showTime=`, which are gone. Duration and show time are now settings for
  the whole panel.
* A missing pattern, or an invalid color or opacity token.
* A row past the end of the shorter of the two lists. The old plugin paired the lists by position
  and ignored the leftovers, so these never applied; they are imported off so you can see them.

Matching now ignores case, which widens each pattern slightly.

**Font Style** is a plain Small/Regular/Bold choice again. RuneLite had turned its font setting into
a picker over every font installed on your system; if you had chosen one of those, it no longer reads
and falls back to Bold.

## Video demo

https://user-images.githubusercontent.com/87504405/180604701-3876d03f-e058-418c-a545-199b737b8293.mp4
