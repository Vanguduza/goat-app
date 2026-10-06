-- 017: protocol-pack slots → tasks, cattle days-on-feed / lot close-out,
-- goat lactation follow-up, FEFO reorder records, group census.

alter table public.inventory_items
    add column if not exists reorder_milli bigint not null default 0 check (reorder_milli >= 0);

create table if not exists public.health_schedule_slots (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    pack_id uuid not null,
    slot_code text not null,
    title text not null,
    offset_days integer not null,
    from_event text not null check (from_event in ('apply_date','expected_birth','kidding','kindling','placement')),
    is_core boolean not null default true,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, pack_id, slot_code)
);

create table if not exists public.health_pack_applications (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    pack_id uuid not null,
    animal_id uuid,
    group_id uuid,
    anchor_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.cattle_lot_placements (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid not null,
    head_count integer not null check (head_count > 0),
    placed_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.cattle_days_on_feed (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid not null,
    days_on_feed integer not null check (days_on_feed >= 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.cattle_lot_closeouts (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid not null,
    head_out integer not null check (head_out > 0),
    weight_grams bigint,
    days_on_feed integer,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.goat_lactation_plans (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    kidding_id uuid,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.inventory_reorder_alerts (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    item_id uuid not null,
    on_hand_milli bigint not null,
    reorder_milli bigint not null,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.group_census_records (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid not null,
    head_count integer not null check (head_count >= 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

alter table public.health_schedule_slots enable row level security;
alter table public.health_pack_applications enable row level security;
alter table public.cattle_lot_placements enable row level security;
alter table public.cattle_days_on_feed enable row level security;
alter table public.cattle_lot_closeouts enable row level security;
alter table public.goat_lactation_plans enable row level security;
alter table public.inventory_reorder_alerts enable row level security;
alter table public.group_census_records enable row level security;

create policy health_slots_sel on public.health_schedule_slots for select to authenticated using (public.is_farm_member(farm_id));
create policy health_apply_sel on public.health_pack_applications for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_place_sel on public.cattle_lot_placements for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_dof_sel on public.cattle_days_on_feed for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_close_sel on public.cattle_lot_closeouts for select to authenticated using (public.is_farm_member(farm_id));
create policy goat_lact_sel on public.goat_lactation_plans for select to authenticated using (public.is_farm_member(farm_id));
create policy reorder_sel on public.inventory_reorder_alerts for select to authenticated using (public.is_farm_member(farm_id));
create policy census_sel on public.group_census_records for select to authenticated using (public.is_farm_member(farm_id));

revoke insert, update, delete on public.health_schedule_slots, public.health_pack_applications,
    public.cattle_lot_placements, public.cattle_days_on_feed, public.cattle_lot_closeouts,
    public.goat_lactation_plans, public.inventory_reorder_alerts, public.group_census_records
    from authenticated;
grant select on public.health_schedule_slots, public.health_pack_applications,
    public.cattle_lot_placements, public.cattle_days_on_feed, public.cattle_lot_closeouts,
    public.goat_lactation_plans, public.inventory_reorder_alerts, public.group_census_records
    to authenticated;

create or replace function public.health_pack_slot_add_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_pack uuid; v_from text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'slotId')::uuid;
    v_pack := (p_payload->>'packId')::uuid;
    v_from := coalesce(p_payload->>'fromEvent','apply_date');
    if btrim(coalesce(p_payload->>'slotCode','')) = '' or btrim(coalesce(p_payload->>'title','')) = ''
        or (p_payload->>'offsetDays') is null
        or v_from not in ('apply_date','expected_birth','kidding','kindling','placement') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Pack slot needs a code, title, offset, and listed anchor');
    end if;
    if not exists (select 1 from public.health_protocol_packs where farm_id = p_farm_id and id = v_pack and status = 'vet_accepted') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Slot needs a vet-accepted protocol pack');
    end if;
    insert into public.health_schedule_slots(id, farm_id, pack_id, slot_code, title, offset_days, from_event, is_core)
    values (
        v_id, p_farm_id, v_pack, btrim(p_payload->>'slotCode'), btrim(p_payload->>'title'),
        (p_payload->>'offsetDays')::integer, v_from, coalesce((p_payload->>'isCore')::boolean, true)
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'health.pack_slot_added.v1', 'health.pack_slot_add.v1', 'health_protocol_pack', v_pack,
        'health_protocol_pack:' || v_pack::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'health_protocol_pack:' || v_pack::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.health_pack_apply_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_pack uuid; v_animal uuid; v_group uuid; v_anchor date; v_slot record; v_module text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'applyId')::uuid;
    v_pack := (p_payload->>'packId')::uuid;
    v_animal := nullif(p_payload->>'animalId','')::uuid;
    v_group := nullif(p_payload->>'groupId','')::uuid;
    v_anchor := date '1970-01-01' + ((p_payload->>'anchorEpochDay')::integer);
    if v_animal is null and v_group is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Pack apply needs an animal or group');
    end if;
    select species_code into v_module from public.health_protocol_packs
        where farm_id = p_farm_id and id = v_pack and status = 'vet_accepted';
    if v_module is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Pack apply needs a vet-accepted protocol pack');
    end if;
    if not exists (select 1 from public.health_schedule_slots where farm_id = p_farm_id and pack_id = v_pack and is_core) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Pack apply needs at least one core slot');
    end if;
    insert into public.health_pack_applications(id, farm_id, pack_id, animal_id, group_id, anchor_on)
    values (v_id, p_farm_id, v_pack, v_animal, v_group, v_anchor);
    for v_slot in
        select id, slot_code, title, offset_days from public.health_schedule_slots
        where farm_id = p_farm_id and pack_id = v_pack and is_core
    loop
        perform public.insert_lifecycle_task(
            p_farm_id,
            gen_random_uuid(),
            v_module,
            'PACK_SLOT',
            v_slot.title,
            v_anchor + v_slot.offset_days,
            v_animal,
            v_group
        );
    end loop;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'health.pack_applied.v1', 'health.pack_apply.v1', 'health_protocol_pack', v_pack,
        'health_protocol_pack:' || v_pack::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'health_protocol_pack:' || v_pack::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.cattle_lot_place_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid; v_heads integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'placementId')::uuid;
    v_group := (p_payload->>'groupId')::uuid;
    v_heads := (p_payload->>'headCount')::integer;
    if v_heads is null or v_heads <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lot place needs a cattle lot and head count');
    end if;
    if not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'cattle') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lot place needs a cattle lot');
    end if;
    insert into public.cattle_lot_placements(id, farm_id, group_id, head_count, placed_on)
    values (v_id, p_farm_id, v_group, v_heads, date '1970-01-01' + ((p_payload->>'placedEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.lot_placed.v1', 'cattle.lot_place.v1', 'animal_group', v_group,
        'animal_group:' || v_group::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal_group:' || v_group::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.cattle_record_dof_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid; v_days integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'recordId')::uuid;
    v_group := (p_payload->>'groupId')::uuid;
    v_days := (p_payload->>'daysOnFeed')::integer;
    if v_days is null or v_days < 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Days on feed needs a cattle lot and a non-negative day count');
    end if;
    if not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'cattle') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Days on feed needs a cattle lot');
    end if;
    insert into public.cattle_days_on_feed(id, farm_id, group_id, days_on_feed, occurred_on)
    values (v_id, p_farm_id, v_group, v_days, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.dof_recorded.v1', 'cattle.record_dof.v1', 'animal_group', v_group,
        'animal_group:' || v_group::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal_group:' || v_group::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.cattle_lot_close_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid; v_heads integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'closeoutId')::uuid;
    v_group := (p_payload->>'groupId')::uuid;
    v_heads := (p_payload->>'headOut')::integer;
    if v_heads is null or v_heads <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lot close-out needs a cattle lot and head-out count');
    end if;
    if not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'cattle') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lot close-out needs a cattle lot');
    end if;
    insert into public.cattle_lot_closeouts(id, farm_id, group_id, head_out, weight_grams, days_on_feed, occurred_on)
    values (
        v_id, p_farm_id, v_group, v_heads, (p_payload->>'weightGrams')::bigint, (p_payload->>'daysOnFeed')::integer,
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer)
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.lot_closed.v1', 'cattle.lot_close.v1', 'animal_group', v_group,
        'animal_group:' || v_group::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal_group:' || v_group::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.goat_plan_lactation_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_on date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'planId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_on := date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer);
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'goat' and sex = 'FEMALE') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lactation plan needs a doe');
    end if;
    insert into public.goat_lactation_plans(id, farm_id, animal_id, kidding_id, occurred_on)
    values (v_id, p_farm_id, v_animal, nullif(p_payload->>'kiddingId','')::uuid, v_on);
    perform public.insert_lifecycle_task(
        p_farm_id, coalesce((p_payload->>'checkTaskId')::uuid, gen_random_uuid()),
        'goat', 'LACTATION_CHECK', 'Lactation follow-up', v_on + 7, v_animal, null
    );
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.lactation_planned.v1', 'goat.plan_lactation.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.inventory_set_reorder_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_item uuid; v_point bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_item := (p_payload->>'itemId')::uuid;
    v_point := (p_payload->>'reorderMilli')::bigint;
    if v_point is null or v_point < 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Reorder point needs a non-negative milli quantity');
    end if;
    if not exists (select 1 from public.inventory_items where farm_id = p_farm_id and id = v_item) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Inventory item not found');
    end if;
    update public.inventory_items set reorder_milli = v_point, updated_at = now()
        where farm_id = p_farm_id and id = v_item;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'inventory.reorder_set.v1', 'inventory.set_reorder.v1', 'inventory_item', v_item,
        'inventory_item:' || v_item::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'inventory_item:' || v_item::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.inventory_record_reorder_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_item uuid; v_onhand bigint; v_point bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'alertId')::uuid;
    v_item := (p_payload->>'itemId')::uuid;
    select quantity_milli, reorder_milli into v_onhand, v_point
        from public.inventory_items where farm_id = p_farm_id and id = v_item;
    if not found then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Inventory item not found');
    end if;
    if v_point <= 0 or v_onhand > v_point then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Reorder alert needs on-hand at or below the reorder point');
    end if;
    insert into public.inventory_reorder_alerts(id, farm_id, item_id, on_hand_milli, reorder_milli, occurred_on)
    values (v_id, p_farm_id, v_item, v_onhand, v_point, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'inventory.reorder_alerted.v1', 'inventory.record_reorder.v1', 'inventory_item', v_item,
        'inventory_item:' || v_item::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'inventory_item:' || v_item::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.group_census_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid; v_heads integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'censusId')::uuid;
    v_group := (p_payload->>'groupId')::uuid;
    v_heads := (p_payload->>'headCount')::integer;
    if v_heads is null or v_heads < 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Census needs a group and a head count');
    end if;
    if not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Census needs a group on this farm');
    end if;
    insert into public.group_census_records(id, farm_id, group_id, head_count, occurred_on)
    values (v_id, p_farm_id, v_group, v_heads, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    update public.animal_groups set head_count = v_heads where farm_id = p_farm_id and id = v_group;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'group.census_recorded.v1', 'group.census.v1', 'animal_group', v_group,
        'animal_group:' || v_group::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal_group:' || v_group::text),
        p_payload, null, null
    );
end;
$$;

grant execute on function public.health_pack_slot_add_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.health_pack_apply_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_lot_place_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_dof_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_lot_close_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.goat_plan_lactation_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.inventory_set_reorder_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.inventory_record_reorder_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.group_census_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
