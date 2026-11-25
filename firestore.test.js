const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid_001";
const LABOUR_UID = "labour_uid_002";
const OUTSIDER_UID = "outsider_uid_003";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedStaffUsers() {
  const now = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      username: "admin",
      role: "owner",
      name: "Shop Owner",
      email: "owner@thennavalcombo.com",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(LABOUR_UID).set({
      userId: LABOUR_UID,
      username: "labour",
      role: "labour",
      name: "Ravi Technician",
      email: "labour@thennavalcombo.com",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated users cannot read or write any collection", async () => {
  await seedStaffUsers();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("stocks").get());
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("payments").get());
  await assertFails(unauthDb.collection("returns").get());
  await assertFails(unauthDb.collection("shops").get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb.collection("stocks").doc("s1").set({
      brand: "Vivo",
      partName: "Display",
      category: "Screen",
      unitPrice: 2500,
      quantity: 5,
      lowStockLimit: 2,
      status: "In Stock",
      updatedBy: "Hacker",
      updatedByUid: "none",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Self-registration prevents privilege escalation; bootstrapped owner email allowed", async () => {
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID, {
    email: "stranger@example.com",
    email_verified: true,
  }).firestore();

  // Cannot self-assign owner or active=true
  await assertFails(
    outsiderDb.collection("users").doc(OUTSIDER_UID).set({
      userId: OUTSIDER_UID,
      username: "stranger",
      role: "owner",
      name: "Stranger",
      email: "stranger@example.com",
      active: true,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Pending labour registration (role=labour, active=false) succeeds
  await assertSucceeds(
    outsiderDb.collection("users").doc(OUTSIDER_UID).set({
      userId: OUTSIDER_UID,
      username: "stranger",
      role: "labour",
      name: "Stranger",
      email: "stranger@example.com",
      active: false,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Inactive user cannot read stocks
  await assertFails(outsiderDb.collection("stocks").get());

  // Bootstrapped owner emails can self-register as active owner with hashed staff passcode
  const bootstrapDb = testEnv.authenticatedContext("boot_owner", {
    email: "mechmoorthi007@gmail.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    bootstrapDb.collection("users").doc("boot_owner").set({
      userId: "boot_owner",
      username: "admin",
      role: "owner",
      name: "Thennaval Owner",
      email: "mechmoorthi007@gmail.com",
      active: true,
      passcodeHash: "a1b2c3d4e5f6",
      passcodeSalt: "salt1234",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Owner can query users by username; outsider/labour cannot list users
  await assertSucceeds(
    bootstrapDb.collection("users").where("username", "==", "admin").limit(1).get()
  );
  await assertSucceeds(
    bootstrapDb.collection("notifications").doc("boot_notif").set({
      type: "manual_check",
      channel: "email",
      message: "Bootstrapped owner test email",
      status: "sent",
      recipient: "mechmoorthi007@gmail.com",
      createdAt: pastDate(500),
    })
  );
  await assertFails(
    outsiderDb.collection("users").where("username", "==", "admin").limit(1).get()
  );
});

test("Stock CRUD: active staff can create & update; only owner can delete", async () => {
  await seedStaffUsers();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const labourDb = testEnv.authenticatedContext(LABOUR_UID).firestore();
  const now = pastDate(8000);

  // Labour creates stock (NO @DocumentId id key in payload)
  await assertSucceeds(
    labourDb.collection("stocks").doc("stock_1").set({
      brand: "Samsung",
      partName: "Display",
      category: "Display & Touch",
      unitPrice: 3200.0,
      purchaseCost: 2400.0,
      quantity: 10,
      lowStockLimit: 3,
      status: "In Stock",
      updatedBy: "Ravi Technician",
      updatedByUid: LABOUR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject payload with extra @DocumentId 'id' key
  await assertFails(
    labourDb.collection("stocks").doc("stock_2").set({
      id: "stock_2",
      brand: "Vivo",
      partName: "Battery",
      category: "Power",
      unitPrice: 900.0,
      quantity: 5,
      lowStockLimit: 2,
      status: "In Stock",
      updatedBy: "Ravi Technician",
      updatedByUid: LABOUR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Labour updates quantity
  await assertSucceeds(
    labourDb.collection("stocks").doc("stock_1").update({
      quantity: 2,
      status: "Low Stock",
      updatedBy: "Ravi Technician",
      updatedByUid: LABOUR_UID,
      updatedAt: pastDate(1000),
    })
  );

  // Labour CANNOT delete stock
  await assertFails(labourDb.collection("stocks").doc("stock_1").delete());

  // Owner CAN delete stock
  await assertSucceeds(ownerDb.collection("stocks").doc("stock_1").delete());
});

test("Orders, Payments & Returns enforce relational existence, RBAC, and validation", async () => {
  await seedStaffUsers();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const labourDb = testEnv.authenticatedContext(LABOUR_UID).firestore();
  const now = pastDate(8000);

  // Create order by labour
  await assertSucceeds(
    labourDb.collection("orders").doc("ord_1").set({
      orderNumber: "ORD-1001",
      customerName: "Karthik",
      mobile: "9876543210",
      items: [
        {
          stockId: "stock_1",
          brand: "Redmi",
          partName: "Battery",
          quantity: 1,
          unitPrice: 1100.0,
          purchaseCost: 750.0,
          lineTotal: 1100.0,
        },
      ],
      brandSummary: "Redmi",
      partSummary: "Battery",
      totalQuantity: 1,
      totalAmount: 1100.0,
      discount: 100.0,
      finalAmount: 1000.0,
      paidAmount: 400.0,
      balanceAmount: 600.0,
      orderStatus: "Partial Payment",
      paymentStatus: "Partial",
      labourName: "Ravi Technician",
      createdBy: "Ravi Technician",
      createdByUid: LABOUR_UID,
      updatedBy: "Ravi Technician",
      updatedByUid: LABOUR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Payment for non-existent order fails
  await assertFails(
    labourDb.collection("payments").doc("pay_bad").set({
      orderId: "non_existent",
      orderNumber: "ORD-9999",
      customerName: "Karthik",
      mobile: "9876543210",
      amount: 600.0,
      paymentMethod: "UPI",
      collectedBy: "Ravi Technician",
      collectedByUid: LABOUR_UID,
      paymentDate: pastDate(2000),
      createdAt: pastDate(2000),
    })
  );

  // Payment for existing order succeeds
  await assertSucceeds(
    labourDb.collection("payments").doc("pay_1").set({
      orderId: "ord_1",
      orderNumber: "ORD-1001",
      customerName: "Karthik",
      mobile: "9876543210",
      amount: 600.0,
      paymentMethod: "UPI",
      collectedBy: "Ravi Technician",
      collectedByUid: LABOUR_UID,
      paymentDate: pastDate(2000),
      createdAt: pastDate(2000),
    })
  );

  // Labour can update order payment balance
  await assertSucceeds(
    labourDb.collection("orders").doc("ord_1").update({
      paidAmount: 1000.0,
      balanceAmount: 0.0,
      orderStatus: "Completed",
      paymentStatus: "Paid",
      updatedBy: "Ravi Technician",
      updatedByUid: LABOUR_UID,
      updatedAt: pastDate(1000),
    })
  );

  // Return creation succeeds for existing order
  await assertSucceeds(
    labourDb.collection("returns").doc("ret_1").set({
      orderId: "ord_1",
      orderNumber: "ORD-1001",
      customerName: "Karthik",
      mobile: "9876543210",
      stockId: "stock_1",
      product: "Redmi - Battery",
      quantity: 1,
      reason: "Connector mismatch",
      refundAmount: 1000.0,
      status: "Processed",
      processedBy: "Ravi Technician",
      processedByUid: LABOUR_UID,
      returnDate: pastDate(500),
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Shop settings & notifications: labour blocked, owner allowed
  await assertFails(
    labourDb.collection("shops").doc("main_shop").set({
      shopName: "Hacked",
      address: "Addr",
      mobile: "9999999999",
      ownerName: "Owner",
      email: "a@b.com",
      whatsapp: "9999999999",
      lowStockLimit: 3,
      dailyMorningAlert: true,
      dailyNightAlert: true,
      weeklyEmailReport: true,
      updatedBy: "Ravi",
      updatedByUid: LABOUR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    ownerDb.collection("shops").doc("main_shop").set({
      shopName: "Thennaval Combo",
      address: "Main Bazaar Road",
      mobile: "9876543210",
      ownerName: "Shop Owner",
      email: "owner@thennavalcombo.com",
      whatsapp: "+919876543210",
      emailWebhookUrl: "http://insecure.example.com/hook",
      lastWeeklyReportKey: "2026-W48",
      lowStockLimit: 3,
      dailyMorningAlert: true,
      dailyNightAlert: true,
      weeklyEmailReport: true,
      updatedBy: "Shop Owner",
      updatedByUid: OWNER_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("shops").doc("main_shop").set({
      shopName: "Thennaval Combo",
      address: "Main Bazaar Road",
      mobile: "9876543210",
      ownerName: "Shop Owner",
      email: "owner@thennavalcombo.com",
      whatsapp: "+919876543210",
      emailWebhookUrl: "https://script.google.com/macros/s/demo-id/exec",
      lastWeeklyReportKey: "2026-W48",
      lowStockLimit: 3,
      dailyMorningAlert: true,
      dailyNightAlert: true,
      weeklyEmailReport: true,
      updatedBy: "Shop Owner",
      updatedByUid: OWNER_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Active labour can log an instant stock notification when creating an order,
  // but cannot read, list, update, or delete notification logs.
  await assertSucceeds(
    labourDb.collection("notifications").doc("notif_1").set({
      type: "instant_stock_alert",
      channel: "email",
      message: "Low Stock Alert: Samsung Display",
      status: "sent",
      recipient: "owner@thennavalcombo.com",
      createdAt: pastDate(500),
    })
  );

  await assertFails(labourDb.collection("notifications").get());
  await assertFails(labourDb.collection("notifications").doc("notif_1").get());
  await assertFails(
    labourDb.collection("notifications").doc("notif_1").update({ status: "failed" })
  );
  await assertFails(labourDb.collection("notifications").doc("notif_1").delete());
  await assertSucceeds(ownerDb.collection("notifications").get());
});
