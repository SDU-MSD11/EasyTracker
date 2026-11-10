import { Router } from "express";
import { z } from "zod";
import { q } from "../db";
import { hashPassword, checkPassword, signToken, toUserJson } from "../auth";
import { requireAuth, AuthedRequest } from "../middleware/auth";

export const authRouter = Router();
const reg = z.object({ name: z.string().min(1), email: z.string().email(), password: z.string().min(8) });
const login = z.object({ email: z.string().email(), password: z.string().min(1) });

authRouter.post("/auth/register", async (req, res) => {
  const p = reg.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: "invalid body" });
  const { name, email, password } = p.data;
  const exists = await q("SELECT id FROM users WHERE email=$1", [email.toLowerCase()]);
  if (exists.rowCount) return res.status(409).json({ error: "email taken" });
  const r = await q(
    "INSERT INTO users(name,email,password_hash) VALUES($1,$2,$3) RETURNING id,name,email,contact",
    [name, email.toLowerCase(), await hashPassword(password)]
  );
  const user = toUserJson(r.rows[0]);
  res.status(201).json({ token: signToken({ id: user.id, email: user.email }), user });
});

authRouter.post("/auth/login", async (req, res) => {
  const p = login.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: "invalid body" });
  const r = await q("SELECT * FROM users WHERE email=$1", [p.data.email.toLowerCase()]);
  const u = r.rows[0];
  if (!u || !(await checkPassword(p.data.password, u.password_hash)))
    return res.status(401).json({ error: "invalid credentials" });
  const user = toUserJson(u);
  res.json({ token: signToken({ id: user.id, email: user.email }), user });
});

authRouter.get("/me", requireAuth, async (req: AuthedRequest, res) => {
  const r = await q("SELECT id,name,email,contact FROM users WHERE id=$1", [req.user!.id]);
  if (!r.rowCount) return res.status(404).json({ error: "not found" });
  res.json(toUserJson(r.rows[0]));
});
