require('dotenv').config();
const nodemailer = require('nodemailer');
const transporter = nodemailer.createTransport({
  host: process.env.SMTP_HOST,
  port: Number(process.env.SMTP_PORT || 587),
  secure: String(process.env.SMTP_SECURE || 'false').toLowerCase() === 'true',
  auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS },
  tls: { rejectUnauthorized: true }
});

transporter.verify((verifyErr) => {
  if (verifyErr) {
    console.error('SMTP_VERIFY_FAILED');
    console.error(verifyErr.message || String(verifyErr));
    process.exit(1);
  }

  console.log('SMTP_VERIFY_OK');

  transporter.sendMail({
    from: process.env.EMAIL_FROM,
    to: process.env.SMTP_USER,
    subject: 'Laundry SMTP Test',
    text: 'This is a live SMTP test from Laundry Market.'
  }, (sendErr, info) => {
    if (sendErr) {
      console.error('SMTP_SEND_FAILED');
      console.error(sendErr.message || String(sendErr));
      process.exit(1);
    }

    console.log('SMTP_SEND_OK');
    console.log(info && (info.messageId || info.response) ? (info.messageId || info.response) : 'accepted');
  });
});
