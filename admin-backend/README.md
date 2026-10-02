# Laundry Admin Backend

A simple Express admin API for the Laundry marketplace.

## Start

```bash
npm install
npm start
```

## Admin login

Default admin account:

- Username: admin
- Password: admin123

## Key endpoints

- `POST /api/admin/login`
- `GET /api/admin/dashboard`
- `GET /api/admin/users`
- `GET /api/admin/kyc`
- `PATCH /api/admin/kyc/:userId`
- `GET /api/admin/orders`
- `GET /api/admin/payouts`
- `PATCH /api/admin/payouts/:payoutId`

Every protected route requires a bearer token returned from the login endpoint.
