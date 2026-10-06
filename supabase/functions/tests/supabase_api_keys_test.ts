import {
  supabasePublishableKey,
  supabaseSecretKey,
  supabaseServiceHeaders,
} from "../_shared/supabase_api_keys.ts";

const names = [
  "SUPABASE_PUBLISHABLE_KEY",
  "SUPABASE_PUBLISHABLE_KEYS",
  "SUPABASE_ANON_KEY",
  "SUPABASE_SECRET_KEY",
  "SUPABASE_SECRET_KEYS",
  "SUPABASE_SERVICE_ROLE_KEY",
];

function withCleanEnv(test: () => void) {
  const snapshot = new Map<string, string | undefined>(
    names.map((name) => [name, Deno.env.get(name)]),
  );
  try {
    for (const name of names) Deno.env.delete(name);
    test();
  } finally {
    for (const [name, value] of snapshot) {
      if (value == null) Deno.env.delete(name);
      else Deno.env.set(name, value);
    }
  }
}

function assertEquals(actual: unknown, expected: unknown, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

Deno.test("publishable key prefers modern singular environment", () => {
  withCleanEnv(() => {
    Deno.env.set("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_modern");
    Deno.env.set("SUPABASE_ANON_KEY", "legacy-anon");
    assertEquals(supabasePublishableKey(), "sb_publishable_modern", "wrong publishable key selected");
  });
});

Deno.test("named key dictionary resolves the default hosted key", () => {
  withCleanEnv(() => {
    Deno.env.set("SUPABASE_SECRET_KEYS", JSON.stringify({ default: "sb_secret_default", search: "sb_secret_search" }));
    assertEquals(supabaseSecretKey(), "sb_secret_default", "wrong named secret key selected");
  });
});

Deno.test("modern secret key stays in apikey and is never used as bearer JWT", () => {
  withCleanEnv(() => {
    Deno.env.set("SUPABASE_SECRET_KEY", "sb_secret_modern");
    const headers = supabaseServiceHeaders();
    assertEquals(headers.apikey, "sb_secret_modern", "modern secret missing from apikey");
    assertEquals(headers.Authorization, undefined, "modern secret was incorrectly emitted as bearer token");
  });
});

Deno.test("legacy service-role JWT remains compatible with local Supabase", () => {
  withCleanEnv(() => {
    const legacy = "eyJlegacy-service-role-jwt";
    Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", legacy);
    const headers = supabaseServiceHeaders();
    assertEquals(headers.apikey, legacy, "legacy key missing from apikey");
    assertEquals(headers.Authorization, `Bearer ${legacy}`, "legacy JWT bearer compatibility missing");
  });
});
