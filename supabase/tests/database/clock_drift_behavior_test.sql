begin;

create extension if not exists pgtap with schema extensions;
select plan(5);

insert into auth.users (id, email)
values ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'clock-drift@test.invalid');

insert into public.farms (id, name)
values ('33333333-3333-4333-8333-333333333333', 'Clock Drift Farm');

insert into public.farm_users (farm_id, user_id, role)
values (
    '33333333-3333-4333-8333-333333333333',
    'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    'owner'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', true);

select is(
    public.goat_register_v1(
        'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
        '33333333-3333-4333-8333-333333333333',
        'device-clock-ahead',
        0,
        1900000000000,
        jsonb_build_object(
            'animalId', 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
            'tag', 'CLOCK-001',
            'name', 'Chronos',
            'sex', 'MALE'
        )
    )->>'code',
    'ACCEPTED',
    'future-skewed registration timestamp is accepted without becoming authoritative ordering'
);

select is(
    public.goat_record_weight_v1(
        'ffffffff-ffff-4fff-8fff-ffffffffffff',
        '33333333-3333-4333-8333-333333333333',
        'device-clock-behind',
        1,
        1500000000000,
        jsonb_build_object(
            'animalId', 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
            'measurementId', 'abababab-abab-4bab-8bab-abababababab',
            'weightGrams', 28500,
            'measuredAtEpochMillis', 1500000000000
        )
    )->>'code',
    'ACCEPTED',
    'backward-skewed later mutation is accepted at the next stream version'
);

select ok(
    (
        select later.occurred_at < earlier.occurred_at
        from public.domain_events earlier
        join public.domain_events later
          on later.farm_id = earlier.farm_id
         and later.aggregate_id = earlier.aggregate_id
        where earlier.mutation_id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
          and later.mutation_id = 'ffffffff-ffff-4fff-8fff-ffffffffffff'
    ),
    'client occurred_at can move backwards without changing causal stream order'
);

select ok(
    (
        select later.change_cursor > earlier.change_cursor
           and later.stream_version > earlier.stream_version
        from public.domain_events earlier
        join public.domain_events later
          on later.farm_id = earlier.farm_id
         and later.aggregate_id = earlier.aggregate_id
        where earlier.mutation_id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
          and later.mutation_id = 'ffffffff-ffff-4fff-8fff-ffffffffffff'
    ),
    'authoritative stream version and change cursor remain monotonic under clock rewind'
);

select ok(
    (
        select later.recorded_at >= earlier.recorded_at
        from public.domain_events earlier
        join public.domain_events later
          on later.farm_id = earlier.farm_id
         and later.aggregate_id = earlier.aggregate_id
        where earlier.mutation_id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
          and later.mutation_id = 'ffffffff-ffff-4fff-8fff-ffffffffffff'
    ),
    'server recorded_at remains non-decreasing even when device occurred_at is reversed'
);

select * from finish();
rollback;
