-- Rabbit kits, retention, waitlist, contracts, market plan,
-- goat BCS, cattle locomotion/SCC, sheep shearing.
-- Writes stay on versioned RPCs. Money stays integer minor units.

create table if not exists public.rabbit_kits (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    wave_id uuid not null,
    animal_id uuid,
    temp_label text not null,
    sex text not null default 'unknown' check (sex in ('male','female','unknown')),
    status text not null default 'alive' check (status in ('alive','dead','fostered_out','missing')),
    retention text not null default 'undecided' check (retention in ('undecided','keep_breeder','grow_meat','sale_pet','cull')),
    ear_tag text,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, ear_tag)
);

create table if not exists public.rabbit_retention_decisions (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    kit_id uuid not null,
    decision text not null check (decision in ('keep_breeder','grow_meat','sale_pet','cull','undecided')),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, kit_id) references public.rabbit_kits(farm_id, id)
);

create table if not exists public.rabbit_sales_waitlist (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    contact_name text not null,
    desired_sex text,
    qty integer not null check (qty > 0),
    status text not null default 'open' check (status in ('open','matched','fulfilled','cancelled')),
    matched_kit_id uuid,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, matched_kit_id) references public.rabbit_kits(farm_id, id)
);

create table if not exists public.rabbit_sales_contracts (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    waitlist_id uuid,
    buyer_name text not null,
    animal_id uuid,
    amount_minor bigint not null check (amount_minor > 0),
    currency text not null default 'USD',
    status text not null default 'draft' check (status in ('draft','agreed','paid','delivered','cancelled')),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, waitlist_id) references public.rabbit_sales_waitlist(farm_id, id)
);

create table if not exists public.rabbit_market_plans (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    kit_id uuid,
    wave_id uuid,
    target_weight_grams integer not null check (target_weight_grams > 0),
    target_epoch_day integer not null,
    purpose text not null check (purpose in ('meat','pet_sale','show')),
    status text not null default 'active' check (status in ('active','ready','sold','butchered','cancelled')),
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.goat_bcs_scores (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    score_tenths integer not null check (score_tenths between 10 and 50),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.cattle_locomotion_scores (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    score integer not null check (score between 1 and 5),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.cattle_scc_records (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    cells_per_ml integer not null check (cells_per_ml > 0),
    dim_days integer,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.sheep_shearing_events (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid,
    group_id uuid,
    kind text not null check (kind in ('shearing','crutching','classing')),
    greasy_grams integer,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (animal_id is not null or group_id is not null)
);

alter table public.rabbit_kits enable row level security;
alter table public.rabbit_retention_decisions enable row level security;
alter table public.rabbit_sales_waitlist enable row level security;
alter table public.rabbit_sales_contracts enable row level security;
alter table public.rabbit_market_plans enable row level security;
alter table public.goat_bcs_scores enable row level security;
alter table public.cattle_locomotion_scores enable row level security;
alter table public.cattle_scc_records enable row level security;
alter table public.sheep_shearing_events enable row level security;

create policy rabbit_kits_sel on public.rabbit_kits for select to authenticated using (public.is_farm_member(farm_id));
create policy rabbit_retention_sel on public.rabbit_retention_decisions for select to authenticated using (public.is_farm_member(farm_id));
create policy rabbit_waitlist_sel on public.rabbit_sales_waitlist for select to authenticated using (public.is_farm_member(farm_id));
create policy rabbit_contracts_sel on public.rabbit_sales_contracts for select to authenticated using (public.is_farm_member(farm_id));
create policy rabbit_plans_sel on public.rabbit_market_plans for select to authenticated using (public.is_farm_member(farm_id));
create policy goat_bcs_sel on public.goat_bcs_scores for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_loco_sel on public.cattle_locomotion_scores for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_scc_sel on public.cattle_scc_records for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_shear_sel on public.sheep_shearing_events for select to authenticated using (public.is_farm_member(farm_id));

revoke insert, update, delete on public.rabbit_kits, public.rabbit_retention_decisions, public.rabbit_sales_waitlist,
    public.rabbit_sales_contracts, public.rabbit_market_plans, public.goat_bcs_scores, public.cattle_locomotion_scores,
    public.cattle_scc_records, public.sheep_shearing_events from authenticated;
grant select on public.rabbit_kits, public.rabbit_retention_decisions, public.rabbit_sales_waitlist,
    public.rabbit_sales_contracts, public.rabbit_market_plans, public.goat_bcs_scores, public.cattle_locomotion_scores,
    public.cattle_scc_records, public.sheep_shearing_events to authenticated;

create or replace function public.rabbit_kit_register_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_wave uuid; v_label text; v_sex text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'kitId')::uuid;
    v_wave := (p_payload->>'waveId')::uuid;
    v_label := btrim(coalesce(p_payload->>'tempLabel',''));
    v_sex := coalesce(p_payload->>'sex','unknown');
    if v_label = '' or v_sex not in ('male','female','unknown') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Kit needs a wave and a temporary label');
    end if;
    if not exists (select 1 from public.rabbit_breeding_waves where farm_id = p_farm_id and id = v_wave) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Wave not found');
    end if;
    insert into public.rabbit_kits(id, farm_id, wave_id, temp_label, sex)
    values (v_id, p_farm_id, v_wave, v_label, v_sex);
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.kit_registered.v1', 'rabbit.kit_register.v1', 'rabbit_wave', v_wave,
        'rabbit_wave:' || v_wave::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'rabbit_wave:' || v_wave::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.rabbit_kit_promote_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_kit uuid; v_animal uuid; v_tag text; v_sex text; v_row public.rabbit_kits;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_kit := (p_payload->>'kitId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_tag := btrim(coalesce(p_payload->>'tag',''));
    select * into v_row from public.rabbit_kits where farm_id = p_farm_id and id = v_kit;
    if not found or v_row.status != 'alive' or v_row.animal_id is not null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Alive unpromoted kit not found');
    end if;
    if v_tag = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Promote needs an ear tag');
    end if;
    v_sex := case v_row.sex when 'female' then 'FEMALE' when 'male' then 'MALE' else coalesce(p_payload->>'sex','FEMALE') end;
    if v_sex not in ('FEMALE','MALE') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Promote needs doe or buck sex');
    end if;
    insert into public.animals(id, farm_id, species_code, tag, sex, status)
    values (v_animal, p_farm_id, 'rabbit', v_tag, v_sex, 'active');
    update public.rabbit_kits set animal_id = v_animal, ear_tag = v_tag, retention = 'keep_breeder'
    where farm_id = p_farm_id and id = v_kit;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.kit_promoted.v1', 'rabbit.kit_promote.v1', 'animal', v_animal,
        'animal:' || v_animal::text, 1, p_payload, 'animal', v_animal
    );
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','Tag already exists');
end;
$$;

create or replace function public.rabbit_retention_decide_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_kit uuid; v_decision text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'decisionId')::uuid;
    v_kit := (p_payload->>'kitId')::uuid;
    v_decision := p_payload->>'decision';
    if v_decision not in ('keep_breeder','grow_meat','sale_pet','cull','undecided') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Retention needs a listed decision');
    end if;
    if not exists (select 1 from public.rabbit_kits where farm_id = p_farm_id and id = v_kit) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Kit not found');
    end if;
    insert into public.rabbit_retention_decisions(id, farm_id, kit_id, decision, occurred_on)
    values (v_id, p_farm_id, v_kit, v_decision, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    update public.rabbit_kits set retention = v_decision where farm_id = p_farm_id and id = v_kit;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.retention_decided.v1', 'rabbit.retention_decide.v1', 'rabbit_kit', v_kit,
        'rabbit_kit:' || v_kit::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'rabbit_kit:' || v_kit::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.rabbit_waitlist_enqueue_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'waitlistId')::uuid;
    if btrim(coalesce(p_payload->>'contactName','')) = '' or (p_payload->>'qty')::integer is null or (p_payload->>'qty')::integer <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Waitlist needs a buyer name and quantity');
    end if;
    insert into public.rabbit_sales_waitlist(id, farm_id, contact_name, desired_sex, qty)
    values (v_id, p_farm_id, btrim(p_payload->>'contactName'), nullif(btrim(coalesce(p_payload->>'desiredSex','')),''), (p_payload->>'qty')::integer);
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.waitlist_enqueued.v1', 'rabbit.waitlist_enqueue.v1', 'rabbit_waitlist', v_id,
        'rabbit_waitlist:' || v_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.rabbit_waitlist_fulfill_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_kit uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'waitlistId')::uuid;
    v_kit := (p_payload->>'kitId')::uuid;
    if not exists (select 1 from public.rabbit_sales_waitlist where farm_id = p_farm_id and id = v_id and status = 'open') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Open waitlist row not found');
    end if;
    if not exists (select 1 from public.rabbit_kits where farm_id = p_farm_id and id = v_kit and status = 'alive' and retention = 'sale_pet') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Waitlist match needs a living kit marked sale_pet');
    end if;
    update public.rabbit_sales_waitlist
    set status = 'fulfilled', matched_kit_id = v_kit
    where farm_id = p_farm_id and id = v_id;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.waitlist_fulfilled.v1', 'rabbit.waitlist_fulfill.v1', 'rabbit_waitlist', v_id,
        'rabbit_waitlist:' || v_id::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'rabbit_waitlist:' || v_id::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.rabbit_contract_agree_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_amount bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'contractId')::uuid;
    v_amount := (p_payload->>'amountMinor')::bigint;
    if btrim(coalesce(p_payload->>'buyerName','')) = '' or v_amount is null or v_amount <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Contract needs a buyer and amount');
    end if;
    insert into public.rabbit_sales_contracts(id, farm_id, waitlist_id, buyer_name, animal_id, amount_minor, currency, status, occurred_on)
    values (
        v_id, p_farm_id, nullif(p_payload->>'waitlistId','')::uuid, btrim(p_payload->>'buyerName'),
        nullif(p_payload->>'animalId','')::uuid, v_amount, coalesce(nullif(btrim(p_payload->>'currency'),''),'USD'),
        'agreed', date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer)
    );
    insert into public.sales_records(id, farm_id, item_kind, quantity_milli, amount_minor, currency, occurred_on)
    values (v_id, p_farm_id, 'live_rabbit', 1000, v_amount, coalesce(nullif(btrim(p_payload->>'currency'),''),'USD'),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    insert into public.money_records(id, farm_id, kind, category_code, amount_minor, currency, occurred_on, note)
    values (v_id, p_farm_id, 'income', 'sales', v_amount, coalesce(nullif(btrim(p_payload->>'currency'),''),'USD'),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer), 'rabbit contract');
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.contract_agreed.v1', 'rabbit.contract_agree.v1', 'rabbit_contract', v_id,
        'rabbit_contract:' || v_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.rabbit_market_plan_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'planId')::uuid;
    if (p_payload->>'targetWeightGrams')::integer is null or (p_payload->>'targetWeightGrams')::integer <= 0
        or coalesce(p_payload->>'purpose','') not in ('meat','pet_sale','show') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Market plan needs a target weight and purpose');
    end if;
    insert into public.rabbit_market_plans(id, farm_id, kit_id, wave_id, target_weight_grams, target_epoch_day, purpose)
    values (
        v_id, p_farm_id, nullif(p_payload->>'kitId','')::uuid, nullif(p_payload->>'waveId','')::uuid,
        (p_payload->>'targetWeightGrams')::integer, (p_payload->>'targetEpochDay')::integer, p_payload->>'purpose'
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.market_plan_recorded.v1', 'rabbit.market_plan.v1', 'rabbit_market_plan', v_id,
        'rabbit_market_plan:' || v_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.goat_record_bcs_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_score int;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'scoreId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_score := (p_payload->>'scoreTenths')::integer;
    if v_score is null or v_score < 10 or v_score > 50 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Goat BCS must be 1 to 5');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'goat') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Goat not found');
    end if;
    insert into public.goat_bcs_scores(id, farm_id, animal_id, score_tenths, occurred_on)
    values (v_id, p_farm_id, v_animal, v_score, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.bcs_recorded.v1', 'goat.record_bcs.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, 'animal', v_animal
    );
end;
$$;

create or replace function public.cattle_record_locomotion_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_score int;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'scoreId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_score := (p_payload->>'score')::integer;
    if v_score is null or v_score < 1 or v_score > 5 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Locomotion score must be 1 to 5');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'cattle') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cow not found');
    end if;
    insert into public.cattle_locomotion_scores(id, farm_id, animal_id, score, occurred_on)
    values (v_id, p_farm_id, v_animal, v_score, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.locomotion_recorded.v1', 'cattle.record_locomotion.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, 'animal', v_animal
    );
end;
$$;

create or replace function public.cattle_record_scc_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_cells int;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'recordId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_cells := (p_payload->>'cellsPerMl')::integer;
    if v_cells is null or v_cells <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','SCC needs cells per millilitre');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'cattle' and sex = 'FEMALE') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cow not found');
    end if;
    insert into public.cattle_scc_records(id, farm_id, animal_id, cells_per_ml, dim_days, occurred_on)
    values (v_id, p_farm_id, v_animal, v_cells, (p_payload->>'dimDays')::integer, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.scc_recorded.v1', 'cattle.record_scc.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, 'animal', v_animal
    );
end;
$$;

create or replace function public.sheep_record_shearing_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_kind text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'eventId')::uuid;
    v_kind := p_payload->>'kind';
    if v_kind not in ('shearing','crutching','classing') or (nullif(p_payload->>'animalId','') is null and nullif(p_payload->>'groupId','') is null) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Shearing needs a kind and a sheep or mob');
    end if;
    insert into public.sheep_shearing_events(id, farm_id, animal_id, group_id, kind, greasy_grams, occurred_on)
    values (
        v_id, p_farm_id, nullif(p_payload->>'animalId','')::uuid, nullif(p_payload->>'groupId','')::uuid,
        v_kind, (p_payload->>'greasyGrams')::integer, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer)
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.sheared.v1', 'sheep.record_shearing.v1',
        case when nullif(p_payload->>'animalId','') is not null then 'animal' else 'animal_group' end,
        coalesce(nullif(p_payload->>'animalId','')::uuid, nullif(p_payload->>'groupId','')::uuid),
        case when nullif(p_payload->>'animalId','') is not null then 'animal:' || (p_payload->>'animalId') else 'animal_group:' || (p_payload->>'groupId') end,
        1, p_payload, case when nullif(p_payload->>'animalId','') is not null then 'animal' end, nullif(p_payload->>'animalId','')::uuid
    );
end;
$$;

grant execute on function public.rabbit_kit_register_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_kit_promote_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_retention_decide_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_waitlist_enqueue_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_waitlist_fulfill_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_contract_agree_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_market_plan_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.goat_record_bcs_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_locomotion_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_scc_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_shearing_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
