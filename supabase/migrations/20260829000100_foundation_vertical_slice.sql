-- Farm OS foundation + designated vertical slice.
-- Authority: FARM_OS_IMPLEMENTATION_CLOSURE_v3 / full realisation pack.

create extension if not exists pgcrypto;

create table public.farms (
    id uuid primary key,
    name text not null check (char_length(btrim(name)) between 1 and 120),
    created_at timestamptz not null default now()
);

create table public.farm_users (
    farm_id uuid not null references public.farms(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null check (role in ('owner','farm_manager','breeding_manager','vet_health','worker','finance','read_only')),
    created_at timestamptz not null default now(),
    primary key (farm_id, user_id)
);

create table public.animals (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    species_code text not null,
    tag text not null,
    name text,
    sex text not null,
    status text not null default 'active',
    date_of_birth date,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, species_code, tag),
    check (species_code in ('goat','rabbit','poultry','sheep','cattle')),
    check (status in ('active','sold','dead','culled','closed'))
);

create table public.measurements (
    id uuid primary key,
    farm_id uuid not null,
    animal_id uuid not null,
    type text not null,
    value_long bigint not null,
    unit text not null,
    measured_at timestamptz not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, animal_id) references public.animals(farm_id, id),
    check (type in ('weight')),
    check (unit in ('g')),
    check (value_long > 0)
);

create table public.domain_events (
    event_id uuid primary key default gen_random_uuid(),
    event_type text not null,
    schema_version integer not null,
    farm_id uuid not null references public.farms(id),
    aggregate_type text not null,
    aggregate_id uuid not null,
    stream_id text not null,
    stream_version bigint not null,
    occurred_at timestamptz not null,
    recorded_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id),
    device_id text not null,
    mutation_id uuid not null,
    correlation_id uuid,
    causation_id uuid,
    payload jsonb not null,
    metadata jsonb not null default '{}'::jsonb,
    change_cursor bigint generated always as identity,
    unique (stream_id, stream_version),
    unique (farm_id, mutation_id)
);

create table public.command_receipts (
    farm_id uuid not null references public.farms(id),
    mutation_id uuid not null,
    command_name text not null,
    event_id uuid references public.domain_events(event_id),
    stream_version bigint,
    change_cursor bigint,
    applied_at timestamptz not null default now(),
    primary key (farm_id, mutation_id)
);

create table public.search_index_jobs (
    job_id bigint generated always as identity primary key,
    farm_id uuid not null references public.farms(id),
    entity_type text not null,
    entity_id uuid not null,
    operation text not null check (operation in ('upsert','delete')),
    projection_version bigint not null,
    attempts integer not null default 0,
    state text not null default 'pending' check (state in ('pending','in_flight','retry','done','dead_letter')),
    next_attempt_at timestamptz,
    meili_task_uid bigint,
    last_error text,
    created_at timestamptz not null default now(),
    completed_at timestamptz
);

create index animals_farm_species_status_idx on public.animals(farm_id, species_code, status);
create index measurements_farm_animal_time_idx on public.measurements(farm_id, animal_id, measured_at desc);
create index domain_events_farm_cursor_idx on public.domain_events(farm_id, change_cursor);
create index domain_events_stream_idx on public.domain_events(stream_id, stream_version desc);
create index search_jobs_state_idx on public.search_index_jobs(state, next_attempt_at, job_id);

create or replace function public.is_farm_member(p_farm_id uuid)
returns boolean
language sql
stable
security invoker
set search_path = public
as $$
    select exists (
        select 1 from public.farm_users fu
        where fu.farm_id = p_farm_id and fu.user_id = auth.uid()
    );
$$;

alter table public.farms enable row level security;
alter table public.farm_users enable row level security;
alter table public.animals enable row level security;
alter table public.measurements enable row level security;
alter table public.domain_events enable row level security;
alter table public.command_receipts enable row level security;
alter table public.search_index_jobs enable row level security;

create policy farms_member_select on public.farms
for select to authenticated
using (public.is_farm_member(id));

create policy farm_users_self_select on public.farm_users
for select to authenticated
using (user_id = auth.uid());

create policy animals_member_select on public.animals
for select to authenticated
using (public.is_farm_member(farm_id));

create policy measurements_member_select on public.measurements
for select to authenticated
using (public.is_farm_member(farm_id));

create policy events_member_select on public.domain_events
for select to authenticated
using (public.is_farm_member(farm_id));

create policy receipts_member_select on public.command_receipts
for select to authenticated
using (public.is_farm_member(farm_id));

-- search jobs are worker-owned. Authenticated app users never read/write the queue directly.

revoke insert, update, delete on public.animals from authenticated;
revoke insert, update, delete on public.measurements from authenticated;
revoke insert, update, delete on public.domain_events from authenticated;
revoke insert, update, delete on public.command_receipts from authenticated;
revoke all on public.search_index_jobs from authenticated;

grant select on public.farms, public.farm_users, public.animals, public.measurements, public.domain_events, public.command_receipts to authenticated;

create or replace function public.farm_create_v1(
    p_farm_id uuid,
    p_name text
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
begin
    if auth.uid() is null then
        return jsonb_build_object('code','AUTH_REJECTED','safeMessage','Authentication required');
    end if;
    if char_length(btrim(p_name)) < 1 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Farm name is required');
    end if;
    insert into public.farms(id, name) values (p_farm_id, btrim(p_name));
    insert into public.farm_users(farm_id, user_id, role) values (p_farm_id, auth.uid(), 'owner');
    return jsonb_build_object('code','ACCEPTED');
exception
    when unique_violation then
        return jsonb_build_object('code','CONFLICT','safeMessage','Farm identifier already exists');
end;
$$;

create or replace function public.goat_register_v1(
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
    v_receipt public.command_receipts%rowtype;
    v_event_id uuid;
    v_cursor bigint;
    v_animal_id uuid;
    v_tag text;
    v_name text;
    v_sex text;
    v_dob date;
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

    if coalesce(p_expected_stream_version, 0) <> 0 then
        return jsonb_build_object('code','CONFLICT','safeMessage','New goat expected stream version must be 0');
    end if;

    v_animal_id := (p_payload->>'animalId')::uuid;
    v_tag := btrim(coalesce(p_payload->>'tag',''));
    v_name := nullif(btrim(coalesce(p_payload->>'name','')), '');
    v_sex := p_payload->>'sex';
    v_dob := case when p_payload ? 'dateOfBirthEpochDay' and p_payload->>'dateOfBirthEpochDay' is not null
        then date '1970-01-01' + ((p_payload->>'dateOfBirthEpochDay')::integer)
        else null end;

    if v_tag = '' or v_sex not in ('FEMALE','MALE') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Invalid goat identity payload');
    end if;

    insert into public.animals(id, farm_id, species_code, tag, name, sex, status, date_of_birth)
    values (v_animal_id, p_farm_id, 'goat', v_tag, v_name, v_sex, 'active', v_dob);

    insert into public.domain_events(
        event_type, schema_version, farm_id, aggregate_type, aggregate_id,
        stream_id, stream_version, occurred_at, actor_user_id, device_id,
        mutation_id, payload
    ) values (
        'goat.registered.v1', 1, p_farm_id, 'animal', v_animal_id,
        'animal:' || v_animal_id::text, 1,
        to_timestamp(p_occurred_at_epoch_ms / 1000.0), auth.uid(), p_device_id,
        p_mutation_id, p_payload
    ) returning event_id, change_cursor into v_event_id, v_cursor;

    insert into public.command_receipts(farm_id, mutation_id, command_name, event_id, stream_version, change_cursor)
    values (p_farm_id, p_mutation_id, 'goat.register.v1', v_event_id, 1, v_cursor);

    insert into public.search_index_jobs(farm_id, entity_type, entity_id, operation, projection_version)
    values (p_farm_id, 'animal', v_animal_id, 'upsert', 1);

    return jsonb_build_object('code','ACCEPTED','eventId',v_event_id,'streamVersion',1,'changeCursor',v_cursor);
exception
    when unique_violation then
        return jsonb_build_object('code','CONFLICT','safeMessage','Goat tag or identifier already exists in this farm');
end;
$$;

create or replace function public.goat_record_weight_v1(
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
    v_receipt public.command_receipts%rowtype;
    v_event_id uuid;
    v_cursor bigint;
    v_animal_id uuid;
    v_measurement_id uuid;
    v_weight bigint;
    v_measured_at timestamptz;
    v_current_version bigint;
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

    v_animal_id := (p_payload->>'animalId')::uuid;
    v_measurement_id := (p_payload->>'measurementId')::uuid;
    v_weight := (p_payload->>'weightGrams')::bigint;
    v_measured_at := to_timestamp((p_payload->>'measuredAtEpochMillis')::bigint / 1000.0);

    if v_weight <= 0 or v_weight > 300000 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Weight is outside the goat safety bound');
    end if;

    if not exists (
        select 1 from public.animals
        where farm_id = p_farm_id and id = v_animal_id and species_code = 'goat' and status = 'active'
    ) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Active goat not found');
    end if;

    select coalesce(max(stream_version), 0) into v_current_version
    from public.domain_events
    where stream_id = 'animal:' || v_animal_id::text;

    if p_expected_stream_version is not null and p_expected_stream_version <> v_current_version then
        return jsonb_build_object('code','CONFLICT','streamVersion',v_current_version,'safeMessage','Goat changed on another device');
    end if;

    insert into public.measurements(id, farm_id, animal_id, type, value_long, unit, measured_at)
    values (v_measurement_id, p_farm_id, v_animal_id, 'weight', v_weight, 'g', v_measured_at);

    insert into public.domain_events(
        event_type, schema_version, farm_id, aggregate_type, aggregate_id,
        stream_id, stream_version, occurred_at, actor_user_id, device_id,
        mutation_id, payload
    ) values (
        'goat.weight_recorded.v1', 1, p_farm_id, 'animal', v_animal_id,
        'animal:' || v_animal_id::text, v_current_version + 1,
        to_timestamp(p_occurred_at_epoch_ms / 1000.0), auth.uid(), p_device_id,
        p_mutation_id, p_payload
    ) returning event_id, change_cursor into v_event_id, v_cursor;

    insert into public.command_receipts(farm_id, mutation_id, command_name, event_id, stream_version, change_cursor)
    values (p_farm_id, p_mutation_id, 'goat.record_weight.v1', v_event_id, v_current_version + 1, v_cursor);

    insert into public.search_index_jobs(farm_id, entity_type, entity_id, operation, projection_version)
    values (p_farm_id, 'animal', v_animal_id, 'upsert', v_current_version + 1);

    return jsonb_build_object(
        'code','ACCEPTED',
        'eventId',v_event_id,
        'streamVersion',v_current_version + 1,
        'changeCursor',v_cursor
    );
exception
    when unique_violation then
        -- A duplicate measurement ID with a different mutation is never silently merged.
        return jsonb_build_object('code','CONFLICT','safeMessage','Measurement identifier already exists');
end;
$$;

create or replace function public.pull_changes_v1(
    p_farm_id uuid,
    p_after_cursor bigint default 0,
    p_limit integer default 200
)
returns table(
    event_id uuid,
    event_type text,
    schema_version integer,
    aggregate_type text,
    aggregate_id uuid,
    stream_version bigint,
    occurred_at timestamptz,
    recorded_at timestamptz,
    change_cursor bigint,
    payload jsonb
)
language sql
stable
security definer
set search_path = public, auth
as $$
    select e.event_id, e.event_type, e.schema_version, e.aggregate_type, e.aggregate_id,
           e.stream_version, e.occurred_at, e.recorded_at, e.change_cursor, e.payload
    from public.domain_events e
    where e.farm_id = p_farm_id
      and public.is_farm_member(p_farm_id)
      and e.change_cursor > p_after_cursor
    order by e.change_cursor
    limit least(greatest(p_limit, 1), 500);
$$;

grant execute on function public.farm_create_v1(uuid,text) to authenticated;
grant execute on function public.goat_register_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.goat_record_weight_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.pull_changes_v1(uuid,bigint,integer) to authenticated;
