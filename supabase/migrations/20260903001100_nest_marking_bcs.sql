-- Rabbit nest-box cycle, wave wean, sheep marking/wean, cattle BCS.
-- Writes stay on versioned RPCs.

alter table public.rabbit_nest_boxes drop constraint if exists rabbit_nest_boxes_status_check;
alter table public.rabbit_nest_boxes
    add constraint rabbit_nest_boxes_status_check
    check (status in ('available','sanitized','assigned','in_cage','dirty'));

create table if not exists public.rabbit_weans (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    wave_id uuid not null,
    weaned_count integer not null check (weaned_count >= 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.sheep_markings (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid,
    animal_id uuid,
    marked_count integer not null check (marked_count > 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (group_id is not null or animal_id is not null)
);

create table if not exists public.sheep_weanings (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    group_id uuid,
    animal_id uuid,
    weaned_count integer not null check (weaned_count > 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (group_id is not null or animal_id is not null)
);

create table if not exists public.cattle_bcs_scores (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    scale text not null check (scale in ('1_5','1_9')),
    score_tenths integer not null,
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (
        (scale = '1_5' and score_tenths between 10 and 50) or
        (scale = '1_9' and score_tenths between 10 and 90)
    )
);

create table if not exists public.sheep_wool_clips (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid,
    group_id uuid,
    greasy_grams integer not null check (greasy_grams > 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    check (animal_id is not null or group_id is not null)
);

alter table public.rabbit_weans enable row level security;
alter table public.sheep_markings enable row level security;
alter table public.sheep_weanings enable row level security;
alter table public.cattle_bcs_scores enable row level security;
alter table public.sheep_wool_clips enable row level security;

create policy rabbit_weans_sel on public.rabbit_weans for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_markings_sel on public.sheep_markings for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_weanings_sel on public.sheep_weanings for select to authenticated using (public.is_farm_member(farm_id));
create policy cattle_bcs_sel on public.cattle_bcs_scores for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_wool_sel on public.sheep_wool_clips for select to authenticated using (public.is_farm_member(farm_id));

revoke insert, update, delete on public.rabbit_weans, public.sheep_markings, public.sheep_weanings,
    public.cattle_bcs_scores, public.sheep_wool_clips from authenticated;
grant select on public.rabbit_weans, public.sheep_markings, public.sheep_weanings,
    public.cattle_bcs_scores, public.sheep_wool_clips to authenticated;

create or replace function public.rabbit_nest_box_set_status_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_from text; v_to text;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'nestBoxId')::uuid;
    v_to := p_payload->>'status';
    select status into v_from from public.rabbit_nest_boxes where farm_id = p_farm_id and id = v_id;
    if v_from is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Nest box not found');
    end if;
    if not (
        (v_from in ('available','sanitized') and v_to = 'assigned') or
        (v_from in ('available','sanitized','assigned') and v_to = 'in_cage') or
        (v_from in ('assigned','in_cage') and v_to = 'dirty') or
        (v_from = 'dirty' and v_to in ('sanitized','available'))
    ) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Nest box status change is not allowed');
    end if;
    update public.rabbit_nest_boxes set status = v_to where farm_id = p_farm_id and id = v_id;
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.nest_box_status_changed.v1', 'rabbit.nest_box_set_status.v1', 'rabbit_nest_box', v_id,
        'rabbit_nest_box:' || v_id::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'rabbit_nest_box:' || v_id::text),
        p_payload || jsonb_build_object('fromStatus', v_from), null, null
    );
end;
$$;

create or replace function public.rabbit_record_wean_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_wave uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'weanId')::uuid;
    v_wave := (p_payload->>'waveId')::uuid;
    if (p_payload->>'weanedCount')::integer is null or (p_payload->>'weanedCount')::integer < 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Wean needs a kit count');
    end if;
    if not exists (select 1 from public.rabbit_breeding_waves where farm_id = p_farm_id and id = v_wave) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Wave not found');
    end if;
    insert into public.rabbit_weans(id, farm_id, wave_id, weaned_count, occurred_on)
    values (v_id, p_farm_id, v_wave, (p_payload->>'weanedCount')::integer, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'rabbit.weaned.v1', 'rabbit.record_wean.v1', 'rabbit_wave', v_wave,
        'rabbit_wave:' || v_wave::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'rabbit_wave:' || v_wave::text),
        p_payload, null, null
    );
end;
$$;

create or replace function public.sheep_record_marking_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid; v_animal uuid; v_count int;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'markingId')::uuid;
    v_group := nullif(p_payload->>'groupId','')::uuid;
    v_animal := nullif(p_payload->>'animalId','')::uuid;
    v_count := (p_payload->>'markedCount')::integer;
    if v_count is null or v_count <= 0 or (v_group is null and v_animal is null) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Marking needs a mob or lamb and a count');
    end if;
    if v_group is not null and not exists (select 1 from public.animal_groups where farm_id = p_farm_id and id = v_group and species_code = 'sheep') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Sheep mob not found');
    end if;
    if v_animal is not null and not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'sheep') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Lamb not found');
    end if;
    insert into public.sheep_markings(id, farm_id, group_id, animal_id, marked_count, occurred_on)
    values (v_id, p_farm_id, v_group, v_animal, v_count, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.marked.v1', 'sheep.record_marking.v1',
        case when v_animal is not null then 'animal' else 'animal_group' end,
        coalesce(v_animal, v_group),
        case when v_animal is not null then 'animal:' || v_animal::text else 'animal_group:' || v_group::text end,
        1, p_payload, case when v_animal is not null then 'animal' end, v_animal
    );
end;
$$;

create or replace function public.sheep_record_weaning_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_group uuid; v_animal uuid; v_count int;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'weaningId')::uuid;
    v_group := nullif(p_payload->>'groupId','')::uuid;
    v_animal := nullif(p_payload->>'animalId','')::uuid;
    v_count := (p_payload->>'weanedCount')::integer;
    if v_count is null or v_count <= 0 or (v_group is null and v_animal is null) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Weaning needs a mob or lamb and a count');
    end if;
    insert into public.sheep_weanings(id, farm_id, group_id, animal_id, weaned_count, occurred_on)
    values (v_id, p_farm_id, v_group, v_animal, v_count, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.weaned.v1', 'sheep.record_weaning.v1',
        case when v_animal is not null then 'animal' else 'animal_group' end,
        coalesce(v_animal, v_group),
        case when v_animal is not null then 'animal:' || v_animal::text else 'animal_group:' || v_group::text end,
        1, p_payload, case when v_animal is not null then 'animal' end, v_animal
    );
end;
$$;

create or replace function public.cattle_record_bcs_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_scale text; v_score int;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'scoreId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    v_scale := coalesce(p_payload->>'scale','1_5');
    v_score := (p_payload->>'scoreTenths')::integer;
    if v_scale not in ('1_5','1_9') or v_score is null
        or (v_scale = '1_5' and v_score not between 10 and 50)
        or (v_scale = '1_9' and v_score not between 10 and 90) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','BCS must use the 1-5 or 1-9 scale');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'cattle') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Cow not found');
    end if;
    insert into public.cattle_bcs_scores(id, farm_id, animal_id, scale, score_tenths, occurred_on)
    values (v_id, p_farm_id, v_animal, v_scale, v_score, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.bcs_recorded.v1', 'cattle.record_bcs.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, 'animal', v_animal
    );
end;
$$;

create or replace function public.sheep_record_wool_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid; v_group uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'clipId')::uuid;
    v_animal := nullif(p_payload->>'animalId','')::uuid;
    v_group := nullif(p_payload->>'groupId','')::uuid;
    if (p_payload->>'greasyGrams')::integer is null or (p_payload->>'greasyGrams')::integer <= 0 or (v_animal is null and v_group is null) then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Wool clip needs greasy grams and a sheep or mob');
    end if;
    insert into public.sheep_wool_clips(id, farm_id, animal_id, group_id, greasy_grams, occurred_on)
    values (v_id, p_farm_id, v_animal, v_group, (p_payload->>'greasyGrams')::integer, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.wool_clipped.v1', 'sheep.record_wool.v1',
        case when v_animal is not null then 'animal' else 'animal_group' end,
        coalesce(v_animal, v_group),
        case when v_animal is not null then 'animal:' || v_animal::text else 'animal_group:' || v_group::text end,
        1, p_payload, case when v_animal is not null then 'animal' end, v_animal
    );
end;
$$;

grant execute on function public.rabbit_nest_box_set_status_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.rabbit_record_wean_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_marking_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_weaning_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_bcs_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
create table if not exists public.cattle_milk_records (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    litres_milli bigint not null check (litres_milli > 0),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.sheep_dag_scores (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    score integer not null check (score between 0 and 5),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

create table if not exists public.sheep_footrot_scores (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    score integer not null check (score between 0 and 5),
    occurred_on date not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id)
);

alter table public.cattle_milk_records enable row level security;
alter table public.sheep_dag_scores enable row level security;
alter table public.sheep_footrot_scores enable row level security;
create policy cattle_milk_sel on public.cattle_milk_records for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_dag_sel on public.sheep_dag_scores for select to authenticated using (public.is_farm_member(farm_id));
create policy sheep_footrot_sel on public.sheep_footrot_scores for select to authenticated using (public.is_farm_member(farm_id));
revoke insert, update, delete on public.cattle_milk_records, public.sheep_dag_scores, public.sheep_footrot_scores from authenticated;
grant select on public.cattle_milk_records, public.sheep_dag_scores, public.sheep_footrot_scores to authenticated;

create or replace function public.cattle_record_milk_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare v_gate jsonb; v_id uuid; v_animal uuid;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_id := (p_payload->>'milkId')::uuid;
    v_animal := (p_payload->>'animalId')::uuid;
    if (p_payload->>'litresMilli')::bigint is null or (p_payload->>'litresMilli')::bigint <= 0 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Milk record needs litres');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'cattle' and sex = 'FEMALE' and status = 'active') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Active cow not found');
    end if;
    insert into public.cattle_milk_records(id, farm_id, animal_id, litres_milli, occurred_on)
    values (v_id, p_farm_id, v_animal, (p_payload->>'litresMilli')::bigint, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'cattle.milk_recorded.v1', 'cattle.record_milk.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, 'animal', v_animal
    );
end;
$$;

create or replace function public.sheep_record_dag_v1(
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
    if v_score is null or v_score < 0 or v_score > 5 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Dag score must be 0 to 5');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'sheep') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Sheep not found');
    end if;
    insert into public.sheep_dag_scores(id, farm_id, animal_id, score, occurred_on)
    values (v_id, p_farm_id, v_animal, v_score, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.dag_recorded.v1', 'sheep.record_dag.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, 'animal', v_animal
    );
end;
$$;

create or replace function public.sheep_record_footrot_v1(
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
    if v_score is null or v_score < 0 or v_score > 5 then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Footrot score must be 0 to 5');
    end if;
    if not exists (select 1 from public.animals where farm_id = p_farm_id and id = v_animal and species_code = 'sheep') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Sheep not found');
    end if;
    insert into public.sheep_footrot_scores(id, farm_id, animal_id, score, occurred_on)
    values (v_id, p_farm_id, v_animal, v_score, date '1970-01-01' + ((p_payload->>'occurredEpochDay')::integer));
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'sheep.footrot_recorded.v1', 'sheep.record_footrot.v1', 'animal', v_animal,
        'animal:' || v_animal::text,
        (select coalesce(max(stream_version),0)+1 from public.domain_events where stream_id = 'animal:' || v_animal::text),
        p_payload, 'animal', v_animal
    );
end;
$$;

grant execute on function public.sheep_record_wool_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.cattle_record_milk_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_dag_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
grant execute on function public.sheep_record_footrot_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
