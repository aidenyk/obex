<p align="center">
  <img src="docs/logo.png" alt="Obex logo" width="140">
</p>

<h1 align="center">Obex</h1>

<p align="center">
  <b>Instagram, without the scroll.</b><br>
  Your DMs and your friends' stories. No feed, no Reels, no Explore.
</p>

<p align="center">
  <a href="../../releases/latest"><b>⬇️ Download the latest version</b></a>
</p>

---

Obex is a free, open-source Android app that keeps the useful part of Instagram (talking to your friends) and removes the parts designed to keep you scrolling.

You sign in with your normal Instagram account on Instagram's own login page. Obex never sees or stores your password.

## Features

**What you keep**
- 💬 **Messages**: DMs and group chats, replies, reactions, photos, videos and voice messages
- ⭕ **Stories**: your friends' stories, with sound on
- 🔍 **Search**: find people, and search for posts and videos
- 👤 **Profiles**: your own profile and other people's

**What's gone**
- 🚫 The home feed. The Stories tab shows just the stories row with "Feed blocked" underneath.
- 🚫 The endless Reels feed and Explore
- 🚫 Swiping from one reel to the next. A reel a friend sends you opens on its own, and you can't swipe on to more.

**Extras**
- 🔒 **Instagram lock (optional)**: keep the real Instagram app installed for its notifications. Whenever it opens, or you tap one of its notifications, Obex opens instead.
- A slim, Instagram-style dock: Messages · Stories · Search · Profile · Lock
- Follows your phone's dark or light mode

## Install

Obex isn't on the Google Play Store. You install it straight from this page:

1. On your Android phone, open the **[latest release](../../releases/latest)** and tap **`Obex.apk`** to download it.
2. Open the downloaded file. If Android asks, allow your browser to **install unknown apps**.
   - **Samsung phones:** if the install is blocked, turn off **Settings → Security and privacy → Auto Blocker** first. You can turn it back on afterwards.
3. Open **Obex** and sign in to Instagram.

**Requirements:** Android 10 or newer. Tested on a Samsung Galaxy S25 Ultra.

**Updating:** download the newer `Obex.apk` from Releases and install it over the old one. You stay signed in.

### Turning on the Instagram lock (optional)

1. In Obex, open the **Lock** tab and tap **Open Accessibility Settings**.
2. Go to **Installed apps → Obex** and switch it on.
3. If the switch is greyed out (Android 13+ does this for apps installed outside the Play Store), go to **Settings → Apps → Obex → ⋮ (top right) → Allow restricted settings** and try again.

## Privacy

Obex has **no servers, no analytics, no ads and no tracking**. It doesn't collect anything.

- Your Instagram session is stored **only on your phone**, the same way a web browser stores it.
- Obex talks only to Instagram. It never sends your data anywhere else.
- The Accessibility permission is used **only** to notice when the Instagram app opens. Obex is only told about Instagram, and it never reads your screen, your typing or your messages.

Full details: [PRIVACY.md](PRIVACY.md)

## How it works

Obex shows **Instagram's own website** (instagram.com) inside the app, much like a browser, and adds three layers on top:

1. **Route guard.** A small script watches every page change. If you end up on the feed, Reels or Explore, it sends you back to your inbox.
2. **Hiding rules.** CSS hides the buttons that lead to the blocked areas.
3. **Native dock.** The app's own bottom tabs replace Instagram's.

The code is short and readable. The interesting parts are:
- [`guard.js`](app/src/main/assets/guard.js): the route guard and visual fixes
- [`hide.css`](app/src/main/assets/hide.css): the hiding rules
- [`MainActivity.kt`](app/src/main/java/app/obex/MainActivity.kt): the app screen and dock
- [`InstagramLockService.kt`](app/src/main/java/app/obex/InstagramLockService.kt): the Instagram lock

## Limitations

- **Instagram can change its website at any time**, which may break parts of Obex until it's updated. If something stops working, please [open an issue](../../issues).
- No push notifications from Obex itself. Keep the real Instagram app installed if you want notifications, and turn on the lock.
- Posting new photos and reels isn't the focus. Use the real app for that if you need to.

## Security

Only download Obex from this page's [Releases](../../releases). Copies from anywhere else could be modified to steal your password. You can check that a download is genuine: see [SECURITY.md](SECURITY.md).

## Building from source

1. Install [Android Studio](https://developer.android.com/studio).
2. Clone this repository and open it in Android Studio, or run:
   ```bash
   ./gradlew assembleDebug
   ```
3. The app is built to `app/build/outputs/apk/debug/app-debug.apk`.

## Disclaimer

Obex is an independent project. It is **not affiliated with, endorsed by, or sponsored by Instagram or Meta Platforms, Inc.** "Instagram" is a trademark of Meta Platforms, Inc. Obex is provided as-is, without warranty. Use it at your own risk and in line with Instagram's terms.

## License

Obex is free software, released under the [GNU General Public License v3.0](LICENSE). You can use, study, share and modify it. If you share a modified version, it must stay open source under the same license.
