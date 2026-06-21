import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const port = Number(process.argv[2] || 8766);
const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const apkPath = path.join(root, "app", "build", "outputs", "apk", "debug", "app-debug.apk");
const logPath = path.join(root, "download-server.log");

function log(message) {
  fs.appendFileSync(logPath, `${new Date().toISOString()} ${message}\n`);
}

if (!fs.existsSync(apkPath)) {
  log(`APK missing: ${apkPath}`);
  process.exit(1);
}

const server = http.createServer((req, res) => {
  log(`${req.socket.remoteAddress} ${req.method} ${req.url}`);

  if (req.method !== "GET") {
    res.writeHead(405, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Only GET is supported.");
    return;
  }

  if (req.url !== "/" && req.url !== "/app-debug.apk") {
    res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Open /app-debug.apk");
    return;
  }

  const stat = fs.statSync(apkPath);
  res.writeHead(200, {
    "Content-Type": "application/vnd.android.package-archive",
    "Content-Disposition": 'attachment; filename="energy-optimizer-phase1.apk"',
    "Content-Length": stat.size,
    "Cache-Control": "no-store",
    "Connection": "close",
  });
  fs.createReadStream(apkPath).pipe(res);
});

server.on("error", (error) => {
  log(`ERROR ${error.stack || error.message}`);
  process.exit(1);
});

server.listen(port, "0.0.0.0", () => {
  log(`Listening on 0.0.0.0:${port}, serving ${apkPath}`);
});
