-- 018: kid records from a kidding. Creates the kid animal and a dam pedigree
-- link. No COI engine.

create table if not exists public.goat_kid_records (
    id uuid primary key,
    farm_id uuid not null references public.farms(id),
    animal_id uuid not null,
    kidding_id uuid not null,
    dam_id uuid not null,
    created_at timestamptz not null default now(),
    unique (farm_id, id),
    unique (farm_id, animal_id)
);

alter table public.goat_kid_records enable row level security;
create policy goat_kids_sel on public.goat_kid_records for select to authenticated using (public.is_farm_member(farm_id));
revoke insert, update, delete on public.goat_kid_records from authenticated;
grant select on public.goat_kid_records to authenticated;

create or replace function public.goat_register_kid_v1(
    p_mutation_id uuid, p_farm_id uuid, p_device_id text, p_expected_stream_version bigint,
    p_occurred_at_epoch_ms bigint, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare
    v_gate jsonb;
    v_kid uuid;
    v_kidding uuid;
    v_dam uuid;
    v_tag text;
    v_sex text;
    v_name text;
    v_live integer;
    v_have integer;
    v_dob date;
begin
    v_gate := public.command_gate_v1(p_farm_id, p_mutation_id);
    if v_gate is not null then return v_gate; end if;
    v_kid := (p_payload->>'animalId')::uuid;
    v_kidding := (p_payload->>'kiddingId')::uuid;
    v_tag := btrim(coalesce(p_payload->>'tag',''));
    v_sex := coalesce(p_payload->>'sex','');
    v_name := nullif(btrim(coalesce(p_payload->>'name','')),'');
    if v_tag = '' or v_sex not in ('FEMALE','MALE') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Kid record needs a tag and listed sex');
    end if;
    select dam_id, live_count, occurred_on into v_dam, v_live, v_dob
        from public.kidding_events where farm_id = p_farm_id and id = v_kidding;
    if not found then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Kid record needs a kidding on this farm');
    end if;
    select count(*) into v_have from public.goat_kid_records where farm_id = p_farm_id and kidding_id = v_kidding;
    if v_have >= v_live then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Kid records cannot exceed live kids from that kidding');
    end if;
    if p_payload ? 'dateOfBirthEpochDay' and p_payload->>'dateOfBirthEpochDay' is not null then
        v_dob := date '1970-01-01' + ((p_payload->>'dateOfBirthEpochDay')::integer);
    end if;
    insert into public.animals(id, farm_id, tag, name, species_code, sex, status, date_of_birth)
    values (v_kid, p_farm_id, v_tag, v_name, 'goat', v_sex, 'active', v_dob);
    insert into public.goat_kid_records(id, farm_id, animal_id, kidding_id, dam_id)
    values (v_kid, p_farm_id, v_kid, v_kidding, v_dam);
    insert into public.pedigree_relations(id, farm_id, animal_id, parent_id, relation_type)
    values (coalesce((p_payload->>'pedigreeLinkId')::uuid, gen_random_uuid()), p_farm_id, v_kid, v_dam, 'dam');
    return public.append_command_event_v1(
        p_mutation_id, p_farm_id, p_device_id, p_occurred_at_epoch_ms,
        'goat.kid_registered.v1', 'goat.register_kid.v1', 'animal', v_kid,
        'animal:' || v_kid::text, 1, p_payload, null, null
    );
end;
$$;

grant execute on function public.goat_register_kid_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;
