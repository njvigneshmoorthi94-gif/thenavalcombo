/**
 * Thennaval (@2026 thennaval powered by suwa)
 * 100% FREE Automatic Gmail & WhatsApp Alert Script (Google Apps Script)
 *
 * SETUP (2 minutes):
 * 1. Open https://script.google.com signed in with your Gmail (mechmoorthi007@gmail.com).
 * 2. Click "New project", paste this entire file, and save.
 * 3. Click Deploy -> New deployment -> Type: Web app
 *    - Execute as: Me
 *    - Who has access: Anyone
 * 4. Copy the Web App URL (https://script.google.com/macros/s/.../exec)
 *    and paste it into Settings -> Google Apps Script Web App URL -> Save.
 */

function escapeHtml(value) {
  return String(value == null ? "" : value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function buildHtmlTable(title, headers, rows, footerLines) {
  var ths = headers
    .map(function (h) {
      return (
        '<th style="text-align:left;padding:8px 12px;background:#131B31;color:#F8FAFC;border:1px solid #233054;">' +
        escapeHtml(h) +
        "</th>"
      );
    })
    .join("");

  var trs = rows
    .map(function (cols) {
      var tds = cols
        .map(function (c) {
          return (
            '<td style="padding:8px 12px;border:1px solid #233054;color:#E2E8F0;background:#0C1222;">' +
            escapeHtml(c) +
            "</td>"
          );
        })
        .join("");
      return "<tr>" + tds + "</tr>";
    })
    .join("");

  var footerHtml = (footerLines || [])
    .map(function (line) {
      return '<p style="margin:4px 0;color:#CBD5E1;font-size:13px;">' + escapeHtml(line) + "</p>";
    })
    .join("");

  return (
    '<div style="font-family:Arial,sans-serif;background:#060913;color:#F8FAFC;padding:20px;">' +
    '<h2 style="margin:0 0 12px;color:#A78BFA;">' +
    escapeHtml(title) +
    "</h2>" +
    '<table style="border-collapse:collapse;width:100%;max-width:680px;font-size:14px;">' +
    "<thead><tr>" +
    ths +
    "</tr></thead>" +
    "<tbody>" +
    trs +
    "</tbody>" +
    "</table>" +
    '<div style="margin-top:14px;">' +
    footerHtml +
    "</div>" +
    '<p style="margin-top:16px;color:#94A3B8;font-size:11px;">@2026 thennaval powered by suwa</p>' +
    "</div>"
  );
}

function doPost(e) {
  try {
    var payload = JSON.parse((e && e.postData && e.postData.contents) || "{}");
    var recipient = String(
      payload.email || payload.to || Session.getEffectiveUser().getEmail() || "mechmoorthi007@gmail.com"
    ).trim();
    if (!recipient) {
      return ContentService.createTextOutput(JSON.stringify({ ok: false, error: "No recipient email" }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    var action = String(payload.action || payload.type || "alert");
    var phone = String(payload.phone || "918526820655").replace(/[^0-9]/g, "");
    var item = String(payload.item || "");
    var stock = payload.stock != null ? Number(payload.stock) : null;
    var minStock = payload.minStock != null ? Number(payload.minStock) : 5;
    var soldQty = payload.soldQty != null ? Number(payload.soldQty) : 0;

    var statusLabel = stock != null && stock <= 0 ? "EMPTY STOCK" : "LOW STOCK";
    var subject = payload.subject
      ? String(payload.subject)
      : action === "weekly_report"
      ? "Thennaval - Weekly Sales & Stock Report"
      : "URGENT " + statusLabel + ": " + (item || "Inventory Item") + " (Stock: " + stock + ")";

    var title = payload.title
      ? String(payload.title)
      : action === "weekly_report"
      ? "Thennaval Weekly Status Report"
      : "Thennaval - " + statusLabel + " Alert";

    var headers = Array.isArray(payload.headers) ? payload.headers : [];
    var rows = Array.isArray(payload.rows) ? payload.rows : [];
    var footerLines = Array.isArray(payload.footerLines) ? payload.footerLines : [];

    if ((headers.length === 0 || rows.length === 0) && item) {
      headers = ["Item", "Action", "Sold Qty", "Stock", "Min Stock", "Status"];
      rows = [[item, action, String(soldQty), String(stock), String(minStock), statusLabel]];
    } else if (action === "weekly_report" && headers.length === 0) {
      headers = ["Report", "Email", "WhatsApp", "Status"];
      rows = [["Weekly Shop Summary", recipient, phone, "Triggered"]];
    }

    var textBody = String(
      payload.textBody ||
        (title + "\nItem: " + item + "\nStock: " + stock + " / Min: " + minStock + "\nWhatsApp: " + phone)
    );

    var whatsappText = encodeURIComponent(textBody);
    var whatsappUrl = phone ? "https://wa.me/" + phone + "?text=" + whatsappText : "";
    if (whatsappUrl) {
      footerLines = footerLines.concat(["WhatsApp quick notify link: " + whatsappUrl]);
    }

    var htmlBody =
      headers.length > 0 && rows.length > 0
        ? buildHtmlTable(title, headers, rows, footerLines)
        : '<div style="font-family:Arial,sans-serif;padding:16px;"><pre>' + escapeHtml(textBody) + "</pre></div>";

    MailApp.sendEmail({
      to: recipient,
      subject: subject,
      body: textBody,
      htmlBody: htmlBody,
    });

    return ContentService.createTextOutput(
      JSON.stringify({ ok: true, to: recipient, action: action, whatsappUrl: whatsappUrl })
    ).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ ok: false, error: String(err) }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
