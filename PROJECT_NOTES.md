# GPX Trail Project Notes

## Overview

GPX Trail is a lightweight Wear OS app for tracking off-road sessions on a Galaxy Watch and moving GPX files between watch and phone.

Main goals:

- track a session directly on the watch
- save GPX files
- mark and navigate to waypoints
- return toward the start/home point
- send GPX from watch to phone
- import GPX from phone to watch

## Repo Layout

- `app/`: watch app
- `mobile/`: phone companion app
- Local working repo: `C:\Users\Admin\GitHub\gpx-trail`
- GitHub repo: `https://github.com/ajbrownoh/gpx-trail`

## Important Storage Paths

Watch app internal storage:

- recorded session GPX: `files/gpx/`
- imported GPX from phone: `files/imported_gpx/`
- draft active session: `files/gpx/draft.gpx`

Phone app:

- received GPX files are saved to `Downloads/GPX Trail`

## What Has Been Completed

### Session Tracking

- session-based watch tracking flow
- start, pause, resume, and stop
- manual save/discard flow
- session naming flow
- draft recovery after interrupted sessions
- three tracking quality modes:
  `MEDIUM`, `HIGH`, `LOW`

### Map / Compass / Return Guidance

- full-screen live map
- overlay controls on top of the map
- compass labels around the edge
- bearing-to-start guidance
- home/start return guidance
- GPS lost state shown only when actually lost
- active waypoint guidance on the main map
- blue waypoint distance readout on the live screen

### Waypoints

- mark waypoints during a live session
- name waypoints with voice/text flow
- dedicated waypoint navigation screen
- large blue waypoint navigation arrow
- distance-to-waypoint display
- Home button for navigating back to the start/home point
- active waypoint clear/deselect support
- active waypoint persistence when reopening the app
- waypoint visibility manager
- multi-select show/hide for waypoints

### Saved Sessions

- Saved Sessions list on watch
- recorded watch GPX files listed there
- imported GPX files also listed there
- imported entries labeled as imported
- session detail map view
- waypoint legend/list in saved session detail
- edit, delete, and send-to-phone actions
- ability to start tracking toward a waypoint from saved session detail

### Phone Companion

- receive GPX files from watch to phone
- share the latest received GPX
- open Downloads from the phone app
- pick a GPX file on the phone and send it to the watch

### GPX Import Handling

- imported GPX contributes waypoints to the waypoint system
- imported GPX appears in Saved Sessions
- exact duplicate imports are reused instead of duplicated
- same filename with different content is saved with a unique suffix like `_2`, `_3`

### Ambient / Always-On

- ambient screen while tracking
- current time shown in ambient
- session duration shown in ambient
- distance shown in ambient
- battery percentage shown in ambient
- GPS lost message shown in ambient when applicable

## Current UX Intent

The app is intentionally biased toward:

- quick glanceability on-watch
- simple manual GPX workflow
- low visual clutter
- battery-aware behavior

## Operational Notes

- Wireless debugging ports rotate often on the watch.
- ADB may show the watch by mDNS name instead of IP:port.
- Every new PowerShell window usually needs env vars re-set before building.
- Use `C:\Users\Admin\GitHub\gpx-trail` for new work, not the old Downloads copy.

## Current Open/Useful Areas

- add a small deploy helper script later if repeated terminal setup gets annoying
- add tests around GPX parsing and duplicate import handling
- add more explicit import success/failure feedback in the UI
- add a README index that points to `RUNBOOK.md` and `PROJECT_NOTES.md`

