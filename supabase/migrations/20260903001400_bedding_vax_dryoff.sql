-- Rabbit programme inventory links (bind only; nest place does not auto-issue),
-- poultry flock vaccination against a vet-approved formulary item,
-- cattle dry-off as a recorded dairy event.
-- Writes stay on versioned RPCs. Quantity stays integer milli-units.

create table if not exists public.rabbit_programme_inventory_links (
    farm_id uuid primary key references public.farms(id),
    nest_bedding_item_id uuid,
    nest_bedding_qty_milli bigint not null default 1000 check (nest_bedding_qty_milli > 0),
    doe_feed_item_id uuid,
    low_stock_notify boolean not null default true,
    updated_at timestamptz not null default now(),
    foreign key (farm_id, nest_bedding_item_id) references public.inventory_items(farm_id, id),
    foreign key (farm_id, doe_feed_item_id) references public.inventory_items(farm_id, id)
);

create table if not exists public.poultry_vaccinations (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid not null,
    poultry_kind_code text not null check (poultry_kind_code in (
        'chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined'
    )),
    formulary_item_id uuid not null,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, group_id) references public.animal_groups(farm_id, id),
    foreign key (farm_id, formulary_item_id) references public.formulary_items(farm_id, id)
);

create table if not exists public.cattle_dry_offs (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    occurred_on date not null,
    expected_calving_on date,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

alter table public.rabbit_programme_inventory_links enable row level security;
alter table public.poultry_vaccinations enable row level security;
alter table public.cattle_dry_offs enable row level security;

create policy rabbit_links_sel on public.rabbit_programme_inventory_links for select to authenticated using (public.is_farm_member(farm_id));
create policy poultry_vax_sel on public.poultry_vaccinations for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_dry_sel on public.cattle_dry_offs for select to authenticated using (public.is_farm_member(farm_id));

revoke insert, update, delete on public.rabbit_programme_inventory_links, public.poultry_vaccinations, public.cattle_dry_offs from authenticated;
grant select on public.rabbit_programme_inventory_links, public.poultry_vaccinations, public.cattle_dry_offs to authenticated;

create or replace function public.rabbit_bedding_bind_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_bed uuid; v_feed uuid; v_qty bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_bed := nullif(p_payload->>'beddingItemId','')::uuid;
    v_feed := nullif(p_payload->>'feedItemId','')::uuid;
    v_qty := coalesce((p_payload->>'beddingQtyMilli')::bigint, 1000);
    if v_qty is null or v_qty <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Bedding bind needs a quantity in milli-units');
    end if;
    if v_bed is not null and not exists (select 1 from public.inventory_items where farm_id = p_farm_id and id = v_bed) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Bedding item not found on this farm');
    end if;
    if v_feed is not null and not exists (select 1 from public.inventory_items where farm_id = p_farm_id and id = v_feed) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Feed item not found on this farm');
    end if;
    insert into public.rabbit_programme_inventory_links(farm_id, nest_bedding_item_id, nest_bedding_qty_milli, doe_feed_item_id)
    values (p_farm_id, v_bed, v_qty, v_feed)
    on conflict (farm_id) do update
        set nest_bedding_item_id = excluded.nest_bedding_item_id,
            nest_bedding_qty_milli = excluded.nest_bedding_qty_milli,
            doe_feed_item_id = excluded.doe_feed_item_id,
            updated_at = now();
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.bedding_bound.v1', 'rabbit.bedding_bind.v1', 'farm', p_farm_id,
        'farm:' || p_farm_id::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'farm:' || p_farm_id::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.poultry_record_vaccination_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid; v_kind text; v_form uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'vaccinationId')::uuid;
    v_group := (p_payload->>'groupId')::uuid;
    v_kind := coalesce(p_payload->>'poultryKindCode','');
    v_form := (p_payload->>'formularyItemId')::uuid;
    if v_kind not in ('chicken','duck','muscovy','guinea_fowl','turkey','goose','quail','pigeon','farm_defined') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Vaccination needs a poultry kind');
    end if;
    if not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'poultry') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Vaccination needs a poultry flock');
    end if;
    if not exists (
        select 1 from public.formulary_items
        where farm_id = p_farm_id and id = v_form and vet_approved and species_code = 'poultry'
    ) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Vaccination needs a vet-approved poultry formulary item');
    end if;
    insert into public.poultry_vaccinations(id, farm_id, group_id, poultry_kind_code, formulary_item_id, occurred_on)
    values (v_id, p_farm_id, v_group, v_kind, v_form, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'poultry.vaccination_recorded.v1', 'poultry.record_vaccination.v1', 'animal_group', v_group,
        'animal_group:' || v_group::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal_group:' || v_group::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.cattle_record_dryoff_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_calving date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'dryOffId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_calving := case
        when nullif(p_payload->>'expectedCalvingEpochDay','') is null then null
        else date '1970-01-01' + ((p_payload->>'expectedCalvingEpochDay')::integer)
    end;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'cattle') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Dry-off needs a cow');
    end if;
    insert into public.cattle_dry_offs(id, farm_id, animal_id, occurred_on, expected_calving_on)
    values (v_id, p_farm_id, v_animal, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer), v_calving);
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.dryoff_recorded.v1', 'cattle.record_dryoff.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, null, null
    );
end;
$$;

grant execute on function public.rabbit_bedding_bind_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_record_vaccination_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_dryoff_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
