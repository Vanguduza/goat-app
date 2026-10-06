-- Canonicalize persisted goat domain-event payloads at the authoritative ledger boundary.
-- Domain events are replay contracts: they must contain normalized, typed values rather than
-- caller-shaped JSON that can diverge from the authoritative relational projection.

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
    end if;

    return new;
end;
$$;

drop trigger if exists canonicalize_goat_event_payload_before_insert on public.domain_events;
create trigger canonicalize_goat_event_payload_before_insert
before insert on public.domain_events
for each row
execute function public.canonicalize_goat_event_payload_v1();
