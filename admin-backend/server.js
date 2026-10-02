require('dotenv').config();

const express = require('express');
const path = require('path');
const fs = require('fs');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const nodemailer = require('nodemailer');
const Database = require('better-sqlite3');

const app = express();
const PORT = process.env.PORT || 4001;
const JWT_SECRET = process.env.JWT_SECRET || 'laundry-admin-secret';
const EMAIL_FROM = process.env.EMAIL_FROM || process.env.MAIL_FROM || 'Laundry Market <no-reply@laundrymarket.local>';
const isProduction = process.env.NODE_ENV === 'production';
if (isProduction && JWT_SECRET === 'laundry-admin-secret') {
  throw new Error('JWT_SECRET must be configured in production');
}
const dataDir = path.join(__dirname, 'data');
const dbPath = path.join(dataDir, 'laundry.db');

app.disable('x-powered-by');
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  if (isProduction) res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  next();
});
app.use(cors({ origin: process.env.CORS_ORIGIN || (isProduction ? false : true) }));
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true }));

fs.mkdirSync(dataDir, { recursive: true });

const db = new Database(dbPath);
db.pragma('journal_mode = WAL');

db.exec(`
  CREATE TABLE IF NOT EXISTS admins (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    name TEXT NOT NULL,
    role TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT NOT NULL,
    kyc_status TEXT NOT NULL,
    kyc_score INTEGER NOT NULL,
    wallet_balance_cents INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS orders (
    id TEXT PRIMARY KEY,
    customer_id TEXT,
    customer_name TEXT NOT NULL,
    vendor_id TEXT,
    vendor_name TEXT NOT NULL,
    driver_id TEXT,
    driver_name TEXT,
    status TEXT NOT NULL,
    total_cents INTEGER NOT NULL,
    created_at TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS payouts (
    id TEXT PRIMARY KEY,
    vendor_id TEXT,
    vendor_name TEXT NOT NULL,
    amount_cents INTEGER NOT NULL,
    status TEXT NOT NULL,
    due_date TEXT NOT NULL,
    created_at TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS wallet_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    user_name TEXT NOT NULL,
    type TEXT NOT NULL,
    amount_cents INTEGER NOT NULL,
    note TEXT,
    created_at TEXT NOT NULL
  );
`);

const adminSeed = [
  { username: 'admin', password: 'admin123', name: 'Laundry Ops Admin', role: 'super-admin' }
];

const insertAdmin = db.prepare(`INSERT OR IGNORE INTO admins (username, password, name, role) VALUES (@username, @password, @name, @role)`);
for (const admin of adminSeed) insertAdmin.run(admin);

const userColumns = db.prepare('PRAGMA table_info(users)').all();
const hasPasswordColumn = userColumns.some((column) => column.name === 'password');
if (!hasPasswordColumn) {
  db.exec('ALTER TABLE users ADD COLUMN password TEXT');
}

const demoPasswordsByRole = {
  customer: 'customer123',
  vendor: 'vendor123',
  driver: 'driver123'
};

const userCount = db.prepare('SELECT COUNT(*) AS count FROM users').get();
if (!userCount || Number(userCount.count) === 0) {
  db.exec(`
    INSERT INTO users (id, name, email, role, status, kyc_status, kyc_score, wallet_balance_cents, password, created_at, updated_at) VALUES
      ('cust-001', 'Ava Patel', 'ava.patel@example.com', 'customer', 'active', 'approved', 94, 240000, 'customer123', '2026-07-01T10:00:00.000Z', '2026-08-01T10:00:00.000Z'),
      ('vendor-001', 'Fresh Fold Laundry', 'ops@freshfold.com', 'vendor', 'active', 'pending', 71, 680000, 'vendor123', '2026-06-11T16:30:00.000Z', '2026-08-10T16:30:00.000Z'),
      ('driver-001', 'Noah Hill', 'noah.hill@example.com', 'driver', 'active', 'approved', 89, 195000, 'driver123', '2026-07-05T08:15:00.000Z', '2026-08-12T08:15:00.000Z'),
      ('vendor-002', 'Sparkle Suds', 'support@sparklesuds.com', 'vendor', 'review', 'rejected', 42, 0, 'vendor123', '2026-07-14T12:45:00.000Z', '2026-08-14T12:45:00.000Z'),
      ('driver-002', 'Liam Stone', 'liam.stone@example.com', 'driver', 'inactive', 'pending', 58, 50000, 'driver123', '2026-08-10T09:10:00.000Z', '2026-08-10T09:10:00.000Z');
  `);
}

for (const row of db.prepare('SELECT id, role, password FROM users').all()) {
  const fallbackPassword = demoPasswordsByRole[row.role] || 'password123';
  if (!row.password || String(row.password).trim() === '') {
    db.prepare('UPDATE users SET password = ? WHERE id = ?').run(fallbackPassword, row.id);
  }
}

const VALID_USER_ROLES = ['customer', 'vendor', 'driver'];
const DEFAULT_CUSTOMER_ID = 'cust-001';
const DEFAULT_VENDOR_ID = 'vendor-001';
const DEFAULT_DRIVER_ID = 'driver-001';
const adminLoginAttempts = new Map();

function isRateLimited(key) {
  const now = Date.now();
  const current = adminLoginAttempts.get(key);
  if (!current || now - current.startedAt > 15 * 60 * 1000) {
    adminLoginAttempts.set(key, { startedAt: now, count: 1 });
    return false;
  }
  current.count += 1;
  return current.count > 10;
}

const DEMO_SHOPS = [
  {
    shopId: 'vendor-001',
    name: 'Fresh Fold Laundry',
    address: '12 Main Street, Downtown',
    rating: 4.8,
    services: [
      { id: 'svc-wash-fold', name: 'Wash & Fold', priceCents: 500, unit: 'kg' },
      { id: 'svc-dry-clean', name: 'Dry Cleaning', priceCents: 1200, unit: 'piece' },
      { id: 'svc-ironing', name: 'Ironing', priceCents: 300, unit: 'piece' }
    ],
    latitude: 6.5244,
    longitude: 3.3792
  },
  {
    shopId: 'vendor-002',
    name: 'Sparkle Suds',
    address: '88 Harbor Avenue',
    rating: 4.6,
    services: [
      { id: 'svc-wash-fold', name: 'Wash & Fold', priceCents: 550, unit: 'kg' },
      { id: 'svc-dry-clean', name: 'Dry Cleaning', priceCents: 1300, unit: 'piece' },
      { id: 'svc-ironing', name: 'Ironing', priceCents: 350, unit: 'piece' }
    ],
    latitude: 6.5013,
    longitude: 3.3945
  }
];

function normalizeUserRole(role) {
  return String(role || '').trim().toLowerCase();
}

function getDefaultCustomer() {
  return db.prepare("SELECT * FROM users WHERE role = 'customer' ORDER BY created_at ASC LIMIT 1").get() || {
    id: DEFAULT_CUSTOMER_ID,
    name: 'Ava Patel',
    email: 'ava.patel@example.com'
  };
}

function getDefaultVendor() {
  return db.prepare("SELECT * FROM users WHERE role = 'vendor' ORDER BY created_at ASC LIMIT 1").get() || {
    id: DEFAULT_VENDOR_ID,
    name: 'Fresh Fold Laundry',
    email: 'ops@freshfold.com'
  };
}

function getDefaultDriver() {
  return db.prepare("SELECT * FROM users WHERE role = 'driver' ORDER BY created_at ASC LIMIT 1").get() || {
    id: DEFAULT_DRIVER_ID,
    name: 'Noah Hill',
    email: 'noah.hill@example.com'
  };
}

function serializeOrderRow(row) {
  return {
    orderId: row.id,
    status: row.status,
    customerId: row.customer_id || getDefaultCustomer().id,
    vendorId: row.vendor_id || getDefaultVendor().id,
    driverId: row.driver_id || null,
    totalCents: Number(row.total_cents || 0),
    items: []
  };
}

function createEmailTransporter() {
  const mailHost = process.env.MAIL_HOST || process.env.SMTP_HOST;
  const mailUser = process.env.MAIL_USER || process.env.SMTP_USER;
  const mailPass = process.env.MAIL_PASS || process.env.SMTP_PASS;
  const mailPort = Number(process.env.MAIL_PORT || process.env.SMTP_PORT || 587);
  const mailSecure = String(process.env.MAIL_SECURE ?? process.env.SMTP_SECURE ?? 'false').toLowerCase() === 'true';

  if (mailHost && mailUser && mailPass) {
    const transportConfig = {
      host: mailHost,
      port: mailPort,
      secure: mailSecure,
      auth: { user: mailUser, pass: mailPass },
      tls: {
        rejectUnauthorized: String(process.env.MAIL_TLS_REJECT_UNAUTHORIZED ?? 'true').toLowerCase() !== 'false'
      }
    };

    if (process.env.MAIL_IGNORE_TLS === 'true' || process.env.SMTP_IGNORE_TLS === 'true') {
      transportConfig.tls = { ...transportConfig.tls, rejectUnauthorized: false };
    }

    return nodemailer.createTransport(transportConfig);
  }

  return nodemailer.createTransport({ jsonTransport: true });
}

const emailTransporter = createEmailTransporter();

async function sendMessage({ to, subject, text, html }) {
  try {
    const info = await emailTransporter.sendMail({
      from: EMAIL_FROM,
      to,
      subject,
      text,
      html
    });

    console.log(`Email sent to ${to}: ${info.messageId || 'queued'}`);
    return { ok: true, messageId: info.messageId || null, response: info.response || null };
  } catch (error) {
    console.error(`Email send failed for ${to}:`, error.message || error);
    return { ok: false, error: error.message || String(error) };
  }
}

function sanitizeUser(row) {
  if (!row) return null;
  return {
    id: row.id,
    name: row.name,
    email: row.email,
    role: row.role,
    status: row.status,
    kycStatus: row.kyc_status || row.kycStatus || 'pending',
    walletBalanceCents: Number(row.wallet_balance_cents ?? row.walletBalanceCents ?? 0),
    phone: row.phone || null
  };
}

const orderCount = db.prepare('SELECT COUNT(*) AS count FROM orders').get();
if (!orderCount || Number(orderCount.count) === 0) {
  db.exec(`
    INSERT INTO orders (id, customer_id, customer_name, vendor_id, vendor_name, driver_id, driver_name, status, total_cents, created_at) VALUES
      ('ord-1001', 'cust-001', 'Ava Patel', 'vendor-001', 'Fresh Fold Laundry', 'driver-001', 'Noah Hill', 'picked_up', 4850, '2026-08-15T10:30:00.000Z'),
      ('ord-1002', 'cust-001', 'Ava Patel', 'vendor-002', 'Sparkle Suds', NULL, NULL, 'queued', 2500, '2026-08-16T08:10:00.000Z'),
      ('ord-1003', 'cust-001', 'Ava Patel', 'vendor-001', 'Fresh Fold Laundry', 'driver-001', 'Noah Hill', 'delivered', 6100, '2026-08-14T15:20:00.000Z');
  `);
}

const payoutCount = db.prepare('SELECT COUNT(*) AS count FROM payouts').get();
if (!payoutCount || Number(payoutCount.count) === 0) {
  db.exec(`
    INSERT INTO payouts (id, vendor_id, vendor_name, amount_cents, status, due_date, created_at) VALUES
      ('payout-100', 'vendor-001', 'Fresh Fold Laundry', 640000, 'pending', '2026-08-18T00:00:00.000Z', '2026-08-16T00:00:00.000Z'),
      ('payout-101', 'vendor-002', 'Sparkle Suds', 210000, 'processing', '2026-08-17T00:00:00.000Z', '2026-08-16T00:00:00.000Z');
  `);
}

const VALID_USER_STATUSES = ['active', 'inactive', 'review', 'on_route'];
const VALID_ORDER_STATUSES = ['queued', 'accepted', 'pickup_assigned', 'picked_up', 'at_vendor', 'processing', 'ready_for_delivery', 'delivery_assigned', 'out_for_delivery', 'delivered', 'cancelled'];

function verifyAdminToken(authHeader) {
  if (!authHeader || !authHeader.startsWith('Bearer ')) return null;
  try {
    return jwt.verify(authHeader.replace('Bearer ', ''), JWT_SECRET);
  } catch (error) {
    return null;
  }
}

function listWalletEvents(userId) {
  const query = userId
    ? 'SELECT * FROM wallet_events WHERE user_id = ? ORDER BY created_at DESC'
    : 'SELECT * FROM wallet_events ORDER BY created_at DESC';
  return userId ? db.prepare(query).all(userId) : db.prepare(query).all();
}

function listUsers() {
  return db.prepare(`
    SELECT id, name, email, role, status, kyc_status AS kycStatus, kyc_score AS kycScore, wallet_balance_cents AS walletBalanceCents, created_at AS createdAt
    FROM users
    ORDER BY created_at DESC
  `).all();
}

function listOrders() {
  return db.prepare(`
    SELECT id, customer_name AS customerName, vendor_name AS vendorName, driver_name AS driverName, status, total_cents AS totalCents, created_at AS createdAt
    FROM orders
    ORDER BY created_at DESC
  `).all();
}

function listPayouts() {
  return db.prepare(`
    SELECT id, vendor_name AS vendorName, amount_cents AS amountCents, status, due_date AS dueDate, created_at AS createdAt
    FROM payouts
    ORDER BY due_date ASC
  `).all();
}

function getKycSummary() {
  const counts = db.prepare(`
    SELECT kyc_status AS status, COUNT(*) AS total
    FROM users
    WHERE role IN ('customer', 'vendor', 'driver')
    GROUP BY kyc_status
  `).all();

  const result = { pending: 0, approved: 0, rejected: 0 };
  for (const row of counts) {
    if (result[row.status] !== undefined) {
      result[row.status] = Number(row.total);
    }
  }

  return result;
}

function buildDashboard() {
  const revenue = db.prepare('SELECT COALESCE(SUM(total_cents), 0) AS total FROM orders').get();
  const activeOrders = db.prepare("SELECT COUNT(*) AS total FROM orders WHERE status NOT IN ('delivered', 'cancelled')").get();
  const totalUsers = db.prepare('SELECT COUNT(*) AS total FROM users').get();
  const vendorCount = db.prepare("SELECT COUNT(*) AS total FROM users WHERE role = 'vendor'").get();
  const driverCount = db.prepare("SELECT COUNT(*) AS total FROM users WHERE role = 'driver'").get();
  const customerCount = db.prepare("SELECT COUNT(*) AS total FROM users WHERE role = 'customer'").get();

  const kycTrend = db.prepare(`
    SELECT role, kyc_status AS status, COUNT(*) AS total
    FROM users
    WHERE role IN ('customer', 'vendor', 'driver')
    GROUP BY role, kyc_status
    ORDER BY role, status
  `).all();

  const payoutTrend = db.prepare(`
    SELECT strftime('%Y-%m', due_date) AS month, SUM(amount_cents) AS total
    FROM payouts
    GROUP BY strftime('%Y-%m', due_date)
    ORDER BY month DESC
    LIMIT 6
  `).all();

  return {
    summary: {
      totalUsers: Number(totalUsers.total),
      totalVendors: Number(vendorCount.total),
      totalDrivers: Number(driverCount.total),
      totalCustomers: Number(customerCount.total),
      activeOrders: Number(activeOrders.total),
      revenueCents: Number(revenue.total),
      pendingKyc: Number(getKycSummary().pending),
      totalPayouts: db.prepare('SELECT COUNT(*) AS total FROM payouts').get().total
    },
    kycSummary: getKycSummary(),
    recentOrders: listOrders().slice(0, 5),
    payoutSummary: {
      pending: db.prepare("SELECT COUNT(*) AS total FROM payouts WHERE status = 'pending'").get().total,
      processing: db.prepare("SELECT COUNT(*) AS total FROM payouts WHERE status = 'processing'").get().total,
      paid: db.prepare("SELECT COUNT(*) AS total FROM payouts WHERE status = 'paid'").get().total
    },
    kycTrend,
    payoutTrend: payoutTrend.reverse(),
    users: listUsers(),
    orders: listOrders(),
    payouts: listPayouts()
  };
}

app.get('/api/admin/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'laundry-admin-backend',
    version: '1.0.0',
    environment: process.env.NODE_ENV || 'development',
    database: db.open ? 'connected' : 'disconnected',
    smtp: (process.env.SMTP_HOST || process.env.MAIL_HOST) &&
      (process.env.SMTP_USER || process.env.MAIL_USER) &&
      (process.env.SMTP_PASS || process.env.MAIL_PASS) ? 'configured' : 'fallback'
  });
});

app.post('/api/admin/login', (req, res) => {
  const { username, password } = req.body || {};
  const clientKey = `${req.ip}:${String(username || '').toLowerCase()}`;
  if (isRateLimited(clientKey)) {
    return res.status(429).json({ error: 'Too many login attempts. Please try again later.' });
  }
  const admin = db.prepare('SELECT * FROM admins WHERE username = ? AND password = ?').get(username, password);

  if (!admin) {
    return res.status(401).json({ error: 'Invalid admin credentials' });
  }

  const token = jwt.sign({ sub: admin.username, role: admin.role, name: admin.name }, JWT_SECRET, { expiresIn: '8h' });

  return res.json({
    token,
    admin: { username: admin.username, role: admin.role, name: admin.name }
  });
});

app.post('/api/auth/register', (req, res) => {
  const { name, email, password, role, phone } = req.body || {};
  const normalizedRole = normalizeUserRole(role);

  if (!name || !email || !password || !normalizedRole || !VALID_USER_ROLES.includes(normalizedRole)) {
    return res.status(400).json({ error: 'Name, email, password, and valid role are required' });
  }

  const existing = db.prepare('SELECT * FROM users WHERE lower(email) = lower(?) AND role = ?').get(String(email).trim(), normalizedRole);
  if (existing) {
    return res.status(409).json({ error: 'An account with this email already exists for this role' });
  }

  const userId = `user-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const now = new Date().toISOString();

  db.prepare(`
    INSERT INTO users (id, name, email, role, status, kyc_status, kyc_score, wallet_balance_cents, password, phone, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    userId,
    String(name).trim(),
    String(email).trim(),
    normalizedRole,
    'active',
    'pending',
    0,
    0,
    String(password),
    phone ? String(phone).trim() : null,
    now,
    now
  );

  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  const token = jwt.sign({ sub: user.id, role: user.role, email: user.email, name: user.name }, JWT_SECRET, { expiresIn: '8h' });

  sendMessage({
    to: user.email,
    subject: 'Welcome to Laundry Market',
    text: `Hi ${user.name},\n\nYour ${user.role} account has been created successfully.\n\nYou can sign in with your email and password to continue.`,
    html: `
      <h2>Welcome to Laundry Market</h2>
      <p>Hi ${user.name},</p>
      <p>Your ${user.role} account has been created successfully.</p>
      <p>You can sign in with your email and password to continue.</p>
    `
  });

  return res.status(201).json({
    token,
    user: sanitizeUser(user),
    message: 'Account created successfully'
  });
});

app.post('/api/auth/login', (req, res) => {
  const { email, password, role } = req.body || {};
  const normalizedRole = normalizeUserRole(role);

  if (!email || !password || !normalizedRole || !VALID_USER_ROLES.includes(normalizedRole)) {
    return res.status(400).json({ error: 'Email, password, and valid role are required' });
  }

  const user = db.prepare('SELECT * FROM users WHERE lower(email) = lower(?) AND role = ? AND password = ?').get(String(email).trim(), normalizedRole, String(password));
  if (!user) {
    return res.status(401).json({ error: 'Invalid email, password, or role' });
  }

  const token = jwt.sign({ sub: user.id, role: user.role, email: user.email, name: user.name }, JWT_SECRET, { expiresIn: '8h' });

  return res.json({
    token,
    user: sanitizeUser(user)
  });
});

app.post('/api/auth/forgot-password', (req, res) => {
  const { email, role } = req.body || {};
  const normalizedRole = normalizeUserRole(role);

  if (!email || !normalizedRole || !VALID_USER_ROLES.includes(normalizedRole)) {
    return res.status(400).json({ error: 'Email and valid role are required' });
  }

  const user = db.prepare('SELECT * FROM users WHERE lower(email) = lower(?) AND role = ?').get(String(email).trim(), normalizedRole);
  if (user) {
    const resetToken = jwt.sign({ sub: user.id, purpose: 'password_reset', email: user.email }, JWT_SECRET, { expiresIn: '1h' });
    const resetUrl = `${process.env.APP_BASE_URL || 'http://localhost:4001'}/reset-password?token=${resetToken}&email=${encodeURIComponent(user.email)}`;

    sendMessage({
      to: user.email,
      subject: 'Reset your Laundry Market password',
      text: `Hi ${user.name},\n\nUse this link to reset your password: ${resetUrl}\n\nThis link expires in 1 hour.`,
      html: `
        <h2>Reset your password</h2>
        <p>Hi ${user.name},</p>
        <p>Use the link below to reset your password:</p>
        <p><a href="${resetUrl}">${resetUrl}</a></p>
        <p>This link expires in 1 hour.</p>
      `
    });
  }

  return res.json({
    message: 'If that account exists, a password reset link has been sent.'
  });
});

app.get('/customer/shops', (req, res) => {
  return res.json(DEMO_SHOPS);
});

app.get('/customer/orders', (req, res) => {
  const customer = getDefaultCustomer();
  const rows = db.prepare('SELECT * FROM orders WHERE customer_id = ? ORDER BY created_at DESC').all(customer.id);
  return res.json(rows.map(serializeOrderRow));
});

app.post('/customer/orders', (req, res) => {
  const { vendorId, items = [] } = req.body || {};
  const customer = getDefaultCustomer();
  const vendor = DEMO_SHOPS.find((shop) => shop.shopId === vendorId) || DEMO_SHOPS[0];

  if (!vendorId) {
    return res.status(400).json({ error: 'vendorId is required' });
  }

  const totalCents = items.reduce((sum, item) => {
    const quantity = Number(item.quantity || 0);
    const price = Number(item.priceCents || 0);
    return sum + quantity * price;
  }, 0);

  const orderId = `ord-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const createdAt = new Date().toISOString();

  db.prepare(`
    INSERT INTO orders (id, customer_id, customer_name, vendor_id, vendor_name, driver_id, driver_name, status, total_cents, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(orderId, customer.id, customer.name, vendor.shopId, vendor.name, null, null, 'queued', totalCents, createdAt);

  const order = db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId);
  return res.status(201).json({
    ...serializeOrderRow(order),
    items: items.map((item) => ({
      name: item.name || 'Laundry item',
      quantity: Number(item.quantity || 0),
      priceCents: Number(item.priceCents || 0)
    }))
  });
});

app.post('/customer/orders/:orderId/review', (req, res) => {
  const { rating, comment } = req.body || {};
  return res.status(201).json({
    id: `review-${Date.now()}`,
    orderId: req.params.orderId,
    rating: Number(rating || 0),
    comment: String(comment || ''),
    authorName: 'Customer'
  });
});

app.post('/customer/orders/:orderId/dispute', (req, res) => {
  const { reason } = req.body || {};
  return res.status(201).json({
    id: `dispute-${Date.now()}`,
    orderId: req.params.orderId,
    reason: String(reason || ''),
    status: 'OPEN',
    createdAt: new Date().toISOString()
  });
});

app.post('/vendor/register', (req, res) => {
  const { name, address } = req.body || {};
  const shopId = `shop-${Date.now()}`;
  const shop = {
    shopId,
    name: String(name || 'New Laundry Shop').trim() || 'New Laundry Shop',
    address: String(address || 'Main Road').trim() || 'Main Road',
    rating: 4.9,
    services: [
      { id: 'svc-wash-fold', name: 'Wash & Fold', priceCents: 500, unit: 'kg' },
      { id: 'svc-dry-clean', name: 'Dry Cleaning', priceCents: 1200, unit: 'piece' },
      { id: 'svc-ironing', name: 'Ironing', priceCents: 300, unit: 'piece' }
    ],
    latitude: 6.5244,
    longitude: 3.3792
  };

  return res.status(201).json(shop);
});

app.get('/vendor/orders', (req, res) => {
  const vendor = getDefaultVendor();
  const rows = db.prepare('SELECT * FROM orders WHERE vendor_id = ? ORDER BY created_at DESC').all(vendor.id);
  return res.json(rows.map(serializeOrderRow));
});

app.patch('/vendor/orders/:orderId/status', (req, res) => {
  const { status } = req.body || {};
  const orderId = req.params.orderId;

  if (!VALID_ORDER_STATUSES.includes(status)) {
    return res.status(400).json({ error: 'Invalid order status' });
  }

  db.prepare('UPDATE orders SET status = ? WHERE id = ?').run(status, orderId);
  const row = db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId);
  if (!row) {
    return res.status(404).json({ error: 'Order not found' });
  }

  return res.json(serializeOrderRow(row));
});

app.get('/driver/assignments', (req, res) => {
  const driver = getDefaultDriver();
  const rows = db.prepare('SELECT * FROM orders WHERE driver_id = ? ORDER BY created_at DESC').all(driver.id);
  return res.json(rows.map(serializeOrderRow));
});

app.patch('/driver/orders/:orderId/status', (req, res) => {
  const { status } = req.body || {};
  const orderId = req.params.orderId;

  if (!VALID_ORDER_STATUSES.includes(status)) {
    return res.status(400).json({ error: 'Invalid order status' });
  }

  db.prepare('UPDATE orders SET status = ? WHERE id = ?').run(status, orderId);
  const row = db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId);
  if (!row) {
    return res.status(404).json({ error: 'Order not found' });
  }

  return res.json(serializeOrderRow(row));
});

app.use((req, res, next) => {
  if (!req.path.startsWith('/api/admin')) return next();
  const decoded = verifyAdminToken(req.headers.authorization);
  if (!decoded) return res.status(401).json({ error: 'Admin authentication required' });
  req.admin = decoded;
  return next();
});

app.get('/api/admin/dashboard', (req, res) => {
  res.json(buildDashboard());
});

app.get('/api/admin/users', (req, res) => {
  const { role, status } = req.query;
  let rows = listUsers();
  if (role) rows = rows.filter((user) => user.role === role);
  if (status) rows = rows.filter((user) => user.status === status);
  res.json(rows);
});

app.get('/api/admin/wallet-events', (req, res) => {
  const { userId } = req.query;
  res.json(listWalletEvents(userId || null));
});

app.patch('/api/admin/users/:userId/status', (req, res) => {
  const { userId } = req.params;
  const { status } = req.body || {};

  if (!VALID_USER_STATUSES.includes(status)) {
    return res.status(400).json({ error: 'Invalid user status' });
  }

  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  if (!user) return res.status(404).json({ error: 'User not found' });

  db.prepare(`
    UPDATE users
    SET status = ?, updated_at = ?
    WHERE id = ?
  `).run(status, new Date().toISOString(), userId);

  const updated = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  return res.json({ message: 'User status updated successfully', user: updated });
});

app.patch('/api/admin/users/:userId/wallet', (req, res) => {
  const { userId } = req.params;
  const { amountCents, type, note } = req.body || {};
  const amount = Number(amountCents || 0);
  const kind = type || 'topup';

  if (!['topup', 'refund'].includes(kind)) {
    return res.status(400).json({ error: 'Wallet adjustment type must be topup or refund' });
  }

  if (!Number.isFinite(amount) || amount <= 0) {
    return res.status(400).json({ error: 'amountCents must be a positive number' });
  }

  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  if (!user) return res.status(404).json({ error: 'User not found' });

  const delta = kind === 'topup' ? amount : -amount;
  const finalBalance = (Number(user.wallet_balance_cents) || 0) + delta;

  db.prepare(`
    UPDATE users
    SET wallet_balance_cents = ?, updated_at = ?
    WHERE id = ?
  `).run(finalBalance, new Date().toISOString(), userId);

  db.prepare(`
    INSERT INTO wallet_events (user_id, user_name, type, amount_cents, note, created_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(userId, user.name, kind, amount, note || `${kind} adjustment`, new Date().toISOString());

  const updated = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  return res.json({ message: 'Wallet updated successfully', user: updated, deltaCents: delta });
});

app.get('/api/admin/kyc', (req, res) => {
  const { status } = req.query;
  let rows = listUsers().filter((user) => ['customer', 'vendor', 'driver'].includes(user.role));
  if (status) rows = rows.filter((user) => user.kycStatus === status);
  res.json(rows);
});

app.patch('/api/admin/kyc/:userId', (req, res) => {
  const { userId } = req.params;
  const { status, notes } = req.body || {};
  const valid = ['pending', 'approved', 'rejected'];

  if (!valid.includes(status)) {
    return res.status(400).json({ error: 'Invalid KYC status' });
  }

  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  if (!user) return res.status(404).json({ error: 'User not found' });

  db.prepare(`
    UPDATE users
    SET kyc_status = ?, status = ?, updated_at = ?
    WHERE id = ?
  `).run(status, status === 'approved' ? 'active' : user.status, new Date().toISOString(), userId);

  if (notes) {
    db.prepare(`UPDATE users SET updated_at = ? WHERE id = ?`).run(new Date().toISOString(), userId);
  }

  const updated = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  return res.json({ message: 'KYC updated successfully', user: updated });
});

app.get('/api/admin/orders', (req, res) => {
  const { status } = req.query;
  let rows = listOrders();
  if (status) rows = rows.filter((order) => order.status === status);
  res.json(rows);
});

app.patch('/api/admin/orders/:orderId/status', (req, res) => {
  const { orderId } = req.params;
  const { status } = req.body || {};

  if (!VALID_ORDER_STATUSES.includes(status)) {
    return res.status(400).json({ error: 'Invalid order status' });
  }

  const order = db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId);
  if (!order) return res.status(404).json({ error: 'Order not found' });

  db.prepare(`UPDATE orders SET status = ? WHERE id = ?`).run(status, orderId);
  const updated = db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId);
  return res.json({ message: 'Order status updated successfully', order: updated });
});

app.get('/api/admin/payouts', (req, res) => {
  const { status } = req.query;
  let rows = listPayouts();
  if (status) rows = rows.filter((payout) => payout.status === status);
  res.json(rows);
});

app.get('/wallets', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'wallets.html'));
});

app.patch('/api/admin/payouts/:payoutId', (req, res) => {
  const { payoutId } = req.params;
  const { status } = req.body || {};

  const payout = db.prepare('SELECT * FROM payouts WHERE id = ?').get(payoutId);
  if (!payout) return res.status(404).json({ error: 'Payout not found' });

  db.prepare(`UPDATE payouts SET status = ? WHERE id = ?`).run(status || 'paid', payoutId);
  const updated = db.prepare('SELECT * FROM payouts WHERE id = ?').get(payoutId);
  return res.json({ message: 'Payout status updated', payout: updated });
});

app.use(express.static(path.join(__dirname, 'public')));

app.use((error, req, res, next) => {
  console.error('Unhandled backend error:', error);
  if (res.headersSent) return next(error);
  return res.status(500).json({ error: 'Internal server error' });
});

app.get('/', (req, res) => {
  res.redirect('/login');
});

app.get('/login', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'login.html'));
});

app.get('/dashboard', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'dashboard.html'));
});

app.get('/kyc', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'kyc.html'));
});

app.get('/orders', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'orders.html'));
});

app.get('/vendors', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'vendors.html'));
});

app.get('/drivers', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'drivers.html'));
});

app.get('/payouts', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'payouts.html'));
});

app.use((req, res) => {
  res.status(404).json({ error: 'Route not found' });
});

app.listen(PORT, () => {
  console.log(`Admin backend running on http://localhost:${PORT}`);
});
