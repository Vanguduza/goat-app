const encoder = new TextEncoder();

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function base64UrlJson(value: unknown): string {
  return base64Url(encoder.encode(JSON.stringify(value)));
}

async function signTenantToken(secret: string, payload: unknown): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const signingInput = `${base64UrlJson(header)}.${base64UrlJson(payload)}`;
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(signingInput));
  return `${signingInput}.${base64Url(new Uint8Array(signature))}`;
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const publishableKey = Deno.env.get("SUPABASE_ANON_KEY")!;
  const meiliSearchKey = Deno.env.get("MEILI_SEARCH_KEY")!;
  const meiliSearchKeyUid = Deno.env.get("MEILI_SEARCH_KEY_UID")!;
  const indexPrefix = Deno.env.get("MEILI_INDEX_PREFIX") ?? "farm-os";
  const authorization = request.headers.get("Authorization") ?? "";

  if (!authorization.startsWith("Bearer ")) {
    return Response.json({ error: "Authentication required" }, { status: 401 });
  }

  const { farmId } = await request.json();
  if (typeof farmId !== "string") {
    return Response.json({ error: "farmId is required" }, { status: 400 });
  }

  const authResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { Authorization: authorization, apikey: publishableKey },
  });
  if (!authResponse.ok) return Response.json({ error: "Invalid session" }, { status: 401 });
  const user = await authResponse.json();

  const membershipUrl = new URL(`${supabaseUrl}/rest/v1/farm_users`);
  membershipUrl.searchParams.set("farm_id", `eq.${farmId}`);
  membershipUrl.searchParams.set("user_id", `eq.${user.id}`);
  membershipUrl.searchParams.set("select", "role");
  const membershipResponse = await fetch(membershipUrl, {
    headers: { Authorization: authorization, apikey: publishableKey },
  });
  const memberships = membershipResponse.ok ? await membershipResponse.json() : [];
  if (!Array.isArray(memberships) || memberships.length !== 1) {
    return Response.json({ error: "Farm access denied" }, { status: 403 });
  }

  const expiresAt = Math.floor(Date.now() / 1000) + 15 * 60;
  const mandatoryFilter = `farm_id = "${farmId.replaceAll('"', '\\"')}"`;
  const payload = {
    apiKeyUid: meiliSearchKeyUid,
    exp: expiresAt,
    searchRules: {
      [`${indexPrefix}-animals-v1`]: { filter: mandatoryFilter },
      [`${indexPrefix}-inventory-v1`]: { filter: mandatoryFilter },
      [`${indexPrefix}-reference-v1`]: { filter: mandatoryFilter },
    },
  };

  const token = await signTenantToken(meiliSearchKey, payload);
  return Response.json({ token, expiresAt, indexPrefix });
});
