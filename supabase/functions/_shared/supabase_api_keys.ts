function namedKey(
  singularName: string,
  pluralJsonName: string,
  legacyName: string,
): string {
  const singular = Deno.env.get(singularName)?.trim();
  if (singular) return singular;

  const named = Deno.env.get(pluralJsonName)?.trim();
  if (named) {
    const parsed = JSON.parse(named) as Record<string, unknown>;
    const defaultKey = parsed.default;
    if (typeof defaultKey === "string" && defaultKey.trim()) return defaultKey.trim();
    throw new Error(`${pluralJsonName} does not contain a non-empty default key`);
  }

  const legacy = Deno.env.get(legacyName)?.trim();
  if (legacy) return legacy;
  throw new Error(`No Supabase API key found in ${singularName}, ${pluralJsonName}, or ${legacyName}`);
}

export function supabasePublishableKey(): string {
  return namedKey(
    "SUPABASE_PUBLISHABLE_KEY",
    "SUPABASE_PUBLISHABLE_KEYS",
    "SUPABASE_ANON_KEY",
  );
}

export function supabaseSecretKey(): string {
  return namedKey(
    "SUPABASE_SECRET_KEY",
    "SUPABASE_SECRET_KEYS",
    "SUPABASE_SERVICE_ROLE_KEY",
  );
}

function isLegacyJwtKey(key: string): boolean {
  return key.startsWith("eyJ");
}

export function supabaseServiceHeaders(extra: Record<string, string> = {}): Record<string, string> {
  const key = supabaseSecretKey();
  return {
    apikey: key,
    ...(isLegacyJwtKey(key) ? { Authorization: `Bearer ${key}` } : {}),
    "Content-Type": "application/json",
    ...extra,
  };
}
