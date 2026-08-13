# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| `v0.1.0-rc1` (and later release candidates) | :white_check_mark: |
| Older snapshots | :x: |

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Instead,
report them privately through the GitHub repository's **Security →
Report a vulnerability** flow, or open a private issue and tag a
maintainer. You can expect an acknowledgment within a few days.

Please include:

- Affected version (from the About screen)
- Steps to reproduce
- Impact and any suggested fix, if known

We handle all reports confidentially and credit reporters (unless they
prefer to stay anonymous).

## What this project takes seriously

- **Credentials and secrets**: keystores and signing passwords are only
  ever provided through CI secrets; they must never appear in the
  repository.
- **Personal data**: launcher logs are redacted before export
  (`[REDACTED]` for account usernames); accounts are local and offline.
- **Arbitrary file writes**: archive extraction (runtimes, assets)
  rejects path-traversal entries.
- **Downloads**: all HTTP endpoints use HTTPS; libraries/assets are
  SHA-1/size verified before being trusted for launch.
