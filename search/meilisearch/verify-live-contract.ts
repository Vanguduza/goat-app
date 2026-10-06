import { signTenantToken } from "../../supabase/functions/_shared/meili_tenant_token.ts";
import { applyAnimalsIndexContract, waitForMeiliTask } from "./index_contract.ts";

type TaskResponse = { taskUid: number };
type SearchResponse = { hits: Array<Record<string, unknown>> };
type ApiKeyResponse = { uid: string; key: string };

const host = (Deno.env.get("MEILI_HOST") ?? "http://127.0.0.1:7700").replace(/\/$/, "");
const masterKey = Deno.env.get("MEILI_MASTER_KEY") ?? "farm-os-ci-master-key-32-bytes-minimum";
const indexPrefix = Deno.env.get("MEILI_INDEX_PREFIX") ?? "farm-os";
const index = `${indexPrefix}-animals-v1`;
const searchKeyUid = "3c098ac0-28c1-4f27-96c0-57a2f7a70a11";
const farmA = "11111111-1111-4111-8111-111111111111";
const farmB = "22222222-2222-4222-8222-222222222222";

function adminHeaders(extra: Record<string, string> = {}) {
  return {
    Authorization: `Bearer ${masterKey}`,
    "Content-Type": "application/json",
    ...extra,
  };
}

async function jsonOrText(response: Response): Promise<unknown> {
  const text = await response.text();
  if (text.length === 0) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function requireOk(response: Response, action: string): Promise<unknown> {
  const body = await jsonOrText(response);
  if (!response.ok) {
    throw new Error(`${action} failed: HTTP ${response.status} ${JSON.stringify(body)}`);
  }
  return body;
}

async function seedDocuments() {
  const documents = [
    {
      id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      farm_id: farmA,
      module_id: "goat",
      species_code: "goat",
      poultry_kind_code: null,
      entity_type: "animal",
      display_name: "Nala",
      tag: "A-001",
      identifiers: ["A-001"],
      status: "active",
      search_text: "A-001 Nala",
      updated_projection_version: 2,
    },
    {
      id: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      farm_id: farmB,
      module_id: "goat",
      species_code: "goat",
      poultry_kind_code: null,
      entity_type: "animal",
      display_name: "Zuri",
      tag: "B-001",
      identifiers: ["B-001"],
      status: "active",
      search_text: "B-001 Zuri",
      updated_projection_version: 1,
    },
  ];
  const response = await fetch(`${host}/indexes/${index}/documents`, {
    method: "POST",
    headers: adminHeaders(),
    body: JSON.stringify(documents),
  });
  const task = await requireOk(response, "seed tenant-isolation documents") as TaskResponse;
  await waitForMeiliTask(host, masterKey, task.taskUid);
}

async function createSearchKey(): Promise<ApiKeyResponse> {
  const response = await fetch(`${host}/keys`, {
    method: "POST",
    headers: adminHeaders(),
    body: JSON.stringify({
      uid: searchKeyUid,
      name: "Farm OS CI tenant-token parent",
      description: "Search-only key used to verify Farm OS tenant isolation",
      actions: ["search"],
      indexes: [index],
      expiresAt: null,
    }),
  });
  return await requireOk(response, "create search-only parent key") as ApiKeyResponse;
}

async function tenantToken(searchKey: ApiKeyResponse, farmId: string, expOffsetSeconds = 600) {
  return await signTenantToken(searchKey.key, {
    apiKeyUid: searchKey.uid,
    exp: Math.floor(Date.now() / 1000) + expOffsetSeconds,
    searchRules: {
      [index]: { filter: `farm_id = "${farmId}"` },
    },
  });
}

async function search(token: string, body: Record<string, unknown>): Promise<Response> {
  return await fetch(`${host}/indexes/${index}/search`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
}

function assertIds(result: SearchResponse, expected: string[], message: string) {
  const actual = result.hits.map((hit) => String(hit.id)).sort();
  const wanted = [...expected].sort();
  if (JSON.stringify(actual) !== JSON.stringify(wanted)) {
    throw new Error(`${message}: expected ${JSON.stringify(wanted)}, got ${JSON.stringify(actual)}`);
  }
}

await applyAnimalsIndexContract({ host, masterKey, indexPrefix });
await seedDocuments();
const searchKey = await createSearchKey();

const farmAToken = await tenantToken(searchKey, farmA);
const farmBToken = await tenantToken(searchKey, farmB);

const farmAResult = await requireOk(await search(farmAToken, { q: "", limit: 20 }), "Farm A tenant search") as SearchResponse;
assertIds(farmAResult, ["aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"], "Farm A token leaked or lost documents");

const farmBResult = await requireOk(await search(farmBToken, { q: "", limit: 20 }), "Farm B tenant search") as SearchResponse;
assertIds(farmBResult, ["bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"], "Farm B token leaked or lost documents");

const attemptedOverride = await requireOk(
  await search(farmAToken, { q: "", filter: `farm_id = "${farmB}"`, limit: 20 }),
  "Farm A attempted cross-farm filter",
) as SearchResponse;
assertIds(attemptedOverride, [], "Caller filter overrode mandatory tenant rule");

const expiredToken = await tenantToken(searchKey, farmA, -10);
const expiredResponse = await search(expiredToken, { q: "", limit: 20 });
if (expiredResponse.ok) throw new Error("Expired tenant token was accepted");

const writeAttempt = await fetch(`${host}/indexes/${index}/documents`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${farmAToken}`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify([{ id: "should-not-write", farm_id: farmA, tag: "X" }]),
});
if (writeAttempt.ok) throw new Error("Tenant token unexpectedly received document-write authority");

console.log("Farm OS Meilisearch live contract passed: settings applied, farm isolation enforced, expiry enforced, writes denied.");
