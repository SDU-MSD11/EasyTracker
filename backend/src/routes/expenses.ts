import { Router } from "express";
import { z } from "zod";
import { q } from "../db";
import { requireAuth, AuthedRequest } from "../middleware/auth";

export const expensesRouter = Router();
const patch = z.object({
  amountCents: z.number().int().min(0).optional(),
  description: z.string().optional(),
  splitAmong: z.array(z.string().uuid()).optional(),
  receiptUri: z.string().nullable().optional(),
}).strict();

const toJson = (r: any) => ({
  id: r.id, groupId: r.group_id, payerId: r.payer_id,
  amountCents: Number(r.amount_cents), description: r.description,
  splitAmong: r.split_among ?? [], receiptUri: r.receipt_uri ?? null,
  createdAt: r.created_at,
});

async function memberExpense(expenseId: string, userId: string) {
  const r = await q("SELECT e.* FROM expenses e JOIN group_members m ON m.group_id=e.group_id AND m.user_id=$2 AND m.status='ACCEPTED' WHERE e.id=$1", [expenseId, userId]);
  return r.rows[0] as any | undefined;
}

async function isGroupOwner(groupId: string, userId: string) {
  return (await q("SELECT 1 FROM groups WHERE id=$1 AND owner_id=$2", [groupId, userId])).rowCount! > 0;
}

expensesRouter.use(requireAuth);

expensesRouter.get("/expenses/:id", async (req: AuthedRequest, res) => {
  const e = await memberExpense(req.params.id, req.user!.id);
  if (!e) return res.status(404).json({ error: "not found" });
  res.json(toJson(e));
});

expensesRouter.patch("/expenses/:id", async (req: AuthedRequest, res) => {
  const p = patch.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: "invalid body" });
  const e = await memberExpense(req.params.id, req.user!.id);
  if (!e) return res.status(404).json({ error: "not found" });
  // Edit permission: only the payer or the group owner may patch (403 otherwise).
  if (e.payer_id !== req.user!.id && !await isGroupOwner(e.group_id, req.user!.id))
    return res.status(403).json({ error: "payer or owner only" });
  const b = p.data;
  const r = await q(
    `UPDATE expenses SET amount_cents=COALESCE($1,amount_cents), description=COALESCE($2,description),
     split_among=COALESCE($3,split_among), receipt_uri=COALESCE($4,receipt_uri) WHERE id=$5 RETURNING *`,
    [b.amountCents ?? null, b.description ?? null, b.splitAmong ?? null, b.receiptUri ?? null, e.id]
  );
  res.json(toJson(r.rows[0]));
});

expensesRouter.delete("/expenses/:id", async (req: AuthedRequest, res) => {
  const e = await memberExpense(req.params.id, req.user!.id);
  if (!e) return res.status(404).json({ error: "not found" });
  // Same gate as PATCH: only the payer or the group owner may delete (403 otherwise).
  if (e.payer_id !== req.user!.id && !await isGroupOwner(e.group_id, req.user!.id))
    return res.status(403).json({ error: "payer or owner only" });
  await q("DELETE FROM expenses WHERE id=$1", [e.id]);
  res.status(204).end();
});
