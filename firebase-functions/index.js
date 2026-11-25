const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const nodemailer = require("nodemailer");

initializeApp();
const db = getFirestore();

// Configure these secrets in Firebase CLI before deploying:
// firebase functions:secrets:set SMTP_HOST SMTP_PORT SMTP_USER SMTP_PASS WHATSAPP_TOKEN WHATSAPP_PHONE_NUMBER_ID
const smtpHost = defineSecret("SMTP_HOST");
const smtpPort = defineSecret("SMTP_PORT");
const smtpUser = defineSecret("SMTP_USER");
const smtpPass = defineSecret("SMTP_PASS");
const whatsappToken = defineSecret("WHATSAPP_TOKEN");
const whatsappPhoneNumberId = defineSecret("WHATSAPP_PHONE_NUMBER_ID");

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function padCell(value, width) {
  const str = String(value ?? "");
  if (str.length >= width) return str.slice(0, width);
  return str + " ".repeat(width - str.length);
}

function formatIstShortDate(ts) {
  const date = ts && typeof ts.toDate === "function" ? ts.toDate() : new Date();
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata",
    day: "2-digit",
    month: "short",
    year: "2-digit",
  }).formatToParts(date);
  const get = (type) => parts.find((p) => p.type === type)?.value || "";
  return `${get("day")}-${get("month").toLowerCase()}-${get("year")}`;
}

function mapPaymentLabel(orderStatus) {
  if (orderStatus === "Completed") return "Completed";
  if (orderStatus === "Returned") return "Return";
  return "Pending";
}

async function loadShopConfig() {
  const snap = await db.collection("shops").doc("main_shop").get();
  return snap.exists ? snap.data() : null;
}

async function resolveOwnerEmail(shop) {
  const configured = String(shop?.email || "").trim();
  if (configured) return configured;

  const ownersSnap = await db
    .collection("users")
    .where("role", "==", "owner")
    .where("active", "==", true)
    .get();
  const emails = [];
  ownersSnap.forEach((doc) => {
    const email = String(doc.data()?.email || "").trim();
    if (email && !emails.includes(email)) {
      emails.push(email);
    }
  });
  return emails.join(", ");
}

async function sendEmail(toEmail, subject, textBody, htmlBody) {
  const host = smtpHost.value();
  const user = smtpUser.value();
  const pass = smtpPass.value();
  const port = parseInt(smtpPort.value() || "587", 10);

  if (!host || !user || !pass || !toEmail) {
    return { status: "requires_backend_config", detail: "SMTP secrets or owner email missing" };
  }

  const transporter = nodemailer.createTransport({
    host,
    port,
    secure: port === 465,
    auth: { user, pass },
  });

  await transporter.sendMail({
    from: `"Thennaval Combo Alerts" <${user}>`,
    to: toEmail,
    subject,
    text: textBody,
    html: htmlBody,
  });

  return { status: "sent", detail: `Delivered to ${toEmail}` };
}

async function sendWhatsAppMessage(toPhone, bodyText) {
  const token = whatsappToken.value();
  const phoneId = whatsappPhoneNumberId.value();

  if (!token || !phoneId || !toPhone) {
    return { status: "requires_backend_config", detail: "WhatsApp Cloud API secrets or recipient missing" };
  }

  const normalizedPhone = toPhone.replace(/[^0-9]/g, "");
  const response = await fetch(`https://graph.facebook.com/v20.0/${phoneId}/messages`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      messaging_product: "whatsapp",
      to: normalizedPhone,
      type: "text",
      text: { body: bodyText },
    }),
  });

  if (!response.ok) {
    const errText = await response.text();
    throw new Error(`WhatsApp Cloud API error (${response.status}): ${errText}`);
  }

  return { status: "sent", detail: `Delivered to +${normalizedPhone}` };
}

function buildHtmlTable(title, headers, rows, footerLines = []) {
  const thCells = headers
    .map(
      (h) =>
        `<th style="text-align:left;padding:8px 12px;background:#131B31;color:#F8FAFC;border:1px solid #233054;">${escapeHtml(h)}</th>`
    )
    .join("");
  const bodyRows = rows
    .map((cols) => {
      const tds = cols
        .map(
          (c) =>
            `<td style="padding:8px 12px;border:1px solid #233054;color:#E2E8F0;background:#0C1222;">${escapeHtml(c)}</td>`
        )
        .join("");
      return `<tr>${tds}</tr>`;
    })
    .join("");
  const footerHtml = footerLines
    .map((l) => `<p style="margin:4px 0;color:#CBD5E1;font-size:13px;">${escapeHtml(l)}</p>`)
    .join("");

  return `
    <div style="font-family:Arial,sans-serif;background:#060913;color:#F8FAFC;padding:20px;">
      <h2 style="margin:0 0 12px;color:#A78BFA;">${escapeHtml(title)}</h2>
      <table style="border-collapse:collapse;width:100%;max-width:680px;font-size:14px;">
        <thead><tr>${thCells}</tr></thead>
        <tbody>${bodyRows}</tbody>
      </table>
      <div style="margin-top:14px;">${footerHtml}</div>
    </div>
  `;
}

function buildTextTable(title, headers, widths, rows, footerLines = []) {
  const headerLine = headers.map((h, i) => padCell(h, widths[i])).join(" | ");
  const divider = widths.map((w) => "-".repeat(w)).join("-+-");
  const rowLines = rows.map((cols) => cols.map((c, i) => padCell(c, widths[i])).join(" | "));
  return [title, "", "```", headerLine, divider, ...rowLines, "```", ...footerLines].join("\n");
}

async function runDailyStockNotification(slotType) {
  const shop = await loadShopConfig();
  const ownerEmail = await resolveOwnerEmail(shop);

  const isMorning = slotType === "daily_stock_morning";
  if (isMorning && shop && shop.dailyMorningAlert === false) return;
  if (!isMorning && shop && shop.dailyNightAlert === false) return;

  const stocksSnap = await db.collection("stocks").get();
  const lowOrEmpty = [];
  stocksSnap.forEach((doc) => {
    const s = doc.data();
    const qty = Number(s.quantity || 0);
    const limit = Number(s.lowStockLimit || shop?.lowStockLimit || 3);
    if (qty === 0 || qty <= limit) {
      const restockQty = Math.max(limit * 2 - qty, 1);
      lowOrEmpty.push([
        s.brand || "-",
        s.partName || "-",
        String(qty),
        qty === 0 ? "Empty" : "Low",
        `+${restockQty}`,
      ]);
    }
  });

  if (lowOrEmpty.length === 0) return;

  const slotLabel = isMorning ? "9:00 AM IST" : "9:00 PM IST";
  const title = `Thennaval Combo Stock Alert (${slotLabel})`;
  const headers = ["Brand", "Part", "Qty", "Status", "Restock"];
  const textBody = buildTextTable(title, headers, [10, 16, 4, 6, 7], lowOrEmpty);
  const htmlBody = buildHtmlTable(title, headers, lowOrEmpty);

  try {
    const emailRes = await sendEmail(
      ownerEmail,
      `Thennaval Combo - Stock Alert (${lowOrEmpty.length} items)`,
      textBody,
      htmlBody
    );
    await db.collection("notifications").add({
      type: slotType,
      channel: "email",
      message: textBody,
      status: emailRes.status,
      recipient: ownerEmail || "",
      createdAt: FieldValue.serverTimestamp(),
    });
  } catch (err) {
    await db.collection("notifications").add({
      type: slotType,
      channel: "email",
      message: `${textBody}\nError: ${err.message}`,
      status: "failed",
      recipient: ownerEmail || "",
      createdAt: FieldValue.serverTimestamp(),
    });
  }

  try {
    const waRes = await sendWhatsAppMessage(shop?.whatsapp, textBody);
    await db.collection("notifications").add({
      type: slotType,
      channel: "whatsapp",
      message: textBody,
      status: waRes.status,
      recipient: shop?.whatsapp || "",
      createdAt: FieldValue.serverTimestamp(),
    });
  } catch (err) {
    await db.collection("notifications").add({
      type: slotType,
      channel: "whatsapp",
      message: `${textBody}\nError: ${err.message}`,
      status: "failed",
      recipient: shop?.whatsapp || "",
      createdAt: FieldValue.serverTimestamp(),
    });
  }
}

// Instant automatic Email (+ WhatsApp if configured) immediately when stock hits Low or Empty Stock
exports.onStockLowOrEmptyAlert = onDocumentWritten(
  {
    document: "stocks/{stockId}",
    database: "(default)",
    secrets: [smtpHost, smtpPort, smtpUser, smtpPass, whatsappToken, whatsappPhoneNumberId],
  },
  async (event) => {
    const afterSnap = event.data?.after;
    if (!afterSnap || !afterSnap.exists) return;

    const after = afterSnap.data();
    const before = event.data?.before?.exists ? event.data.before.data() : null;

    const shop = await loadShopConfig();
    const limit = Number(after.lowStockLimit ?? shop?.lowStockLimit ?? 3);
    const afterQty = Number(after.quantity || 0);
    if (afterQty > limit) return;

    const prevQty = before ? Number(before.quantity || 0) : null;
    const prevLimit = before ? Number(before.lowStockLimit ?? shop?.lowStockLimit ?? 3) : null;
    const wasAlerting = prevQty !== null && prevLimit !== null && prevQty <= prevLimit;

    // Only alert immediately when entering Low/Empty state or when quantity drops further
    if (wasAlerting && afterQty >= prevQty) return;

    const ownerEmail = await resolveOwnerEmail(shop);
    const stockStatus = afterQty === 0 ? "Empty Stock" : "Low Stock";
    const triggeredLabel = `${after.brand || "-"} ${after.partName || "-"}`.trim();

    const stocksSnap = await db.collection("stocks").get();
    const lowOrEmpty = [];
    stocksSnap.forEach((doc) => {
      const s = doc.data();
      const qty = Number(s.quantity || 0);
      const itemLimit = Number(s.lowStockLimit ?? shop?.lowStockLimit ?? 3);
      if (qty === 0 || qty <= itemLimit) {
        const restockQty = Math.max(itemLimit * 2 - qty, 1);
        lowOrEmpty.push([
          s.brand || "-",
          s.partName || "-",
          String(qty),
          qty === 0 ? "Empty" : "Low",
          `+${restockQty}`,
        ]);
      }
    });

    const shopHeading = shop?.shopName || "Thennaval Combo";
    const title = `${shopHeading} - Immediate ${stockStatus} Alert`;
    const headers = ["Brand", "Part", "Qty", "Status", "Restock"];
    const footerLines = [
      `Triggered Item: ${triggeredLabel} (Qty: ${afterQty}, Status: ${stockStatus})`,
      `Updated By: ${after.updatedBy || "Staff"}`,
    ];
    const textBody = buildTextTable(title, headers, [10, 16, 4, 6, 7], lowOrEmpty, footerLines);
    const htmlBody = buildHtmlTable(title, headers, lowOrEmpty, footerLines);

    try {
      const emailRes = await sendEmail(
        ownerEmail,
        `URGENT ${stockStatus}: ${triggeredLabel} (Qty ${afterQty})`,
        textBody,
        htmlBody
      );
      await db.collection("notifications").add({
        type: "instant_stock_alert",
        channel: "email",
        message: textBody,
        status: emailRes.status,
        recipient: ownerEmail || "",
        createdAt: FieldValue.serverTimestamp(),
      });
    } catch (err) {
      await db.collection("notifications").add({
        type: "instant_stock_alert",
        channel: "email",
        message: `${textBody}\nError: ${err.message}`,
        status: "failed",
        recipient: ownerEmail || "",
        createdAt: FieldValue.serverTimestamp(),
      });
    }

    if (shop?.whatsapp) {
      try {
        const waRes = await sendWhatsAppMessage(shop.whatsapp, textBody);
        await db.collection("notifications").add({
          type: "instant_stock_alert",
          channel: "whatsapp",
          message: textBody,
          status: waRes.status,
          recipient: shop.whatsapp,
          createdAt: FieldValue.serverTimestamp(),
        });
      } catch (err) {
        await db.collection("notifications").add({
          type: "instant_stock_alert",
          channel: "whatsapp",
          message: `${textBody}\nError: ${err.message}`,
          status: "failed",
          recipient: shop.whatsapp,
          createdAt: FieldValue.serverTimestamp(),
        });
      }
    }
  }
);

// Daily 9:00 AM IST
exports.dailyStockMorningAlert = onSchedule(
  {
    schedule: "0 9 * * *",
    timeZone: "Asia/Kolkata",
    secrets: [smtpHost, smtpPort, smtpUser, smtpPass, whatsappToken, whatsappPhoneNumberId],
  },
  async () => runDailyStockNotification("daily_stock_morning")
);

// Daily 9:00 PM IST
exports.dailyStockNightAlert = onSchedule(
  {
    schedule: "0 21 * * *",
    timeZone: "Asia/Kolkata",
    secrets: [smtpHost, smtpPort, smtpUser, smtpPass, whatsappToken, whatsappPhoneNumberId],
  },
  async () => runDailyStockNotification("daily_stock_night")
);

// Weekly Monday 8:00 AM IST (Email + WhatsApp Table View)
exports.weeklyOwnerReport = onSchedule(
  {
    schedule: "0 8 * * 1",
    timeZone: "Asia/Kolkata",
    secrets: [smtpHost, smtpPort, smtpUser, smtpPass, whatsappToken, whatsappPhoneNumberId],
  },
  async () => {
    const shop = await loadShopConfig();
    if (shop && shop.weeklyEmailReport === false) return;
    const ownerEmail = await resolveOwnerEmail(shop);

    const weekAgo = Timestamp.fromMillis(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const [ordersSnap, paymentsSnap, returnsSnap, stocksSnap] = await Promise.all([
      db.collection("orders").where("createdAt", ">=", weekAgo).get(),
      db.collection("payments").where("paymentDate", ">=", weekAgo).get(),
      db.collection("returns").where("returnDate", ">=", weekAgo).get(),
      db.collection("stocks").get(),
    ]);

    let weeklySales = 0;
    let completedOrders = 0;
    let pendingOrders = 0;
    let returnedOrders = 0;
    let pendingPayments = 0;
    const orderRows = [];

    ordersSnap.forEach((doc) => {
      const o = doc.data();
      weeklySales += Number(o.finalAmount || 0);
      pendingPayments += Number(o.balanceAmount || 0);
      const statusLabel = mapPaymentLabel(o.orderStatus);
      if (statusLabel === "Completed") completedOrders++;
      else if (statusLabel === "Return") returnedOrders++;
      else pendingOrders++;

      orderRows.push([
        o.customerName || "Customer",
        formatIstShortDate(o.createdAt),
        statusLabel,
      ]);
    });

    if (orderRows.length === 0) {
      orderRows.push(["No orders", formatIstShortDate(Timestamp.now()), "-"]);
    }

    let collectedPayments = 0;
    paymentsSnap.forEach((doc) => {
      collectedPayments += Number(doc.data().amount || 0);
    });

    let lowStockCount = 0;
    let emptyStockCount = 0;
    stocksSnap.forEach((doc) => {
      const s = doc.data();
      const qty = Number(s.quantity || 0);
      const limit = Number(s.lowStockLimit || 3);
      if (qty === 0) emptyStockCount++;
      else if (qty <= limit) lowStockCount++;
    });

    const shopHeading = shop?.shopName || "Thennaval Combo";
    const title = `${shopHeading} - Weekly Status (Monday 8:00 AM IST)`;
    const headers = ["Shop / Customer", "Date", "Payment"];
    const footerLines = [
      `Weekly Sales: ₹${weeklySales.toFixed(2)} | Collected: ₹${collectedPayments.toFixed(2)} | Pending: ₹${pendingPayments.toFixed(2)}`,
      `Completed: ${completedOrders} | Pending: ${pendingOrders} | Return: ${returnedOrders} (Records: ${returnsSnap.size})`,
      `Stock Alerts -> Low: ${lowStockCount} | Empty: ${emptyStockCount}`,
    ];

    const textBody = buildTextTable(title, headers, [18, 11, 10], orderRows, footerLines);
    const htmlBody = buildHtmlTable(title, headers, orderRows, footerLines);

    try {
      const emailRes = await sendEmail(
        ownerEmail,
        `${shopHeading} - Weekly Status Report`,
        textBody,
        htmlBody
      );
      await db.collection("notifications").add({
        type: "weekly_report",
        channel: "email",
        message: textBody,
        status: emailRes.status,
        recipient: ownerEmail || "",
        createdAt: FieldValue.serverTimestamp(),
      });
    } catch (err) {
      await db.collection("notifications").add({
        type: "weekly_report",
        channel: "email",
        message: `${textBody}\nError: ${err.message}`,
        status: "failed",
        recipient: ownerEmail || "",
        createdAt: FieldValue.serverTimestamp(),
      });
    }

    try {
      const waRes = await sendWhatsAppMessage(shop?.whatsapp, textBody);
      await db.collection("notifications").add({
        type: "weekly_report",
        channel: "whatsapp",
        message: textBody,
        status: waRes.status,
        recipient: shop?.whatsapp || "",
        createdAt: FieldValue.serverTimestamp(),
      });
    } catch (err) {
      await db.collection("notifications").add({
        type: "weekly_report",
        channel: "whatsapp",
        message: `${textBody}\nError: ${err.message}`,
        status: "failed",
        recipient: shop?.whatsapp || "",
        createdAt: FieldValue.serverTimestamp(),
      });
    }
  }
);
