# Home Board

To-dos, notes and reminders from your phone, shown on the Clean Your TV home screen in three columns between the
clock and the dock. View only on the TV: add, edit and tick things off in the **Todoist** app on your phone.

- One Todoist project, **TV Board**, with three sections: **To do**, **Notes**, **Reminders** (open the project in
  Board view and they are its columns). Tasks in no section count as to-dos; other sections stay off the TV, and so
  do sub-tasks. The names can be changed, see below.
- Reminders are tasks with a due date (and time); they are listed soonest first, overdue ones in red.
- A note's description shows after its title, dimmer.
- **Birthdays** (optional fourth section): a task per person, the name as the task and the birthday as a due date
  that repeats every year ("Mau every Nov 17"). On the day, the launcher's ASCII background turns into fireworks
  behind "HAPPY BIRTHDAY MAU", on Home and in the screensaver, and goes back to the animals the next day.
- Ticking a task off on the phone removes it from the TV within about a minute.

## How it works

The add-on has no screen and no app tile. Home asks it for the board when it comes on screen and once a minute
while it stays there; the add-on fetches from `api.todoist.com` (API v1, read only) when its copy is older than a
minute, and keeps the last board on the TV for when the network is down. There is no background service. The
launcher itself stays offline: it only reads the add-on's `content://io.github.whitevulpes.homeboard.board/items`.

Permissions: `INTERNET`, only for api.todoist.com. The token is stored privately in the add-on, never logged, and
can only be set through a setup door that only the ADB shell can open.

## Install

```bash
cd addons/home-board && ./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Set `home_board` to `io.github.whitevulpes.homeboard` in the launcher's `config.xml` (or a theme) and reinstall it.

## Connect Todoist

Get your token in Todoist: Settings > Integrations > Developer > API token. Then, with debugging on:

```bash
TV_HOST=<tv ip>:5555 addons/home-board/setup.sh                            # asks for the token, not echoed
TV_HOST=<tv ip>:5555 addons/home-board/setup.sh "My Board"                    # another project
TV_HOST=<tv ip>:5555 addons/home-board/setup.sh "My Board|Tasks|Ideas|Alarms"  # and other section names
TV_HOST=<tv ip>:5555 addons/home-board/setup.sh --status
```

To disconnect: `adb shell content call --uri content://io.github.whitevulpes.homeboard.setup --method token`
(no `--arg` clears it), or uninstall the add-on.
