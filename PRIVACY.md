# Obex privacy policy

_Last updated: November 8, 2026_

Obex is a free, open-source Android app that shows Instagram's website with the feed, Reels and Explore removed. This policy explains what happens to your information when you use it.

## The short version

**Obex doesn't collect, store or share any of your information.** It has no servers, no accounts, no analytics, no ads and no tracking.

## Your Instagram account

- You sign in on **Instagram's own login page** (instagram.com), shown inside the app.
- Your username and password go **directly to Instagram**. Obex never reads, stores or sends them anywhere.
- Instagram's sign-in cookies are stored **only on your phone**, inside Obex's private app storage, the same way a web browser stores them. They're used to keep you signed in.
- Uninstalling Obex deletes this data. You can also sign out from Instagram itself or from Instagram's "Where you're logged in" settings.

## What Obex stores on your phone

| Data | Why | Where |
|---|---|---|
| Instagram cookies and website data | Keeps you signed in | On your phone only |
| Your profile address and profile picture | Shows your picture on the Profile button | On your phone only |
| The Instagram lock on/off setting | Remembers your choice | On your phone only |

Obex turns off Android's app-data backup, so none of this is copied to cloud backups.

## Network connections

Obex only connects to **Instagram and Meta's servers** (instagram.com, cdninstagram.com, fbcdn.net, and facebook.com during some login checks), exactly like a web browser visiting instagram.com. When you tap a link to another website, it opens in your normal browser instead. Instagram's own privacy policy covers what Instagram does with your data.

## Permissions

| Permission | Used for |
|---|---|
| Internet | Loading Instagram |
| Camera | Taking photos or videos in DMs, only when you choose to |
| Microphone | Recording voice messages, only when you choose to |
| Accessibility (optional) | The Instagram lock (see below) |

### The Accessibility permission

If you turn on the Instagram lock, Obex uses Android's Accessibility permission for **one thing only**: to notice when the Instagram app comes to the front, so Obex can open instead.

- Android only tells Obex about the Instagram app (`com.instagram.android`), nothing else.
- Obex **cannot and does not** read what's on your screen, what you type, or your messages. Window-content access is switched off in its [configuration](app/src/main/res/xml/lock_service_config.xml).
- Nothing about this is stored or sent anywhere.
- It's optional, and you can turn it off at any time in Settings → Accessibility.

## Children

Obex is meant for people who meet Instagram's minimum age requirement.

## Changes

Any changes to this policy will be published in this file, with a new "Last updated" date.

## Contact

Questions? Please [open an issue](../../issues) on this project's GitHub page.
