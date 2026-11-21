# Nudge Drive Privacy Policy

**Effective date:** 17 November 2026

This policy explains what information the Nudge Drive app and the Nudge Drive service at [nudge.qd.je](https://nudge.qd.je) handle, why, where it is kept, and the choices you have. It applies to the Nudge Drive Android app (package `com.nudge.drive`), the Nudge Drive desktop apps and the website.

Nudge Drive is run by the **Nudge Drive team**, an individual developer based in India ("we", "us"). For anything in this policy, write to [support@nudge.qd.je](mailto:support@nudge.qd.je).

## The short version

- Your files are stored in **your own** Google Drive accounts or S3 buckets, not on our server. Our server passes files between your device and your storage and keeps a list of them (names, sizes, folders and similar details) so the app can show them.
- We do **not** sell your data, show ads, or use analytics or crash-reporting tools.
- We use Google user data only to provide the features you see in the app. We never use it for advertising and never use it to train AI models.
- You can delete your account at any time in the app (**Profile › Delete account**) or at [nudge.qd.je/delete-account](https://nudge.qd.je/delete-account). Your files stay in your own storage.

## 1. Information we collect

### 1.1 Information you give us

- **Account details:** your name, email address and password when you sign up with email. We store only a one-way hash of your password (Argon2), never the password itself.
- **Google sign-in and connected Google accounts:** when you sign in with Google or connect a Google Drive, Google gives us your Google account ID, email address, name and profile picture link, plus access tokens that let Nudge Drive work with that Drive. The tokens are stored encrypted (AES-256-GCM).
- **S3 storage you add:** bucket name, region, endpoint, an optional storage limit, and your access key ID and secret key. The keys are stored encrypted (AES-256-GCM).
- **People you invite:** if you invite someone to a file or folder, we store the email address you type, the role you chose and what you shared. We do not send them an email; the invite takes effect if they sign in to Nudge Drive with that address.
- **Messages to support:** if you email us, we keep the conversation so we can help you.

### 1.2 Information created when you use the service

- **File and folder details:** for every file you upload, import or create through Nudge Drive we store its name, type, size, checksum (when available), the folder it is in, folder names and colours, the storage account it lives in, its ID in that storage, upload and change dates, its trash status and, for photos and videos, the date it was taken.
- **Storage usage:** the total, used and free space of each connected storage account, so we can show it and choose where uploads go. We also store the upload rule you pick (most free space, take turns, or your own order).
- **Upload progress:** while an upload is running we keep the file name, type, size, status and, for Google Drive, a temporary upload link. These records stay with your account.
- **Share links:** the link code, whether it is on or off, and an expiry date if one is set.
- **Activity history:** a log of actions in your account (for example uploads, moves, renames and deletions) with the item involved. You can see it in the app under **Activity**.
- **Sign-in sessions:** for each device you sign in on, we keep a hashed sign-in token, the IP address and the app or browser name (user agent) used at sign-in, and when the session was created, expires (after 30 days) or was signed out.
- **API keys** you create: their name, a short prefix, a one-way hash of the key, permissions and when they were last used.
- **Email verification codes:** stored only as one-way hashes, valid for 15 minutes. Account-deletion codes are kept only in the server's memory for 15 minutes.
- **Previews:** to show thumbnails and to open documents and spreadsheets in the app, the server keeps a cache of thumbnails and converted copies (PDF or table data) of files you open. The whole cache is limited to 1 GB and the oldest items are removed automatically when it fills up.
- **Technical logs:** the server keeps short technical logs (for example, an upload's file name, size, internal IDs and error messages) to find and fix problems. They are not used for any other purpose and are cleared regularly.

### 1.3 Your file contents

When you upload, download, preview, edit or share a file, its contents pass through our server on the way between your device (or the person using your share link) and your storage. We do not keep a copy, except the preview cache described above.

To show a preview of some document types stored in Google Drive (for example Word or PowerPoint files), the server asks Google Drive to make a temporary converted copy in your Drive (named `nudge-preview-…`), exports it as a PDF, and deletes the temporary copy straight away.

When you edit a document inside the app, it is opened in an editing service (Collabora Online) that runs on our own server. The document is sent from your storage to that editor while you work and saved back to your storage. Nothing is sent to Collabora's company.

### 1.4 Information that stays on your device

The app keeps these on your phone only. Signing out removes your sign-in tokens and backup settings; uninstalling the app or clearing its data removes everything:

- your sign-in tokens, your name and email (so you stay signed in), and your settings (theme, notifications, view options);
- your auto-backup settings and a list of the phone files already backed up (their device IDs, sizes and dates), so the same photo is not uploaded twice;
- a cache of thumbnails and recently opened files.

### 1.5 Device permissions

The app asks for these permissions only when you use the feature that needs them. You can refuse or turn them off in Android settings; the rest of the app keeps working.

- **Photos and videos** (Android 13 and later: "Photos and videos"; earlier versions: "Storage"): used only for **Auto backup**, to find new photos and videos in the phone folders you switch on and upload them to your storage. If you allow only selected photos, only those are visible to the app. Uploading single files uses the system picker and needs no permission.
- **Notifications:** to show upload, download and backup progress and storage alerts.
- **Nearby devices** (Bluetooth and Wi-Fi) and **Location:** used only by **Nearby share** to find and connect to nearby phones. Android requires the location permission for this kind of Bluetooth and Wi-Fi scanning. Nudge Drive **never reads, stores or sends your location**, and only uses these permissions while you are using Nearby share.
- **Run in the background (foreground service):** keeps uploads, downloads and auto backup going when you leave the app, with a visible progress notification.

## 2. Nearby share

Nearby share sends files directly between devices, without our server:

- **Phone to phone** uses Google Play services Nearby Connections over Bluetooth and Wi-Fi. Both people see a matching code and the receiver must accept before anything is sent.
- **Phone to computer** (and phones on the same Wi-Fi) uses a direct connection over your local network, encrypted with AES-GCM using fresh keys that the two devices agree on.

While you are sending or receiving, nearby devices can see a name made of your first name and your phone model (for example "Asha · Pixel 8"). Received files are saved in your phone's **Downloads › Nudge Drive** folder. The file contents and names are never sent to our server. Google Play services may collect limited diagnostics about Nearby Connections under Google's own privacy policy.

## 3. How we use information

We use the information above only to:

- create and secure your account, keep you signed in, verify your email and confirm account deletion;
- connect to your Google Drive accounts and S3 buckets, list, upload, download, move, rename, preview, edit, share and delete files at your request, and import the files already in a Drive when you ask;
- show your storage pool and pick where each upload goes according to your rule;
- run auto backup and Nearby share when you turn them on;
- show your activity history and share links, and let invited people open what you shared with them;
- send you service emails (verification codes and account-deletion messages). We do not send marketing emails;
- answer support requests, prevent abuse and fix problems.

We do not use your information for advertising, profiling, analytics or selling, and we do not make automated decisions about you.

## 4. Google user data

Nudge Drive asks Google for permission to see your basic profile (name, email address, profile picture) and to **see, edit, create and delete your Google Drive files** (`https://www.googleapis.com/auth/drive`). We use this access only to:

- sign you in and show which Google account is connected;
- show the space used and free in that Drive;
- upload files you add (they go into a folder that Nudge Drive creates in your Drive), and download, preview, rename, move, share and delete files when you ask;
- import the files and folders already in your Drive when you choose **Import**, so they appear in Nudge Drive, including the date photos were taken;
- make a Drive file viewable or editable by link when you tap **Drive link** (this changes the file's sharing setting in Google Drive to "anyone with the link can edit" — you can change it back in Google Drive);
- open Google Docs, Sheets and Slides files for viewing or editing.

**Limited Use disclosure:** Nudge Drive's use and transfer to any other app of information received from Google APIs will adhere to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements. In particular:

- we use Google user data only to provide and improve the user-facing features described above;
- we do not transfer it to anyone, except as needed to provide those features, to comply with the law, or as part of a merger or sale of the service with notice to you;
- we do not use or transfer it for advertising, including personalised or retargeted ads;
- we do not sell it, and we do not use it to determine credit-worthiness or for lending;
- no person reads your Google data unless you ask us to (for example in a support request), it is needed for security reasons such as investigating abuse, it is required by law, or the data has been aggregated and anonymised for internal operations;
- we do not use Google user data to develop, improve or train generalised or non-personalised AI or machine-learning models.

You can remove Nudge Drive's access to a Google account at any time at [myaccount.google.com/permissions](https://myaccount.google.com/permissions). Disconnecting a drive inside the app stops Nudge Drive from using it; its encrypted tokens and file list stay in your account until you delete your account or reconnect.

## 5. Who we share information with

We do **not** sell or rent your information, and we do not share it with advertisers or data brokers. Information leaves our hands only in these cases:

- **Your own storage providers**, because you asked: files and file actions go to Google Drive (Google LLC) and to the S3-compatible provider you chose, under their own terms and privacy policies.
- **Service providers** that run parts of the service for us:
  - **Microsoft Azure** (Microsoft Corporation) hosts our server and database in the Azure **Central India** region.
  - **Google** sends our service emails through Gmail, and provides the Google Drive API and Google sign-in.
  - **Google Play services** on your phone provides Nearby Connections and the "Turn on location" prompt used by Nearby share.
- **People you choose:** anyone with a share link you created can see the file's name, type and size and download it until you turn the link off. People you invite can open what you shared with them. People you send files to with Nearby share receive those files.
- **Legal reasons:** if required by Indian law, a court order or a lawful request from a government authority, or to protect the rights, safety or property of our users, the public or us.
- **Change of ownership:** if the service is transferred to someone else, your information would move with it under this policy, and we would tell you first.

## 6. Where your information is stored

Our server and database are in Microsoft Azure's Central India region. Your files are stored wherever your Google Drive or S3 provider keeps them, which may be outside India. Our service emails go through Google's servers.

## 7. How long we keep information

- **Account information, file and folder details, connected accounts, share links, invites you sent, activity history, sessions and API keys:** for as long as your account exists. Signed-out and expired sessions stay in your account's history until the account is deleted.
- **Email verification codes:** valid for 15 minutes; the hashed records stay with your account. **Account-deletion codes:** 15 minutes, in memory only.
- **Preview cache:** until it is pushed out by newer previews (the cache is limited to 1 GB in total), and always removed when you delete your account.
- **Database backups:** we make a daily backup of the database to recover from failures. Each backup is kept for **14 days** and then deleted automatically, so information from a deleted account disappears from backups within 14 days.
- **Support emails:** for as long as needed to handle your request, and not more than 2 years.
- **On your phone:** sign-in details and backup settings until you sign out; everything else until you clear the app's data or uninstall the app.

## 8. Deleting your account

You can delete your account:

- **in the app:** **Profile › Delete account**, then confirm with your password or with a code we email to you;
- **on the web:** at [nudge.qd.je/delete-account](https://nudge.qd.je/delete-account);
- **by email:** write to [support@nudge.qd.je](mailto:support@nudge.qd.je) from your account's email address.

When you delete your account we immediately revoke Nudge Drive's access to your connected Google accounts, delete your account and everything linked to it (file and folder details, connected accounts and their tokens and keys, share links, invites sent to and from you, activity history, sessions, API keys and verification records), and remove your cached previews. We then send a confirmation email. **Your files are not deleted** — they stay in your own Google Drive or S3 storage. Copies in database backups are deleted within 14 days. See [Account deletion](https://nudge.qd.je/delete-account) for details.

You can also delete single files, folders, share links, invites and API keys at any time in the app. Deleted files first go to the Trash, where you can restore them. Deleting a file forever from the Trash, or deleting a folder, also deletes it from your Google Drive or S3 storage.

## 9. Security

- All traffic between the app, the website and our server is encrypted with HTTPS (TLS). Our server talks to Google and S3 providers over HTTPS too.
- Storage tokens and S3 keys are encrypted at rest (AES-256-GCM). Passwords, sign-in tokens, API keys and verification codes are stored only as one-way hashes. Share link codes are long random values (so the app can show you the link again).
- Sign-in access tokens expire after a short time and sessions expire after 30 days.
- The database and preview cache are not reachable from the internet; only our web server is.

No system is perfectly secure. If we learn of a personal data breach that affects you, we will tell you and the Data Protection Board of India as required by law.

## 10. Your rights

Under India's Digital Personal Data Protection Act, 2023 and other applicable law, you can:

- **access** a summary of the personal data we hold about you and how we use it;
- **correct or update** it (you can also change most details in the app or in your Google account);
- **erase** it by deleting your account, or ask us to delete specific data;
- **withdraw consent** at any time by disconnecting a storage account, revoking access in your Google account, turning off auto backup or permissions, or deleting your account (this does not affect what was done before);
- **nominate** another person to exercise these rights for you if you die or become unable to do so;
- **complain** to our Grievance Officer and, if you are not satisfied, to the [Data Protection Board of India](https://www.dpdpa.gov.in/).

To use any of these rights, email [support@nudge.qd.je](mailto:support@nudge.qd.je) from the email address on your account. We may ask you to confirm it is you. We will reply within 30 days.

## 11. Grievance Officer

In line with the Information Technology Act, 2000 and the rules under it, and the Digital Personal Data Protection Act, 2023:

- **Grievance Officer:** Nudge Drive team
- **Email:** [support@nudge.qd.je](mailto:support@nudge.qd.je)

We will acknowledge your complaint within 24 hours and aim to resolve it within 15 days.

## 12. Children

Nudge Drive is not meant for children. You must be **18 or older** to create an account. We do not knowingly process personal data of anyone under 18. If you believe a child has created an account, email us and we will delete it.

## 13. Changes to this policy

We will post any change on this page and update the effective date. If a change materially affects how we use your information, we will tell you in the app or by email before it takes effect.

## 14. Contact

Nudge Drive team, India
Email: [support@nudge.qd.je](mailto:support@nudge.qd.je)
Website: [nudge.qd.je](https://nudge.qd.je)
