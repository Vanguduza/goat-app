-- Species-native lifecycle events, rabbit palpation/kindling/foster,
-- procurement purchases, and treatment withdrawal windows.
-- Writes stay on versioned RPCs.

create table if not exists public.sheep_joinings (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid not null,
    started_on date not null,
    ended_on date,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, group_id) references public.animal_groups(farm_id, id)
);

create table if not exists public.sheep_scans (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    result text not null check (result in ('dry','single','twin','triplet')),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.sheep_lambings (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    dam_id uuid not null,
    born_count integer not null check (born_count > 0),
    live_count integer not null check (live_count >= 0),
    dead_count integer not null check (dead_count >= 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (live_count + dead_count = born_count)
);

create table if not exists public.cattle_services (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    method text not null check (method in ('ai','natural','et')),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.cattle_pd (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    result text not null check (result in ('pregnant','open')),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.cattle_calvings (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    dam_id uuid not null,
    born_count integer not null check (born_count > 0),
    live_count integer not null check (live_count >= 0),
    dead_count integer not null check (dead_count >= 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (live_count + dead_count = born_count)
);

create table if not exists public.rabbit_palpations (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    wave_id uuid not null,
    result text not null check (result in ('pregnant','open')),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.rabbit_kindlings (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    wave_id uuid not null,
    live_count integer not null check (live_count >= 0),
    dead_count integer not null check (dead_count >= 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.rabbit_fosters (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    from_wave_id uuid not null,
    to_wave_id uuid not null,
    kit_count integer not null check (kit_count > 0),
    within_window boolean not null,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.suppliers (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    name text not null,
    lead_time_days integer not null default 0 check (lead_time_days >= 0),
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, name)
);

create table if not exists public.purchases (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    supplier_id uuid not null,
    item_id uuid not null,
    quantity_milli bigint not null check (quantity_milli > 0),
    amount_minor bigint not null check (amount_minor > 0),
    currency text not null default 'USD',
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, supplier_id) references public.suppliers(farm_id, id),
    foreign key (farm_id, item_id) references public.inventory_items(farm_id, id)
);

create table if not exists public.withdrawal_windows (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    treatment_id uuid not null,
    product text not null,
    window_kind text not null check (window_kind in ('meat','milk','egg')),
    ends_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, treatment_id) references public.health_treatments(farm_id, id)
);

create table if not exists public.goat_milk_records (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    litres_milli bigint not null check (litres_milli > 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.health_protocol_packs (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    species_code text not null,
    name text not null,
    status text not null check (status in ('draft','vet_accepted','retired')),
    accepted_by_vet text,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

alter table public.sheep_joinings enable row level security;
alter table public.sheep_scans enable row level security;
alter table public.sheep_lambings enable row level security;
alter table public.cattle_services enable row level security;
alter table public.cattle_pd enable row level security;
alter table public.cattle_calvings enable row level security;
alter table public.rabbit_palpations enable row level security;
alter table public.rabbit_kindlings enable row level security;
alter table public.rabbit_fosters enable row level security;
alter table public.suppliers enable row level security;
alter table public.purchases enable row level security;
alter table public.withdrawal_windows enable row level security;
alter table public.goat_milk_records enable row level security;
alter table public.health_protocol_packs enable row level security;

create policy sheep_joinings_sel on public.sheep_joinings for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_scans_sel on public.sheep_scans for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_lambings_sel on public.sheep_lambings for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_services_sel on public.cattle_services for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_pd_sel on public.cattle_pd for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_calvings_sel on public.cattle_calvings for select to authenticated using (public.is_farm_member(farm_id));
create policy rabbit_palpations_sel on public.rabbit_palpations for select to authenticated using (public.is_farm_member(farm_id));
create policy rabbit_kindlings_sel on public.rabbit_kindlings for select to authenticated using (public.is_farm_member(farm_id));
create policy rabbit_fosters_sel on public.rabbit_fosters for select to authenticated using (public.is_farm_member(farm_id));
create policy suppliers_sel on public.suppliers for select to authenticated using (public.is_farm_member(farm_id));
create policy purchases_sel on public.purchases for select to authenticated using (public.is_farm_member(farm_id));
create policy withdrawals_sel on public.withdrawal_windows for select to authenticated using (public.is_farm_member(farm_id));
create policy goat_milk_sel on public.goat_milk_records for select to authenticated using (public.is_farm_member(farm_id));
create policy packs_sel on public.health_protocol_packs for select to authenticated using (public.is_farm_member(farm_id));

revoke insert, update, delete on public.sheep_joinings, public.sheep_scans, public.sheep_lambings,
    public.cattle_services, public.cattle_pd, public.cattle_calvings, public.rabbit_palpations,
    public.rabbit_kindlings, public.rabbit_fosters, public.suppliers, public.purchases,
    public.withdrawal_windows, public.goat_milk_records, public.health_protocol_packs from authenticated;
grant select on public.sheep_joinings, public.sheep_scans, public.sheep_lambings, public.cattle_services,
    public.cattle_pd, public.cattle_calvings, public.rabbit_palpations, public.rabbit_kindlings,
    public.rabbit_fosters, public.suppliers, public.purchases, public.withdrawal_windows,
    public.goat_milk_records, public.health_protocol_packs to authenticated;

create or replace function public.insert_lifecycle_task(
    p_farm_id uuid, p_id uuid, p_module text, p_code text, p_title text, p_due date, p_animal uuid, p_group uuid
) returns void language plpgsql as $$
begin
    insert into public.farm_tasks(id, farm_id, module_code, task_code, title, due_on, animal_id)
    values (p_id, p_farm_id, p_module, p_code, p_title, p_due, p_animal);
end;
$$;

create or replace function public.sheep_record_joining_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid; v_start date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'joiningId')::uuid;
    v_group := (p_payload->>'groupId')::uuid;
    v_start := date '1970-01-01' + ((p_payload->>'startedEpochDay')::integer);
    if not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'sheep') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Joining needs a sheep mob');
    end if;
    insert into public.sheep_joinings(id, farm_id, group_id, started_on) values (v_id, p_farm_id, v_group, v_start);
    perform public.insert_lifecycle_task(p_farm_id, coalesce((p_payload->>'scanTaskId')::uuid, gen_random_uuid()), 'sheep', 'SCAN', 'Pregnancy scanning', v_start + 70, null, v_group);
    perform public.insert_lifecycle_task(p_farm_id, coalesce((p_payload->>'preLambTaskId')::uuid, gen_random_uuid()), 'sheep', 'PRE_LAMB', 'Pre-lambing vaccination / nutrition', v_start + 140, null, v_group);
    perform public.insert_lifecycle_task(p_farm_id, coalesce((p_payload->>'paddockTaskId')::uuid, gen_random_uuid()), 'sheep', 'LAMBING_PADDOCK', 'Lambing paddock set-up', v_start + 140, null, v_group);
    perform public.insert_lifecycle_task(p_farm_id, coalesce((p_payload->>'lambingTaskId')::uuid, gen_random_uuid()), 'sheep', 'EXPECTED_LAMBING', 'Expected lambing start', v_start + 147, null, v_group);
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.joining_recorded.v1', 'sheep.record_joining.v1', 'animal_group', v_group, 'animal_group:' || v_group::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal_group:' || v_group::text), p_payload, null, null);
end;
$$;

create or replace function public.sheep_record_scan_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_result text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'scanId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_result := p_payload->>'result';
    if v_result not in ('dry','single','twin','triplet') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Scan result must be dry, single, twin, or triplet');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'sheep') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Ewe not found');
    end if;
    insert into public.sheep_scans(id, farm_id, animal_id, result, occurred_on)
    values (v_id, p_farm_id, v_animal, v_result, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.scanned.v1', 'sheep.record_scan.v1', 'animal', v_animal, 'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text), p_payload, 'animal', v_animal);
end;
$$;

create or replace function public.sheep_record_lambing_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_dam uuid; v_born int; v_live int; v_dead int;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'lambingId')::uuid;
    v_dam := (p_payload->>'damAnimalId')::uuid;
    v_born := (p_payload->>'bornCount')::integer;
    v_live := (p_payload->>'liveCount')::integer;
    v_dead := (p_payload->>'deadCount')::integer;
    if v_born is null or v_born <= 0 or v_live is null or v_dead is null or v_live + v_dead <> v_born then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lambing counts must add up and be greater than zero');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_dam and species_code = 'sheep' and sex = 'FEMALE' and status = 'active') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Active ewe not found');
    end if;
    insert into public.sheep_lambings(id, farm_id, dam_id, born_count, live_count, dead_count, occurred_on)
    values (v_id, p_farm_id, v_dam, v_born, v_live, v_dead, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.lambed.v1', 'sheep.record_lambing.v1', 'animal', v_dam, 'animal:' || v_dam::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_dam::text), p_payload, 'animal', v_dam);
end;
$$;

create or replace function public.cattle_record_service_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_method text; v_day date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'serviceId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_method := coalesce(p_payload->>'method','ai');
    v_day := date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer);
    if v_method not in ('ai','natural','et') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cattle service must be ai, natural, or et');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'cattle' and status = 'active') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Active cow not found');
    end if;
    insert into public.cattle_services(id, farm_id, animal_id, method, occurred_on) values (v_id, p_farm_id, v_animal, v_method, v_day);
    perform public.insert_lifecycle_task(p_farm_id, coalesce((p_payload->>'pdTaskId')::uuid, gen_random_uuid()), 'cattle', 'PD', 'Pregnancy diagnosis (PD)', v_day + 32, v_animal, null);
    perform public.insert_lifecycle_task(p_farm_id, coalesce((p_payload->>'paddockTaskId')::uuid, gen_random_uuid()), 'cattle', 'CALVING_PADDOCK', 'Calving paddock / close-up pen', v_day + 259, v_animal, null);
    perform public.insert_lifecycle_task(p_farm_id, coalesce((p_payload->>'calvingTaskId')::uuid, gen_random_uuid()), 'cattle', 'EXPECTED_CALVING', 'Expected calving', v_day + 280, v_animal, null);
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.service_recorded.v1', 'cattle.record_service.v1', 'animal', v_animal, 'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text), p_payload, 'animal', v_animal);
end;
$$;

create or replace function public.cattle_record_pd_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_result text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'pdId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_result := p_payload->>'result';
    if v_result not in ('pregnant','open') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','PD result must be pregnant or open');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'cattle') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cow not found');
    end if;
    insert into public.cattle_pd(id, farm_id, animal_id, result, occurred_on)
    values (v_id, p_farm_id, v_animal, v_result, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.pd_recorded.v1', 'cattle.record_pd.v1', 'animal', v_animal, 'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text), p_payload, 'animal', v_animal);
end;
$$;

create or replace function public.cattle_record_calving_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_dam uuid; v_born int; v_live int; v_dead int;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'calvingId')::uuid;
    v_dam := (p_payload->>'damAnimalId')::uuid;
    v_born := (p_payload->>'bornCount')::integer;
    v_live := (p_payload->>'liveCount')::integer;
    v_dead := (p_payload->>'deadCount')::integer;
    if v_born is null or v_born <= 0 or v_live + v_dead <> v_born then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Calving counts must add up and be greater than zero');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_dam and species_code = 'cattle' and sex = 'FEMALE' and status = 'active') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Active cow not found');
    end if;
    insert into public.cattle_calvings(id, farm_id, dam_id, born_count, live_count, dead_count, occurred_on)
    values (v_id, p_farm_id, v_dam, v_born, v_live, v_dead, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.calved.v1', 'cattle.record_calving.v1', 'animal', v_dam, 'animal:' || v_dam::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_dam::text), p_payload, 'animal', v_dam);
end;
$$;

create or replace function public.rabbit_record_palpation_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_wave uuid; v_result text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'palpationId')::uuid;
    v_wave := (p_payload->>'waveId')::uuid;
    v_result := p_payload->>'result';
    if v_result not in ('pregnant','open') or not exists (select 1 from public.rabbit_breeding_waves where farm_id = p_farm_id and id = v_wave) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Palpation needs a wave and pregnant or open');
    end if;
    insert into public.rabbit_palpations(id, farm_id, wave_id, result, occurred_on)
    values (v_id, p_farm_id, v_wave, v_result, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.palpated.v1', 'rabbit.record_palpation.v1', 'rabbit_wave', v_wave, 'rabbit_wave:' || v_wave::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'rabbit_wave:' || v_wave::text), p_payload, null, null);
end;
$$;

create or replace function public.rabbit_record_kindling_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_wave uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'kindlingId')::uuid;
    v_wave := (p_payload->>'waveId')::uuid;
    if (p_payload->>'liveCount')::integer is null or (p_payload->>'deadCount')::integer is null or not exists (select 1 from public.rabbit_breeding_waves where farm_id = p_farm_id and id = v_wave) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Kindling needs a wave and live/dead counts');
    end if;
    insert into public.rabbit_kindlings(id, farm_id, wave_id, live_count, dead_count, occurred_on)
    values (v_id, p_farm_id, v_wave, (p_payload->>'liveCount')::integer, (p_payload->>'deadCount')::integer, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.kindled.v1', 'rabbit.record_kindling.v1', 'rabbit_wave', v_wave, 'rabbit_wave:' || v_wave::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'rabbit_wave:' || v_wave::text), p_payload, null, null);
end;
$$;

create or replace function public.rabbit_record_foster_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_from uuid; v_to uuid; v_from_k date; v_to_k date; v_on date; v_window boolean;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'fosterId')::uuid;
    v_from := (p_payload->>'fromWaveId')::uuid;
    v_to := (p_payload->>'toWaveId')::uuid;
    v_on := date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer);
    if (p_payload->>'kitCount')::integer is null or (p_payload->>'kitCount')::integer <= 0 or v_from = v_to then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Foster needs two waves and a kit count');
    end if;
    select kindling_on into v_from_k from public.rabbit_breeding_waves where farm_id = p_farm_id and id = v_from;
    select kindling_on into v_to_k from public.rabbit_breeding_waves where farm_id = p_farm_id and id = v_to;
    if v_from_k is null or v_to_k is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Foster waves not found');
    end if;
    v_window := (abs(v_on - v_from_k) <= 3) or (abs(v_on - v_to_k) <= 3);
    if not v_window and coalesce((p_payload->>'ackOutsideWindow')::boolean, false) is not true then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Foster after 3 days from kindling needs an explicit acknowledgement');
    end if;
    insert into public.rabbit_fosters(id, farm_id, from_wave_id, to_wave_id, kit_count, within_window, occurred_on)
    values (v_id, p_farm_id, v_from, v_to, (p_payload->>'kitCount')::integer, v_window, v_on);
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.fostered.v1', 'rabbit.record_foster.v1', 'rabbit_wave', v_to, 'rabbit_wave:' || v_to::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'rabbit_wave:' || v_to::text),
        p_payload || jsonb_build_object('withinWindow', v_window), null, null);
end;
$$;

create or replace function public.supplier_create_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'supplierId')::uuid;
    if btrim(coalesce(p_payload->>'name','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Supplier name is required');
    end if;
    insert into public.suppliers(id, farm_id, name, lead_time_days)
    values (v_id, p_farm_id, btrim(p_payload->>'name'), coalesce((p_payload->>'leadTimeDays')::integer, 0));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'supplier.created.v1', 'supplier.create.v1', 'supplier', v_id, 'supplier:' || v_id::text, 1, p_payload, null, null);
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','Supplier name already exists');
end;
$$;

create or replace function public.purchase_record_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_supplier uuid; v_item uuid; v_qty bigint; v_amount bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'purchaseId')::uuid;
    v_supplier := (p_payload->>'supplierId')::uuid;
    v_item := (p_payload->>'itemId')::uuid;
    v_qty := (p_payload->>'quantityMilli')::bigint;
    v_amount := (p_payload->>'amountMinor')::bigint;
    if v_qty is null or v_qty <= 0 or v_amount is null or v_amount <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Purchase needs a quantity and amount');
    end if;
    if not exists (select 1 from public.suppliers where farm_id = p_farm_id and id = v_supplier) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Supplier not found');
    end if;
    if not exists (select 1 from public.inventory_items where farm_id = p_farm_id and id = v_item) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Inventory item not found');
    end if;
    insert into public.purchases(id, farm_id, supplier_id, item_id, quantity_milli, amount_minor, currency, occurred_on)
    values (v_id, p_farm_id, v_supplier, v_item, v_qty, v_amount, coalesce(nullif(btrim(p_payload->>'currency'),''),'USD'),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    insert into public.inventory_movements(id, farm_id, item_id, direction, quantity_milli, occurred_at)
    values (v_id, p_farm_id, v_item, 'receive', v_qty, to_timestamp(p_occurred_at_epoch_ms / 1000.0));
    update public.inventory_items set quantity_milli = quantity_milli + v_qty, updated_at = now() where farm_id = p_farm_id and id = v_item;
    insert into public.money_records(id, farm_id, kind, category_code, amount_minor, currency, occurred_on, note)
    values (v_id, p_farm_id, 'expense', 'purchase', v_amount, coalesce(nullif(btrim(p_payload->>'currency'),''),'USD'),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer), 'purchase');
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'purchase.recorded.v1', 'purchase.record.v1', 'inventory_item', v_item, 'inventory_item:' || v_item::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'inventory_item:' || v_item::text), p_payload, null, null);
end;
$$;

create or replace function public.goat_record_milk_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'milkId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    if (p_payload->>'litresMilli')::bigint is null or (p_payload->>'litresMilli')::bigint <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Milk record needs litres');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'goat' and sex = 'FEMALE' and status = 'active') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Active doe not found');
    end if;
    insert into public.goat_milk_records(id, farm_id, animal_id, litres_milli, occurred_on)
    values (v_id, p_farm_id, v_animal, (p_payload->>'litresMilli')::bigint, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.milk_recorded.v1', 'goat.record_milk.v1', 'animal', v_animal, 'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text), p_payload, 'animal', v_animal);
end;
$$;

create or replace function public.health_pack_accept_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'packId')::uuid;
    if btrim(coalesce(p_payload->>'name','')) = '' or btrim(coalesce(p_payload->>'speciesCode','')) = '' or btrim(coalesce(p_payload->>'acceptedByVet','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','A protocol pack needs a species, name, and attending vet');
    end if;
    insert into public.health_protocol_packs(id, farm_id, species_code, name, status, accepted_by_vet)
    values (v_id, p_farm_id, btrim(p_payload->>'speciesCode'), btrim(p_payload->>'name'), 'vet_accepted', btrim(p_payload->>'acceptedByVet'));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'health.pack_accepted.v1', 'health.pack_accept.v1', 'health_protocol_pack', v_id, 'health_protocol_pack:' || v_id::text, 1, p_payload, null, null);
end;
$$;

create or replace function public.health_record_treatment_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_form uuid; v_meat integer; v_milk integer; v_egg integer; v_name text; v_on date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    if p_payload ? 'productName' or p_payload ? 'dose' or p_payload ? 'medication' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Treatments must use a vet-approved formulary item, not a free-typed product or dose');
    end if;
    v_id := (p_payload->>'treatmentId')::uuid;
    v_form := (p_payload->>'formularyItemId')::uuid;
    select meat_withdrawal_days, milk_withdrawal_days, egg_withdrawal_days, product_name
    into v_meat, v_milk, v_egg, v_name
    from public.formulary_items
    where farm_id = p_farm_id and id = v_form and vet_approved;
    if not found then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Treatment needs a vet-approved formulary item');
    end if;
    if btrim(coalesce(p_payload->>'reason','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Treatment reason is required');
    end if;
    insert into public.health_treatments(id, farm_id, animal_id, species_code, formulary_item_id, reason, meat_withdrawal_days, milk_withdrawal_days, egg_withdrawal_days, occurred_at)
    values (v_id, p_farm_id, nullif(p_payload->>'animalId','')::uuid, btrim(p_payload->>'speciesCode'), v_form, btrim(p_payload->>'reason'),
        v_meat, v_milk, v_egg, to_timestamp((p_payload->>'occurredAtEpochMillis')::bigint / 1000.0));
    v_on := (to_timestamp((p_payload->>'occurredAtEpochMillis')::bigint / 1000.0))::date;
    if v_meat is not null then
        insert into public.withdrawal_windows(id, farm_id, treatment_id, product, window_kind, ends_on)
        values (gen_random_uuid(), p_farm_id, v_id, v_name, 'meat', v_on + v_meat);
    end if;
    if v_milk is not null then
        insert into public.withdrawal_windows(id, farm_id, treatment_id, product, window_kind, ends_on)
        values (gen_random_uuid(), p_farm_id, v_id, v_name, 'milk', v_on + v_milk);
    end if;
    if v_egg is not null then
        insert into public.withdrawal_windows(id, farm_id, treatment_id, product, window_kind, ends_on)
        values (gen_random_uuid(), p_farm_id, v_id, v_name, 'egg', v_on + v_egg);
    end if;
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'health.treatment_recorded.v1', 'health.record_treatment.v1', 'health_treatment', v_id, 'health_treatment:' || v_id::text, 1, p_payload, null, null);
end;
$$;

grant execute on function public.sheep_record_joining_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_scan_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_lambing_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_service_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_pd_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_calving_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_record_palpation_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_record_kindling_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_record_foster_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.supplier_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.purchase_record_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.goat_record_milk_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.health_pack_accept_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.health_record_treatment_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
