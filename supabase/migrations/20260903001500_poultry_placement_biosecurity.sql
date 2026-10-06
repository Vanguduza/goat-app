-- Poultry flock placement into a house and a biosecurity walk.
-- Placement creates inspection and vaccination-reminder tasks by kind.
-- Writes stay on versioned RPCs.

create table if not exists public.poultry_placements (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid not null,
    house_id uuid not null,
    poultry_kind_code text not null check (poultry_kind_code in (
        'chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined'
    )),
    head_count integer not null check (head_count > 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, group_id) references public.animal_groups(farm_id, id),
    foreign key (farm_id, house_id) references public.poultry_houses(farm_id, id)
);

create table if not exists public.poultry_biosecurity_walks (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    house_id uuid,
    group_id uuid,
    findings text not null check (char_length(btrim(findings)) between 1 and 2000),
    mixed_species boolean not null default false,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (house_id is not null or group_id is not null)
);

alter table public.poultry_placements enable row level security;
alter table public.poultry_biosecurity_walks enable row level security;

create policy poultry_place_sel on public.poultry_placements for select to authenticated using (public.is_farm_member(farm_id));
create policy poultry_bio_sel on public.poultry_biosecurity_walks for select to authenticated using (public.is_farm_member(farm_id));

revoke insert, update, delete on public.poultry_placements, public.poultry_biosecurity_walks from authenticated;
grant select on public.poultry_placements, public.poultry_biosecurity_walks to authenticated;

create or replace function public.poultry_flock_place_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid; v_house uuid; v_kind text; v_heads integer; v_on date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'placementId')::uuid;
    v_group := (p_payload->>'groupId')::uuid;
    v_house := (p_payload->>'houseId')::uuid;
    v_kind := coalesce(p_payload->>'poultryKindCode','');
    v_heads := (p_payload->>'headCount')::integer;
    v_on := date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer);
    if v_kind not in ('chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined')
        or v_heads is null or v_heads <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Placement needs a poultry kind and head count');
    end if;
    if not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'poultry') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Placement needs a poultry flock');
    end if;
    if not exists (select 1 from public.poultry_houses where farm_id = p_farm_id and id = v_house) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Placement needs a house');
    end if;
    insert into public.poultry_placements(id, farm_id, group_id, house_id, poultry_kind_code, head_count, occurred_on)
    values (v_id, p_farm_id, v_group, v_house, v_kind, v_heads, v_on);
    perform public.insert_lifecycle_task(
        p_farm_id, coalesce((p_payload->>'inspectTaskId')::uuid, gen_random_uuid()),
        'poultry', 'BIOSECURITY', 'Placement inspection / biosecurity', v_on, null, v_group
    );
    perform public.insert_lifecycle_task(
        p_farm_id, coalesce((p_payload->>'vaxTaskId')::uuid, gen_random_uuid()),
        'poultry', 'FLOCK_VAX', 'Kind vaccination pack', v_on + 1, null, v_group
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'poultry.flock_placed.v1', 'poultry.flock_place.v1', 'animal_group', v_group,
        'animal_group:' || v_group::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal_group:' || v_group::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.poultry_record_biosecurity_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_house uuid; v_group uuid; v_findings text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'walkId')::uuid;
    v_house := nullif(p_payload->>'houseId','')::uuid;
    v_group := nullif(p_payload->>'groupId','')::uuid;
    v_findings := btrim(coalesce(p_payload->>'findings',''));
    if char_length(v_findings) < 1 or (v_house is null and v_group is null) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Biosecurity walk needs findings and a house or flock');
    end if;
    if v_house is not null and not exists (select 1 from public.poultry_houses where farm_id = p_farm_id and id = v_house) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','House not found');
    end if;
    if v_group is not null and not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'poultry') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Biosecurity walk needs a poultry flock');
    end if;
    insert into public.poultry_biosecurity_walks(id, farm_id, house_id, group_id, findings, mixed_species, occurred_on)
    values (
        v_id, p_farm_id, v_house, v_group, v_findings,
        coalesce((p_payload->>'mixedSpecies')::boolean, false),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer)
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'poultry.biosecurity_recorded.v1', 'poultry.record_biosecurity.v1', 'poultry_house', coalesce(v_house, v_group),
        'poultry_house:' || coalesce(v_house, v_group)::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'poultry_house:' || coalesce(v_house, v_group)::text),
        p_payload, null, null
    );
end;
$$;

grant execute on function public.poultry_flock_place_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_record_biosecurity_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
