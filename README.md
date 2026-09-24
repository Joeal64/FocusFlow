# FocusFlow

An Android study app for short, focused study sessions in the style of the Pomodoro technique.
Its **focus lockdown** means a session only counts while you stay in the app. Leave the app and the timer pauses.

Built with Kotlin and Jetpack Compose as a team of two for the Mobile Software Development module
(BSc Computer Science, TU Dublin, 3rd year, Nov-Dec 2025).

## Features

- **Pomodoro-style focus timer**: drag the circular dial to set the session length
- **Focus lockdown**: the timer stops and the session is marked as interrupted if the app goes to the background;
  the back button is disabled while a session is running
- **Daily streaks**: consecutive days of completed sessions, stored locally
- **Weekly stats**: a graph of focus time over the last seven days
- **Session history** kept in a Room (SQLite) database
- **Sensor controls**: shake the phone to pause; Bluetooth headphones disconnecting pauses the timer and reconnecting resumes it
- **Notifications and vibration** when a session finishes, plus a notification when headphones disconnect
- **Session summary** screen after each completed session
- Dark mode, landscape layout, and translations in **English, Spanish and Chinese**

## Tech stack

| Area | Used |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3 |
| Persistence | Room (DAO + entities, via kapt) |
| Android APIs | Activity lifecycle, SensorManager (accelerometer), Bluetooth broadcast receivers, notifications, vibration |
| Build | Gradle Kotlin DSL, version catalog |
| Target | minSdk 24, targetSdk 36 |

## Project structure

```
app/src/main/java/com/example/project_focusflow/
├── MainActivity.kt        # app scaffold, lifecycle hooks, sensor + Bluetooth registration
├── Timer.kt               # timer screen, dial, lockdown and sensor behaviour
├── WeeklyStatsScreen.kt   # weekly focus graph
├── SummaryActivity.kt     # post-session summary
├── AppDatabase.kt, FocusSession.kt, FocusSessionDao.kt, DailyStreak.kt   # Room layer
├── NotificationHelper.kt, BluetoothReceiver.kt, Shake.kt, AppEvents.kt
└── ui/theme/              # colours, typography, theme
```

## Running it

Open the project in Android Studio (Ladybug or newer), let Gradle sync, and run the `app` configuration on an
emulator or a device with Android 7.0+. Bluetooth and shake features need a physical device.

## Team and contributions

Built by **Joeal Joseph** and **Abdalmumin Abusalama**.

**Joeal:** the core timer and dial behaviour, focus-lockdown handling in the timer (pausing and flagging interrupted sessions
when the app is backgrounded, disabling back navigation mid-session), daily-streak tracking, the weekly stats graph and
the Room queries behind it, and the final UI redesign (theme, typography, colours).

**Abdalmumin:** the Room database setup, app lifecycle events, notifications, the summary screen, Bluetooth
and shake detection, dark mode, landscape layout, and the Spanish and Chinese translations.

The original project proposal is in [`MSDProject.pdf`](MSDProject.pdf).
