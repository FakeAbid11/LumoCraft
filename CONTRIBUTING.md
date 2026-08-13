# Contributing to LumoCraft

Thanks for your interest! LumoCraft is an open, from-scratch Android
launcher for Minecraft: Java Edition. This page explains how to
contribute cleanly.

## Code of Conduct

By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## Development setup

- JDK 17+ and an Android SDK (Android Studio includes both).
- Put the SDK path in `local.properties` (gitignored):
  ```
  sdk.dir=C\:\\path\\to\\Android\\sdk
  ```
- Build and check:
  ```
  ./gradlew assembleDebug
  ./gradlew lintDebug
  ```
  Lint must finish with **0 errors**. If the environment is low-end, push
  the branch and let the GitHub Actions build validate it.
- Release builds use the `release.yml` workflow (see README → CI).

## Where things live

- `app/src/main/java/com/lumocraft/app/` is organized feature-first:
  - `domain/` — interfaces + models (no Android deps where possible)
  - `data/` — implementations, persistence, networking, storage
  - `ui/` — one package per feature screen
  - `core/` — cross-cutting config and version handling
- Repositories are constructed once in `LumoCraftApplication` (manual DI —
  no DI framework). ViewModels resolve them via their factory.
- Strings live in `res/values/strings.xml`; never hardcode user-visible text.

## Workflow

1. Fork the repository and create a branch (`feature/…`, `fix/…`).
2. Keep changes small and focused; follow existing naming and comment style.
3. Add or update tests when behavior changes (unit tests for pure logic in
   `domain`/`data` are welcome).
4. Run `./gradlew lintDebug` and confirm no new errors.
5. Open a pull request against `main` and fill in the PR template.
6. CI runs `assembleDebug` on every PR — it must pass.

## Commit style

- One logical change per commit.
- Use the conventional style used in this repo, e.g.
  `Phase 11: <summary>` or `Fix: <short description>`.
- Never commit secrets, keystores or `local.properties`.
