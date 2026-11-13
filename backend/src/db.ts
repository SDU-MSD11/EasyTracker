import { Client, Pool } from "pg";
import { readFileSync } from "fs";
import { join } from "path";
import { config } from "./config";

const ssl = config.pgSsl ? { rejectUnauthorized: false } : undefined;

export const pool = new Pool({ ...config.pg, ssl, connectionTimeoutMillis: 10000 });
pool.on("error", (e) => console.error("[db] pool error:", e.message));
export const q = <T extends Record<string, any> = any>(text: string, params: unknown[] = []) =>
  pool.query<T>(text, params as any[]);

export async function ensureDatabase(): Promise<void> {
  const { database } = config.pg;
  if (!/^[A-Za-z0-9_]+$/.test(database)) throw new Error(`[db] invalid PGDATABASE: ${database}`);
  const admin = new Client({
    ...config.pg,
    database: "postgres",
    ssl,
    connectionTimeoutMillis: 10000,
  });
  try {
    await admin.connect();
  } catch (e) {
    console.error("[db] ensure database skipped (is Postgres up?):", (e as Error).message);
    return;
  }
  try {
    const { rowCount } = await admin.query("SELECT 1 FROM pg_database WHERE datname=$1", [database]);
    if (rowCount) return console.log(`[db] database ${database} exists`);
    try {
      await admin.query(`CREATE DATABASE "${database}"`);
      console.log(`[db] created database ${database}`);
    } catch (e: any) {
      if (e?.code === "42P04") return console.log(`[db] database ${database} exists`);
      throw e;
    }
  } finally {
    await admin.end().catch(() => {});
  }
}

export async function initSchema(): Promise<void> {
  const sql = readFileSync(join(__dirname, "schema.sql"), "utf8");
  await pool.query(sql);
}
