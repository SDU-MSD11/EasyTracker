import { Request, Response, NextFunction } from "express";
import { verifyToken } from "../auth";

export interface AuthedRequest extends Request { user?: { id: string; email: string } }

export function requireAuth(req: AuthedRequest, res: Response, next: NextFunction) {
  const h = req.headers.authorization ?? "";
  const token = h.startsWith("Bearer ") ? h.slice(7) : "";
  if (!token) return res.status(401).json({ error: "missing token" });
  try {
    req.user = verifyToken(token);
    next();
  } catch {
    return res.status(401).json({ error: "invalid token" });
  }
}
