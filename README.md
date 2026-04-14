# IMSI Catching Logger (Android 8.0+)

This project is a small Android app that:

- Logs telephony/network context to an on-device file with timestamps.
- Displays the same stream live on screen while logging.
- Supports Android 8.0 (API 26) and newer.

## Captured fields (when exposed by Android APIs and permissions)

- Current network type (data and voice)
- Service state transitions
- Registration details (via `NetworkRegistrationInfo` on API 29+)
- Signal strength updates
- Cell identities for LTE/NR/GSM/WCDMA/TDSCDMA/CDMA
- PLMN/TAC/PCI (or equivalent IDs by RAT)
- Data enabled state (`isDataEnabled` / user mobile data state changes)
- Emergency-only / out-of-service state

## Log file location

The app writes logs to:

- `filesDir/telemetry_log.txt`

The exact absolute path is shown at the top of the UI.

## Permissions

The app requests at runtime:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `READ_PHONE_STATE`

Without these permissions, Android may withhold cell and operator details.
