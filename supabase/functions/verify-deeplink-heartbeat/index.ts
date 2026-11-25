import "@supabase/functions-js/edge-runtime.d.ts";
import { withSupabase } from "@supabase/server";

interface HeartbeatPayload {
  nonce?: string;
  package_name?: string;
  build_version?: string;
}

/**
 * Tier-2 Cryptographic Deep-Link Verification Endpoint
 * Called by a listed Android beta app's 10-line deep-link handler when launched via:
 *   <scheme>://buildgate-verify?nonce=bg_...&callback=buildgate://verify-callback
 */
export default {
  fetch: withSupabase({ auth: "none" }, async (req, ctx) => {
    if (req.method !== "POST") {
      return Response.json({ error: "Method not allowed" }, { status: 405 });
    }

    let body: HeartbeatPayload;
    try {
      body = await req.json();
    } catch {
      return Response.json({ error: "Invalid JSON payload" }, { status: 400 });
    }

    const nonce = body.nonce?.trim();
    const packageName = body.package_name?.trim();

    if (!nonce || !packageName) {
      return Response.json(
        { error: "Both nonce and package_name are required" },
        { status: 400 },
      );
    }

    const { data, error } = await ctx.supabaseAdmin.rpc(
      "confirm_deeplink_nonce_service",
      {
        p_session_nonce: nonce,
        p_package_name: packageName,
      },
    );

    if (error) {
      return Response.json(
        { verified: false, error: error.message },
        { status: 400 },
      );
    }

    return Response.json({
      verified: true,
      result: data,
      callback_deeplink: `buildgate://verify-callback?nonce=${encodeURIComponent(nonce)}&proof=server_verified`,
    });
  }),
};
