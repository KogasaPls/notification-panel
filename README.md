# Notification Panel
This is a RuneLite plugin that displays notifications in an overlay panel. It is much more useful than native notifications when using multiple clients, and it's OS-agnostic.

![image demo](https://user-images.githubusercontent.com/87504405/180604834-a8cd83af-46b8-4095-abf9-74632a4aba24.png)

## Features / Options

* The notification panel can be repositioned and locked to anchors like any other overlay panel.

* The width of the notifications can be adjusted by alt-clicking on a border and dragging.

* The maximum number of notifications shown at once can range from 1 to 5.

* The expiration time can be shown or hidden.

* The duration each notification lasts can be set in units of seconds or ticks. Setting the duration to 0 will make
  notifications last forever (or until they are replaced by newer notifications). In this case, the "show time" setting
  will show the age of the notification.

* The font can be adjusted between "small," "regular," and "bold."

* Shift-right clicking a notification will show a "Clear" option which will clear all notifications.

### Conditional Formatting for Notifications

You can enter a list of regex patterns, one per line, and a list of **format strings**, one per line. Notifications
matching a regex pattern will be formatted using the options in the format string, overriding the defaults, if
specified.

A **format string** is a comma-separated list of one or more of the following options:

* Background color ("#ff0000")
* Opacity ("opacity=x" where x is an integer from 0 to 100)
* Visibility ("hide" or "show")

For example, `#bf616a`, `#bf616a, opacity=25`, and `hide` are all valid format strings.

If a notification matches multiple regex patterns, each attribute will be taken from the first matching format string
specifying this attribute. This can be used to simplify complex patterns: for example, if a notification looks like "You
received (quantity) (item)", you could match on "quantity" to set the opacity, and "item" to set the color.

## Video Demo

https://user-images.githubusercontent.com/87504405/180604701-3876d03f-e058-418c-a545-199b737b8293.mp4

## TODO

* Add a simple custom GUI for conditional formatting
* Bug testing
* Right-click option to clear individual notifications
