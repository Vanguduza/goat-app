# Farm OS — Embedded AI module (Supabase + app only)

**Companion to:** `GOAT_RABBIT_FARM_PLATFORM_TECHNICAL_IMPLEMENTATION_MASTER_PLAN.md`  
**Date:** 20 August 2026 (hosting pass)  
**Android packages:** `:domain-ai` · `:feature-ai` · optional `:ai-runtime-litert` / `:ai-runtime-onnx` · `:ai-client`  
**Backend footprint:** **Supabase only** (Postgres + Auth + Storage + Edge Functions). **No** farm-hosted Ktor analytics service, **no** Ollama/Hermes sidecar, **no** Python ML VM.

### Hosting law (non-negotiable)

| Allowed | Not allowed |
|---------|-------------|
| Logic and UI inside the **Android APK** | Farm-operated Docker / VMs / “free” sidecars for AI |
| Data + RLS in **Supabase Postgres** | Separate analytics microservice |
| Thin **Supabase Edge Functions** (Deno) as optional secret-holding proxies | Self-hosted Hermes + Ollama as part of product deploy |
| **BYO Model API** URL + key in Settings (vendor free tiers OK: Groq, OpenRouter, etc.) | Shipping a requirement to rent GPU / always-on agent hosts |

**Hermes / free-sidecar verdict:** Hermes needs a **persistent** process plus an LLM backend. Official local guidance is roughly **8 GB RAM minimum** (tiny chat models) and **~32 GB+** for reliable tool-calling agents, with **≥64k context**. Free sidecars (Render/Railway/Fly free allowances, Supabase Edge) do **not** provide that RAM, persistence, or GPU. Therefore **Hermes is out of the hosting footprint**. Copilot is implemented **in-app** against Supabase + optional BYO OpenAI-compatible API. Hermes remains a documented *optional personal experiment* only if someone runs it on their own PC — not a product dependency.

---

## 0. Locked decisions (binding)

These decisions are product law. Do not re-litigate in implementation without updating this file and master plan §12 / §12a.

### 0.1 LLM backends — Settings only

| Decision | Lock |
|----------|------|
| Where backends are configured | **Settings → AI & analytics** only (Owner; Farm Manager may set endpoint/model; only Owner rotates keys) |
| How | BYO OpenAI-compatible: `model_api_base_url` + `model_id` + `model_api_key` (Keystore) **or** `use_supabase_proxy` |
| What ships as presets | Groq, OpenRouter, Google AI Studio, OpenAI, Custom URL |
| What must **not** appear in Settings | Hermes URL, Ollama LAN URL, farm analytics microservice URL |
| Empty Model API | Valid product mode — charts + Lane A anomalies still work; Copilot NL chat disabled |

### 0.2 Hermes task → concrete replacement (locked)

| Former Hermes task | Locked replacement | Concrete tool / component |
|--------------------|--------------------|---------------------------|
| LLM “brain” / chat completions | BYO Model API from Settings | `:ai-client` OpenAI-compat HTTP (SSE); optional Edge `ai-proxy` |
| Agent runtime / tool loop | In-app engine | `CopilotEngine` in `:feature-ai` / `:domain-ai` |
| Farm MCP protocol (Hermes↔data) | **Same tool jobs, different transport** — see §0.3 | `FarmToolRegistry` + Supabase Kotlin client / RPC (RLS) |
| Module skills | Bundled Markdown | `skills/{module}-*.md` in APK (optional sync from Storage) |
| Session / memory | App + DB | Room conversation cache + `ai_audit`; scope `farm_id`+user+module |
| Cron / daily briefing | Supabase schedule | `pg_cron` or scheduled Edge Function → `tasks` / push |
| Event → investigate | DB / Edge | Trigger or Edge on `analytics_anomalies` insert → optional Lane C enrich |
| Draft recommendations | Unchanged product rule | `propose_recommendation` → Accept/Reject UI |
| Terminal / shell tools | **Removed** | Never reintroduce |

### 0.3 Why “Farm MCP” is not kept (and what is kept)

**Farm MCP was not the farm data API.** It was a **protocol adapter** so a *remote* Hermes process could call read tools over MCP HTTP/stdio.

Without Hermes:

- There is **no remote agent** that needs MCP.
- The **same tool contracts** (`search_in_module`, `get_animal`, `get_kpis`, …) stay as the Copilot allow-list.
- The app executes them **directly** via Supabase (JWT + RLS) inside `FarmToolRegistry`.

| Kept | Dropped |
|------|---------|
| Tool **names, args, species scoping, read-mostly rules** | MCP server process / MCP wire protocol |
| `propose_recommendation` as the only write-shaped tool | Hermes `mcp_servers` config pointing at Ktor |
| Audit of which tool ran | Exposing tools to any external agent runtime |

**Rule:** Do not say “we removed farm data tools.” Say “we removed the MCP *transport* because Copilot runs in-process; `FarmToolRegistry` is the successor to Farm MCP’s tool surface.”

If a future optional external agent is ever added (not planned), it would call the **same** `FarmToolRegistry` contracts via a controlled Edge gateway — not by resurrecting Hermes+MCP as the default.

---

### Product law

| The app **does** | The app **does not** |
|------------------|----------------------|
| Show **graphs** on chart-compatible features (growth, milk, eggs, FCR, FAMACHA, SCC, …) | Replace tables with charts only |
| Flag **positive / negative / watch** anomalies with evidence | Auto-treat, auto-cull, or invent drug doses |
| Recommend **corrective actions** as Accept/Reject drafts or tasks | Mutate biology/finance without a human |
| Call a **Model API from Settings** (BYO) or stay fully on-device | Require a farm-hosted model server |
| Run **Lane A rules** (+ optional LiteRT/ONNX) offline | Depend on the network for basic ADG / mortality flags |

---

## 1. Architecture (app + Supabase)

```
┌─────────────────────────────────────────────────────────────────┐
│  Feature screens (growth, milk, eggs, health scores, KPIs)      │
│  Table | Chart | Both  +  Anomaly chips  +  Actions CTA         │
└────────────────────────────┬────────────────────────────────────┘
                             │ AnalyticsFeature
┌────────────────────────────▼────────────────────────────────────┐
│  :feature-ai / :domain-ai  (EMBEDDED IN APK)                     │
│  ┌──────────────┐  ┌────────────────┐  ┌─────────────────────┐ │
│  │ Lane A Rules │  │ Lane B On-dev  │  │ Lane C BYO Model API│ │
│  │ ADG / MAD    │  │ LiteRT / ONNX  │  │ OpenAI-compat HTTP  │ │
│  │ cohort bands │  │ optional packs │  │ (Settings URL+key)  │ │
│  └──────┬───────┘  └───────┬────────┘  └──────────┬──────────┘ │
│         └──────────────────┴──────────────────────┘             │
│              AnomalyEngine → RecommendationEngine                │
│              CopilotEngine (tool-calling in Kotlin, not Hermes)  │
└──────────────┬─────────────────────────────┬────────────────────┘
               │                             │
               ▼                             ▼
     Room (offline cache)          Supabase (hosted)
     anomaly drafts                Postgres + RLS
                                   Auth JWT (farm_id)
                                   Storage (optional model packs)
                                   Edge Functions (optional proxy)
```

### Routing policy (Settings → AI)

1. **Offline / `on_device_only`:** Lane A always; Lane B if assets installed. Copilot = local template answers + cached tips only (or queue until online).  
2. **`hybrid` (default):** Lane A/B first; Lane C enrich when online and Model API configured.  
3. **`api_preferred`:** Lane C for forecast/explain when online; still fall back to A/B on failure.  
4. **Copilot:** App → (optional Edge Function proxy) → BYO OpenAI-compat chat completions. Tools execute **in the app** against Supabase client (RLS), never as a remote agent with a shell.  
5. **No Hermes URL** in Settings. No “farm analytics microservice” URL — only BYO Model API or empty (on-device only).

---

## 2. What runs where

| Capability | Where it runs | Notes |
|------------|---------------|-------|
| Charts (Vico) | App | Additional representation only |
| ADG / cohort / FAMACHA / egg-drop rules | App (`:domain-ai`) | Primary brain for anomalies |
| Corrective action catalog | App | Templates → Tasks / Health drafts |
| Optional tiny `.tflite` / ONNX | App + optional download from Supabase Storage | Size-gated; not required for MVP |
| Persist anomalies / recommendations | Supabase tables + Room mirror | Sync like other farm rows |
| KPI SQL / views | Supabase Postgres | Same DB as SoR projections |
| Pedigree / inbreeding (light) | App Kotlin first; heavy matrices later via Edge only if needed | No PyAGH sidecar for MVP |
| NL Copilot | App tool-loop + BYO Model API | Skills = bundled Markdown + system prompt |
| Secret Model API keys | Android Keystore **or** Supabase Vault + Edge proxy | Prefer Edge proxy so keys never leave device storage awkwardly shared |
| Scheduled briefings | Supabase `pg_cron` / scheduled Edge Function → push / `tasks` | Not Hermes cron |

---

## 3. Chart-compatible features

Every time-series or cohort screen implements `ChartableFeature`.

| Feature | Module(s) | Series | Chart (Vico) |
|---------|-----------|--------|--------------|
| Growth / ADG | Mammals; poultry meat/breeders | Weight vs age; cohort band | Line + band |
| Litter / kit weights | Rabbit | Litter mean + individuals | Line / grouped |
| Milk / lactation | Goat dairy, cattle dairy | kg, fat, protein | Multi-series |
| SCC / SCS | Dairy | SCC log or SCS | Line + threshold |
| Egg production | Poultry layers (by kind) | Hen-day %, hen-housed | Line |
| FCR / feed | Rabbit, poultry, beef | Feed/gain or egg mass | Line |
| Hatchability | Poultry hatchery | Hatch % by set | Line / bar |
| FAMACHA / BCS | Goat, sheep, cattle | Score over time | Step / line |
| Mortality | Poultry flock, rabbit litter | Daily / cumulative | Line / bar |
| KPI scorecard | Module home | Target vs actual | Bullet / bar |

**UI:** `Table | Chart | Both`. Phone default **Both**. Library: [Vico](https://github.com/patrykandpatrick/vico).

---

## 4. Anomalies & corrective actions

### 4.1 Polarity

| Polarity | Meaning | Examples |
|----------|---------|----------|
| **Negative** | Risk / underperformance | ADG &lt; cohort −2σ; FAMACHA ≥ 4; mortality spike; SCC flag |
| **Positive** | Favourable | ADG &gt; +2σ; FCR better than pack; conception up |
| **Watch** | Early signal | ADG −1.5σ; one-day egg dip |

Fields: `feature_code`, subject (`animal_id` | `group_id`), `species_code`, optional `poultry_kind_code`, `polarity`, `severity`, evidence JSON, `lane` (`rules` | `on_device` | `model_api`), `status`.

### 4.2 Actions (drafts only)

| Trigger | Action templates |
|---------|------------------|
| Growth negative | Recheck scale; BCS/FAMACHA; feed review; reweigh 7 d; **vet if FAMACHA≥4 / off-feed** |
| Growth positive | Confirm measure; genetics note; optional breeding-candidate flag |
| Milk/SCC negative | Hygiene / CMT SOP task; withdrawal check; vet if toxic signs |
| Egg drop | Water/feed/light; mortality sheet; biosecurity walk |
| Mortality spike | Quarantine house; stop movements; Health outbreak path |
| FAMACHA negative | Selective drench **only via vet-accepted pack**; recheck 7 d |

**Never** invent named drugs/doses. Prefer “Open Health → pack” / “Call vet”.

### 4.3 Kotlin contracts (`:domain-ai`)

```kotlin
enum class InferenceLane { RULES, ON_DEVICE, MODEL_API }

interface AnalyticsFeature {
    val code: String
    fun supportsChart(): Boolean
    fun series(ctx: AnalyticsContext): TimeSeries
    fun detect(ctx: AnalyticsContext): List<Anomaly>
    fun actionsFor(anomaly: Anomaly): List<CorrectiveAction>
}
```

Lane A growth: cohort = same `species_code` (+ poultry kind + purpose); expected ADG = median; flag with MAD/z-score; ≥3 points else Watch.

---

## 5. Settings → LLM / Model API (locked)

**Screen:** Settings → **AI & analytics** (Owner; Farm Manager may set endpoint/model; only Owner rotates keys).

**This is the only place** LLM backends are configured. Copilot and Lane C both read these fields. No hard-coded vendor; no Hermes/Ollama fields.

| Setting | Purpose |
|---------|---------|
| `ai_enabled` | Master switch |
| `analytics_mode` | `on_device_only` \| `hybrid` \| `api_preferred` |
| `model_api_base_url` | OpenAI-compatible base URL |
| `model_api_key` | Keystore-backed (or unused when proxy on) |
| `model_id` | Vendor model id (must support **tool/function calling** for Copilot) |
| `use_supabase_proxy` | If true, app calls Edge `ai-proxy`; key in Supabase secrets |
| `on_device_models` | Enable downloaded LiteRT/ONNX packs |
| `share_animal_ids` | Default **off** — opaque series only |
| `copilot_enabled` | In-app Copilot using the same Model API |

**Locked presets:** Groq · OpenRouter · Google AI Studio · OpenAI · Custom URL.  
**Empty URL:** charts + Lane A only; Copilot NL off.

**Do not ship presets for:** Hermes LAN, Ollama on farm server, “Farm Analytics Ktor”.

### Optional Supabase Edge proxy

```
POST /functions/v1/ai-proxy
Authorization: Bearer <user JWT>
Body: { "purpose": "chat"|"anomaly_enrich"|"forecast", "payload": {...} }
```

Edge Function:

1. Validates JWT → `farm_id` claim.  
2. Loads Model API key from Supabase secrets (or farm row encrypted at rest).  
3. Forwards minimised JSON to BYO provider.  
4. Returns structured anomalies/actions that must match the **app action catalog** (no free-form doses).  
5. Logs `ai_audit` row (prompt hash, model, farm_id, species_code) — not full pedigree dumps.

CPU-bound Chronos / sklearn Isolation Forest **are not hosted**. If Lane C is used for “forecast”, it is **LLM or vendor API** interpretation of series the app already computed — or skip forecast and show rule-based bands only.

---

## 6. In-app Copilot + FarmToolRegistry (successor to Farm MCP tools)

```
User message
    → CopilotEngine builds system prompt (module skill Markdown)
    → Model API chat.completions (backend from Settings; tools from FarmToolRegistry)
    → App executes FarmToolRegistry via Supabase client (RLS)
    → Loop until final answer
    → Citations = row IDs from tool results
    → Optional propose_recommendation → Accept/Reject UI
```

### 6.1 FarmToolRegistry (locked tool surface)

Same jobs formerly exposed over Farm MCP to Hermes. **Transport = in-process Kotlin + Supabase**, not MCP.

| Tool | Purpose |
|------|---------|
| `search_in_module` | Tag / RFID / name / flock code within session species |
| `get_animal` / `get_group` | Reject if species ≠ session module |
| `get_timeline` | Events (capped) |
| `get_weight_series` / `get_kpis` | Series and scorecard — never raw SQL |
| `list_module_today` | That species’ Today |
| `get_withdrawal_status` | Meat/milk/egg hold |
| `list_open_anomalies` | From `analytics_anomalies` |
| `search_health_library` | Read-only first-aid / catalog |
| `propose_recommendation` | Draft for Accept/Reject |

**Forbidden:** raw SQL, shell, arbitrary HTTP, writing treatments/finance without Accept.

Skills: `skills/{module}-*.md` in APK (optional Storage sync).  
Morning briefing: Supabase scheduled function → `tasks` / push — not Hermes cron.

---

## 7. Gradle layout

```
:domain-ai/           # AnalyticsFeature, AnomalyEngine, RecommendationEngine, Copilot tool schemas
:feature-ai/          # Charts, anomaly UI, Copilot screen, Settings AI
:ai-client/           # OpenAI-compat OkHttp + optional Supabase function client
:ai-runtime-litert/   # Optional
:ai-runtime-onnx/     # Optional
```

Species modules register `AnalyticsFeature` via Hilt multibinding. They do not own ML code.

**Removed from product deploy:** `:server/modules/analytics` as a separate host, `hermes/` container, Ollama compose service.

---

## 8. Supabase data model

```sql
-- Same shape as master plan §4.11; apply RLS with farm_id = auth JWT claim

CREATE TABLE analytics_anomalies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    feature_code TEXT NOT NULL,
    polarity TEXT NOT NULL CHECK (polarity IN ('negative', 'positive', 'watch')),
    severity TEXT NOT NULL CHECK (severity IN ('info', 'warning', 'critical')),
    species_code TEXT NOT NULL,
    poultry_kind_code TEXT,
    animal_id UUID REFERENCES animals(id),
    group_id UUID REFERENCES animal_groups(id),
    title TEXT NOT NULL,
    detail TEXT,
    evidence JSONB NOT NULL DEFAULT '{}',
    lane TEXT NOT NULL CHECK (lane IN ('rules', 'on_device', 'model_api')),
    status TEXT NOT NULL DEFAULT 'open'
        CHECK (status IN ('open', 'acknowledged', 'resolved', 'dismissed')),
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (animal_id IS NOT NULL OR group_id IS NOT NULL)
);

CREATE TABLE analytics_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    anomaly_id UUID NOT NULL REFERENCES analytics_anomalies(id),
    action_code TEXT NOT NULL,
    title TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'proposed'
        CHECK (status IN ('proposed', 'accepted', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    user_id UUID,
    purpose TEXT NOT NULL,
    model_id TEXT,
    species_code TEXT,
    prompt_hash TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Non-secret defaults on farms.config JSONB: model_api_base_url template, model_id, mode
-- Secrets: device Keystore OR Supabase Vault / Edge secrets — never plaintext in Postgres
```

---

## 9. Tooling (fits this footprint)

| Need | Choice | Why |
|------|--------|-----|
| Charts | Vico | In-app |
| Rules / anomalies | Kotlin `:domain-ai` | No server ML |
| On-device ML (optional) | LiteRT / ONNX Runtime Mobile | Runs in APK |
| Tiny forecast experiments | Chronos-Bolt **only if** size allows on-device or via BYO API — not self-hosted | No farm GPU |
| LLM Copilot | BYO OpenAI-compat (Groq/OpenRouter/…) | User’s key / free tier |
| Proxy / secrets | Supabase Edge Functions | Inside Supabase env |
| Agent framework | **Not Hermes** for product | Free sidecar infeasible |
| Client pattern | Thin OkHttp client (Kompletions-style refs) | Avoid shipping whole chat apps |

**Explicitly deferred / out of footprint:** Hermes Agent, Ollama/vLLM farm hosts, scikit-learn/XGBoost farm microservices, Chronos on farm GPU, PyAGH as a required sidecar.

---

## 10. Safety & privacy

1. Advisory only — Accept/Reject for mutations.  
2. Minimised payloads to Model API; `share_animal_ids` default off.  
3. Keys in Keystore or Supabase secrets.  
4. Species / poultry-kind isolation on every series.  
5. Health formulary still gates treatments (`FARM_OS_HEALTH_MODULE_SPEC.md`).  
6. Copilot tools are a fixed allow-list; no terminal.

---

## 11. Delivery checklist

| Phase | Deliver |
|-------|---------|
| **3 Growth** | Vico charts; Lane A anomalies + actions; Room cache |
| **4+** | Sync anomalies to Supabase; RLS |
| **8 Intelligence** | Settings BYO Model API; optional Edge `ai-proxy`; in-app Copilot tool-loop; `ai_audit` |
| **Optional** | LiteRT/ONNX packs in Supabase Storage; on-device RAG over protocol packs (PocketSage-style) |

**Not in checklist:** Hermes compose file, Ollama, free sidecar experiments as product gates.

---

## 12. Growth page (reference)

Route: `goat/{id}/growth` (same pattern per species).

1. Table of weights / ADG.  
2. Chart + cohort band (Lane A).  
3. Anomaly chips.  
4. Actions → tasks.  
5. **Explain** button → Copilot with citations if Model API configured; else show rule evidence text only.

---

*Embedded AI for this product = APK engines + Supabase persistence/proxy + optional BYO Model API. No farm-hosted AI sidecars. Hermes is not part of the deploy.*
