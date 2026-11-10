import "dotenv/config";

export const config = {
  port: Number(process.env.PORT ?? 3000),
  pg: {
    host: process.env.PGHOST ?? "localhost",
    port: Number(process.env.PGPORT ?? 5432),
    user: process.env.PGUSER ?? "easytracker",
    password: process.env.PGPASSWORD ?? "changeme",
    database: process.env.PGDATABASE ?? "easytracker",
  },
  jwtSecret: process.env.JWT_SECRET ?? "dev-secret-change-me",
  pgSsl: ["true", "1", "require"].includes((process.env.PGSSL ?? "").toLowerCase()),
};

if (!process.env.JWT_SECRET) console.warn("[warn] JWT_SECRET not set, using dev fallback");
