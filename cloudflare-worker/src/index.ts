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

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(request) });
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
