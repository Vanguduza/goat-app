import { applyAnimalsIndexContract } from "./index_contract.ts";

const host = Deno.env.get("MEILI_HOST") ?? "http://127.0.0.1:7700";
const masterKey = Deno.env.get("MEILI_MASTER_KEY")
  ?? Deno.env.get("MEILI_ADMIN_KEY")
  ?? "";
if (!masterKey) throw new Error("MEILI_MASTER_KEY or MEILI_ADMIN_KEY is required");

const result = await applyAnimalsIndexContract({
  host,
  masterKey,
  indexPrefix: Deno.env.get("MEILI_INDEX_PREFIX") ?? "farm-os",
});

console.log(`Meilisearch index contract applied: ${result.index}`);
