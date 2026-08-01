# Sunny — Privacy Policy

_Last updated: 2 August 2026_

Sunny is a skin‑tracking app built to be private by construction. This policy
explains exactly what the app and Sunny's public website do and do not do with
your data.

## The short version
- Saved photos and descriptions stay encrypted on your device. When Sunny AI
  Cloud is selected, each photo chosen for analysis is sent to Sunny's configured
  inference server. Pro users can instead install Sunny Offline for local inference.
- Health data is **encrypted at rest** by Sunny in addition to Android's device protections.
- Sunny **describes** what a spot looks like and helps you track changes over
  time. It is **not a medical device** and does not diagnose.
- The website waitlist asks only for an email address and never asks for health
  information.

## What Sunny stores, and where
- **Photos** you capture or choose, the model’s six‑field description,
  reference-based size estimates, active photo-check progress, and PDF reports
  are stored in the app’s **private storage on your device only**.
- This data, ABCDE responses, and reminder metadata are **encrypted at rest** using
  a key protected by Android Keystore (hardware-backed where the device supports
  it): AES-GCM for files/preferences and SQLCipher for the database.
- An optional **app‑lock PIN** is stored only as a strong salted verifier — never
  in plain text — and additionally wraps the data-encryption key. Repeated wrong
  attempts are rate-limited using boot-aware monotonic time.

## What leaves the device
- **Device mode:** scan photos and descriptions do not leave for inference.
- **Sunny AI Cloud:** each photo selected for analysis is sent to the configured
  inference server and its generated visual description is returned. The app does
  not claim that the Android client can enforce server-side retention or logging;
  those controls must be documented and operated separately.
- **Optional model-improvement contribution:** this is off by default and requires
  a separate explicit, versioned opt-in. When enabled, Sunny sends the photo, body area,
  model output, any corrected output, device model, and app version to the
  configured contribution endpoint. Turning it off prevents future submissions.
- **Freemium quota:** Sunny creates a random app-install identifier containing no
  hardware, advertising, account, or user identifier. The access service stores
  an opaque one-way derivative plus daily/monthly request counts to enforce the
  Free and Pro cloud allowances. Counters are deleted after 62 days of inactivity.
  Uninstalling generates a different identifier.
- **Model download:** verified Pro users can perform an optional, user-initiated
  model download through expiring signed HTTPS URLs over Wi-Fi or mobile data.
- If you tap **Share** on a report, you choose the destination app; the report
  leaves the device only through that action. Sunny decrypts it into an operating-
  system pipe and does not leave a plaintext sharing copy in its cache.
- If you export a full backup, Sunny streams scans, notes, ABCDE answers,
  reminders, photos and reports into a password-derived AES-256-GCM archive.
  The archive leaves only when you choose a destination in Android's share UI;
  Sunny never writes a plaintext backup.

## Permissions
- **Camera** — to take photos of a spot. Images go straight to private storage.
- **Internet / Network state** — anonymous quota authorization, model download,
  and the configured inference/contribution modes described above.
- **Notifications** — to deliver the skin‑check reminders you set.
- **Boot completed** — to re‑arm your reminders after the phone restarts.
- **Foreground service** — to keep the one‑time model download alive if you
  leave the app.

## No tracking
Sunny contains no analytics, advertising, or third‑party tracking SDKs. The
random installation identifier is used only for abuse-resistant quota accounting.

The public website uses no advertising pixels or third-party analytics.
Infrastructure providers may temporarily process standard request logs for
security and reliability.

## Website waitlist
If you join the waitlist at <https://sunny.adityaps.work>, the email address you
provide and its subscription status are sent to
[Brevo](https://www.brevo.com/legal/privacypolicy/), Sunny's email-list provider,
so Sunny can send product updates, testing openings, and launch news. The
waitlist does not request health information. Your email is retained while you
remain subscribed. Every marketing message provides an unsubscribe option, and
you can request access or deletion by contacting Sunny.

## Medical disclaimer
Sunny is a tracking tool only. It provides visual descriptions, not medical
diagnoses or advice. Always consult a qualified healthcare professional for any
skin concern.

## Model and dataset notices
Sunny AI Cloud and Sunny Offline use a modified `google/gemma-4-E2B-it` model.
Gemma 4 is provided by Google under the Apache License 2.0. Sunny fine-tuned the
language tower, merged the adapter, converted the result to GGUF, and quantized
the text model and vision projector. Google does not endorse Sunny. A complete
Apache 2.0 license copy and modification notice ship in the app and accompany
the downloadable model pack.

The fine-tune used 2,292 accepted smartphone images from **PAD-UFES-20**:
Pacheco et al., <https://doi.org/10.17632/zr7vgbcyr2.1>. PAD-UFES-20 is licensed
under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Images were
resized and normalized, non-diagnostic appearance descriptions were generated,
records were split by lesion, and the model was fine-tuned and quantized. The
dataset creators and institutions do not endorse Sunny. Full attribution is in
`licenses/THIRD_PARTY_NOTICES.txt`.

## Your control
Deleting a scan removes its photos, measurements and reminders from the device.
**Delete all local data** removes scans, photos, measurements, private notes,
ABCDE answers, reminders, active photo-check progress, reports and encrypted
backup archives while leaving the installed model and app
preferences. Uninstalling removes all local app data. Neither action deletes a
contribution already sent to the beta server; a server-side deletion and
retention process must be documented before external beta testing.

## Contact
For website or waitlist privacy requests, email `aditya@adityaps.work`.

Release builds are blocked unless a monitored email address or HTTPS contact is
configured through `SUNNY_PRIVACY_CONTACT`; the same value appears in-app and
must match the developer contact published on Sunny's app-store page.
