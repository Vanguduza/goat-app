-- Goat heat/mating/pregnancy check, sheep FAMACHA, official identifiers and
-- cattle/sheep movements, FEFO inventory lots, vet visits, lab results,
-- cattle weaning, wool micron, poultry-kind enable, rabbit GI-stasis flag,
-- and record-only pedigree links (no COI engine).
-- Writes stay on versioned RPCs. Quantities stay integer milli-units.

create table if not exists public.goat_heats (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.goat_matings (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    dam_id uuid not null,
    sire_id uuid,
    method text not null check (method in ('natural','ai','hand_mating')),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.goat_pregnancy_checks (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    result text not null check (result in ('pregnant','open')),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.animal_identifiers (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    type text not null check (type in (
        'farm_id','official_id','rfid','eid','ear_tag','tattoo','registration','name',
        'wing_band','leg_band','nlis','nait','freeze_brand','herd_book'
    )),
    value text not null,
    is_active boolean not null default true,
    assigned_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, animal_id, type, value)
);

create unique index if not exists animal_identifiers_active_value
    on public.animal_identifiers (farm_id, value) where is_active;

create table if not exists public.official_movements (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    species_code text not null check (species_code in ('cattle','sheep')),
    direction text not null check (direction in ('on','off','transfer')),
    from_place text,
    to_place text,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.inventory_lots (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    item_id uuid not null,
    lot_code text not null,
    expires_on date not null,
    quantity_milli bigint not null check (quantity_milli >= 0),
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, item_id, lot_code),
    foreign key (farm_id, item_id) references public.inventory_items(farm_id, id)
);

create table if not exists public.vet_visits (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    species_code text not null check (species_code in ('goat','rabbit','poultry','sheep','cattle')),
    animal_id uuid,
    group_id uuid,
    reason text not null,
    attending_vet text not null,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.lab_results (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid,
    group_id uuid,
    test_name text not null,
    result_text text not null,
    cells_per_ml integer,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (animal_id is not null or group_id is not null)
);

create table if not exists public.cattle_weanings (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid,
    group_id uuid,
    weight_grams bigint,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (animal_id is not null or group_id is not null)
);

create table if not exists public.sheep_micron_tests (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid,
    group_id uuid,
    micron_tenths integer not null check (micron_tenths between 80 and 500),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (animal_id is not null or group_id is not null)
);

create table if not exists public.farm_enabled_poultry_kinds (
    farm_id uuid not null references public.farms(id),
    poultry_kind_code text not null check (poultry_kind_code in (
        'chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined'
    )),
    created_at timestamptz not null default now(),
    primary key (farm_id, poultry_kind_code)
);

create table if not exists public.rabbit_gi_stasis_flags (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    signs text not null,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.pedigree_relations (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    parent_id uuid not null,
    relation_type text not null check (relation_type in ('sire','dam','genetic_dam','recipient_dam')),
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, animal_id, relation_type),
    check (animal_id <> parent_id)
);

alter table public.goat_heats enable row level security;
alter table public.goat_matings enable row level security;
alter table public.goat_pregnancy_checks enable row level security;
alter table public.animal_identifiers enable row level security;
alter table public.official_movements enable row level security;
alter table public.inventory_lots enable row level security;
alter table public.vet_visits enable row level security;
alter table public.lab_results enable row level security;
alter table public.cattle_weanings enable row level security;
alter table public.sheep_micron_tests enable row level security;
alter table public.farm_enabled_poultry_kinds enable row level security;
alter table public.rabbit_gi_stasis_flags enable row level security;
alter table public.pedigree_relations enable row level security;

create policy goat_heats_sel on public.goat_heats for select to authenticated using (public.is_farm_member(farm_id));
create policy goat_matings_sel on public.goat_matings for select to authenticated using (public.is_farm_member(farm_id));
create policy goat_preg_sel on public.goat_pregnancy_checks for select to authenticated using (public.is_farm_member(farm_id));
create policy animal_ids_sel on public.animal_identifiers for select to authenticated using (public.is_farm_member(farm_id));
create policy official_move_sel on public.official_movements for select to authenticated using (public.is_farm_member(farm_id));
create policy inv_lots_sel on public.inventory_lots for select to authenticated using (public.is_farm_member(farm_id));
create policy vet_visits_sel on public.vet_visits for select to authenticated using (public.is_farm_member(farm_id));
create policy lab_results_sel on public.lab_results for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_wean_sel on public.cattle_weanings for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_micron_sel on public.sheep_micron_tests for select to authenticated using (public.is_farm_member(farm_id));
create policy poultry_kinds_sel on public.farm_enabled_poultry_kinds for select to authenticated using (public.is_farm_member(farm_id));
create policy rabbit_gi_sel on public.rabbit_gi_stasis_flags for select to authenticated using (public.is_farm_member(farm_id));
create policy pedigree_sel on public.pedigree_relations for select to authenticated using (public.is_farm_member(farm_id));

revoke insert, update, delete on public.goat_heats, public.goat_matings, public.goat_pregnancy_checks,
    public.animal_identifiers, public.official_movements, public.inventory_lots, public.vet_visits,
    public.lab_results, public.cattle_weanings, public.sheep_micron_tests, public.farm_enabled_poultry_kinds,
    public.rabbit_gi_stasis_flags, public.pedigree_relations from authenticated;
grant select on public.goat_heats, public.goat_matings, public.goat_pregnancy_checks,
    public.animal_identifiers, public.official_movements, public.inventory_lots, public.vet_visits,
    public.lab_results, public.cattle_weanings, public.sheep_micron_tests, public.farm_enabled_poultry_kinds,
    public.rabbit_gi_stasis_flags, public.pedigree_relations to authenticated;

create or replace function public.goat_record_heat_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'heatId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'goat' and sex = 'FEMALE') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Heat needs a doe');
    end if;
    insert into public.goat_heats(id, farm_id, animal_id, occurred_on)
    values (v_id, p_farm_id, v_animal, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.heat_recorded.v1', 'goat.record_heat.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.goat_record_mating_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_dam uuid; v_sire uuid; v_method text; v_on date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'matingId')::uuid;
    v_dam := (p_payload->>'damId')::uuid;
    v_sire := nullif(p_payload->>'sireId','')::uuid;
    v_method := coalesce(p_payload->>'method','');
    v_on := date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer);
    if v_method not in ('natural','ai','hand_mating') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Mating method must be natural, ai, or hand_mating');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_dam and species_code = 'goat' and sex = 'FEMALE') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Mating needs a doe');
    end if;
    if v_sire is not null and not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_sire and species_code = 'goat') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Sire not found on this farm');
    end if;
    insert into public.goat_matings(id, farm_id, dam_id, sire_id, method, occurred_on)
    values (v_id, p_farm_id, v_dam, v_sire, v_method, v_on);
    perform public.insert_lifecycle_task(
        p_farm_id, coalesce((p_payload->>'pregCheckTaskId')::uuid, gen_random_uuid()),
        'goat', 'PREG_CHECK', 'Pregnancy check', v_on + 45, v_dam, null
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.mating_recorded.v1', 'goat.record_mating.v1', 'animal', v_dam,
        'animal:' || v_dam::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_dam::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.goat_record_pregnancy_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_result text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'checkId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_result := coalesce(p_payload->>'result','');
    if v_result not in ('pregnant','open') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Pregnancy check must be pregnant or open');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'goat') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Pregnancy check needs a goat');
    end if;
    insert into public.goat_pregnancy_checks(id, farm_id, animal_id, result, occurred_on)
    values (v_id, p_farm_id, v_animal, v_result, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.pregnancy_checked.v1', 'goat.record_pregnancy.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.sheep_record_famacha_v1(
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
    if v_score is null or v_score < 1 or v_score > 5 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','FAMACHA score must be 1 to 5');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'sheep') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Sheep FAMACHA needs a sheep');
    end if;
    insert into public.famacha_scores(id, farm_id, animal_id, score, occurred_on)
    values (v_id, p_farm_id, v_animal, v_score, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.famacha_recorded.v1', 'sheep.record_famacha.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.animal_identifier_assign_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_type text; v_value text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'identifierId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_type := coalesce(p_payload->>'type','');
    v_value := btrim(coalesce(p_payload->>'value',''));
    if v_value = '' or v_type not in (
        'farm_id','official_id','rfid','eid','ear_tag','tattoo','registration','name',
        'wing_band','leg_band','nlis','nait','freeze_brand','herd_book'
    ) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Identifier needs a listed type and a value');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Animal not found');
    end if;
    insert into public.animal_identifiers(id, farm_id, animal_id, type, value, assigned_on)
    values (v_id, p_farm_id, v_animal, v_type, v_value, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'animal.identifier_assigned.v1', 'animal.identifier_assign.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','That identifier is already active on this farm');
end;
$$;

create or replace function public.official_record_movement_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_dir text; v_species text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'movementId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_dir := coalesce(p_payload->>'direction','');
    if v_dir not in ('on','off','transfer') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Movement must be on, off, or transfer');
    end if;
    select species_code into v_species from public.animals where farm_id = p_farm_id and id = v_animal;
    if v_species is null or v_species not in ('cattle','sheep') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Official movement needs a cattle or sheep record');
    end if;
    insert into public.official_movements(id, farm_id, animal_id, species_code, direction, from_place, to_place, occurred_on)
    values (
        v_id, p_farm_id, v_animal, v_species, v_dir,
        nullif(btrim(coalesce(p_payload->>'fromPlace','')),''),
        nullif(btrim(coalesce(p_payload->>'toPlace','')),''),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer)
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'official.movement_recorded.v1', 'official.record_movement.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.inventory_lot_receive_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_item uuid; v_qty bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'lotId')::uuid;
    v_item := (p_payload->>'itemId')::uuid;
    v_qty := (p_payload->>'quantityMilli')::bigint;
    if v_qty is null or v_qty <= 0 or btrim(coalesce(p_payload->>'lotCode','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lot receive needs a lot code, expiry, and quantity');
    end if;
    if not exists (select 1 from public.inventory_items where farm_id = p_farm_id and id = v_item) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Inventory item not found');
    end if;
    insert into public.inventory_lots(id, farm_id, item_id, lot_code, expires_on, quantity_milli)
    values (v_id, p_farm_id, v_item, btrim(p_payload->>'lotCode'), date '1970-01-01' + ((p_payload->>'expiresEpochDay')::integer), v_qty);
    update public.inventory_items set quantity_milli = quantity_milli + v_qty, updated_at = now()
        where farm_id = p_farm_id and id = v_item;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'inventory.lot_received.v1', 'inventory.lot_receive.v1', 'inventory_item', v_item,
        'inventory_item:' || v_item::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'inventory_item:' || v_item::text),
        p_payload, null, null
    );
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','Lot code already exists for this item');
end;
$$;

create or replace function public.inventory_lot_issue_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_item uuid; v_need bigint; v_lot record; v_take bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_item := (p_payload->>'itemId')::uuid;
    v_need := (p_payload->>'quantityMilli')::bigint;
    if v_need is null or v_need <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lot issue needs a quantity');
    end if;
    if not exists (select 1 from public.inventory_items where farm_id = p_farm_id and id = v_item and quantity_milli >= v_need) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Not enough stock on this farm');
    end if;
    if coalesce((select sum(quantity_milli) from public.inventory_lots where farm_id = p_farm_id and item_id = v_item), 0) < v_need then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Not enough lot stock. Receive a dated lot first.');
    end if;
    for v_lot in
        select id, quantity_milli from public.inventory_lots
        where farm_id = p_farm_id and item_id = v_item and quantity_milli > 0
        order by expires_on, created_at
    loop
        v_take := least(v_lot.quantity_milli, v_need);
        update public.inventory_lots set quantity_milli = quantity_milli - v_take where farm_id = p_farm_id and id = v_lot.id;
        v_need := v_need - v_take;
        exit when v_need = 0;
    end loop;
    if v_need > 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Not enough lot stock. Receive a dated lot first.');
    end if;
    update public.inventory_items set quantity_milli = quantity_milli - (p_payload->>'quantityMilli')::bigint, updated_at = now()
        where farm_id = p_farm_id and id = v_item;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'inventory.lot_issued.v1', 'inventory.lot_issue.v1', 'inventory_item', v_item,
        'inventory_item:' || v_item::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'inventory_item:' || v_item::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.health_record_vet_visit_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_species text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'visitId')::uuid;
    v_species := coalesce(p_payload->>'speciesCode','');
    if v_species not in ('goat','rabbit','poultry','sheep','cattle')
        or btrim(coalesce(p_payload->>'reason','')) = ''
        or btrim(coalesce(p_payload->>'attendingVet','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Vet visit needs a species, reason, and attending vet');
    end if;
    insert into public.vet_visits(id, farm_id, species_code, animal_id, group_id, reason, attending_vet, occurred_on)
    values (
        v_id, p_farm_id, v_species, nullif(p_payload->>'animalId','')::uuid, nullif(p_payload->>'groupId','')::uuid,
        btrim(p_payload->>'reason'), btrim(p_payload->>'attendingVet'),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer)
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'health.vet_visit_recorded.v1', 'health.record_vet_visit.v1', 'vet_visit', v_id,
        'vet_visit:' || v_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.health_record_lab_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'resultId')::uuid;
    if btrim(coalesce(p_payload->>'testName','')) = '' or btrim(coalesce(p_payload->>'resultText','')) = ''
        or (nullif(p_payload->>'animalId','') is null and nullif(p_payload->>'groupId','') is null) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lab result needs a test, result, and an animal or group');
    end if;
    insert into public.lab_results(id, farm_id, animal_id, group_id, test_name, result_text, cells_per_ml, occurred_on)
    values (
        v_id, p_farm_id, nullif(p_payload->>'animalId','')::uuid, nullif(p_payload->>'groupId','')::uuid,
        btrim(p_payload->>'testName'), btrim(p_payload->>'resultText'), (p_payload->>'cellsPerMl')::integer,
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer)
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'health.lab_recorded.v1', 'health.record_lab.v1', 'lab_result', v_id,
        'lab_result:' || v_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.cattle_record_weaning_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_group uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'weaningId')::uuid;
    v_animal := nullif(p_payload->>'animalId','')::uuid;
    v_group := nullif(p_payload->>'groupId','')::uuid;
    if v_animal is null and v_group is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cattle weaning needs a calf or lot');
    end if;
    if v_animal is not null and not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'cattle') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cattle weaning needs a calf');
    end if;
    if v_group is not null and not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'cattle') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cattle weaning needs a cattle lot');
    end if;
    insert into public.cattle_weanings(id, farm_id, animal_id, group_id, weight_grams, occurred_on)
    values (v_id, p_farm_id, v_animal, v_group, (p_payload->>'weightGrams')::bigint, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.weaning_recorded.v1', 'cattle.record_weaning.v1', 'animal', coalesce(v_animal, v_group),
        'animal:' || coalesce(v_animal, v_group)::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || coalesce(v_animal, v_group)::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.sheep_record_micron_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_group uuid; v_micron integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'testId')::uuid;
    v_animal := nullif(p_payload->>'animalId','')::uuid;
    v_group := nullif(p_payload->>'groupId','')::uuid;
    v_micron := (p_payload->>'micronTenths')::integer;
    if v_micron is null or v_micron < 80 or v_micron > 500 or (v_animal is null and v_group is null) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Micron needs a sheep or mob and tenths from 80 to 500');
    end if;
    insert into public.sheep_micron_tests(id, farm_id, animal_id, group_id, micron_tenths, occurred_on)
    values (v_id, p_farm_id, v_animal, v_group, v_micron, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.micron_recorded.v1', 'sheep.record_micron.v1', 'animal', coalesce(v_animal, v_group),
        'animal:' || coalesce(v_animal, v_group)::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || coalesce(v_animal, v_group)::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.poultry_kind_enable_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_kind text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_kind := coalesce(p_payload->>'poultryKindCode','');
    if v_kind not in ('chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Enable needs a listed poultry kind');
    end if;
    insert into public.farm_enabled_poultry_kinds(farm_id, poultry_kind_code) values (p_farm_id, v_kind)
    on conflict (farm_id, poultry_kind_code) do nothing;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'poultry.kind_enabled.v1', 'poultry.kind_enable.v1', 'farm', p_farm_id,
        'farm:' || p_farm_id::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'farm:' || p_farm_id::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.rabbit_record_gi_stasis_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_signs text; v_on date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'flagId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_signs := btrim(coalesce(p_payload->>'signs',''));
    v_on := date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer);
    if v_signs = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','GI stasis flag needs signs');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'rabbit') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','GI stasis flag needs a rabbit');
    end if;
    insert into public.rabbit_gi_stasis_flags(id, farm_id, animal_id, signs, occurred_on)
    values (v_id, p_farm_id, v_animal, v_signs, v_on);
    insert into public.health_observations(id, farm_id, animal_id, species_code, signs, red_flag, occurred_at)
    values (v_id, p_farm_id, v_animal, 'rabbit', v_signs, true, v_on::timestamptz);
    perform public.insert_lifecycle_task(
        p_farm_id, coalesce((p_payload->>'taskId')::uuid, gen_random_uuid()),
        'rabbit', 'GI_STASIS', 'GI stasis red flag. Call the vet.', v_on, v_animal, null
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.gi_stasis_flagged.v1', 'rabbit.record_gi_stasis.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.pedigree_link_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_parent uuid; v_rel text; v_aspecies text; v_pspecies text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'linkId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_parent := (p_payload->>'parentId')::uuid;
    v_rel := coalesce(p_payload->>'relationType','');
    if v_rel not in ('sire','dam','genetic_dam','recipient_dam') or v_animal = v_parent then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Pedigree link needs a listed relation and two different animals');
    end if;
    select species_code into v_aspecies from public.animals where farm_id = p_farm_id and id = v_animal;
    select species_code into v_pspecies from public.animals where farm_id = p_farm_id and id = v_parent;
    if v_aspecies is null or v_pspecies is null or v_aspecies <> v_pspecies then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Pedigree link needs two animals of the same species on this farm');
    end if;
    insert into public.pedigree_relations(id, farm_id, animal_id, parent_id, relation_type)
    values (v_id, p_farm_id, v_animal, v_parent, v_rel);
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'pedigree.linked.v1', 'pedigree.link.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','That parent relation is already recorded');
end;
$$;

grant execute on function public.goat_record_heat_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.goat_record_mating_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.goat_record_pregnancy_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_famacha_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.animal_identifier_assign_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.official_record_movement_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.inventory_lot_receive_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.inventory_lot_issue_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.health_record_vet_visit_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.health_record_lab_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_weaning_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_micron_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_kind_enable_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_record_gi_stasis_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.pedigree_link_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
