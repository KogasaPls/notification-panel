# Notification Panel

A RuneLite plugin that shows game notifications in an overlay panel instead of your OS notification
tray. Useful if you run several clients at once, and it behaves the same on every platform.

![image demo](https://user-images.githubusercontent.com/87504405/180604834-a8cd83af-46b8-4095-abf9-74632a4aba24.png)

## Settings

Most settings are self-explanatory.

* A duration of 0 keeps a notification until newer ones push it out. With show time on, its label
  counts up from when it arrived.
* "Default visibility" decides what happens to a notification matching no rule: "Panel and sidebar"
  puts it on screen and in the sidebar's Notifications list, "Sidebar only" keeps the record without
  putting it on screen, and "Hidden" drops it. A notification matching an enabled rule is shown
  unless a rule says otherwise, so a rule can work in either direction: set this to "Hidden" and use
  rules as an allowlist, or leave it showing and use a rule to quieten the handful of messages you
  don't want.
* "Default Color" and "Default Opacity" are what a notification is drawn with when no rule overrides
  it. "Show test notification" pins a sample notification that never expires, so you can see those
  two applied and have something to grab while moving or resizing the panel.
* Turning off "Show sidebar button" removes the plugin's button from the RuneLite toolbar. The rule
  editor lives behind that button, so turning it back on here is how you reach it again. Every
  setting stays reachable either way.

Alt-click a panel border to drag its width like any other overlay. Shift-right-click will clear all
notifications, except the pinned test notification: it is a setting rather than a notification, so
turn it off with the same setting that turned it on.

## Notification Panel Rules

The Notification Panel button in the RuneLite toolbar opens the sidebar, and turning off "Show
sidebar button" in the settings removes that button if you never use it. A rule matches
notifications by wildcard pattern and can override the background color, opacity, or visibility —
Show, Sidebar only, or Hide. A pattern too long to fit is clipped with an ellipsis in the list; hover
the rule to see more of it in a tooltip, along with the reason an imported rule arrived switched off.
A notification matched by no rule uses "Default Color" and "Default Opacity" from the settings, and
follows "Default visibility" for what happens to it.

Color, opacity, and visibility each resolve separately, taken from the topmost enabled rule that
matches and sets that attribute. Given "You received (quantity) (item)," one rule can match the
quantity to set the opacity and a later one can match the item to set the color. Visibility works
the same way: if no matching rule sets it, a notification matched by any enabled rule still shows;
"Default visibility" decides only for a notification that no enabled rule matched.

Visibility has three values. "Show" puts a matching notification on the panel and in the
Notifications list, "Sidebar only" keeps it in the list without putting it on the panel, and "Hide"
drops it entirely — a hidden notification is not recorded anywhere. Because a notification matched
by any enabled rule is shown unless a rule says otherwise, a colour-only rule promotes its matches
to the panel even when "Default visibility" is "Sidebar only"; set that rule's visibility to
"Sidebar only" to keep them off it.

### Wildcard Patterns

`*` matches any run of characters including none, every other character is literal, and matching
ignores case.

A pattern has to describe the whole message, so wrapping with `*` is how you match substrings. `dragon` matches
only the message "dragon", while `*dragon*` matches any message containing "dragon". `Your*thrall*grave.`
matches "Your lesser thrall returns to the grave."

## The Notifications list

The sidebar's **Notifications** tab keeps the last 200 notifications of the session, newest first,
including any a rule sent there instead of to the panel. It fills whether or not the sidebar is
open, and starts empty when you restart the client.

Right-click an entry to copy it, or to create a rule from it. A rule created this way is added at
the bottom of the list, so any rules named under "Matched by" already outrank it.

## Upgrading from before 2.0.0

Older versions matched with regular expressions, so nearly every pattern translates exactly and is
imported switched on: `.*dragon.*` becomes `*dragon*`, `^dragon$` becomes `dragon`, and a leading or
trailing `.*` becomes a `*` in the same place. A row's `hide` or `show` token becomes that rule's
visibility override, imported switched on like any other translated pattern.

Your two parallel lists of regex patterns and format strings are migrated into the rule list once,
the first time the plugin loads after updating. Each non-empty row becomes one rule, up to a limit
of 1000. The original values are kept, hidden, so nothing is destroyed.

A row that doesn't translate cleanly arrives disabled rather than dropped, flagged with what it
needs:

* A lone `.` matched exactly one character, where `*` matches any run, so `level .` becomes
  `level *` and would match more than it used to. Review it and switch it on. A pattern built only
  from dots collapses to a bare `*` and needs rewriting instead.
* Alternation, groups, and character classes have no wildcard equivalent, so these keep their
  original text for you to rewrite by hand.
* A per-rule `duration=` or `showTime=`, which are gone. Duration and show time are now settings for
  the whole panel.
* A missing pattern, or an invalid color or opacity token.
* A row past the end of the shorter of the two lists. The old plugin paired the lists by position
  and ignored the leftovers, so these never applied; they are imported off so you can see them.

If you ran 2.0.0, which had no per-rule hide, any rule that 2.0.0 disabled solely for using `hide`
is repaired automatically the next time the plugin loads: it comes back switched on, set to hide,
with that warning removed. A rule that also had another problem stays disabled and keeps that other
problem.

Matching now ignores case, which widens each pattern slightly.

**Font Style** is a plain Small/Regular/Bold choice again. RuneLite had turned its font setting into
a picker over every font installed on your system; if you had chosen one of those, it no longer reads
and falls back to Bold.

## Video demo

https://user-images.githubusercontent.com/87504405/180604701-3876d03f-e058-418c-a545-199b737b8293.mp4
