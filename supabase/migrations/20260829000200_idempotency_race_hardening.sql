-- A retry may race the first attempt before command_receipts is visible.
-- Unique-key arbitration remains the final guard; after the losing statement unwinds,
-- re-read the receipt and return ALREADY_APPLIED when the same mutation won elsewhere.

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
        return jsonb_build_object('code','CONFLICT','safeMessage','Measurement identifier or stream version already exists');
end;
$$;
