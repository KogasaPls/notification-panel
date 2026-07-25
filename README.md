# Notification Panel

A RuneLite plugin that shows game notifications in an overlay panel instead of your OS notification
tray. Useful if you run several clients at once, and it behaves the same on every platform.

![image demo](https://user-images.githubusercontent.com/87504405/180604834-a8cd83af-46b8-4095-abf9-74632a4aba24.png)

Alt-click a panel border to drag its width. Shift-right-click clears the panel. A duration of 0 keeps
a notification until newer ones push it out.

## Rules

The toolbar button opens the sidebar. A rule matches notifications by wildcard pattern and overrides
the background colour, the opacity, or where the message goes: the panel, the Notifications list
only, or nowhere.

Colour, opacity and visibility resolve separately, each taken from the topmost enabled rule that
matches and sets it — so one notification can take its colour from one rule and its opacity from
another. Anything no enabled rule matched follows the defaults in the settings.

A notification an enabled rule matches is shown unless a rule says otherwise, so a colour-only rule
puts its matches on the panel even when "Default visibility" is "Sidebar only".

### Wildcard patterns

`*` matches any run of characters, everything else is literal, and matching ignores case. A pattern
must match the whole message: `dragon` matches only "dragon", `*dragon*` matches any message
containing it, and `Your*thrall*grave.` matches "Your lesser thrall returns to the grave."

## Notifications list

The sidebar's Notifications tab keeps the session's last 200, including any a rule sent there instead
of to the panel. It fills whether or not the sidebar is open, and starts empty when you restart the
client.

Right-click an entry to copy it, or to create a rule from it.

## Upgrading from before 2.0.0

Patterns are wildcards now, not regular expressions. Your old Regex and Options lists are imported
into the rule list once and then left alone, so nothing is lost. Rows that didn't translate cleanly
arrive switched off, each with a note in the rule list saying what it needs.

**Font Style** is a plain Small/Regular/Bold choice again. A font picked from the system-font picker
RuneLite briefly offered no longer reads, and falls back to Bold.

## Video demo

https://user-images.githubusercontent.com/87504405/180604701-3876d03f-e058-418c-a545-199b737b8293.mp4
