# Alert Notes - Privacy Policy

**Last updated:** 28 July 2026
**Applies to:** Alert Notes for Android, version 1.0.0

> **Before publishing:** this document is written to be accurate about what the
> app actually does, based on the code in this repository. It is not legal
> advice. Have it reviewed by a qualified adviser, replace every `[PLACEHOLDER]`
> with real details, and host it at a stable public URL before submitting to
> Google Play - Play requires the policy to be reachable from the store listing
> and from inside the app.

**Data controller:** `[LEGAL ENTITY OR INDIVIDUAL NAME]`
**Contact:** `[PRIVACY CONTACT EMAIL]`
**Postal address:** `[ADDRESS]`

---

## 1. The short version

Alert Notes has two modes, and which one you choose decides everything below.

**Offline mode** collects nothing. The app never opens a network connection.
Your reminders are stored on your device and never leave it, except through a
backup file you explicitly create and save yourself.

**Online mode** requires an account and adds friends, family, reminder sharing
and chat. It collects what those features need, and nothing else. There is no
advertising, no profiling, and no analytics.

You can switch between modes at any time, and you can delete your account and
everything attached to it from inside the app.

---

## 2. What we collect, and why

### 2.1 Offline mode

Nothing. No account, no identifiers, no network requests, no crash reporting.

Reminder data, drawings, checklists, acknowledgement records and settings are
stored locally using Android's own storage. Automatic Android backup is
disabled (`allowBackup="false"`), so the operating system will not copy this
data to Google's servers either.

### 2.2 Online mode

| Data | Why it exists | Where it is stored |
| --- | --- | --- |
| Email address | Account identity, sign-in, password reset | Firebase Authentication |
| Password | Sign-in. We never see it - Firebase stores a hash | Firebase Authentication |
| Display name, @username | So people can find and recognise you | Cloud Firestore |
| Profile photo | Optional. Shown to people you interact with | Cloud Storage |
| Status message, profile theme | Optional personalisation | Cloud Firestore |
| Online status, last active time | Shows friends whether you are reachable | Cloud Firestore |
| Friends and family relationships | The features themselves | Cloud Firestore |
| Blocked accounts | To enforce your block | Cloud Firestore |
| Privacy settings | To enforce your choices | Cloud Firestore |
| Chat messages | To deliver them | Cloud Firestore |
| Shared reminder content | To deliver a reminder you chose to share | Cloud Firestore |
| Acknowledgement records | So the sender can see the reminder was handled | Cloud Firestore |
| Acknowledgement photos | Only when a reminder is configured to require one | Cloud Storage |
| Acknowledgement location | Only when a reminder is configured to require one | Cloud Firestore |
| Device model, Android version, app version | Diagnosing delivery problems, showing your signed-in devices | Cloud Firestore |
| Push notification token | To deliver notifications to this device | Cloud Firestore |

### 2.3 Location

Location is **not** collected in the background and **not** collected
continuously.

A single location fix is taken only at the moment you acknowledge a reminder
that was explicitly configured to require location proof, and only after you
have granted the permission. The coordinates are attached to that one
acknowledgement. If you decline the permission, the reminder can still be
acknowledged another way.

### 2.4 Camera

The camera is used only when you take an acknowledgement photo or set a profile
picture. Photos are captured at the moment you press the button, never in the
background.

### 2.5 Analytics and advertising

None. The Firebase Analytics library is present as a dependency of the Firebase
SDK, with collection disabled in the app manifest and never enabled at runtime.
There is no advertising SDK, no attribution SDK, and no third-party tracker of
any kind in this app.

---

## 3. Who your data is shared with

**Other users**, and only as a direct result of something you did:

- People you accept as friends see your profile, presence and status.
- People in your family circle see the same, plus whatever the per-member
  permissions you set allow.
- Someone you send a reminder to receives that reminder's content.
- Someone you chat with receives your messages.
- When you acknowledge a reminder someone shared with you, that person sees
  when and how you acknowledged it - including the photo or location if the
  reminder required one.

Your privacy settings control who may reach you and what they can see. They are
enforced on the server, not just in the app.

**Google (Firebase)** processes and stores the data above as our infrastructure
provider. See <https://firebase.google.com/support/privacy>.

**Nobody else.** Your data is not sold, rented, shared with advertisers, or used
to train anything.

---

## 4. Where data is stored

Firebase resources for this app are configured in the
`[FIREBASE REGION, e.g. europe-west1]` region. Google may replicate data across
its infrastructure as described in its own documentation.

---

## 5. How long data is kept

| Data | Retention |
| --- | --- |
| Account and profile | Until you delete your account |
| Chat messages | Until you or the other participant deletes them, or either account is deleted |
| Shared reminders | Until cancelled, declined, or either account is deleted |
| Acknowledgement photos | Until the associated share is deleted, or either account is deleted |
| Push tokens | Removed on sign-out, and automatically pruned when a device stops responding |
| Local reminder data | Until you delete it, or uninstall the app |

---

## 6. Your rights

You can, from inside the app:

- **See** your data - your profile, devices, history and shared reminders are
  all visible in the app.
- **Correct** it - display name, username, photo, status and privacy settings
  are all editable.
- **Export** it - Settings has a full ZIP backup and CSV export of reminders and
  history.
- **Restrict** it - the privacy settings limit who can reach you and what they
  see; blocking cuts off an account entirely.
- **Delete** it - **More > Delete account** permanently removes your account,
  profile, username, relationships, shared reminders, conversations, photos and
  acknowledgement proofs. This is irreversible. Reminders stored on your own
  device are kept, and the app returns to offline mode.

If you are in the UK or EU, you also have the right to object to processing, to
data portability, and to complain to your supervisory authority. Contact
`[PRIVACY CONTACT EMAIL]` to exercise any right not available in the app.

---

## 7. Children

Alert Notes is not directed at children under `[AGE, e.g. 13 / 16 depending on
jurisdiction]`, and accounts are not knowingly created for them. If you believe
a child has created an account, contact us and it will be deleted.

---

## 8. Security

- All traffic to Firebase uses TLS.
- Passwords are handled entirely by Firebase Authentication; this app never
  stores or transmits a password itself.
- Access to every document is governed by server-side security rules, which are
  version-controlled in this repository (`firestore.rules`, `storage.rules`) and
  covered by an automated test suite.
- Deleting your account triggers a server-side cascade that removes data the
  client alone cannot reach, including the copies of relationship records held
  in other users' accounts.
- Optional biometric or PIN app lock protects the app on your device.

No system is perfectly secure. If you find a vulnerability, please report it to
`[SECURITY CONTACT EMAIL]` rather than disclosing it publicly.

---

## 9. Permissions

What each Android permission is for is documented in
[PERMISSIONS.md](PERMISSIONS.md). Every permission that can be declined can be
declined, and the app degrades gracefully rather than refusing to work.

---

## 10. Changes to this policy

Material changes will be announced in the app before they take effect. The
"last updated" date at the top always reflects the current version, and the
full history of this document is in the repository.

---

## 11. Contact

`[PRIVACY CONTACT EMAIL]`
`[LEGAL ENTITY NAME]`
`[ADDRESS]`
