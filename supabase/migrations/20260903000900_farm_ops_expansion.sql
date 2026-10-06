-- Farm ops expansion: groups, pasture, labour, assets, feed, water, sales,
-- vet-approved formulary treatments, FAMACHA, poultry flock days.
-- Writes stay on versioned RPCs. Authenticated clients remain select-only.

create table if not exists public.animal_groups (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    species_code text not null check (species_code in ('goat','rabbit','sheep','cattle','poultry')),
    name text not null,
    head_count integer not null check (head_count > 0),
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, species_code, name)
);

create table if not exists public.paddocks (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    code text not null,
    display_name text not null,
    area_m2 integer,
    water_source text not null default 'none' check (water_source in ('none','trough','stream','dam','pipeline')),
    shade boolean not null default false,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, code)
);

create table if not exists public.grazing_sessions (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    paddock_id uuid not null,
    group_id uuid not null,
    species_code text not null,
    entered_on date not null,
    exited_on date,
    head_count integer not null check (head_count > 0),
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, paddock_id) references public.paddocks(farm_id, id),
    foreign key (farm_id, group_id) references public.animal_groups(farm_id, id),
    check (exited_on is null or exited_on >= entered_on)
);

create unique index if not exists grazing_one_open_paddock
    on public.grazing_sessions(farm_id, paddock_id)
    where exited_on is null;

create table if not exists public.labour_entries (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    worker_name text not null,
    task_code text not null,
    minutes integer not null check (minutes > 0),
    occurred_on date not null,
    note text,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.farm_assets (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    code text not null,
    name text not null,
    kind text not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, code)
);

create table if not exists public.maintenance_events (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    asset_id uuid not null,
    title text not null,
    occurred_on date not null,
    note text,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, asset_id) references public.farm_assets(farm_id, id)
);

create table if not exists public.feed_issues (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    item_id uuid not null,
    group_id uuid,
    quantity_milli bigint not null check (quantity_milli > 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, item_id) references public.inventory_items(farm_id, id)
);

create table if not exists public.water_records (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    source text not null,
    litres_milli bigint not null check (litres_milli > 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.sales_records (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    item_kind text not null,
    quantity_milli bigint not null check (quantity_milli > 0),
    amount_minor bigint not null check (amount_minor > 0),
    currency text not null default 'USD',
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.formulary_items (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    product_name text not null,
    species_code text not null,
    vet_class text not null,
    meat_withdrawal_days integer,
    milk_withdrawal_days integer,
    egg_withdrawal_days integer,
    vet_approved boolean not null default false,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.health_treatments (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid,
    species_code text not null,
    formulary_item_id uuid not null,
    reason text not null,
    meat_withdrawal_days integer,
    milk_withdrawal_days integer,
    egg_withdrawal_days integer,
    occurred_at timestamptz not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    foreign key (farm_id, formulary_item_id) references public.formulary_items(farm_id, id)
);

create table if not exists public.famacha_scores (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    score integer not null check (score between 1 and 5),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.poultry_flock_days (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid not null,
    eggs integer not null default 0 check (eggs >= 0),
    dead integer not null default 0 check (dead >= 0),
    culls integer not null default 0 check (culls >= 0),
    feed_grams bigint not null default 0 check (feed_grams >= 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, group_id, occurred_on),
    foreign key (farm_id, group_id) references public.animal_groups(farm_id, id)
);

create table if not exists public.disease_catalog (
    code text primary key,
    species_code text not null,
    display_name text not null,
    signs text not null,
    first_aid text not null,
    prevention text not null,
    vet_class text not null,
    red_flag boolean not null default false
);

insert into public.disease_catalog(code, species_code, display_name, signs, first_aid, prevention, vet_class, red_flag)
values
    ('enterotoxemia_cd','goat','Enterotoxemia','Sudden death, convulsions, bloated kids on rich feed','Isolate remaining animals, stop sudden grain, call the vet','CDT pack accepted by the attending vet','vaccine',true),
    ('haemonchus','goat','Haemonchus','Pale eyelids, bottle jaw, weakness','Shade and water. Do not blanket-drench the herd.','FAMACHA and FEC protocol, not a calendar drench','anthelmintic',true),
    ('gi_stasis','rabbit','Gut stasis','No faeces, hunched, off feed','Keep warm, offer hay and water, call the vet the same day','Hay always available, reduce stress','fluids',true),
    ('pasteurella_snuffles','rabbit','Snuffles','Sneeze, nasal or ocular discharge','Isolate, reduce dust, call the vet','Ventilation and biosecurity','antibiotic',false),
    ('pregnancy_toxaemia_sheep','sheep','Pregnancy toxaemia','Late gestation, down, multiples, poor condition','Prop up and offer energy only from the farm vet SOP','Scan for multiples and feed the litter size','fluids',true),
    ('calf_scour','cattle','Calf scour','Watery faeces, dull calf','Isolate, keep warm, call the vet','Colostrum timing and dry calving area','fluids',true),
    ('coccidiosis_poultry','poultry','Coccidiosis','Bloody droppings, dull chicks','Dry litter, isolate affected birds, call the vet','Litter dryness and a vet-accepted pack','coccidiostat',false)
on conflict (code) do nothing;

alter table public.animal_groups enable row level security;
alter table public.paddocks enable row level security;
alter table public.grazing_sessions enable row level security;
alter table public.labour_entries enable row level security;
alter table public.farm_assets enable row level security;
alter table public.maintenance_events enable row level security;
alter table public.feed_issues enable row level security;
alter table public.water_records enable row level security;
alter table public.sales_records enable row level security;
alter table public.formulary_items enable row level security;
alter table public.health_treatments enable row level security;
alter table public.famacha_scores enable row level security;
alter table public.poultry_flock_days enable row level security;
alter table public.disease_catalog enable row level security;

create policy animal_groups_member_select on public.animal_groups for select to authenticated using (public.is_farm_member(farm_id));
create policy paddocks_member_select on public.paddocks for select to authenticated using (public.is_farm_member(farm_id));
create policy grazing_member_select on public.grazing_sessions for select to authenticated using (public.is_farm_member(farm_id));
create policy labour_member_select on public.labour_entries for select to authenticated using (public.is_farm_member(farm_id));
create policy assets_member_select on public.farm_assets for select to authenticated using (public.is_farm_member(farm_id));
create policy maintenance_member_select on public.maintenance_events for select to authenticated using (public.is_farm_member(farm_id));
create policy feed_issues_member_select on public.feed_issues for select to authenticated using (public.is_farm_member(farm_id));
create policy water_member_select on public.water_records for select to authenticated using (public.is_farm_member(farm_id));
create policy sales_member_select on public.sales_records for select to authenticated using (public.is_farm_member(farm_id));
create policy formulary_member_select on public.formulary_items for select to authenticated using (public.is_farm_member(farm_id));
create policy treatments_member_select on public.health_treatments for select to authenticated using (public.is_farm_member(farm_id));
create policy famacha_member_select on public.famacha_scores for select to authenticated using (public.is_farm_member(farm_id));
create policy flock_days_member_select on public.poultry_flock_days for select to authenticated using (public.is_farm_member(farm_id));
create policy disease_catalog_select on public.disease_catalog for select to authenticated using (true);

revoke insert, update, delete on public.animal_groups, public.paddocks, public.grazing_sessions,
    public.labour_entries, public.farm_assets, public.maintenance_events, public.feed_issues,
    public.water_records, public.sales_records, public.formulary_items, public.health_treatments,
    public.famacha_scores, public.poultry_flock_days, public.disease_catalog from authenticated;
grant select on public.animal_groups, public.paddocks, public.grazing_sessions, public.labour_entries,
    public.farm_assets, public.maintenance_events, public.feed_issues, public.water_records,
    public.sales_records, public.formulary_items, public.health_treatments, public.famacha_scores,
    public.poultry_flock_days, public.disease_catalog to authenticated;

create or replace function public.group_create_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_species text; v_name text; v_head integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'groupId')::uuid;
    v_species := p_payload->>'speciesCode';
    v_name := btrim(coalesce(p_payload->>'name',''));
    v_head := (p_payload->>'headCount')::integer;
    if v_name = '' or v_head is null or v_head <= 0 or v_species not in ('goat','rabbit','sheep','cattle','poultry') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Group needs a species, name, and head count');
    end if;
    insert into public.animal_groups(id, farm_id, species_code, name, head_count)
    values (v_id, p_farm_id, v_species, v_name, v_head);
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'group.created.v1', 'group.create.v1', 'animal_group', v_id, 'animal_group:' || v_id::text, 1, p_payload, null, null);
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','Group name already exists for this species');
end;
$$;

create or replace function public.paddock_create_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_code text; v_name text; v_water text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'paddockId')::uuid;
    v_code := btrim(coalesce(p_payload->>'code',''));
    v_name := btrim(coalesce(p_payload->>'displayName', v_code));
    v_water := coalesce(nullif(btrim(p_payload->>'waterSource'),''),'none');
    if v_code = '' or v_water not in ('none','trough','stream','dam','pipeline') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Paddock needs a code and a water source');
    end if;
    insert into public.paddocks(id, farm_id, code, display_name, area_m2, water_source, shade)
    values (v_id, p_farm_id, v_code, v_name, (p_payload->>'areaM2')::integer, v_water, coalesce((p_payload->>'shade')::boolean, false));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'paddock.created.v1', 'paddock.create.v1', 'paddock', v_id, 'paddock:' || v_id::text, 1, p_payload, null, null);
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','Paddock code already exists');
end;
$$;

create or replace function public.grazing_start_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_paddock uuid; v_group uuid; v_head integer; v_species text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'sessionId')::uuid;
    v_paddock := (p_payload->>'paddockId')::uuid;
    v_group := (p_payload->>'groupId')::uuid;
    v_head := (p_payload->>'headCount')::integer;
    select species_code into v_species from public.animal_groups where farm_id = p_farm_id and id = v_group;
    if v_species is null or v_head is null or v_head <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Grazing needs a group and head count');
    end if;
    if not exists (select 1 from public.paddocks where farm_id = p_farm_id and id = v_paddock and active) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Paddock not found');
    end if;
    if exists (select 1 from public.grazing_sessions where farm_id = p_farm_id and paddock_id = v_paddock and exited_on is null) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','This paddock already has an open grazing session');
    end if;
    insert into public.grazing_sessions(id, farm_id, paddock_id, group_id, species_code, entered_on, head_count)
    values (v_id, p_farm_id, v_paddock, v_group, v_species, date '1970-01-01' + ((p_payload->>'enteredEpochDay')::integer), v_head);
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'grazing.started.v1', 'grazing.start.v1', 'grazing_session', v_id, 'grazing_session:' || v_id::text, 1, p_payload, null, null);
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','This paddock already has an open grazing session');
end;
$$;

create or replace function public.grazing_end_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_exit date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'sessionId')::uuid;
    v_exit := date '1970-01-01' + ((p_payload->>'exitedEpochDay')::integer);
    update public.grazing_sessions
    set exited_on = v_exit
    where farm_id = p_farm_id and id = v_id and exited_on is null;
    if not found then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Open grazing session not found');
    end if;
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'grazing.ended.v1', 'grazing.end.v1', 'grazing_session', v_id, 'grazing_session:' || v_id::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'grazing_session:' || v_id::text),
        p_payload, null, null);
end;
$$;

create or replace function public.labour_record_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'entryId')::uuid;
    if btrim(coalesce(p_payload->>'workerName','')) = '' or btrim(coalesce(p_payload->>'taskCode','')) = '' or (p_payload->>'minutes')::integer is null or (p_payload->>'minutes')::integer <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Labour needs a worker, task code, and minutes');
    end if;
    insert into public.labour_entries(id, farm_id, worker_name, task_code, minutes, occurred_on, note)
    values (v_id, p_farm_id, btrim(p_payload->>'workerName'), btrim(p_payload->>'taskCode'), (p_payload->>'minutes')::integer,
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer), nullif(btrim(coalesce(p_payload->>'note','')),''));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'labour.recorded.v1', 'labour.record.v1', 'labour_entry', v_id, 'labour_entry:' || v_id::text, 1, p_payload, null, null);
end;
$$;

create or replace function public.asset_create_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'assetId')::uuid;
    if btrim(coalesce(p_payload->>'code','')) = '' or btrim(coalesce(p_payload->>'name','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Asset needs a code and name');
    end if;
    insert into public.farm_assets(id, farm_id, code, name, kind)
    values (v_id, p_farm_id, btrim(p_payload->>'code'), btrim(p_payload->>'name'), coalesce(nullif(btrim(p_payload->>'kind'),''),'equipment'));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'asset.created.v1', 'asset.create.v1', 'farm_asset', v_id, 'farm_asset:' || v_id::text, 1, p_payload, null, null);
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','Asset code already exists');
end;
$$;

create or replace function public.maintenance_record_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_asset uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'eventId')::uuid;
    v_asset := (p_payload->>'assetId')::uuid;
    if btrim(coalesce(p_payload->>'title','')) = '' or not exists (select 1 from public.farm_assets where farm_id = p_farm_id and id = v_asset) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Maintenance needs an asset and title');
    end if;
    insert into public.maintenance_events(id, farm_id, asset_id, title, occurred_on, note)
    values (v_id, p_farm_id, v_asset, btrim(p_payload->>'title'), date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer),
        nullif(btrim(coalesce(p_payload->>'note','')),''));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'maintenance.recorded.v1', 'maintenance.record.v1', 'farm_asset', v_asset, 'farm_asset:' || v_asset::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'farm_asset:' || v_asset::text),
        p_payload, null, null);
end;
$$;

create or replace function public.feed_issue_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_item uuid; v_qty bigint; v_current bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'issueId')::uuid;
    v_item := (p_payload->>'itemId')::uuid;
    v_qty := (p_payload->>'quantityMilli')::bigint;
    select quantity_milli into v_current from public.inventory_items where farm_id = p_farm_id and id = v_item;
    if v_current is null or v_qty is null or v_qty <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Feed issue needs a stock item and quantity');
    end if;
    if v_current < v_qty then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Not enough feed on this farm');
    end if;
    insert into public.feed_issues(id, farm_id, item_id, group_id, quantity_milli, occurred_on)
    values (v_id, p_farm_id, v_item, nullif(p_payload->>'groupId','')::uuid, v_qty, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    insert into public.inventory_movements(id, farm_id, item_id, direction, quantity_milli, occurred_at)
    values (v_id, p_farm_id, v_item, 'issue', v_qty, to_timestamp(p_occurred_at_epoch_ms / 1000.0));
    update public.inventory_items set quantity_milli = quantity_milli - v_qty, updated_at = now()
    where farm_id = p_farm_id and id = v_item;
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'feed.issued.v1', 'feed.issue.v1', 'inventory_item', v_item, 'inventory_item:' || v_item::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'inventory_item:' || v_item::text),
        p_payload, null, null);
end;
$$;

create or replace function public.water_record_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'recordId')::uuid;
    if btrim(coalesce(p_payload->>'source','')) = '' or (p_payload->>'litresMilli')::bigint is null or (p_payload->>'litresMilli')::bigint <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Water record needs a source and litres');
    end if;
    insert into public.water_records(id, farm_id, source, litres_milli, occurred_on)
    values (v_id, p_farm_id, btrim(p_payload->>'source'), (p_payload->>'litresMilli')::bigint, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'water.recorded.v1', 'water.record.v1', 'water_record', v_id, 'water_record:' || v_id::text, 1, p_payload, null, null);
end;
$$;

create or replace function public.sale_record_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_amount bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'saleId')::uuid;
    v_amount := (p_payload->>'amountMinor')::bigint;
    if btrim(coalesce(p_payload->>'itemKind','')) = '' or (p_payload->>'quantityMilli')::bigint is null or (p_payload->>'quantityMilli')::bigint <= 0 or v_amount is null or v_amount <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Sale needs an item, quantity, and amount');
    end if;
    insert into public.sales_records(id, farm_id, item_kind, quantity_milli, amount_minor, currency, occurred_on)
    values (v_id, p_farm_id, btrim(p_payload->>'itemKind'), (p_payload->>'quantityMilli')::bigint, v_amount,
        coalesce(nullif(btrim(p_payload->>'currency'),''),'USD'), date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    insert into public.money_records(id, farm_id, kind, category_code, amount_minor, currency, occurred_on, note)
    values (v_id, p_farm_id, 'income', 'sales', v_amount, coalesce(nullif(btrim(p_payload->>'currency'),''),'USD'),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer), btrim(p_payload->>'itemKind'));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sale.recorded.v1', 'sale.record.v1', 'sale_record', v_id, 'sale_record:' || v_id::text, 1, p_payload, null, null);
end;
$$;

create or replace function public.formulary_item_create_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'itemId')::uuid;
    if btrim(coalesce(p_payload->>'productName','')) = '' or btrim(coalesce(p_payload->>'speciesCode','')) = '' or btrim(coalesce(p_payload->>'vetClass','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Formulary item needs a product, species, and vet class');
    end if;
    if coalesce((p_payload->>'vetApproved')::boolean, false) is not true then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Formulary items must be marked vet-approved before they can be used');
    end if;
    insert into public.formulary_items(id, farm_id, product_name, species_code, vet_class, meat_withdrawal_days, milk_withdrawal_days, egg_withdrawal_days, vet_approved)
    values (v_id, p_farm_id, btrim(p_payload->>'productName'), btrim(p_payload->>'speciesCode'), btrim(p_payload->>'vetClass'),
        (p_payload->>'meatWithdrawalDays')::integer, (p_payload->>'milkWithdrawalDays')::integer, (p_payload->>'eggWithdrawalDays')::integer, true);
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'formulary.item_created.v1', 'formulary.item_create.v1', 'formulary_item', v_id, 'formulary_item:' || v_id::text, 1, p_payload, null, null);
end;
$$;

create or replace function public.health_record_treatment_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_form uuid; v_meat integer; v_milk integer; v_egg integer;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    if p_payload ? 'productName' or p_payload ? 'dose' or p_payload ? 'medication' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Treatments must use a vet-approved formulary item, not a free-typed product or dose');
    end if;
    v_id := (p_payload->>'treatmentId')::uuid;
    v_form := (p_payload->>'formularyItemId')::uuid;
    select meat_withdrawal_days, milk_withdrawal_days, egg_withdrawal_days
    into v_meat, v_milk, v_egg
    from public.formulary_items
    where farm_id = p_farm_id and id = v_form and vet_approved;
    if v_form is null or not found then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Treatment needs a vet-approved formulary item');
    end if;
    if btrim(coalesce(p_payload->>'reason','')) = '' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Treatment reason is required');
    end if;
    insert into public.health_treatments(id, farm_id, animal_id, species_code, formulary_item_id, reason, meat_withdrawal_days, milk_withdrawal_days, egg_withdrawal_days, occurred_at)
    values (v_id, p_farm_id, nullif(p_payload->>'animalId','')::uuid, btrim(p_payload->>'speciesCode'), v_form, btrim(p_payload->>'reason'),
        v_meat, v_milk, v_egg, to_timestamp((p_payload->>'occurredAtEpochMillis')::bigint / 1000.0));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'health.treatment_recorded.v1', 'health.record_treatment.v1', 'health_treatment', v_id, 'health_treatment:' || v_id::text, 1, p_payload, null, null);
end;
$$;

create or replace function public.goat_record_famacha_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_score integer; v_current bigint;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'scoreId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_score := (p_payload->>'score')::integer;
    if v_score is null or v_score < 1 or v_score > 5 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','FAMACHA score must be 1 to 5');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'goat' and status = 'active') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Active goat not found');
    end if;
    select coalesce(max(stream_version),0) into v_current from public.domain_events where stream_id = 'animal:' || v_animal::text;
    if p_expected_stream_version is not null and p_expected_stream_version <> v_current then
        return jsonb_build_object('code','CONFLICT','streamVersion',v_current,'safeMessage','Goat changed on another device');
    end if;
    insert into public.famacha_scores(id, farm_id, animal_id, score, occurred_on)
    values (v_id, p_farm_id, v_animal, v_score, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.famacha_recorded.v1', 'goat.record_famacha.v1', 'animal', v_animal, 'animal:' || v_animal::text, v_current + 1, p_payload, 'animal', v_animal);
end;
$$;

create or replace function public.poultry_flock_day_v1(p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint, p_occurred_at_epoch_ms bigint, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'dayId')::uuid;
    v_group := (p_payload->>'groupId')::uuid;
    if not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'poultry') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Poultry flock day needs a poultry group');
    end if;
    insert into public.poultry_flock_days(id, farm_id, group_id, eggs, dead, culls, feed_grams, occurred_on)
    values (v_id, p_farm_id, v_group,
        coalesce((p_payload->>'eggs')::integer, 0),
        coalesce((p_payload->>'dead')::integer, 0),
        coalesce((p_payload->>'culls')::integer, 0),
        coalesce((p_payload->>'feedGrams')::bigint, 0),
        date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'poultry.flock_day_recorded.v1', 'poultry.flock_day.v1', 'animal_group', v_group, 'animal_group:' || v_group::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal_group:' || v_group::text),
        p_payload, null, null);
exception when unique_violation then
    return jsonb_build_object('code','CONFLICT','safeMessage','This flock already has a sheet for that day');
end;
$$;

grant execute on function public.group_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.paddock_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.grazing_start_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.grazing_end_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.labour_record_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.asset_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.maintenance_record_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.feed_issue_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.water_record_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sale_record_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.formulary_item_create_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.health_record_treatment_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.goat_record_famacha_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.poultry_flock_day_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;

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
    elsif new.event_type in ('health.observation_recorded.v1','health.treatment_recorded.v1') then
        new.payload := new.payload - 'productName' - 'dose' - 'medication';
    end if;
    return new;
end;
$$;
