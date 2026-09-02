begin;

create extension if not exists pgtap with schema extensions;
select plan(18);

-- Deterministic identities for tenant and idempotency tests.
insert into auth.users (id, email)
values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'farm-a@test.invalid'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'farm-b@test.invalid');

insert into public.farms (id, name)
values
    ('11111111-1111-4111-8111-111111111111', 'Farm A'),
    ('22222222-2222-4222-8222-222222222222', 'Farm B');

insert into public.farm_users (farm_id, user_id, role)
values
    ('11111111-1111-4111-8111-111111111111', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'owner'),
    ('22222222-2222-4222-8222-222222222222', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'owner');

-- Seed a foreign-tenant row before switching to authenticated RLS context.
insert into public.animals (
    id, farm_id, species_code, tag, name, sex, status
) values (
    '44444444-4444-4444-8444-444444444444',
    '22222222-2222-4222-8222-222222222222',
    'goat', 'B-001', 'Foreign goat', 'FEMALE', 'active'
);

create or replace function pg_temp.cross_farm_measurement_fk_rejected()
returns boolean
language plpgsql
as $$
begin
    insert into public.measurements (
        id, farm_id, animal_id, type, value_long, unit, measured_at
    ) values (
        '14141414-1414-4414-8414-141414141414',
        '11111111-1111-4111-8111-111111111111',
        '44444444-4444-4444-8444-444444444444',
        'weight', 12500, 'g', now()
    );
    return false;
exception
    when foreign_key_violation then
        return true;
end;
$$;

select ok(
    pg_temp.cross_farm_measurement_fk_rejected(),
    'farm-aware measurement foreign key rejects cross-farm animal references'
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
            'tag', 'A-001',
            'name', 'Nala',
            'sex', 'FEMALE'
        )
    )->>'code',
    'ACCEPTED',
    'farm member can register a goat'
);

select is(
    public.goat_register_v1(
        '55555555-5555-4555-8555-555555555555',
        '11111111-1111-4111-8111-111111111111',
        'device-a',
        0,
        1700000000000,
        jsonb_build_object(
            'animalId', '33333333-3333-4333-8333-333333333333',
            'tag', 'A-001',
            'name', 'Nala',
            'sex', 'FEMALE'
        )
    )->>'code',
    'ALREADY_APPLIED',
    'replaying the same mutation is idempotent'
);

select is(
    (select count(*)::bigint from public.animals
     where farm_id = '11111111-1111-4111-8111-111111111111'
       and id = '33333333-3333-4333-8333-333333333333'),
    1::bigint,
    'duplicate register creates one animal'
);

select is(
    (select count(*)::bigint from public.domain_events
     where farm_id = '11111111-1111-4111-8111-111111111111'
       and mutation_id = '55555555-5555-4555-8555-555555555555'),
    1::bigint,
    'duplicate register creates one authoritative event'
);

select is(
    (select count(*)::bigint from public.command_receipts
     where farm_id = '11111111-1111-4111-8111-111111111111'
       and mutation_id = '55555555-5555-4555-8555-555555555555'),
    1::bigint,
    'duplicate register creates one command receipt'
);

select is(
    (select count(*)::bigint from public.animals
     where farm_id = '22222222-2222-4222-8222-222222222222'),
    0::bigint,
    'RLS hides another farm animal from user A'
);

select is(
    public.goat_register_v1(
        '12121212-1212-4212-8212-121212121212',
        '22222222-2222-4222-8222-222222222222',
        'device-a',
        0,
        1700000000001,
        jsonb_build_object(
            'animalId', '13131313-1313-4313-8313-131313131313',
            'tag', 'ATTACK-001',
            'name', 'Cross farm attempt',
            'sex', 'MALE'
        )
    )->>'code',
    'AUTH_REJECTED',
    'RPC rejects a cross-farm mutation'
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
            'weightGrams', 32450,
            'measuredAtEpochMillis', 1700000001000
        )
    )->>'code',
    'ACCEPTED',
    'weight command accepts the current expected stream version'
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
            'weightGrams', 32450,
            'measuredAtEpochMillis', 1700000001000
        )
    )->>'code',
    'ALREADY_APPLIED',
    'replaying the same weight mutation is idempotent'
);

select is(
    public.goat_record_weight_v1(
        '99999999-9999-4999-8999-999999999999',
        '11111111-1111-4111-8111-111111111111',
        'device-b',
        1,
        1700000002000,
        jsonb_build_object(
            'animalId', '33333333-3333-4333-8333-333333333333',
            'measurementId', '88888888-8888-4888-8888-888888888888',
            'weightGrams', 33000,
            'measuredAtEpochMillis', 1700000002000
        )
    )->>'code',
    'CONFLICT',
    'stale expected stream version is rejected'
);

select is(
    (public.goat_record_weight_v1(
        '99999999-9999-4999-8999-999999999999',
        '11111111-1111-4111-8111-111111111111',
        'device-b',
        1,
        1700000002000,
        jsonb_build_object(
            'animalId', '33333333-3333-4333-8333-333333333333',
            'measurementId', '88888888-8888-4888-8888-888888888888',
            'weightGrams', 33000,
            'measuredAtEpochMillis', 1700000002000
        )
    )->>'streamVersion')::bigint,
    2::bigint,
    'conflict returns the authoritative current stream version'
);

select is(
    (select count(*)::bigint from public.measurements
     where farm_id = '11111111-1111-4111-8111-111111111111'
       and animal_id = '33333333-3333-4333-8333-333333333333'),
    1::bigint,
    'stale conflict does not create a second measurement'
);

select is(
    (select count(*)::bigint
     from public.pull_changes_v1('11111111-1111-4111-8111-111111111111', 0, 200)),
    2::bigint,
    'pull returns the two accepted farm A events'
);

select is(
    (select count(*)::bigint
     from public.pull_changes_v1('22222222-2222-4222-8222-222222222222', 0, 200)),
    0::bigint,
    'pull cannot read another farm through a supplied farm id'
);

select is(
    (
        select count(*)::bigint
        from (
            select
                change_cursor,
                lag(change_cursor) over (order by change_cursor) as previous_cursor
            from public.pull_changes_v1('11111111-1111-4111-8111-111111111111', 0, 200)
        ) ordered
        where previous_cursor is not null
          and change_cursor <= previous_cursor
    ),
    0::bigint,
    'pull cursor is strictly monotonic'
);

select set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true);

select is(
    (select count(*)::bigint from public.animals
     where farm_id = '22222222-2222-4222-8222-222222222222'),
    1::bigint,
    'user B sees its own farm animal'
);

select is(
    (select count(*)::bigint from public.animals
     where farm_id = '11111111-1111-4111-8111-111111111111'),
    0::bigint,
    'user B cannot see farm A animals'
);

select * from finish();
rollback;
