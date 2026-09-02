type SearchJob = {
  job_id: number;
  farm_id: string;
  entity_type: string;
  entity_id: string;
  operation: "upsert" | "delete";
  projection_version: number;
  attempts: number;
  state: "pending" | "dispatching" | "in_flight" | "retry" | "done" | "dead_letter";
  meili_task_uid: number | null;
  claimed_at: string | null;
  lease_until: string | null;
};

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const meiliHost = (Deno.env.get("MEILI_HOST") ?? "").replace(/\/$/, "");
const meiliAdminKey = Deno.env.get("MEILI_ADMIN_KEY")!;
const indexPrefix = Deno.env.get("MEILI_INDEX_PREFIX") ?? "farm-os";
const invokeSecret = Deno.env.get("INDEXER_INVOKE_SECRET")!;

function serviceHeaders(extra: Record<string, string> = {}) {
  return {
    apikey: serviceRole,
    Authorization: `Bearer ${serviceRole}`,
    "Content-Type": "application/json",
    ...extra,
  };
}

async function patchJob(id: number, patch: Record<string, unknown>) {
  const response = await fetch(`${supabaseUrl}/rest/v1/search_index_jobs?job_id=eq.${id}`, {
    method: "PATCH",
    headers: serviceHeaders({ Prefer: "return=minimal" }),
    body: JSON.stringify(patch),
  });
  if (!response.ok) {
    throw new Error(`Could not update search job ${id}: ${response.status} ${await response.text()}`);
  }
}

async function meiliTask(uid: number) {
  const response = await fetch(`${meiliHost}/tasks/${uid}`, {
    headers: { Authorization: `Bearer ${meiliAdminKey}` },
  });
  if (!response.ok) throw new Error(`Meilisearch task lookup failed: ${response.status}`);
  return await response.json();
}

async function reconcileInflight(): Promise<number> {
  const response = await fetch(
    `${supabaseUrl}/rest/v1/search_index_jobs?select=*&state=eq.in_flight&meili_task_uid=not.is.null&order=job_id.asc&limit=50`,
    { headers: serviceHeaders() },
  );
  if (!response.ok) throw new Error(`Could not read in-flight jobs: ${response.status}`);
  const jobs = await response.json() as SearchJob[];
  let completed = 0;

  for (const job of jobs) {
    try {
      const task = await meiliTask(job.meili_task_uid!);
      if (task.status === "succeeded") {
        await patchJob(job.job_id, {
          state: "done",
          completed_at: new Date().toISOString(),
          last_error: null,
          lease_until: null,
        });
        completed++;
      } else if (task.status === "failed" || task.status === "canceled") {
        const attempts = job.attempts + 1;
        await patchJob(job.job_id, {
          state: attempts >= 12 ? "dead_letter" : "retry",
          attempts,
          next_attempt_at: new Date(Date.now() + Math.min(300_000, 2 ** Math.min(attempts, 8) * 1000)).toISOString(),
          last_error: task.error?.message ?? `Meilisearch task ${task.status}`,
          meili_task_uid: null,
          lease_until: null,
        });
      }
    } catch (error) {
      await patchJob(job.job_id, { last_error: String(error) });
    }
  }
  return completed;
}

async function buildAnimalDocument(job: SearchJob) {
  const response = await fetch(
    `${supabaseUrl}/rest/v1/animals?id=eq.${job.entity_id}&farm_id=eq.${job.farm_id}&select=id,farm_id,species_code,tag,name,status`,
    { headers: serviceHeaders() },
  );
  if (!response.ok) throw new Error(`Could not read animal projection: ${response.status}`);
  const rows = await response.json();
  if (!Array.isArray(rows) || rows.length === 0) return null;
  const animal = rows[0];
  const displayName = animal.name || animal.tag;
  return {
    id: animal.id,
    farm_id: animal.farm_id,
    module_id: animal.species_code,
    species_code: animal.species_code,
    poultry_kind_code: null,
    entity_type: "animal",
    display_name: displayName,
    tag: animal.tag,
    identifiers: [animal.tag],
    status: animal.status,
    search_text: `${animal.tag} ${animal.name ?? ""}`.trim(),
    updated_projection_version: job.projection_version,
  };
}

async function dispatchJob(job: SearchJob) {
  const index = `${indexPrefix}-animals-v1`;
  let response: Response;

  if (job.operation === "delete") {
    response = await fetch(`${meiliHost}/indexes/${index}/documents/${job.entity_id}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${meiliAdminKey}` },
    });
  } else {
    const document = await buildAnimalDocument(job);
    if (document == null) {
      response = await fetch(`${meiliHost}/indexes/${index}/documents/${job.entity_id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${meiliAdminKey}` },
      });
    } else {
      response = await fetch(`${meiliHost}/indexes/${index}/documents`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${meiliAdminKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify([document]),
      });
    }
  }

  if (!response.ok) throw new Error(`Meilisearch write failed: ${response.status} ${await response.text()}`);
  const task = await response.json();
  if (typeof task.taskUid !== "number") throw new Error("Meilisearch did not return taskUid");
  await patchJob(job.job_id, {
    state: "in_flight",
    meili_task_uid: task.taskUid,
    last_error: null,
    lease_until: null,
  });
}

async function claimDispatchable(): Promise<SearchJob[]> {
  const response = await fetch(`${supabaseUrl}/rest/v1/rpc/claim_search_index_jobs_v1`, {
    method: "POST",
    headers: serviceHeaders(),
    body: JSON.stringify({ p_limit: 50, p_lease_seconds: 120 }),
  });
  if (!response.ok) {
    throw new Error(`Could not claim search jobs: ${response.status} ${await response.text()}`);
  }
  return await response.json() as SearchJob[];
}

async function dispatchClaimed(): Promise<number> {
  const jobs = await claimDispatchable();
  let dispatched = 0;

  for (const job of jobs) {
    try {
      await dispatchJob(job);
      dispatched++;
    } catch (error) {
      const attempts = job.attempts + 1;
      await patchJob(job.job_id, {
        state: attempts >= 12 ? "dead_letter" : "retry",
        attempts,
        next_attempt_at: new Date(Date.now() + Math.min(300_000, 2 ** Math.min(attempts, 8) * 1000)).toISOString(),
        last_error: String(error),
        meili_task_uid: null,
        lease_until: null,
      });
    }
  }
  return dispatched;
}

Deno.serve(async (request) => {
  if (request.headers.get("x-farmos-indexer-secret") !== invokeSecret) {
    return Response.json({ error: "Forbidden" }, { status: 403 });
  }

  const completed = await reconcileInflight();
  const dispatched = await dispatchClaimed();
  return Response.json({ completed, dispatched });
});
