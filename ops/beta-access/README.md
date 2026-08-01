# Sunny automatic beta access

This small host service replaces the manual shared-token field in private beta
builds. The Android app sends only its random installation ID and receives:

- a short-lived signed inference token;
- current cloud quota;
- short-lived signed model URLs when the beta plan is Pro.

The reusable upstream credential never enters the APK. Public Play releases
must use `../entitlement-worker/` with verified purchases instead of granting
every installation the beta Pro plan.

The service listens only on loopback. Nginx exposes it below `/beta-access/`
and serves authorized model files with an internal `X-Accel-Redirect` location.
