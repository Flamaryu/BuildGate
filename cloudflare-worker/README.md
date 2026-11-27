# BuildGate Hub — Cloudflare Worker + R2 Media API

This Worker hosts **both**:
1. The **BuildGate Web Companion** (Kotlin/WasmJS static bundle + `.well-known/assetlinks.json`) on `https://buildgatehub.com`.
2. The **Cloudflare R2 Media Upload & Range-Streaming API** (`/api/media/upload` + `/media/*`) for 15-second MP4 bug clips and WebP screenshots with **zero egress fees**.

## Quick Deploy (`https://buildgatehub.com`)

1. **Build the production Web Companion bundle:**
   ```bash
   cd /Users/markeviswilliams/StudioProjects/BuildGate
   ./gradlew :webApp:wasmJsBrowserDistribution
   ```

2. **Set your Supabase Publishable Key secret on the Worker (one-time):**
   ```bash
   cd cloudflare-worker
   npx wrangler secret put SUPABASE_PUBLISHABLE_KEY
   ```

3. **Deploy to `https://buildgatehub.com`:**
   ```bash
   npx wrangler deploy
   ```

## Enable Cloudflare R2 for Video & Screenshot Storage

1. Open [Cloudflare Dashboard → R2 Object Storage](https://dash.cloudflare.com/81a4110483a176763821cf110aa16522/r2/overview) and enable R2 (free tier: 10 GB storage + **0 egress fees**).
2. Create the bucket:
   ```bash
   npx wrangler r2 bucket create buildgate-media
   ```
3. Uncomment the `r2_buckets` block in `wrangler.jsonc` and re-run `npx wrangler deploy`.
   - Until R2 is enabled, the Android and Web apps automatically fall back to Supabase Storage (`feedback-media`) so bug reporting never breaks.
   - As soon as R2 is bound, uploads automatically flow to `https://buildgatehub.com/api/media/upload` and stream from `https://buildgatehub.com/media/...` with HTTP Range scrubbing!
