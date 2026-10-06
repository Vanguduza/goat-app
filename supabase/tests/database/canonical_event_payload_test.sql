begin;

create extension if not exists pgtap with schema extensions;
select plan(8);

insert into auth.users (id, email)
values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'canonical@test.invalid');

insert into public.farms (id, name)
values ('11111111-1111-4111-8111-111111111111', 'Canonical Farm');

insert into public.farm_users (farm_id, user_id, role)
values (
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'owner'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);

select is(
    public.goat_register_v1(
        '55555555-5555-4555-8555-555555555555',
        '11111111-1111-4111-8111-111111111111',
        'device-a',
        0,
        1700000000000,
        jsonb_build_object(
            'animalId', '33333333-3333-4333-8333-333333333333',
            'tag', '  A-TRIM-01  ',
            'name', '  Nala  ',
            'sex', 'FEMALE',
            'dateOfBirthEpochDay', '19723',
            'untrustedExtraField', 'must-not-enter-ledger'
        )
    )->>'code',
    'ACCEPTED',
    'registration accepts caller payload that requires canonicalization'
);

select is(
    (select tag from public.animals where id = '33333333-3333-4333-8333-333333333333'),
    'A-TRIM-01',
    'authoritative relational tag is trimmed'
);

select is(
    (select payload->>'tag' from public.domain_events where mutation_id = '55555555-5555-4555-8555-555555555555'),
    'A-TRIM-01',
    'registered event stores the canonical trimmed tag'
);

select is(
    (select payload->>'name' from public.domain_events where mutation_id = '55555555-5555-4555-8555-555555555555'),
    'Nala',
    'registered event stores the canonical trimmed name'
);

select is(
    (select jsonb_typeof(payload->'dateOfBirthEpochDay') from public.domain_events where mutation_id = '55555555-5555-4555-8555-555555555555'),
    'number',
    'registered event stores dateOfBirthEpochDay as a number'
);

select ok(
    not (select payload ? 'untrustedExtraField' from public.domain_events where mutation_id = '55555555-5555-4555-8555-555555555555'),
    'registered event drops caller-only fields from the replay contract'
);

select is(
    public.goat_record_weight_v1(
        '77777777-7777-4777-8777-777777777777',
        '11111111-1111-4111-8111-111111111111',
        'device-a',
        1,
        1700000001000,
        jsonb_build_object(
            'animalId', '33333333-3333-4333-8333-333333333333',
            'measurementId', '66666666-6666-4666-8666-666666666666',
            'weightGrams', '32450',
            'measuredAtEpochMillis', '1700000001000',
            'untrustedExtraField', true
        )
    )->>'code',
    'ACCEPTED',
    'weight command accepts values that are coercible to authoritative numeric types'
);

select ok(
    (select jsonb_typeof(payload->'weightGrams') = 'number'
         and jsonb_typeof(payload->'measuredAtEpochMillis') = 'number'
         and not (payload ? 'untrustedExtraField')
     from public.domain_events
     where mutation_id = '77777777-7777-4777-8777-777777777777'),
    'weight event stores typed canonical values and drops caller-only fields'
);

select * from finish();
rollback;
