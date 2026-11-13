import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { config } from "./config";

export interface JwtPayload { id: string; email: string }
export const hashPassword = (pw: string) => bcrypt.hash(pw, 10);
export const checkPassword = (pw: string, hash: string) => bcrypt.compare(pw, hash);
export const signToken = (p: JwtPayload) => jwt.sign(p, config.jwtSecret, { expiresIn: "7d" });
export const verifyToken = (t: string) => jwt.verify(t, config.jwtSecret) as JwtPayload;
export const toUserJson = (r: any) => ({ id: r.id, name: r.name, email: r.email, contact: r.contact ?? "" });
