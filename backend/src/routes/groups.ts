import { Router } from "express";
import { z } from "zod";
import { q } from "../db";
import { requireAuth, AuthedRequest } from "../middleware/auth";

export const groupsRouter = Router();
groupsRouter.use(requireAuth);

const createGroup = z.object({ name: z.string().min(1), description: z.string().default(""), memberEmails: z.array(z.string().email()).default([]) });
const patchGroup = z.object({ name: z.string().min(1).optional(), description: z.string().optional() }).strict();
const addMember = z.object({ email: z.string().email() });
const respond = z.object({ accept: z.boolean() });
const createExpense = z.object({
  payerId: z.string().uuid().optional(),
  amountCents: z.number().int().min(0),
  description: z.string().default(""),
  splitAmong: z.array(z.string().uuid()).default([]),
  receiptUri: z.string().nullable().default(null),
});
const createPayment = z.object({ toId: z.string().uuid(), amountCents: z.number().int().positive() });

const statusOf = async (gid: string, uid: string) =>
  (await q("SELECT status FROM group_members WHERE group_id=$1 AND user_id=$2", [gid, uid])).rows[0]?.status as string | undefined;
const isOwner = async (gid: string, uid: string) =>
  (await q("SELECT 1 FROM groups WHERE id=$1 AND owner_id=$2", [gid, uid])).rowCount! > 0;

async function groupJson(gid: string) {
  const g = (await q("SELECT * FROM groups WHERE id=$1", [gid])).rows[0];
  if (!g) return null;
  const m = (await q("SELECT user_id,status FROM group_members WHERE group_id=$1", [gid])).rows;
  const members = (await q(
    "SELECT u.id,u.name,u.email FROM users u JOIN group_members m ON m.user_id=u.id WHERE m.group_id=$1 AND m.status='ACCEPTED'",
    [gid]
  )).rows;
  return {
    id: g.id, name: g.name, description: g.description, ownerId: g.owner_id,
    memberIds: m.filter((x) => x.status === "ACCEPTED").map((x) => x.user_id),
    invites: m.map((x) => ({ userId: x.user_id, status: x.status })),
    members: members.map((u) => ({ id: u.id, name: u.name, email: u.email })),
    createdAt: g.created_at,
  };
}
const needMember = async (gid: string, uid: string) => (await statusOf(gid, uid)) === "ACCEPTED";
const expJson = (r: any) => ({
  id: r.id, groupId: r.group_id, payerId: r.payer_id, amountCents: Number(r.amount_cents),
  description: r.description, splitAmong: r.split_among ?? [], receiptUri: r.receipt_uri ?? null, createdAt: r.created_at,
  kind: "expense",
});
// Payments fold into the expenses listing so every member sees repayments (history +
// paid progress) on refresh. Same expense shape + stable kind; the id equals the
// payment txId, so clients upserting by id reconcile their local mirror, no dupes.
const payJson = (r: any) => ({
  id: r.id, groupId: r.group_id, payerId: r.from_id, amountCents: Number(r.amount_cents),
  description: "Settlement", splitAmong: [r.to_id], receiptUri: null, createdAt: r.created_at,
  kind: "settlement",
});

groupsRouter.get("/groups", async (req: AuthedRequest, res) => {
  const r = await q("SELECT g.* FROM groups g JOIN group_members m ON m.group_id=g.id WHERE m.user_id=$1 AND m.status='ACCEPTED' ORDER BY g.created_at DESC", [req.user!.id]);
  const out = [];
  for (const g of r.rows) out.push(await groupJson(g.id));
  res.json(out);
});

groupsRouter.post("/groups", async (req: AuthedRequest, res) => {
  const p = createGroup.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: "invalid body" });
  const uid = req.user!.id;
  const g = (await q("INSERT INTO groups(name,description,owner_id) VALUES($1,$2,$3) RETURNING id", [p.data.name, p.data.description, uid])).rows[0];
  await q("INSERT INTO group_members(group_id,user_id,status) VALUES($1,$2,'ACCEPTED')", [g.id, uid]);
  for (const email of p.data.memberEmails) {
    const u = (await q("SELECT id FROM users WHERE email=$1", [email.toLowerCase()])).rows[0];
    if (u && u.id !== uid) await q("INSERT INTO group_members(group_id,user_id,status) VALUES($1,$2,'ACCEPTED') ON CONFLICT(group_id,user_id) DO UPDATE SET status='ACCEPTED'", [g.id, u.id]);
  }
  res.status(201).json(await groupJson(g.id));
});

groupsRouter.get("/groups/:id", async (req: AuthedRequest, res) => {
  if (!await needMember(req.params.id, req.user!.id)) return res.status(404).json({ error: "not found" });
  res.json(await groupJson(req.params.id));
});

groupsRouter.patch("/groups/:id", async (req: AuthedRequest, res) => {
  const p = patchGroup.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: "invalid body" });
  if (!await isOwner(req.params.id, req.user!.id)) return res.status(403).json({ error: "owner only" });
  const b = p.data;
  await q("UPDATE groups SET name=COALESCE($1,name), description=COALESCE($2,description) WHERE id=$3",
    [b.name ?? null, b.description ?? null, req.params.id]);
  res.json(await groupJson(req.params.id));
});

groupsRouter.delete("/groups/:id", async (req: AuthedRequest, res) => {
  if (!await isOwner(req.params.id, req.user!.id)) return res.status(403).json({ error: "owner only" });
  await q("DELETE FROM groups WHERE id=$1", [req.params.id]);
  res.status(204).end();
});

groupsRouter.post("/groups/:id/members", async (req: AuthedRequest, res) => {
  const p = addMember.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: "invalid body" });
  if (!await isOwner(req.params.id, req.user!.id)) return res.status(403).json({ error: "owner only" });
  const u = (await q("SELECT id FROM users WHERE email=$1", [p.data.email.toLowerCase()])).rows[0];
  if (!u) return res.status(404).json({ error: "user not found" });
  await q("INSERT INTO group_members(group_id,user_id,status) VALUES($1,$2,'ACCEPTED') ON CONFLICT(group_id,user_id) DO UPDATE SET status='ACCEPTED'", [req.params.id, u.id]);
  res.status(201).json(await groupJson(req.params.id));
});

groupsRouter.delete("/groups/:id/members/:userId", async (req: AuthedRequest, res) => {
  const { id, userId } = req.params;
  const me = req.user!.id;
  const owner = await isOwner(id, me);
  if (userId !== me && !owner) return res.status(403).json({ error: "owner only" });
  if (owner && userId === me) return res.status(400).json({ error: "owner cannot leave" });
  await q("DELETE FROM group_members WHERE group_id=$1 AND user_id=$2", [id, userId]);
  res.status(204).end();
});

groupsRouter.post("/groups/:id/invites/respond", async (req: AuthedRequest, res) => {
  const p = respond.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: "invalid body" });
  const r = await q("UPDATE group_members SET status=$1 WHERE group_id=$2 AND user_id=$3 AND status='PENDING' RETURNING status",
    [p.data.accept ? "ACCEPTED" : "DECLINED", req.params.id, req.user!.id]);
  if (!r.rowCount) return res.status(404).json({ error: "no pending invite" });
  res.json({ status: r.rows[0].status });
});

groupsRouter.get("/groups/:id/expenses", async (req: AuthedRequest, res) => {
  if (!await needMember(req.params.id, req.user!.id)) return res.status(404).json({ error: "not found" });
  const r = await q("SELECT * FROM expenses WHERE group_id=$1", [req.params.id]);
  const p = await q("SELECT * FROM payments WHERE group_id=$1", [req.params.id]);
  const rows = [...r.rows.map(expJson), ...p.rows.map(payJson)]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  res.json(rows);
});

groupsRouter.post("/groups/:id/expenses", async (req: AuthedRequest, res) => {
  const p = createExpense.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: "invalid body" });
  const gid = req.params.id;
  if (!await needMember(gid, req.user!.id)) return res.status(404).json({ error: "not found" });
  const payer = p.data.payerId ?? req.user!.id;
  if (!await needMember(gid, payer)) return res.status(400).json({ error: "payer not a member" });
  let split = p.data.splitAmong;
  if (!split.length) split = (await q("SELECT user_id FROM group_members WHERE group_id=$1 AND status='ACCEPTED'", [gid])).rows.map((x) => x.user_id);
  if (!split.length) return res.status(400).json({ error: "empty split" });
  const r = await q(
    "INSERT INTO expenses(group_id,payer_id,amount_cents,description,split_among,receipt_uri) VALUES($1,$2,$3,$4,$5,$6) RETURNING *",
    [gid, payer, p.data.amountCents, p.data.description, split, p.data.receiptUri]
  );
  res.status(201).json(expJson(r.rows[0]));
});

groupsRouter.get("/groups/:id/summary", async (req: AuthedRequest, res) => {
  const gid = req.params.id;
  if (!await needMember(gid, req.user!.id)) return res.status(404).json({ error: "not found" });
  const members = (await q("SELECT user_id FROM group_members WHERE group_id=$1 AND status='ACCEPTED'", [gid])).rows.map((x) => x.user_id as string);
  const expenses = (await q("SELECT payer_id,amount_cents,split_among FROM expenses WHERE group_id=$1", [gid])).rows;
  const payments = (await q("SELECT from_id,to_id,amount_cents FROM payments WHERE group_id=$1", [gid])).rows;
  const net: Record<string, number> = Object.fromEntries(members.map((m) => [m, 0]));
  let totalCents = 0;
  for (const e of expenses) {
    const amt = Number(e.amount_cents);
    totalCents += amt;
    const split: string[] = e.split_among ?? [];
    if (!split.length) continue;
    net[e.payer_id] ??= 0;
    for (const id of split) net[id] ??= 0;
    const base = Math.floor(amt / split.length);
    const rem = amt % split.length;
    net[e.payer_id] += amt;
    split.forEach((id, i) => { net[id] -= base + (i < rem ? 1 : 0); });
  }
  for (const pm of payments) {
    const amt = Number(pm.amount_cents);
    net[pm.from_id] ??= 0;
    net[pm.to_id] ??= 0;
    net[pm.from_id] += amt;
    net[pm.to_id] -= amt;
  }
  const ids = Object.keys(net);
  const debtors = ids.filter((m) => net[m] < 0).sort((a, b) => net[a] - net[b]);
  const creditors = ids.filter((m) => net[m] > 0).sort((a, b) => net[b] - net[a]);
  const settlements: { fromId: string; toId: string; amountCents: number }[] = [];
  let i = 0, j = 0;
  const d = debtors.map((m) => ({ id: m, amt: -net[m] }));
  const c = creditors.map((m) => ({ id: m, amt: net[m] }));
  while (i < d.length && j < c.length) {
    const amt = Math.min(d[i].amt, c[j].amt);
    settlements.push({ fromId: d[i].id, toId: c[j].id, amountCents: amt });
    d[i].amt -= amt; c[j].amt -= amt;
    if (d[i].amt === 0) i++;
    if (c[j].amt === 0) j++;
  }
  res.json({ balances: net, settlements, totalCents });
});

groupsRouter.post("/groups/:id/payments", async (req: AuthedRequest, res) => {
  const p = createPayment.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: "invalid body" });
  const gid = req.params.id;
  const from = req.user!.id;
  if (!await needMember(gid, from)) return res.status(404).json({ error: "not found" });
  if (!await needMember(gid, p.data.toId)) return res.status(400).json({ error: "recipient not a member" });
  const r = await q("INSERT INTO payments(group_id,from_id,to_id,amount_cents) VALUES($1,$2,$3,$4) RETURNING id",
    [gid, from, p.data.toId, p.data.amountCents]);
  res.status(201).json({ txId: r.rows[0].id });
});
