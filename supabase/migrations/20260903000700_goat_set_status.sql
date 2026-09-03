-- Lifecycle transitions for goats. Historical records stay; status is a compensating event.

create or replace function public.goat_set_status_v1(
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
    v_status text;
    v_current_version bigint;
    v_current_status text;
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
    v_status := lower(btrim(coalesce(p_payload->>'status', '')));

    if v_status not in ('sold','dead','culled') then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Goat status must be sold, dead, or culled');
    end if;

    select status into v_current_status
    from public.animals
    where farm_id = p_farm_id and id = v_animal_id and species_code = 'goat';

    if v_current_status is null then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Goat not found');
    end if;

    if v_current_status <> 'active' then
        return jsonb_build_object('code','VALIDATION_REJECTED','safeMessage','Only an active goat can change lifecycle status');
    end if;

    select coalesce(max(stream_version), 0) into v_current_version
    from public.domain_events
    where stream_id = 'animal:' || v_animal_id::text;

    if p_expected_stream_version is not null and p_expected_stream_version <> v_current_version then
        return jsonb_build_object('code','CONFLICT','streamVersion',v_current_version,'safeMessage','Goat changed on another device');
    end if;

    update public.animals
    set status = v_status,
        updated_at = now()
    where farm_id = p_farm_id and id = v_animal_id;

    insert into public.domain_events(
        event_type, schema_version, farm_id, aggregate_type, aggregate_id,
        stream_id, stream_version, occurred_at, actor_user_id, device_id,
        mutation_id, payload
    ) values (
        'goat.status_changed.v1', 1, p_farm_id, 'animal', v_animal_id,
        'animal:' || v_animal_id::text, v_current_version + 1,
        to_timestamp(p_occurred_at_epoch_ms / 1000.0), auth.uid(), p_device_id,
        p_mutation_id, p_payload
    ) returning event_id, change_cursor into v_event_id, v_cursor;

    insert into public.command_receipts(farm_id, mutation_id, command_name, event_id, stream_version, change_cursor)
    values (p_farm_id, p_mutation_id, 'goat.set_status.v1', v_event_id, v_current_version + 1, v_cursor);

    insert into public.search_index_jobs(farm_id, entity_type, entity_id, operation, projection_version)
    values (p_farm_id, 'animal', v_animal_id, 'upsert', v_current_version + 1);

    return jsonb_build_object(
        'code','ACCEPTED',
        'eventId',v_event_id,
        'streamVersion',v_current_version + 1,
        'changeCursor',v_cursor
    );
end;
$$;

grant execute on function public.goat_set_status_v1(uuid,uuid,text,bigint,bigint,jsonb) to authenticated;

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
                when new.payload ? 'dateOfBirthEpochDay'
                     and new.payload->>'dateOfBirthEpochDay' is not null
                    then (new.payload->>'dateOfBirthEpochDay')::bigint
                else null
            end
        );
    elsif new.event_type = 'goat.weight_recorded.v1' then
        new.payload := jsonb_build_object(
            'animalId', new.aggregate_id::text,
            'measurementId', ((new.payload->>'measurementId')::uuid)::text,
            'weightGrams', (new.payload->>'weightGrams')::bigint,
            'measuredAtEpochMillis', (new.payload->>'measuredAtEpochMillis')::bigint
        );
    elsif new.event_type = 'goat.status_changed.v1' then
        new.payload := jsonb_build_object(
            'animalId', new.aggregate_id::text,
            'status', lower(btrim(coalesce(new.payload->>'status', '')))
        );
    end if;

    return new;
end;
$$;
