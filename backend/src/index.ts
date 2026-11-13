import express from "express";
import cors from "cors";
import morgan from "morgan";
import { config } from "./config";
import { ensureDatabase, initSchema } from "./db";
import { authRouter } from "./routes/auth";
import { groupsRouter } from "./routes/groups";
import { expensesRouter } from "./routes/expenses";

const app = express();
app.use(cors());
app.use(morgan("dev"));
app.use(express.json({ limit: "2mb" }));

app.get("/health", (_req, res) => res.json({ ok: true }));
app.get("/api/health", (_req, res) => res.json({ ok: true }));
app.use("/api", authRouter, groupsRouter, expensesRouter);
app.use((_req, res) => res.status(404).json({ error: "not found" }));

async function main() {
  try {
    await ensureDatabase();
    await initSchema();
    console.log("[db] schema ready");
  } catch (e) {
    console.error("[db] schema init failed (is Postgres up?):", (e as Error).message);
    console.log(
      "hint: for local dev use docker compose up -d with PGHOST=localhost PGPORT=5432; for hosted Postgres set PGSSL=true",
    );
  }
}
void main();

app.listen(config.port, "::", () =>
  console.log(`[api] listening on [::]:${config.port} (health http://localhost:${config.port}/health)`),
);
