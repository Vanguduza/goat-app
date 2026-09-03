-- Operating spine: remaining species herds, goat kidding, rabbit KudBat waves,
-- farm tasks, observation-only health, money events, inventory movements.
-- Writes stay on versioned RPCs. Authenticated clients remain select-only.

alter table public.animals
    add column if not exists poultry_kind_code text;

alter table public.animals
    drop constraint if exists animals_poultry_kind_chk;

alter table public.animals
    add constraint animals_poultry_kind_chk check (
        (species_code <> 'poultry' and poultry_kind_code is null)
        or (
            species_code = 'poultry'
            and poultry_kind_code in (
                'chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined'
            )
        )
    );

create table if not exists public.kidding_events (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    dam_id uuid not null,
    born_count integer not null check (born_count > 0),
    live_count integer not null check (live_count >= 0),
    dead_count integer not null check (dead_count >= 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, dam_id) references public.animals(farm_id, id),
    check (live_count + dead_count = born_count)
);

create table if not exists public.farm_tasks (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    module_code text not null,
    task_code text not null,
    title text not null,
    due_on date not null,
    status text not null default 'open' check (status in ('open','done','cancelled')),
    animal_id uuid,
    cage_id uuid,
    wave_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.rabbit_cages (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    code text not null,
    doe_capacity integer not null default 11 check (doe_capacity > 0),
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, code)
);

create table if not exists public.rabbit_nest_boxes (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    cage_id uuid not null,
    code text not null,
    status text not null default 'available' check (status in ('available','sanitized','assigned','dirty')),
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, cage_id, code),
    foreign key (farm_id, cage_id) references public.rabbit_cages(farm_id, id)
);

create table if not exists public.rabbit_breeding_waves (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    cage_id uuid not null,
    pack_id text not null default 'kudbat_semi_intensive_excel',
    doe_count integer not null check (doe_count > 0),
    mating_on date not null,
    nest_in_on date not null,
    kindling_on date not null,
    nest_out_on date not null,
    rebreed_on date not null,
    wean_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, cage_id) references public.rabbit_cages(farm_id, id)
);

create table if not exists public.health_observations (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid,
    species_code text not null check (species_code in ('goat','rabbit','poultry','sheep','cattle')),
    signs text not null check (char_length(btrim(signs)) between 1 and 2000),
    first_aid_applied text,
    red_flag boolean not null default false,
    occurred_at timestamptz not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.money_records (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    kind text not null check (kind in ('expense','income')),
    category_code text not null,
    amount_minor bigint not null check (amount_minor > 0),
    currency text not null default 'USD' check (char_length(currency) = 3),
    occurred_on date not null,
    note text,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.inventory_items (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    sku text not null,
    name text not null,
    unit text not null,
    quantity_milli bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, sku)
);

create table if not exists public.inventory_movements (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    item_id uuid not null,
    direction text not null check (direction in ('receive','issue')),
    quantity_milli bigint not null check (quantity_milli > 0),
    occurred_at timestamptz not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, item_id) references public.inventory_items(farm_id, id)
);

alter table public.kidding_events enable row level security;
alter table public.farm_tasks enable row level security;
alter table public.rabbit_cages enable row level security;
alter table public.rabbit_nest_boxes enable row level security;
alter table public.rabbit_breeding_waves enable row level security;
alter table public.health_observations enable row level security;
alter table public.money_records enable row level security;
alter table public.inventory_items enable row level security;
alter table public.inventory_movements enable row level security;

create policy kidding_member_select on public.kidding_events for select to authenticated
using (public.is_farm_member(farm_id));
create policy tasks_member_select on public.farm_tasks for select to authenticated
using (public.is_farm_member(farm_id));
create policy rabbit_cages_member_select on public.rabbit_cages for select to authenticated
using (public.is_farm_member(farm_id));
create policy rabbit_boxes_member_select on public.rabbit_nest_boxes for select to authenticated
using (public.is_farm_member(farm_id));
create policy rabbit_waves_member_select on public.rabbit_breeding_waves for select to authenticated
using (public.is_farm_member(farm_id));
create policy health_obs_member_select on public.health_observations for select to authenticated
using (public.is_farm_member(farm_id));
create policy money_member_select on public.money_records for select to authenticated
using (public.is_farm_member(farm_id));
create policy inventory_items_member_select on public.inventory_items for select to authenticated
using (public.is_farm_member(farm_id));
create policy inventory_movements_member_select on public.inventory_movements for select to authenticated
using (public.is_farm_member(farm_id));

revoke insert, update, delete on public.kidding_events from authenticated;
revoke insert, update, delete on public.farm_tasks from authenticated;
revoke insert, update, delete on public.rabbit_cages from authenticated;
revoke insert, update, delete on public.rabbit_nest_boxes from authenticated;
revoke insert, update, delete on public.rabbit_breeding_waves from authenticated;
revoke insert, update, delete on public.health_observations from authenticated;
revoke insert, update, delete on public.money_records from authenticated;
revoke insert, update, delete on public.inventory_items from authenticated;
revoke insert, update, delete on public.inventory_movements from authenticated;

grant select on public.kidding_events, public.farm_tasks, public.rabbit_cages, public.rabbit_nest_boxes,
    public.rabbit_breeding_waves, public.health_observations, public.money_records,
    public.inventory_items, public.inventory_movements to authenticated;

create or replace function public.command_gate_v1(p_farm_id uuid, p_mutation_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, auth
as $$
declare
    v_receipt public.command_receipts%rowtype;
begin
    if auth.uid() is null or not public.is_farm_member(p_farm_id) then
        return jsonb_build_object('code','AUTH_REJECTED','safeMessage','Farm access denied');
    end if;
    select * into v_receipt from public.command_receipts
    where farm_id = p_farm_id and mutation_id = p_mutation_id;
    if found then
        return jsonb_build_object(
            'code','ALREADY_APPLIED',
            'eventId',v_receipt.event_id,
            'streamVersion',v_receipt.stream_version,
            'changeCursor',v_receipt.change_cursor
        );
    end if;
    return null;
end;
$$;

create or replace function public.append_command_event_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_occurred_at_epoch_ms bigint,
    p_event_type text,
    p_command_name text,
    p_aggregate_type text,
    p_aggregate_id uuid,
    p_stream_id text,
    p_stream_version bigint,
    p_payload jsonb,
    p_search_entity_type text default null,
    p_search_entity_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_event_id uuid;
    v_cursor bigint;
begin
    insert into public.domain_events(
        event_type, schema_version, farm_id, aggregate_type, aggregate_id,
        stream_id, stream_version, occurred_at, actor_user_id, device_id,
        mutation_id, payload
    ) values (
        p_event_type, 1, p_farm_id, p_aggregate_type, p_aggregate_id,
        p_stream_id, p_stream_version,
        to_timestamp(p_occurred_at_epoch_ms / 1000.0), auth.uid(), p_device_id,
        p_mutation_id, p_payload
    ) returning event_id, change_cursor into v_event_id, v_cursor;

    insert into public.command_receipts(farm_id, mutation_id, command_name, event_id, stream_version, change_cursor)
    values (p_farm_id, p_mutation_id, p_command_name, v_event_id, p_stream_version, v_cursor);

    if p_search_entity_type is not null and p_search_entity_id is not null then
        insert into public.search_index_jobs(farm_id, entity_type, entity_id, operation, projection_version)
        values (p_farm_id, p_search_entity_type, p_search_entity_id, 'upsert', p_stream_version);
    end if;

    return jsonb_build_object(
        'code','ACCEPTED',
        'eventId',v_event_id,
        'streamVersion',p_stream_version,
        'changeCursor',v_cursor
    );
end;
$$;

create or replace function public.species_register_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb,
    p_species text,
    p_command_name text,
    p_event_type text
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_animal_id uuid;
    v_tag text;
    v_name text;
    v_sex text;
    v_dob date;
    v_kind text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;

    if coalesce(p_expected_stream_version, 0) <> 0 then
        return jsonb_build_object('code','CONFLICT','safeMessage','New animal expected stream version must be 0');
    end if;

    v_animal_id := (p_payload->>'animalId')::uuid;
    v_tag := btrim(coalesce(p_payload->>'tag',''));
    v_name := nullif(btrim(coalesce(p_payload->>'name','')), '');
    v_sex := p_payload->>'sex';
    v_dob := case when p_payload ? 'dateOfBirthEpochDay' and p_payload->>'dateOfBirthEpochDay' is not null
        then date '1970-01-01' + ((p_payload->>'dateOfBirthEpochDay')::integer)
        else null end;
    v_kind := nullif(btrim(coalesce(p_payload->>'poultryKindCode','')), '');

    if v_tag = '' or v_sex not in ('FEMALE','MALE') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Invalid animal identity payload');
    end if;
    if p_species = 'poultry' and v_kind is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Poultry kind is required');
    end if;
    if p_species <> 'poultry' then
        v_kind := null;
    end if;

    insert into public.animals(id, farm_id, species_code, tag, name, sex, status, date_of_birth, poultry_kind_code)
    values (v_animal_id, p_farm_id, p_species, v_tag, v_name, v_sex, 'active', v_dob, v_kind);

    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        p_event_type, p_command_name, 'animal', v_animal_id,
        'animal:' || v_animal_id::text, 1, p_payload, 'animal', v_animal_id
    );
exception
    when unique_violation then
        v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
        if v_gate is not null then return v_gate; end if;
        return jsonb_build_object('code','CONFLICT','safeMessage','Tag or identifier already exists in this farm');
end;
$$;

create or replace function public.species_record_weight_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb,
    p_species text,
    p_max_grams bigint,
    p_command_name text,
    p_event_type text
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_animal_id uuid;
    v_measurement_id uuid;
    v_weight bigint;
    v_current_version bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;

    v_animal_id := (p_payload->>'animalId')::uuid;
    v_measurement_id := (p_payload->>'measurementId')::uuid;
    v_weight := (p_payload->>'weightGrams')::bigint;

    if v_weight is null or v_weight <= 0 or v_weight > p_max_grams then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Weight is outside the species safety bound');
    end if;
    if not exists (
        select 1 from public.animals
        where farm_id = p_farm_id and id = v_animal_id and species_code = p_species and status = 'active'
    ) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Active animal not found');
    end if;

    select coalesce(max(stream_version), 0) into v_current_version
    from public.domain_events
    where stream_id = 'animal:' || v_animal_id::text;

    if p_expected_stream_version is not null and p_expected_stream_version <> v_current_version then
        return jsonb_build_object('code','CONFLICT','streamVersion',v_current_version,'safeMessage','Animal changed on another device');
    end if;

    insert into public.measurements(id, farm_id, animal_id, type, value_long, unit, measured_at)
    values (
        v_measurement_id, p_farm_id, v_animal_id, 'weight', v_weight, 'g',
        to_timestamp((p_payload->>'measuredAtEpochMillis')::bigint / 1000.0)
    );

    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        p_event_type, p_command_name, 'animal', v_animal_id,
        'animal:' || v_animal_id::text, v_current_version + 1, p_payload, 'animal', v_animal_id
    );
exception
    when unique_violation then
        v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
        if v_gate is not null then return v_gate; end if;
        return jsonb_build_object('code','CONFLICT','safeMessage','Measurement identifier already exists');
end;
$$;

create or replace function public.species_set_status_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb,
    p_species text,
    p_command_name text,
    p_event_type text
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_animal_id uuid;
    v_status text;
    v_current_status text;
    v_current_version bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;

    v_animal_id := (p_payload->>'animalId')::uuid;
    v_status := lower(btrim(coalesce(p_payload->>'status', '')));
    if v_status not in ('sold','dead','culled') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Status must be sold, dead, or culled');
    end if;

    select status into v_current_status
    from public.animals
    where farm_id = p_farm_id and id = v_animal_id and species_code = p_species;
    if v_current_status is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Animal not found');
    end if;
    if v_current_status <> 'active' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Only an active animal can change lifecycle status');
    end if;

    select coalesce(max(stream_version), 0) into v_current_version
    from public.domain_events
    where stream_id = 'animal:' || v_animal_id::text;
    if p_expected_stream_version is not null and p_expected_stream_version <> v_current_version then
        return jsonb_build_object('code','CONFLICT','streamVersion',v_current_version,'safeMessage','Animal changed on another device');
    end if;

    update public.animals
    set status = v_status, updated_at = now()
    where farm_id = p_farm_id and id = v_animal_id;

    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        p_event_type, p_command_name, 'animal', v_animal_id,
        'animal:' || v_animal_id::text, v_current_version + 1, p_payload, 'animal', v_animal_id
    );
end;
$$;

create or replace function public.rabbit_register_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_register_v1($1,$2,$3,$4,$5,$6,'rabbit','rabbit.register.v1','rabbit.registered.v1'); $$;

create or replace function public.sheep_register_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_register_v1($1,$2,$3,$4,$5,$6,'sheep','sheep.register.v1','sheep.registered.v1'); $$;

create or replace function public.cattle_register_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_register_v1($1,$2,$3,$4,$5,$6,'cattle','cattle.register.v1','cattle.registered.v1'); $$;

create or replace function public.poultry_register_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_register_v1($1,$2,$3,$4,$5,$6,'poultry','poultry.register.v1','poultry.registered.v1'); $$;

create or replace function public.rabbit_record_weight_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_record_weight_v1($1,$2,$3,$4,$5,$6,'rabbit',8000,'rabbit.record_weight.v1','rabbit.weight_recorded.v1'); $$;

create or replace function public.sheep_record_weight_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_record_weight_v1($1,$2,$3,$4,$5,$6,'sheep',250000,'sheep.record_weight.v1','sheep.weight_recorded.v1'); $$;

create or replace function public.cattle_record_weight_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_record_weight_v1($1,$2,$3,$4,$5,$6,'cattle',2000000,'cattle.record_weight.v1','cattle.weight_recorded.v1'); $$;

create or replace function public.poultry_record_weight_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_record_weight_v1($1,$2,$3,$4,$5,$6,'poultry',25000,'poultry.record_weight.v1','poultry.weight_recorded.v1'); $$;

create or replace function public.rabbit_set_status_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_set_status_v1($1,$2,$3,$4,$5,$6,'rabbit','rabbit.set_status.v1','rabbit.status_changed.v1'); $$;

create or replace function public.sheep_set_status_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_set_status_v1($1,$2,$3,$4,$5,$6,'sheep','sheep.set_status.v1','sheep.status_changed.v1'); $$;

create or replace function public.cattle_set_status_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_set_status_v1($1,$2,$3,$4,$5,$6,'cattle','cattle.set_status.v1','cattle.status_changed.v1'); $$;

create or replace function public.poultry_set_status_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language sql security definer set search_path = public, auth
as $$ select public.species_set_status_v1($1,$2,$3,$4,$5,$6,'poultry','poultry.set_status.v1','poultry.status_changed.v1'); $$;

create or replace function public.goat_record_kidding_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_kidding_id uuid;
    v_dam_id uuid;
    v_born integer;
    v_live integer;
    v_dead integer;
    v_current_version bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;

    v_kidding_id := (p_payload->>'kiddingId')::uuid;
    v_dam_id := (p_payload->>'damAnimalId')::uuid;
    v_born := (p_payload->>'bornCount')::integer;
    v_live := (p_payload->>'liveCount')::integer;
    v_dead := (p_payload->>'deadCount')::integer;

    if v_born is null or v_born <= 0 or v_live is null or v_dead is null or v_live + v_dead <> v_born then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Kidding counts must add up and be greater than zero');
    end if;
    if not exists (
        select 1 from public.animals
        where farm_id = p_farm_id and id = v_dam_id and species_code = 'goat' and sex = 'FEMALE' and status = 'active'
    ) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Active doe not found');
    end if;

    select coalesce(max(stream_version), 0) into v_current_version
    from public.domain_events
    where stream_id = 'animal:' || v_dam_id::text;
    if p_expected_stream_version is not null and p_expected_stream_version <> v_current_version then
        return jsonb_build_object('code','CONFLICT','streamVersion',v_current_version,'safeMessage','Doe changed on another device');
    end if;

    insert into public.kidding_events(id, farm_id, dam_id, born_count, live_count, dead_count, occurred_on)
    values (
        v_kidding_id, p_farm_id, v_dam_id, v_born, v_live, v_dead,
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer)
    );

    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.kidded.v1', 'goat.record_kidding.v1', 'animal', v_dam_id,
        'animal:' || v_dam_id::text, v_current_version + 1, p_payload, 'animal', v_dam_id
    );
end;
$$;

create or replace function public.task_create_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_task_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_task_id := (p_payload->>'taskId')::uuid;
    if btrim(coalesce(p_payload->>'title','')) = '' or btrim(coalesce(p_payload->>'taskCode','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Task title and code are required');
    end if;
    insert into public.farm_tasks(id, farm_id, module_code, task_code, title, due_on, animal_id, cage_id, wave_id)
    values (
        v_task_id, p_farm_id,
        btrim(p_payload->>'moduleCode'),
        btrim(p_payload->>'taskCode'),
        btrim(p_payload->>'title'),
        date '1970-01-01' + ((p_payload->>'dueEpochDay')::integer),
        nullif(p_payload->>'animalId','')::uuid,
        nullif(p_payload->>'cageId','')::uuid,
        nullif(p_payload->>'waveId','')::uuid
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'task.created.v1', 'task.create.v1', 'task', v_task_id,
        'task:' || v_task_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.task_complete_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_task_id uuid;
    v_current_version bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_task_id := (p_payload->>'taskId')::uuid;
    if not exists (select 1 from public.farm_tasks where farm_id = p_farm_id and id = v_task_id and status = 'open') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Open task not found');
    end if;
    select coalesce(max(stream_version), 0) into v_current_version
    from public.domain_events where stream_id = 'task:' || v_task_id::text;
    if p_expected_stream_version is not null and p_expected_stream_version <> v_current_version then
        return jsonb_build_object('code','CONFLICT','streamVersion',v_current_version,'safeMessage','Task changed on another device');
    end if;
    update public.farm_tasks set status = 'done', updated_at = now()
    where farm_id = p_farm_id and id = v_task_id;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'task.completed.v1', 'task.complete.v1', 'task', v_task_id,
        'task:' || v_task_id::text, v_current_version + 1, p_payload, null, null
    );
end;
$$;

create or replace function public.rabbit_cage_create_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_cage_id uuid;
    v_code text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_cage_id := (p_payload->>'cageId')::uuid;
    v_code := btrim(coalesce(p_payload->>'code',''));
    if v_code = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cage code is required');
    end if;
    insert into public.rabbit_cages(id, farm_id, code, doe_capacity)
    values (v_cage_id, p_farm_id, v_code, coalesce((p_payload->>'doeCapacity')::integer, 11));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.cage_created.v1', 'rabbit.cage_create.v1', 'rabbit_cage', v_cage_id,
        'rabbit_cage:' || v_cage_id::text, 1, p_payload, null, null
    );
exception
    when unique_violation then
        return jsonb_build_object('code','CONFLICT','safeMessage','Cage code already exists');
end;
$$;

create or replace function public.rabbit_nest_box_create_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_box_id uuid;
    v_cage_id uuid;
    v_code text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_box_id := (p_payload->>'nestBoxId')::uuid;
    v_cage_id := (p_payload->>'cageId')::uuid;
    v_code := btrim(coalesce(p_payload->>'code',''));
    if v_code = '' or not exists (select 1 from public.rabbit_cages where farm_id = p_farm_id and id = v_cage_id) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Nest box needs a cage and code');
    end if;
    insert into public.rabbit_nest_boxes(id, farm_id, cage_id, code)
    values (v_box_id, p_farm_id, v_cage_id, v_code);
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.nest_box_created.v1', 'rabbit.nest_box_create.v1', 'rabbit_nest_box', v_box_id,
        'rabbit_nest_box:' || v_box_id::text, 1, p_payload, null, null
    );
exception
    when unique_violation then
        return jsonb_build_object('code','CONFLICT','safeMessage','Nest box code already exists in this cage');
end;
$$;

create or replace function public.rabbit_wave_create_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_wave_id uuid;
    v_cage_id uuid;
    v_doe_count integer;
    v_mating date;
    v_boxes integer;
    v_nest_in date;
    v_kindling date;
    v_rebreed date;
    v_nest_out date;
    v_wean date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;

    v_wave_id := (p_payload->>'waveId')::uuid;
    v_cage_id := (p_payload->>'cageId')::uuid;
    v_doe_count := (p_payload->>'doeCount')::integer;
    v_mating := date '1970-01-01' + ((p_payload->>'matingEpochDay')::integer);
    if v_doe_count is null or v_doe_count <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Doe count is required');
    end if;
    if not exists (select 1 from public.rabbit_cages where farm_id = p_farm_id and id = v_cage_id) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cage not found');
    end if;
    select count(*) into v_boxes
    from public.rabbit_nest_boxes
    where farm_id = p_farm_id and cage_id = v_cage_id and status in ('available','sanitized');
    if v_boxes < v_doe_count then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Nest boxes must at least match the doe count for this wave');
    end if;

    v_nest_in := v_mating + 28;
    v_kindling := v_mating + 32;
    v_rebreed := v_mating + 43;
    v_nest_out := v_kindling + 21;
    v_wean := v_kindling + 35;

    p_payload := p_payload || jsonb_build_object(
        'packId', 'kudbat_semi_intensive_excel',
        'nestInEpochDay', (v_nest_in - date '1970-01-01'),
        'kindlingEpochDay', (v_kindling - date '1970-01-01'),
        'nestOutEpochDay', (v_nest_out - date '1970-01-01'),
        'rebreedEpochDay', (v_rebreed - date '1970-01-01'),
        'weanEpochDay', (v_wean - date '1970-01-01'),
        'placeTaskId', coalesce(p_payload->>'placeTaskId', gen_random_uuid()::text),
        'kindlingTaskId', coalesce(p_payload->>'kindlingTaskId', gen_random_uuid()::text),
        'removeTaskId', coalesce(p_payload->>'removeTaskId', gen_random_uuid()::text),
        'rebreedTaskId', coalesce(p_payload->>'rebreedTaskId', gen_random_uuid()::text),
        'weanTaskId', coalesce(p_payload->>'weanTaskId', gen_random_uuid()::text)
    );

    insert into public.rabbit_breeding_waves(
        id, farm_id, cage_id, pack_id, doe_count, mating_on, nest_in_on, kindling_on, nest_out_on, rebreed_on, wean_on
    ) values (
        v_wave_id, p_farm_id, v_cage_id, 'kudbat_semi_intensive_excel', v_doe_count,
        v_mating, v_nest_in, v_kindling, v_nest_out, v_rebreed, v_wean
    );

    insert into public.farm_tasks(id, farm_id, module_code, task_code, title, due_on, cage_id, wave_id)
    values
        ((p_payload->>'placeTaskId')::uuid, p_farm_id, 'rabbit', 'NEST_BOX_PLACE', 'Place nest box', v_nest_in, v_cage_id, v_wave_id),
        ((p_payload->>'kindlingTaskId')::uuid, p_farm_id, 'rabbit', 'EXPECTED_KINDLING', 'Watch for kindling', v_kindling, v_cage_id, v_wave_id),
        ((p_payload->>'removeTaskId')::uuid, p_farm_id, 'rabbit', 'NEST_BOX_REMOVE', 'Remove nest box', v_nest_out, v_cage_id, v_wave_id),
        ((p_payload->>'rebreedTaskId')::uuid, p_farm_id, 'rabbit', 'REBREED', 'Rebreed', v_rebreed, v_cage_id, v_wave_id),
        ((p_payload->>'weanTaskId')::uuid, p_farm_id, 'rabbit', 'WEAN', 'Wean kits', v_wean, v_cage_id, v_wave_id);

    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.wave_created.v1', 'rabbit.wave_create.v1', 'rabbit_wave', v_wave_id,
        'rabbit_wave:' || v_wave_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.health_record_observation_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    if p_payload ? 'productName' or p_payload ? 'dose' or p_payload ? 'medication' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Observations cannot carry a product or dose');
    end if;
    if btrim(coalesce(p_payload->>'signs','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Signs are required');
    end if;
    v_id := (p_payload->>'observationId')::uuid;
    insert into public.health_observations(
        id, farm_id, animal_id, species_code, signs, first_aid_applied, red_flag, occurred_at
    ) values (
        v_id, p_farm_id,
        nullif(p_payload->>'animalId','')::uuid,
        btrim(p_payload->>'speciesCode'),
        btrim(p_payload->>'signs'),
        nullif(btrim(coalesce(p_payload->>'firstAidApplied','')), ''),
        coalesce((p_payload->>'redFlag')::boolean, false),
        to_timestamp((p_payload->>'occurredAtEpochMillis')::bigint / 1000.0)
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'health.observation_recorded.v1', 'health.record_observation.v1', 'health_observation', v_id,
        'health_observation:' || v_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.money_record_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_id uuid;
    v_kind text;
    v_amount bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'recordId')::uuid;
    v_kind := p_payload->>'kind';
    v_amount := (p_payload->>'amountMinor')::bigint;
    if v_kind not in ('expense','income') or v_amount is null or v_amount <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Money kind and a positive minor-unit amount are required');
    end if;
    insert into public.money_records(id, farm_id, kind, category_code, amount_minor, currency, occurred_on, note)
    values (
        v_id, p_farm_id, v_kind,
        btrim(coalesce(p_payload->>'categoryCode','other')),
        v_amount,
        upper(coalesce(p_payload->>'currency','USD')),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer),
        nullif(btrim(coalesce(p_payload->>'note','')), '')
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'money.recorded.v1', 'money.record.v1', 'money_record', v_id,
        'money:' || v_id::text, 1, p_payload, null, null
    );
end;
$$;

create or replace function public.inventory_item_create_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'itemId')::uuid;
    if btrim(coalesce(p_payload->>'sku','')) = '' or btrim(coalesce(p_payload->>'name','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Inventory sku and name are required');
    end if;
    insert into public.inventory_items(id, farm_id, sku, name, unit)
    values (v_id, p_farm_id, btrim(p_payload->>'sku'), btrim(p_payload->>'name'), coalesce(nullif(btrim(p_payload->>'unit'),''),'kg'));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'inventory.item_created.v1', 'inventory.item_create.v1', 'inventory_item', v_id,
        'inventory_item:' || v_id::text, 1, p_payload, null, null
    );
exception
    when unique_violation then
        return jsonb_build_object('code','CONFLICT','safeMessage','Inventory sku already exists');
end;
$$;

create or replace function public.inventory_move_v1(
    p_mutation_id uuid,
    p_farm_id uuid,
    p_device_id text,
    p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_gate jsonb;
    v_move_id uuid;
    v_item_id uuid;
    v_direction text;
    v_qty bigint;
    v_current bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_move_id := (p_payload->>'movementId')::uuid;
    v_item_id := (p_payload->>'itemId')::uuid;
    v_direction := p_payload->>'direction';
    v_qty := (p_payload->>'quantityMilli')::bigint;
    if v_direction not in ('receive','issue') or v_qty is null or v_qty <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Inventory movement needs a direction and positive quantity');
    end if;
    select quantity_milli into v_current from public.inventory_items where farm_id = p_farm_id and id = v_item_id;
    if v_current is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Inventory item not found');
    end if;
    if v_direction = 'issue' and v_current < v_qty then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Not enough stock on this farm');
    end if;
    insert into public.inventory_movements(id, farm_id, item_id, direction, quantity_milli, occurred_at)
    values (
        v_move_id, p_farm_id, v_item_id, v_direction, v_qty,
        to_timestamp((p_payload->>'occurredAtEpochMillis')::bigint / 1000.0)
    );
    update public.inventory_items
    set quantity_milli = case when v_direction = 'receive' then quantity_milli + v_qty else quantity_milli - v_qty end,
        updated_at = now()
    where farm_id = p_farm_id and id = v_item_id;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'inventory.moved.v1', 'inventory.move.v1', 'inventory_item', v_item_id,
        'inventory_item:' || v_item_id::text,
        (select coalesce(max(stream_version), 0) + 1 from public.domain_events where stream_id = 'inventory_item:' || v_item_id::text),
        p_payload, null, null
    );
end;
$$;

grant execute on function public.rabbit_register_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_register_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_register_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_register_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_record_weight_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_weight_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_weight_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_record_weight_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_set_status_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_set_status_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_set_status_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_set_status_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.goat_record_kidding_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.task_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.task_complete_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_cage_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_nest_box_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_wave_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.health_record_observation_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.money_record_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.inventory_item_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.inventory_move_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;

create or replace function public.canonicalize_goat_event_payload_v1()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if new.event_type = 'goat.registered.v1' then
        new.payload := jsonb_build_object(
            'animalId', new.aggregate_id::text,
            'tag', btrim(coalesce(new.payload->>'tag', '')),
            'name', nullif(btrim(coalesce(new.payload->>'name', '')), ''),
            'sex', new.payload->>'sex',
            'dateOfBirthEpochDay', case
                when new.payload ? 'dateOfBirthEpochDay' and new.payload->>'dateOfBirthEpochDay' is not null
                    then (new.payload->>'dateOfBirthEpochDay')::bigint
                else null
            end
        );
    elsif new.event_type in (
        'rabbit.registered.v1','sheep.registered.v1','cattle.registered.v1','poultry.registered.v1'
    ) then
        new.payload := jsonb_build_object(
            'animalId', new.aggregate_id::text,
            'tag', btrim(coalesce(new.payload->>'tag', '')),
            'name', nullif(btrim(coalesce(new.payload->>'name', '')), ''),
            'sex', new.payload->>'sex',
            'dateOfBirthEpochDay', case
                when new.payload ? 'dateOfBirthEpochDay' and new.payload->>'dateOfBirthEpochDay' is not null
                    then (new.payload->>'dateOfBirthEpochDay')::bigint
                else null
            end,
            'poultryKindCode', nullif(btrim(coalesce(new.payload->>'poultryKindCode','')), '')
        );
    elsif new.event_type in (
        'goat.weight_recorded.v1','rabbit.weight_recorded.v1','sheep.weight_recorded.v1',
        'cattle.weight_recorded.v1','poultry.weight_recorded.v1'
    ) then
        new.payload := jsonb_build_object(
            'animalId', new.aggregate_id::text,
            'measurementId', ((new.payload->>'measurementId')::uuid)::text,
            'weightGrams', (new.payload->>'weightGrams')::bigint,
            'measuredAtEpochMillis', (new.payload->>'measuredAtEpochMillis')::bigint
        );
    elsif new.event_type in (
        'goat.status_changed.v1','rabbit.status_changed.v1','sheep.status_changed.v1',
        'cattle.status_changed.v1','poultry.status_changed.v1'
    ) then
        new.payload := jsonb_build_object(
            'animalId', new.aggregate_id::text,
            'status', lower(btrim(coalesce(new.payload->>'status', '')))
        );
    elsif new.event_type = 'health.observation_recorded.v1' then
        new.payload := new.payload - 'productName' - 'dose' - 'medication';
    end if;
    return new;
end;
$$;
