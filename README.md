# IMSI Catching Logger (Android 8.0+)

This Android app continuously logs telephony/network context in a **foreground service** so collection can continue while the app is backgrounded.

## What it records

- Timestamped events.
- Current network type (voice/data).
- Service state changes (`IN_SERVICE`, `OUT_OF_SERVICE`, `EMERGENCY_ONLY`, etc.).
- Registration state details where Android APIs expose them.
- Signal strength metrics.
- Cell identities across LTE/NR/GSM/WCDMA/TDSCDMA/CDMA.
- LTE/NR identity metrics including **EARFCN/NRARFCN, TAC, MCC, MNC, PLMN, PCI, Cell ID/NCI**.
- Data enabled snapshots / mobile data toggles.

## Output files

The app writes both:

- Text log: `filesDir/telemetry_log.txt`
- CSV log: `filesDir/telemetry_log.csv`

CSV includes dedicated columns for:

- `mcc`, `mnc`, `plmn`, `tac`, `pci`, `earfcn`, `cell_id`

## UI features

- Start/Stop foreground logging service.
- Live event stream shown on-screen.
- CSV export/share button.

## Permissions declared

The app declares broad telephony/background permissions to maximize available data from public APIs:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `READ_PHONE_STATE`
- `READ_BASIC_PHONE_STATE`
- `READ_PRECISE_PHONE_STATE`
- `ACCESS_NETWORK_STATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `POST_NOTIFICATIONS`
- `RECEIVE_BOOT_COMPLETED`

> Note: some permissions (for example precise telephony details) may still be restricted by OEM/carrier policy or privileged protection levels on some builds.
