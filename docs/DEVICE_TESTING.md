# Light Phone III testing

Use test photos/account and never inspect image content through this app.

- Confirm the permission request and grant full (not selected-photo) access.
- Run the first scan; inspect redacted diagnostics/logs to confirm the LP3 camera `RELATIVE_PATH` category. Adjust the camera filter only if device evidence requires it.
- Take one photo, wait at least 30 seconds, then run manual backup on Wi-Fi. Confirm one Google Photos item and ledger count.
- Repeat backup, move/re-index a test photo, restart the app, and reboot the phone; confirm no duplicate.
- Disable cellular and test Wi-Fi waiting; test the one-run cellular override separately from the persistent setting.
- Confirm periodic work after Android's inexact scheduling window and after reboot.
- Interrupt network during byte upload and after byte upload/before media creation; restore it and confirm safe retry.
- Test expired access token, authorization removal, server outage, 429/5xx behavior, and reconnect.
- Delete a disposable local test photo outside this app; scan and confirm `MISSING_LOCAL_FILE` without crash or deletion elsewhere.
- Disconnect and confirm token calls fail afterward.
- Install an update signed with the same key. Separately document that a key mismatch must not trigger automatic uninstall because uninstall erases the Room ledger and credential.

USB local server: `adb reverse tcp:8787 tcp:8787`; otherwise bind safely and use the Mac LAN IP. Do not automate permissions, UI, or real uploads.
