# Thennaval Combo – Firebase Setup & Deployment Guide

## 1. Android App Configuration (`thennaval-combo`)
1. Open the [Firebase Console](https://console.firebase.google.com/project/thennaval-combo/settings/general).
2. Under **Your apps**, register/select the Android app `com.aistudio.thennaval.kxmzqr`.
3. Add the debug signing certificate SHA-1 and SHA-256 fingerprints from `debug.keystore` (and your release keystore fingerprints for production).
4. Enable **Google Sign-In** in **Authentication -> Sign-in method** so `oauth_client` (`client_type: 3`) is included in `google-services.json`.
5. Download `google-services.json` and place it at `app/google-services.json`.
6. Create the Cloud Firestore database (Standard `(default)` edition, or set `firestore_database_id` if using a named Enterprise database).

## 2. Firestore Collections Configured
- `users/{userId}` — Staff accounts (`role`: `"owner"` | `"labour"`, `active`: boolean)
- `shops/{shopId}` — Shop settings, contact info, notification toggles
- `stocks/{stockId}` — Mobile spare parts inventory (`brand`, `partName`, `category`, `unitPrice`, `purchaseCost`, `quantity`, `lowStockLimit`, `status`)
- `orders/{orderId}` — Customer orders with atomic stock deduction and balance tracking
- `payments/{paymentId}` — Partial and full payment history linked to orders
- `returns/{returnId}` — Return & refund history with atomic stock restoration
- `notifications/{notificationId}` — Scheduled Email & WhatsApp delivery audit logs

## 3. Deploy Firestore Security Rules
```bash
firebase use thennaval-combo
firebase deploy --only firestore:rules
```

## 4. 100% Free Automatic Email Setup (Google Apps Script — No Credit Card / No Blaze Plan)
1. Open [Google Apps Script](https://script.google.com) signed in with your Gmail (`njvigneshmoorthi94@gmail.com`).
2. Create a **New project**, copy the entire `free-email-script/Code.gs` file into `Code.gs`, and save.
3. Click **Deploy -> New deployment -> Type: Web app**:
   - **Execute as**: `Me`
   - **Who has access**: `Anyone`
4. Authorize Gmail sending once and copy the generated `https://script.google.com/macros/s/.../exec` Web App URL.
5. In the **Thennaval Combo** app, go to **Shop Settings**, paste the URL into **Free Email Webhook URL (Google Apps Script)**, tap **Save Shop Settings**, and tap **Send Test Email**.
   - Low & Empty Stock alerts send **immediately** across every staff phone whenever stock drops to/below `lowStockLimit` or reaches `0`.
   - Weekly Sales & Stock reports send **automatically every Monday after 8:00 AM IST** (or anytime via **Send Weekly Now**).

## 5. Optional: Scheduled Cloud Functions (Asia/Kolkata — Requires Firebase Blaze Plan)
1. Install dependencies:
   ```bash
   cd firebase-functions && npm install && cd ..
   ```
2. Set Email (SMTP) and Meta WhatsApp Business Cloud API secrets securely on Firebase:
   ```bash
   firebase functions:secrets:set SMTP_HOST
   firebase functions:secrets:set SMTP_PORT
   firebase functions:secrets:set SMTP_USER
   firebase functions:secrets:set SMTP_PASS
   firebase functions:secrets:set WHATSAPP_TOKEN
   firebase functions:secrets:set WHATSAPP_PHONE_NUMBER_ID
   ```
3. Deploy triggers & scheduled jobs (instant Low/Empty Stock email on stock updates, 9:00 AM & 9:00 PM IST daily, Monday 8:00 AM IST weekly):
   ```bash
   firebase deploy --only functions
   ```
