import { supabaseServiceHeaders } from "../_shared/supabase_api_keys.ts";

type Candidate = {
  farm_id: string;
  entity_id: string;
  projection_version: number;
};

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const rebuildSecret = Deno.env.get("MEILI_REBUILD_SECRET")!;

async function candidates(afterId: string | null, limit: number): Promise<Candidate[]> {
  const response = await fetch(`${supabaseUrl}/rest/v1/rpc/search_rebuild_candidates_v1`, {
    method: "POST",
    headers: supabaseServiceHeaders(),
    body: JSON.stringify({ p_after_id: afterId, p_limit: limit }),
  });
  if (!response.ok) throw new Error(`Could not obtain rebuild candidates: ${response.status} ${await response.text()}`);
  return await response.json();
}

async function enqueue(rows: Candidate[]) {
  if (rows.length === 0) return;
  const response = await fetch(`${supabaseUrl}/rest/v1/search_index_jobs`, {
    method: "POST",
    headers: supabaseServiceHeaders({ Prefer: "return=minimal" }),
    body: JSON.stringify(rows.map((row) => ({
      farm_id: row.farm_id,
      entity_type: "animal",
      entity_id: row.entity_id,
      operation: "upsert",
      projection_version: row.projection_version,
      state: "pending",
      origin: "rebuild",
    }))),
  });
  if (!response.ok) throw new Error(`Could not enqueue rebuild jobs: ${response.status} ${await response.text()}`);
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  if (request.headers.get("x-farmos-rebuild-secret") !== rebuildSecret) {
    return Response.json({ error: "Forbidden" }, { status: 403 });
  }

  const body = await request.json().catch(() => ({}));
  const requestedLimit = Number(body.pageSize ?? 500);
  const pageSize = Math.max(1, Math.min(1000, requestedLimit));
  let afterId: string | null = typeof body.afterId === "string" ? body.afterId : null;
  const maxPages = Math.max(1, Math.min(100, Number(body.maxPages ?? 20)));

  let queued = 0;
  let pages = 0;
  for (; pages < maxPages; pages++) {
    const rows = await candidates(afterId, pageSize);
    if (rows.length === 0) break;
    await enqueue(rows);
    queued += rows.length;
    afterId = rows.at(-1)!.entity_id;
    if (rows.length < pageSize) {
      pages++;
      break;
    }
  }

  return Response.json({
    queued,
    pages,
    nextAfterId: afterId,
    complete: queued === 0 || pages < maxPages,
    note: "The authoritative source remains Supabase; meili-indexer completes and verifies queued tasks.",
  });
});
