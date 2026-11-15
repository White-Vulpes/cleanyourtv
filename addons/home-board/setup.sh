#!/usr/bin/env bash
# Give Home Board your Todoist token (and, optionally, your project name) from a computer, instead of
# typing a 40-character token with the remote. The token is read without echo and sent over ADB to the add-on's
# setup door, which only the ADB shell can open; it is not saved on this computer. Install the add-on first.
#
# Usage: TV_HOST=192.168.1.50 addons/home-board/setup.sh                          # asks for the token
#        TV_HOST=192.168.1.50 addons/home-board/setup.sh "My Board"                       # another project name
#        TV_HOST=192.168.1.50 addons/home-board/setup.sh "My Board|Tasks|Ideas|Alarms"  # and other section names
#        TV_HOST=192.168.1.50 addons/home-board/setup.sh --status
set -euo pipefail
cd "$(dirname "$0")/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.whitevulpes.homeboard
URI=content://$PKG.setup
tv() { "$PY" -I tv.py "$@"; }
call() { tv "content call --uri $URI --method $1${2:+ --arg '$2'}" | tr -d '\r' | sed -n 's/.*result=\(.*\)}].*/\1/p'; }

tv "pm path $PKG; true" | grep -q package: || { echo "Home Board is not on the TV: build and install it first (README.md)" >&2; exit 1; }

if [ "${1:-}" = "--status" ]; then call status; exit 0; fi

if [ -n "${1:-}" ]; then
  echo "board: $(call board "$1")"
fi

printf 'Todoist API token (Todoist > Settings > Integrations > Developer; not shown as you type): '
read -rs TOKEN
echo
case "$TOKEN" in
  *[!A-Za-z0-9]*|"") echo "that does not look like a Todoist token (letters and digits only)" >&2; exit 1 ;;
esac
echo "token: $(call token "$TOKEN")"
unset TOKEN
echo "fetching from Todoist on the TV..."
RESULT="$(call refresh)"
echo "$RESULT"
case "$RESULT" in ok*) echo "Done. Press Home on the remote." ;; *) exit 1 ;; esac
