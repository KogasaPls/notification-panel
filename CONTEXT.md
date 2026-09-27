# Notification Panel

A RuneLite plugin that displays game notifications in a configurable on-screen overlay panel and maintains a sidebar notification log with custom styling and filtering rules.

## Language

**Notification**:
A text message or alert dispatched by the game client to the player.
_Avoid_: Message, alert, popup

**Notification Rule**:
A user-defined pattern matching notifications to override their background color, opacity, or visibility.
_Avoid_: Filter, pattern entry, style rule

**Rule Presentation Model**:
The observable state model managing the rule editor's list ordering, selection, view mode, and validation.
_Avoid_: Rule controller, rule state, rule manager

**Rule List View**:
The sidebar view presenting configured notification rules in priority order.
_Avoid_: Rules tab, rule table

**Rule Edit View**:
The sidebar form used to configure an individual rule's attributes.
_Avoid_: Rule form, rule detail view

**Policy**:
The resolved configuration determining notification limits, display lifetime, default styles, and compiled rules.
_Avoid_: Config settings, plugin options

**Active Notification**:
A notification currently rendered on the game overlay countdown.
_Avoid_: Displayed notification, on-screen alert

**Accepted Notification**:
An incoming notification admitted to the overlay and the sidebar log after rule evaluation.
_Avoid_: Logged notification, recorded alert
