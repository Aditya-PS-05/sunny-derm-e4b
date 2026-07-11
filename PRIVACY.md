# Sunny — Privacy Policy

_Last updated: 11 July 2026_

Sunny is a skin‑tracking app built to be private by construction. This policy
explains exactly what the app does and does not do with your data.

## The short version
- Your photos and the AI descriptions of them **stay on your device**. They are
  never uploaded, and there is no account, cloud sync, or analytics.
- All of your health data is **encrypted at rest** on the device.
- Sunny **describes** what a spot looks like and helps you track changes over
  time. It is **not a medical device** and does not diagnose.

## What Sunny stores, and where
- **Photos** you capture or choose, the model’s six‑field description of each,
  and any PDF reports you generate are stored in the app’s **private storage on
  your device only**.
- This data is **encrypted at rest** using a key held in your device’s hardware‑
  backed keystore (AES‑GCM for photos and reports; SQLCipher for the database).
- An optional **app‑lock PIN** is stored only as a salted hash — never in plain
  text — and repeated wrong attempts are rate‑limited.

## What leaves the device
- **Nothing about you.** No photo, description, report, or usage data is ever
  transmitted.
- The **only** network use is an optional, user‑initiated, one‑time download of
  the AI model files. That download only *pulls* model data over HTTPS; it never
  *sends* anything. It can be restricted to Wi‑Fi.
- If you tap **Share** on a report, you choose the destination app; the report
  leaves the device only through that action you initiate.

## Permissions
- **Camera** — to take photos of a spot. Images go straight to private storage.
- **Internet / Network state** — only for the optional model download described
  above, and to check for Wi‑Fi before a large download.
- **Notifications** — to deliver the skin‑check reminders you set.
- **Boot completed** — to re‑arm your reminders after the phone restarts.
- **Foreground service** — to keep the one‑time model download alive if you
  leave the app.

## No tracking
Sunny contains no analytics, advertising, or third‑party tracking SDKs.

## Medical disclaimer
Sunny is a tracking tool only. It provides visual descriptions, not medical
diagnoses or advice. Always consult a qualified healthcare professional for any
skin concern.

## Your control
Deleting a scan removes its photos from the device. Uninstalling the app removes
all of its data.

## Contact
Questions about privacy: add your contact address here before publishing.
