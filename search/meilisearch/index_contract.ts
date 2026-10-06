export type AnimalsIndexContractOptions = {
  host: string;
  masterKey: string;
  indexPrefix?: string;
};

type TaskResponse = { taskUid: number };
type IndexResponse = { uid: string; primaryKey: string | null };

function normalizedHost(host: string): string {
  return host.replace(/\/$/, "");
}

function adminHeaders(masterKey: string, extra: Record<string, string> = {}) {
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

export async function waitForMeilisearch(host: string) {
  const base = normalizedHost(host);
  for (let attempt = 0; attempt < 100; attempt++) {
    try {
      const response = await fetch(`${base}/health`);
      if (response.ok) return;
    } catch {
      // The process/container may still be starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("Meilisearch did not become healthy");
}

export async function waitForMeiliTask(host: string, masterKey: string, taskUid: number) {
  const base = normalizedHost(host);
  for (let attempt = 0; attempt < 200; attempt++) {
    const response = await fetch(`${base}/tasks/${taskUid}`, {
      headers: adminHeaders(masterKey),
    });
    const task = await requireOk(response, `read task ${taskUid}`) as Record<string, unknown>;
    if (task.status === "succeeded") return task;
    if (task.status === "failed" || task.status === "canceled") {
      throw new Error(`Meilisearch task ${taskUid} ${task.status}: ${JSON.stringify(task.error)}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Meilisearch task ${taskUid} did not finish`);
}

export async function applyAnimalsIndexContract(
  options: AnimalsIndexContractOptions,
): Promise<{ index: string }> {
  const host = normalizedHost(options.host);
  const index = `${options.indexPrefix ?? "farm-os"}-animals-v1`;
  await waitForMeilisearch(host);

  const existing = await fetch(`${host}/indexes/${index}`, {
    headers: adminHeaders(options.masterKey),
  });
  if (existing.status === 404) {
    const create = await fetch(`${host}/indexes`, {
      method: "POST",
      headers: adminHeaders(options.masterKey),
      body: JSON.stringify({ uid: index, primaryKey: "id" }),
    });
    const task = await requireOk(create, "create animals index") as TaskResponse;
    await waitForMeiliTask(host, options.masterKey, task.taskUid);
  } else {
    const indexState = await requireOk(existing, "read animals index") as IndexResponse;
    if (indexState.primaryKey !== "id") {
      throw new Error(`Animals index primary key drifted: expected id, got ${indexState.primaryKey}`);
    }
  }

  const settingsUrl = new URL("./animals-v1.settings.json", import.meta.url);
  const settings = JSON.parse(await Deno.readTextFile(settingsUrl));
  const settingsResponse = await fetch(`${host}/indexes/${index}/settings`, {
    method: "PATCH",
    headers: adminHeaders(options.masterKey),
    body: JSON.stringify(settings),
  });
  const settingsTask = await requireOk(settingsResponse, "apply animals index settings") as TaskResponse;
  await waitForMeiliTask(host, options.masterKey, settingsTask.taskUid);

  const actual = await requireOk(
    await fetch(`${host}/indexes/${index}/settings`, {
      headers: adminHeaders(options.masterKey),
    }),
    "read applied animals settings",
  ) as Record<string, unknown>;
  const filterable = actual.filterableAttributes;
  if (!Array.isArray(filterable) || !filterable.includes("farm_id")) {
    throw new Error("farm_id is not filterable after Meilisearch settings reconciliation");
  }

  return { index };
}
