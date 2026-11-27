export interface Env {
  ASSETS: Fetcher;
  MEDIA_BUCKET?: R2Bucket;
  SUPABASE_URL: string;
  SUPABASE_PUBLISHABLE_KEY: string;
  PUBLIC_ORIGIN: string;
}

const MAX_UPLOAD_BYTES = 15 * 1024 * 1024; // 15 MB (covers WebP/PNG screenshots & 15s 720p MP4 clips)
const ALLOWED_MIME_TYPES = new Set([
  "image/webp",
  "image/png",
  "image/jpeg",
  "video/mp4",
  "video/webm",
]);

function corsHeaders(request: Request): Headers {
  const origin = request.headers.get("Origin") || "*";
  const headers = new Headers();
  headers.set("Access-Control-Allow-Origin", origin);
  headers.set("Access-Control-Allow-Methods", "GET, HEAD, POST, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type, Range");
  headers.set("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges, ETag");
  headers.set("Vary", "Origin");
  return headers;
}

function jsonResponse(request: Request, body: unknown, status = 200): Response {
  const headers = corsHeaders(request);
  headers.set("Content-Type", "application/json; charset=utf-8");
  return new Response(JSON.stringify(body), { status, headers });
}

function sanitizeSegment(raw: string, fallback: string): string {
  const cleaned = raw.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 80);
  return cleaned.length > 0 ? cleaned : fallback;
}

async function verifySupabaseUser(request: Request, env: Env): Promise<{ id: string } | null> {
  const authHeader = request.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) return null;
  if (!env.SUPABASE_URL || !env.SUPABASE_PUBLISHABLE_KEY) return null;

  const res = await fetch(`${env.SUPABASE_URL}/auth/v1/user`, {
    method: "GET",
    headers: {
      Authorization: authHeader,
      apikey: env.SUPABASE_PUBLISHABLE_KEY,
    },
  });
  if (!res.ok) return null;
  const user = (await res.json()) as { id?: string };
  return user?.id ? { id: user.id } : null;
}

async function handleMediaUpload(request: Request, env: Env, url: URL): Promise<Response> {
  if (!env.MEDIA_BUCKET) {
    return jsonResponse(
      request,
      { error: "R2 bucket MEDIA_BUCKET is not bound yet. Enable R2 and bind buildgate-media." },
      503
    );
  }

  const user = await verifySupabaseUser(request, env);
  if (!user) {
    return jsonResponse(request, { error: "Unauthorized: valid Supabase session required." }, 401);
  }

  const contentType = (request.headers.get("Content-Type") || "").split(";")[0].trim().toLowerCase();
  if (!ALLOWED_MIME_TYPES.has(contentType)) {
    return jsonResponse(
      request,
      { error: `Unsupported Content-Type '${contentType}'. Allowed: webp, png, jpeg, mp4, webm.` },
      415
    );
  }

  const contentLengthHeader = request.headers.get("Content-Length");
  if (contentLengthHeader && Number(contentLengthHeader) > MAX_UPLOAD_BYTES) {
    return jsonResponse(request, { error: "File exceeds 15 MB upload limit." }, 413);
  }

  const bodyBytes = await request.arrayBuffer();
  if (bodyBytes.byteLength === 0) {
    return jsonResponse(request, { error: "Empty upload body." }, 400);
  }
  if (bodyBytes.byteLength > MAX_UPLOAD_BYTES) {
    return jsonResponse(request, { error: "File exceeds 15 MB upload limit." }, 413);
  }

  const appId = sanitizeSegment(url.searchParams.get("appId") || "general", "general");
  const defaultExt = contentType.includes("video") ? "clip.mp4" : "screenshot.webp";
  const fileName = sanitizeSegment(url.searchParams.get("fileName") || defaultExt, defaultExt);
  const key = `feedback/${user.id}/${appId}/${Date.now()}_${fileName}`;

  await env.MEDIA_BUCKET.put(key, bodyBytes, {
    httpMetadata: {
      contentType,
      cacheControl: "public, max-age=31536000, immutable",
    },
    customMetadata: {
      uploaderId: user.id,
      appId,
    },
  });

  const publicOrigin = (env.PUBLIC_ORIGIN || url.origin).replace(/\/$/, "");
  return jsonResponse(
    request,
    {
      key,
      url: `${publicOrigin}/media/${key}`,
      bytes: bodyBytes.byteLength,
      contentType,
    },
    201
  );
}

async function handleMediaServe(request: Request, env: Env, url: URL): Promise<Response> {
  if (!env.MEDIA_BUCKET) {
    return jsonResponse(request, { error: "R2 bucket MEDIA_BUCKET not configured." }, 503);
  }

  const key = decodeURIComponent(url.pathname.replace(/^\/media\//, ""));
  if (!key || key.includes("..")) {
    return new Response("Not Found", { status: 404 });
  }

  const rangeHeader = request.headers.get("Range");
  let range: R2Range | undefined;
  if (rangeHeader) {
    const match = /^bytes=(\d+)-(\d*)$/.exec(rangeHeader.trim());
    if (match) {
      const start = Number(match[1]);
      const end = match[2] ? Number(match[2]) : undefined;
      range = end !== undefined ? { offset: start, length: end - start + 1 } : { offset: start };
    }
  }

  const object =
    request.method === "HEAD"
      ? await env.MEDIA_BUCKET.head(key)
      : await env.MEDIA_BUCKET.get(key, range ? { range } : undefined);

  if (!object) {
    return new Response("Not Found", { status: 404, headers: corsHeaders(request) });
  }

  const headers = corsHeaders(request);
  object.writeHttpMetadata(headers);
  headers.set("ETag", object.httpEtag);
  headers.set("Accept-Ranges", "bytes");
  if (!headers.has("Cache-Control")) {
    headers.set("Cache-Control", "public, max-age=31536000, immutable");
  }

  if (request.method === "HEAD") {
    headers.set("Content-Length", String(object.size));
    return new Response(null, { status: 200, headers });
  }

  const bodyObj = object as R2ObjectBody;
  if (range && "offset" in range && range.offset !== undefined) {
    const start = range.offset;
    const length = range.length ?? object.size - start;
    const end = Math.min(start + length - 1, object.size - 1);
    headers.set("Content-Range", `bytes ${start}-${end}/${object.size}`);
    headers.set("Content-Length", String(end - start + 1));
    return new Response(bodyObj.body, { status: 206, headers });
  }

  headers.set("Content-Length", String(object.size));
  return new Response(bodyObj.body, { status: 200, headers });
}

function handlePrivacyPolicyPage(): Response {
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Privacy Policy — BuildGate Hub</title>
  <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
  <style>
    :root { --bg:#0f172a; --card:#1e293b; --text:#f8fafc; --muted:#94a3b8; --accent:#818cf8; --mint:#34d399; --danger:#f87171; --border:rgba(148,163,184,0.2); }
    * { box-sizing:border-box; }
    body { margin:0; font-family:-apple-system,BlinkMacSystemFont,"Inter","Segoe UI",Roboto,sans-serif; background:var(--bg); color:var(--text); line-height:1.65; }
    .wrap { max-width:820px; margin:0 auto; padding:36px 20px 64px; }
    header { display:flex; align-items:center; justify-content:space-between; gap:16px; border-bottom:1px solid var(--border); padding-bottom:20px; margin-bottom:28px; flex-wrap:wrap; }
    .brand { display:flex; align-items:center; gap:12px; text-decoration:none; color:var(--text); font-weight:800; font-size:20px; }
    .brand img { width:36px; height:36px; }
    .back { color:var(--accent); text-decoration:none; font-weight:600; font-size:14px; }
    h1 { font-size:30px; margin:0 0 8px; }
    .updated { color:var(--muted); font-size:13px; margin-bottom:28px; }
    section { background:var(--card); border:1px solid var(--border); border-radius:16px; padding:22px; margin-bottom:18px; }
    h2 { font-size:18px; margin:0 0 10px; color:#e2e8f0; }
    p, li { color:#cbd5e1; font-size:15px; }
    ul { padding-left:20px; margin:8px 0 0; }
    li { margin-bottom:6px; }
    .callout { border-left:4px solid var(--mint); padding:12px 14px; background:rgba(16,185,129,0.08); border-radius:8px; margin-top:10px; }
    .danger { border-left:4px solid var(--danger); padding:12px 14px; background:rgba(248,113,113,0.08); border-radius:8px; margin-top:10px; }
    code { background:rgba(15,23,42,0.7); padding:2px 6px; border-radius:6px; font-size:13px; color:#c7d2fe; }
    footer { text-align:center; color:var(--muted); font-size:13px; margin-top:32px; padding-top:20px; border-top:1px solid var(--border); }
    a { color:var(--accent); }
  </style>
</head>
<body>
  <div class="wrap">
    <header>
      <a class="brand" href="/">
        <img src="/favicon.svg" alt="BuildGate Logo" />
        <span>BuildGate Hub</span>
      </a>
      <a class="back" href="/">← Back to BuildGate App</a>
    </header>

    <h1>Privacy Policy</h1>
    <p class="updated">Effective Date: November 27, 2026 &nbsp;•&nbsp; App Package: <code>com.buildgatehub.app</code> &nbsp;•&nbsp; Website: <a href="https://buildgatehub.com">buildgatehub.com</a></p>

    <section>
      <h2>1. Who We Are</h2>
      <p>BuildGate Hub ("BuildGate", "we", "us") is a closed-testing verification and QA collaboration platform for independent Android developers and testers, operated by MKD Interactive.</p>
    </section>

    <section>
      <h2>2. Information We Collect</h2>
      <ul>
        <li><strong>Account Information:</strong> Email address, chosen developer/tester handle, and authentication identifiers managed via Supabase Auth.</li>
        <li><strong>Testing &amp; Campaign Activity:</strong> Enrolled apps, daily 60-second verification timestamps, 14-day continuity streaks, QA mission checkoffs, and Karma ledger history.</li>
        <li><strong>Bug Reports &amp; Optional Media:</strong> Bug titles, reproduction notes, hardware metadata (device model, Android OS version, screen resolution), and optional screenshots or 15-second screen clips that you explicitly capture and submit. Media files are stored on Cloudflare R2 and served over HTTPS.</li>
      </ul>
    </section>

    <section>
      <h2>3. Android Sensitive Permissions &amp; How They Are Used</h2>
      <ul>
        <li><strong>Usage Access (<code>PACKAGE_USAGE_STATS</code> — Optional):</strong> Only used if you explicitly opt in after reviewing our in-app disclosure. During an active 60-second QA testing session, BuildGate checks solely whether the enrolled target app remains in the foreground so the timer pauses if you switch away or lock your screen. We <strong>never</strong> read messages, browsing history, or upload lists of unrelated apps to our servers.</li>
        <li><strong>Screen Capture (<code>MediaProjection</code> &amp; Foreground Service):</strong> Activated strictly on demand after Android's system consent prompt when you tap <em>Screenshot</em> or <em>15s Clip</em> inside the floating QA overlay to attach bug evidence.</li>
        <li><strong>Display Over Other Apps (<code>SYSTEM_ALERT_WINDOW</code>):</strong> Powers the optional floating QA timer pill and inline bug-reporting sheet while you test a closed-beta app. You can close the pill at any time via the in-pill Close button or persistent notification.</li>
        <li><strong>Installed Package Checks:</strong> Uses Android's PackageManager solely to verify whether an app you explicitly enrolled in is installed and matches the developer's minimum build version code.</li>
        <li><strong>Notifications (<code>POST_NOTIFICATIONS</code>):</strong> Used for the active QA session controls and optional local daily queue streak reminders (configurable in the Account tab).</li>
      </ul>
      <div class="callout">
        <strong>Zero Ad Trackers &amp; No Data Selling:</strong> BuildGate does not sell your personal data, does not run third-party advertising SDKs, and does not track you across unrelated apps.
      </div>
    </section>

    <section id="account-deletion">
      <h2>4. Account &amp; Data Deletion (Double-Verified Self-Service)</h2>
      <p>You can permanently delete your BuildGate account and all associated data at any time from either the <strong>BuildGate Android app</strong> or the <strong>BuildGate website</strong> (<a href="https://buildgatehub.com">https://buildgatehub.com</a>):</p>
      <ol style="color:#cbd5e1;font-size:15px;padding-left:20px;">
        <li>Sign in and open the <strong>Account</strong> tab.</li>
        <li>Scroll to the <strong>Danger Zone</strong> section and tap <strong>Delete Account &amp; All Data</strong>.</li>
        <li><strong>Step 1 Verification:</strong> Review the summary of data to be permanently erased and tap <em>Continue to Final Confirmation</em>.</li>
        <li><strong>Step 2 Verification:</strong> Type <code>DELETE</code> to confirm and tap <em>Permanently Delete Account</em>.</li>
      </ol>
      <div class="danger">
        <strong>What Is Permanently Deleted Immediately:</strong> Your authentication credentials, developer/tester profile, Karma balance &amp; ledger history, published app campaigns, QA missions, active/completed enrollments, daily verification logs, and submitted bug reports are immediately and irreversibly deleted via cascading database deletion. Any uploaded bug media files can also be purged upon request by emailing <a href="mailto:support@buildgatehub.com">support@buildgatehub.com</a>.
      </div>
    </section>

    <section>
      <h2>5. Security &amp; Infrastructure</h2>
      <p>All traffic between the BuildGate Android app, website, Supabase (PostgreSQL with Row Level Security), and Cloudflare Workers/R2 is encrypted in transit using HTTPS/TLS.</p>
    </section>

    <section>
      <h2>6. Contact Us</h2>
      <p>Questions, privacy requests, or manual data-deletion requests? Email us at <a href="mailto:support@buildgatehub.com">support@buildgatehub.com</a>.</p>
    </section>

    <footer>
      © 2026 BuildGate Hub (MKD Interactive) &nbsp;•&nbsp; <a href="https://buildgatehub.com">buildgatehub.com</a> &nbsp;•&nbsp; <a href="https://buildgatehub.com/privacy">Privacy Policy</a>
    </footer>
  </div>
</body>
</html>`;

  return new Response(html, {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "public, max-age=300",
    },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(request) });
    }

    if ((url.pathname === "/privacy" || url.pathname === "/privacy-policy") && request.method === "GET") {
      return handlePrivacyPolicyPage();
    }

    if (url.pathname === "/api/media/upload" && request.method === "POST") {
      return handleMediaUpload(request, env, url);
    }

    if (url.pathname.startsWith("/media/") && (request.method === "GET" || request.method === "HEAD")) {
      return handleMediaServe(request, env, url);
    }

    if (url.pathname === "/api/health") {
      return jsonResponse(request, {
        status: "ok",
        host: url.host,
        r2Bound: Boolean(env.MEDIA_BUCKET),
      });
    }

    // Serve Kotlin/WasmJS Web Companion & .well-known/assetlinks.json from Workers Static Assets
    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;
