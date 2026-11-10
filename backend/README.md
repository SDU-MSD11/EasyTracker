# EasyTracker Backend

Express + TypeScript + Postgres 16 API between the Android app and the database.
Mirrors the Android domain models (`User`, `Group`, `Expense`, `Settlement`/`DebtSummary`) in JSON.

## Quickstart

```bash
cd backend
npm install
cp .env.example .env   # then set JWT_SECRET + PG* values
docker compose up -d   # starts Postgres 16
npm run dev            # API on http://localhost:3000
```

## Android BASE_URL

- **Emulator:** `http://10.0.2.2:3000` (host loopback)
- **Physical device (same LAN):** `http://<host-LAN-IP>:3000`, e.g. `http://192.168.1.50:3000`
- Append `/api` paths below, send `Authorization: Bearer <token>`.

## Endpoints (prefix `/api`, JSON)

| Method | Path | Auth | Body → Result |
|---|---|---|---|
| GET | `/health` | – | `{ok:true}` |
| POST | `/api/auth/register` | – | `{name,email,password}` → `{token,user}` |
| POST | `/api/auth/login` | – | `{email,password}` → `{token,user}` |
| GET | `/api/me` | yes | current `user` |
| GET/POST | `/api/groups` | yes | POST `{name,description?,memberEmails?}` |
| GET/PATCH/DELETE | `/api/groups/:id` | yes (PATCH/DELETE: owner) | PATCH `{name?,description?}` |
| POST | `/api/groups/:id/members` | yes (member) | `{email}` → invite PENDING |
| DELETE | `/api/groups/:id/members/:userId` | owner or self | remove member |
| POST | `/api/groups/:id/invites/respond` | yes (invitee) | `{accept:boolean}` |
| GET/POST | `/api/groups/:id/expenses` | yes (member) | POST `{payerId?,amountCents,description?,splitAmong?,receiptUri?}` |
| GET/PATCH/DELETE | `/api/expenses/:id` | yes (member of group) | PATCH `{amountCents?,description?,splitAmong?,receiptUri?}` |
| GET | `/api/groups/:id/summary` | yes (member) | `{balances,settlements,totalCents}` (greedy netting) |
| POST | `/api/groups/:id/payments` | yes (member) | `{toId,amountCents}` → `{txId}` |

Errors are `{error: string}` with 4xx status. Amounts are **minor units** (`amountCents`).

## Notes

- JWT expiry 7 days. Passwords bcrypt-hashed. All SQL parameterized.
- `splitAmong` defaults to all accepted members; empty split is rejected.
- `summary` folds recorded `payments` into nets before greedy settlement.
- Hosted Postgres requiring TLS (Aiven/Neon/Supabase-style): set `PGSSL=true`; local docker needs no SSL.
