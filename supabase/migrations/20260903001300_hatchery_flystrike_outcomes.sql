-- Poultry houses and hatchery set/candle/hatch by kind incubation,
-- sheep flystrike scores, rabbit mating outcomes including false pregnancy,
-- goat SCC as an integer cell count (not a diagnosis).
-- Writes stay on versioned RPCs. Authenticated clients remain select-only.

create or replace function public.poultry_incubation_days(p_kind text, p_farm_defined integer)
returns integer language sql immutable as $$
    select case p_kind
        when 'chicken' then 21
        when 'duck' then 28
        when 'muscovy' then 35
        when 'guinea_fowl' then 28
        when 'turkey' then 28
        when 'goose' then 30
        when 'quail' then 17
        when 'pigeon' then 17
        when 'farm_defined' then case when p_farm_defined between 1 and 60 then p_farm_defined else null end
        else null
    end;
$$;

create or replace function public.poultry_candling_lead_days(p_kind text)
returns integer language sql immutable as $$
    select case p_kind
        when 'duck' then 10
        when 'muscovy' then 10
        when 'guinea_fowl' then 10
        when 'turkey' then 10
        when 'goose' then 10
        else 7
    end;
$$;

create table if not exists public.poultry_houses (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    code text not null,
    kind text not null check (kind in ('house','coop','range','hatchery','brooder','pond','loft')),
    poultry_kind_code text not null check (poultry_kind_code in (
        'chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined'
    )),
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, code)
);

create table if not exists public.poultry_hatches (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    poultry_kind_code text not null check (poultry_kind_code in (
        'chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined'
    )),
    house_id uuid,
    group_id uuid,
    eggs_set integer not null check (eggs_set > 0),
    incubation_days integer not null check (incubation_days between 1 and 60),
    set_on date not null,
    status text not null default 'set' check (status in ('set','candled','hatched')),
    fertile integer,
    infertile integer,
    mid_dead integer,
    hatched integer,
    culls integer,
    placement_group_id uuid,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, house_id) references public.poultry_houses(farm_id, id)
);

create table if not exists public.sheep_flystrike_scores (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    score integer not null check (score between 0 and 5),
    region text,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.rabbit_mating_outcomes (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    wave_id uuid not null,
    outcome text not null check (outcome in ('false_pregnancy','open','pregnant','kindled')),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, wave_id) references public.rabbit_breeding_waves(farm_id, id)
);

create table if not exists public.goat_scc_records (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    cells_per_ml integer not null check (cells_per_ml > 0),
    dim_days integer,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

alter table public.poultry_houses enable row level security;
alter table public.poultry_hatches enable row level security;
alter table public.sheep_flystrike_scores enable row level security;
alter table public.rabbit_mating_outcomes enable row level security;
alter table public.goat_scc_records enable row level security;

create policy poultry_houses_sel on public.poultry_houses for select to authenticated using (public.is_farm_member(farm_id));
create policy poultry_hatches_sel on public.poultry_hatches for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_flystrike_sel on public.sheep_flystrike_scores for select to authenticated using (public.is_farm_member(farm_id));
create policy rabbit_outcomes_sel on public.rabbit_mating_outcomes for select to authenticated using (public.is_farm_member(farm_id));
create policy goat_scc_sel on public.goat_scc_records for select to authenticated using (public.is_farm_member(farm_id));

revoke insert, update, delete on public.poultry_houses, public.poultry_hatches, public.sheep_flystrike_scores,
    public.rabbit_mating_outcomes, public.goat_scc_records from authenticated;
grant select on public.poultry_houses, public.poultry_hatches, public.sheep_flystrike_scores,
    public.rabbit_mating_outcomes, public.goat_scc_records to authenticated;

create or replace function public.poultry_house_create_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_code text; v_kind text; v_pk text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'houseId')::uuid;
    v_code := btrim(coalesce(p_payload->>'code',''));
    v_kind := coalesce(p_payload->>'kind','');
    v_pk := coalesce(p_payload->>'poultryKindCode','');
    if v_code = '' or v_kind not in ('house','coop','range','hatchery','brooder','pond','loft')
        or v_pk not in ('chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','House needs a code, listed housing kind, and poultry kind');
    end if;
    insert into public.poultry_houses(id, farm_id, code, kind, poultry_kind_code)
    values (v_id, p_farm_id, v_code, v_kind, v_pk);
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'poultry.house_created.v1', 'poultry.house_create.v1', 'poultry_house', v_id,
        'poultry_house:' || v_id::text, 1, p_payload, null, null
    );
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','House code already exists');
end;
$$;

create or replace function public.poultry_hatch_set_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_kind text; v_eggs integer; v_days integer; v_set date;
    v_house uuid; v_group uuid; v_candle date; v_hatch date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'hatchId')::uuid;
    v_kind := coalesce(p_payload->>'poultryKindCode','');
    v_eggs := (p_payload->>'eggsSet')::integer;
    v_days := public.poultry_incubation_days(v_kind, (p_payload->>'incubationDays')::integer);
    v_set := date '1970-01-01' + ((p_payload->>'setEpochDay')::integer);
    v_house := nullif(p_payload->>'houseId','')::uuid;
    v_group := nullif(p_payload->>'groupId','')::uuid;
    if v_eggs is null or v_eggs <= 0 or v_days is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Hatch set needs a poultry kind and an egg count');
    end if;
    if v_house is not null and not exists (select 1 from public.poultry_houses where farm_id = p_farm_id and id = v_house) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','House not found');
    end if;
    if v_group is not null and not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'poultry') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Hatch set needs a poultry group');
    end if;
    insert into public.poultry_hatches(id, farm_id, poultry_kind_code, house_id, group_id, eggs_set, incubation_days, set_on)
    values (v_id, p_farm_id, v_kind, v_house, v_group, v_eggs, v_days, v_set);
    v_candle := v_set + public.poultry_candling_lead_days(v_kind);
    v_hatch := v_set + v_days;
    perform public.insert_lifecycle_task(
        p_farm_id, coalesce((p_payload->>'candleTaskId')::uuid, gen_random_uuid()),
        'poultry', 'CANDLING', 'Candling', v_candle, null, v_group
    );
    perform public.insert_lifecycle_task(
        p_farm_id, coalesce((p_payload->>'lockTaskId')::uuid, gen_random_uuid()),
        'poultry', 'LOCKDOWN', 'Transfer / lock-down', v_hatch - 3, null, v_group
    );
    perform public.insert_lifecycle_task(
        p_farm_id, coalesce((p_payload->>'hatchTaskId')::uuid, gen_random_uuid()),
        'poultry', 'EXPECTED_HATCH', 'Expected hatch', v_hatch, null, v_group
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'poultry.eggs_set.v1', 'poultry.hatch_set.v1', 'poultry_hatch', v_id,
        'poultry_hatch:' || v_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.poultry_hatch_candle_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_row public.poultry_hatches; v_fertile integer; v_infertile integer; v_mid integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'hatchId')::uuid;
    v_fertile := coalesce((p_payload->>'fertile')::integer, -1);
    v_infertile := coalesce((p_payload->>'infertile')::integer, -1);
    v_mid := coalesce((p_payload->>'midDead')::integer, -1);
    select * into v_row from public.poultry_hatches where farm_id = p_farm_id and id = v_id;
    if not found or v_row.status != 'set' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Candling needs a set hatch');
    end if;
    if v_fertile < 0 or v_infertile < 0 or v_mid < 0 or (v_fertile + v_infertile + v_mid) != v_row.eggs_set then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Candling counts must add up to eggs set');
    end if;
    update public.poultry_hatches
        set status = 'candled', fertile = v_fertile, infertile = v_infertile, mid_dead = v_mid
        where farm_id = p_farm_id and id = v_id;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'poultry.hatch_candled.v1', 'poultry.hatch_candle.v1', 'poultry_hatch', v_id,
        'poultry_hatch:' || v_id::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'poultry_hatch:' || v_id::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.poultry_hatch_record_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_row public.poultry_hatches; v_hatched integer; v_culls integer; v_place uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'hatchId')::uuid;
    v_hatched := coalesce((p_payload->>'hatched')::integer, -1);
    v_culls := coalesce((p_payload->>'culls')::integer, 0);
    v_place := nullif(p_payload->>'placementGroupId','')::uuid;
    select * into v_row from public.poultry_hatches where farm_id = p_farm_id and id = v_id;
    if not found or v_row.status != 'candled' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Hatch record needs a candled hatch');
    end if;
    if v_hatched < 0 or v_culls < 0 or (v_hatched + v_culls) > coalesce(v_row.fertile, v_row.eggs_set) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Hatched and cull counts cannot exceed fertile eggs');
    end if;
    if v_place is not null and not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_place and species_code = 'poultry') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Placement needs a poultry group');
    end if;
    update public.poultry_hatches
        set status = 'hatched', hatched = v_hatched, culls = v_culls, placement_group_id = v_place
        where farm_id = p_farm_id and id = v_id;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'poultry.hatch_recorded.v1', 'poultry.hatch_record.v1', 'poultry_hatch', v_id,
        'poultry_hatch:' || v_id::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'poultry_hatch:' || v_id::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.sheep_record_flystrike_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_score integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'scoreId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_score := (p_payload->>'score')::integer;
    if v_score is null or v_score < 0 or v_score > 5 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Flystrike score must be 0 to 5');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'sheep') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Flystrike needs a sheep');
    end if;
    insert into public.sheep_flystrike_scores(id, farm_id, animal_id, score, region, occurred_on)
    values (v_id, p_farm_id, v_animal, v_score, nullif(btrim(coalesce(p_payload->>'region','')),''), date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.flystrike_recorded.v1', 'sheep.record_flystrike.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.rabbit_record_mating_outcome_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_wave uuid; v_outcome text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'outcomeId')::uuid;
    v_wave := (p_payload->>'waveId')::uuid;
    v_outcome := coalesce(p_payload->>'outcome','');
    if v_outcome not in ('false_pregnancy','open','pregnant','kindled') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Mating outcome must be false_pregnancy, open, pregnant, or kindled');
    end if;
    if not exists (select 1 from public.rabbit_breeding_waves where farm_id = p_farm_id and id = v_wave) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Wave not found');
    end if;
    insert into public.rabbit_mating_outcomes(id, farm_id, wave_id, outcome, occurred_on)
    values (v_id, p_farm_id, v_wave, v_outcome, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.mating_outcome_recorded.v1', 'rabbit.record_mating_outcome.v1', 'rabbit_wave', v_wave,
        'rabbit_wave:' || v_wave::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'rabbit_wave:' || v_wave::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.goat_record_scc_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_cells integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'recordId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_cells := (p_payload->>'cellsPerMl')::integer;
    if v_cells is null or v_cells <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','SCC needs cells per millilitre');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'goat') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','SCC needs a goat');
    end if;
    insert into public.goat_scc_records(id, farm_id, animal_id, cells_per_ml, dim_days, occurred_on)
    values (v_id, p_farm_id, v_animal, v_cells, (p_payload->>'dimDays')::integer, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.scc_recorded.v1', 'goat.record_scc.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
end;
$$;

grant execute on function public.poultry_house_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_hatch_set_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_hatch_candle_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_hatch_record_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_flystrike_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_record_mating_outcome_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.goat_record_scc_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
