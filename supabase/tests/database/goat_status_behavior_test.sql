begin;

create extension if not exists pgtap with schema extensions;
select plan(6);

insert into auth.users (id, email)
values ('31313131-3131-4313-8313-313131313131', 'status@test.invalid');

insert into public.farms (id, name)
values ('32323232-3232-4323-8323-323232323232', 'Status Farm');

insert into public.farm_users (farm_id, user_id, role)
values (
    '32323232-3232-4323-8323-323232323232',
    '31313131-3131-4313-8313-313131313131',
    'owner'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '31313131-3131-4313-8313-313131313131', true);

select is(
    public.goat_register_v1(
        '33333333-3333-4333-8333-333333333331',
        '32323232-3232-4323-8323-323232323232',
        'status-device',
        0,
        1700000000000,
        jsonb_build_object(
            'animalId', '34343434-3434-4343-8343-343434343434',
            'tag', 'STS-001',
            'name', 'Nala',
            'sex', 'FEMALE'
        )
    )->>'code',
    'ACCEPTED',
    'goat can be registered before a lifecycle change'
);

select is(
    public.goat_set_status_v1(
        '35353535-3535-4353-8353-353535353535',
        '32323232-3232-4323-8323-323232323232',
        'status-device',
        1,
        1700000001000,
        jsonb_build_object(
            'animalId', '34343434-3434-4343-8343-343434343434',
            'status', 'sold'
        )
    )->>'code',
    'ACCEPTED',
    'active goat can move to sold through the versioned lifecycle RPC'
);

select is(
    (select status from public.animals where id = '34343434-3434-4343-8343-343434343434'),
    'sold',
    'authoritative animal row records the sold lifecycle state'
);

select is(
    (select payload->>'status' from public.domain_events where mutation_id = '35353535-3535-4353-8353-353535353535'),
    'sold',
    'status event stores the canonical lowercase status'
);

select is(
    public.goat_record_weight_v1(
        '36363636-3636-4363-8363-363636363636',
        '32323232-3232-4323-8323-323232323232',
        'status-device',
        2,
        1700000002000,
        jsonb_build_object(
            'animalId', '34343434-3434-4343-8343-343434343434',
            'measurementId', '37373737-3737-4373-8373-373737373737',
            'weightGrams', 32000,
            'measuredAtEpochMillis', 1700000002000
        )
    )->>'code',
    'VALIDATION_REJECTED',
    'sold goat cannot accept a later weight'
);

select is(
    public.goat_set_status_v1(
        '38383838-3838-4383-8383-383838383838',
        '32323232-3232-4323-8323-323232323232',
        'status-device',
        2,
        1700000003000,
        jsonb_build_object(
            'animalId', '34343434-3434-4343-8343-343434343434',
            'status', 'dead'
        )
    )->>'code',
    'VALIDATION_REJECTED',
    'sold goat cannot be transitioned again'
);

select * from finish();
rollback;
