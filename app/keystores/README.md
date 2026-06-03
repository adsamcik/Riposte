# Riposte debug signing keystore

`knowncerts-owner.jks` is a **test-only** keystore committed to the repo so
Riposte's debug variant has a stable, predictable signing certificate on every
developer machine and CI runner.

It is intentionally the **same keystore** as
[Mindlayer's `knowncerts-owner.jks`](https://github.com/adsamcik/Mindlayer/blob/main/app/keystores/knowncerts-owner.jks).
Riposte's `core/ml` layer connects to the Mindlayer service over AIDL, and
Mindlayer gates its `BIND_ML_SERVICE` AIDL with
`protectionLevel="signature|knownSigner"`. By signing Riposte's debug build
with the same keystore Mindlayer's debug build uses, the `signature` permission
rule grants `BIND_ML_SERVICE` automatically (no `knownCerts` entry needed) and
Mindlayer's `DebugAllowlistSeeder` auto-approves Riposte at first bind because
they share a signing cert. The result: AI features work out of the box on any
dev machine without biometric/PIN approval dialogs.

This is **not production signing material.**

## Credentials

- Store password: `knowncertstest`
- Key password: `knowncertstest`
- Owner alias: `knowncerts-owner`

Certificate SHA-256 (canonical lowercase 64-hex form):

```text
664735c79928241a813a556fa41a03762c568189096949c7c2cfb533f26a7f52
```

## Why a committed keystore?

Android Studio's default `~/.android/debug.keystore` generates a per-user cert,
so two developers on the same project end up with different signing certs.
That's fine for solo dev, but breaks any cross-app integration (like Mindlayer)
that relies on `signature`-level permissions or known-signer allowlists. The
committed keystore yields a single stable cert across all dev machines and CI.

## Refreshing from Mindlayer

If Mindlayer ever rotates its `knowncerts-owner.jks`, refresh ours to match
(or the auto-trust will break):

```powershell
Copy-Item ..\Mindlayer\app\keystores\knowncerts-owner.jks knowncerts-owner.jks
```

Verify the cert SHA-256 still matches the value in
[Mindlayer's keystore README](https://github.com/adsamcik/Mindlayer/blob/main/app/keystores/README.md).

## Release builds

Release builds are signed via the standalone release configuration set up in
`app/build.gradle.kts` from `local.properties` (`RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`).
This keystore is **not** used for release.
