#!/usr/bin/env bash
# Measures what SVARĀ costs while paused with the screen off: CPU time, wake locks, alarms and jobs.
# Usage: scripts/measure_idle.sh [minutes]   (device connected, app installed, something queued and paused)
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="${ADB:-adb}"
PKG=com.svara.app
MINUTES="${1:-10}"

pid=$($ADB shell pidof $PKG || true)
[ -z "$pid" ] && { echo "App not running"; exit 1; }
ticks() { $ADB shell cat /proc/$pid/stat | awk '{print $14 + $15}'; }   # utime + stime, in clock ticks
hz=$($ADB shell getconf CLK_TCK 2>/dev/null || echo 100)

$ADB shell dumpsys batterystats --reset >/dev/null
$ADB shell input keyevent SLEEP
before=$(ticks)
echo "Paused + screen off for $MINUTES min (pid $pid)…"
sleep $((MINUTES * 60))
after=$(ticks)
$ADB shell input keyevent WAKEUP

cpu_ms=$(( (after - before) * 1000 / hz ))
echo "CPU time used: ${cpu_ms} ms over ${MINUTES} min ($(awk "BEGIN{printf \"%.3f\", $cpu_ms/($MINUTES*600)}")% of one core)"
uid=$($ADB shell dumpsys package $PKG | grep -m1 -oE "userId=[0-9]+" | cut -d= -f2)
echo "--- batterystats for uid $uid (wake locks, alarms, jobs, CPU)"
$ADB shell dumpsys batterystats | grep -A40 -E "^  u0a$((uid - 10000)):" | grep -E "Wake lock|Wakeup alarm|Job |Cpu:|Foreground|Background|Total cpu" || echo "(no app-attributed activity recorded)"
echo "--- wake lock history for $PKG during the window"
$ADB shell dumpsys power | grep "$PKG" | tail -5 || true
