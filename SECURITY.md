# Security

## Only download Obex from this project's Releases page

You sign in to Instagram inside Obex. A modified copy of Obex could be built to capture your password, so **only install `Obex.apk` from this project's [Releases](../../releases) page**.

## Check that your download is genuine

Every official Obex release is signed with the same private key. Android won't install an update signed with a different key over an existing install. You can also check the signature yourself.

**Official signing certificate (SHA-256):**

```
51:CA:EF:DF:07:6F:56:E3:2B:97:F4:3D:3A:A2:4C:29:22:74:B0:F4:06:75:20:97:5E:60:EB:8F:28:C9:F7:1B
```

To check it on a computer with the Android SDK installed:

```bash
apksigner verify --print-certs Obex.apk
```

The `Signer #1 certificate SHA-256 digest` must match the fingerprint above (the same value, without colons). If it doesn't match, don't install it.

On your phone, you can also use an app like [App Manager](https://github.com/MuntashirAkon/AppManager) to view the signature of the installed app.

## What Obex can and can't access

- **Your Instagram password:** typed into Instagram's own login page and sent straight to Instagram. Obex's code never reads it. Check [`guard.js`](app/src/main/assets/guard.js), the only script Obex adds to Instagram's pages.
- **The app–page connection:** Obex's script talks to the app through a message channel that Android makes available **only to instagram.com pages**. Other websites (for example during login) can't reach it.
- **The Accessibility permission (optional lock):** limited to the Instagram app, with screen-content access switched off. See [PRIVACY.md](PRIVACY.md).
- **No servers:** Obex has no backend. There's nothing to hack that holds your data.

## Reporting a security problem

If you find a security issue, please report it privately. Use GitHub's **"Report a vulnerability"** button on this project's **Security** tab, rather than opening a public issue.
