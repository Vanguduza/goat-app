begin;

create extension if not exists pgtap with schema extensions;
select plan(10);

insert into auth.users (id, email)
values ('41414141-4141-4414-8414-414141414141', 'spine@test.invalid');

insert into public.farms (id, name)
values ('42424242-4242-4424-8424-424242424242', 'Spine Farm');

insert into public.farm_users (farm_id, user_id, role)
values (
    '42424242-4242-4424-8424-424242424242',
    '41414141-4141-4414-8414-414141414141',
    'owner'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '41414141-4141-4414-8414-414141414141', true);

select is(
    public.sheep_register_v1(
        '43434343-4343-4434-8434-434343434341',
        '42424242-4242-4424-8424-424242424242',
        'spine-device',
        0,
        1700000000000,
        jsonb_build_object('animalId','44444444-4444-4444-8444-444444444441','tag','EWE-01','sex','FEMALE')
    )->>'code',
    'ACCEPTED',
    'sheep register through the species-native RPC'
);

select is(
    public.poultry_register_v1(
        '43434343-4343-4434-8434-434343434342',
        '42424242-4242-4424-8424-424242424242',
        'spine-device',
        0,
        1700000000000,
        jsonb_build_object('animalId','44444444-4444-4444-8444-444444444442','tag','HEN-01','sex','FEMALE')
    )->>'code',
    'VALIDATION_REJECTED',
    'poultry register without a kind is rejected'
);

select is(
    public.goat_register_v1(
        '43434343-4343-4434-8434-434343434343',
        '42424242-4242-4424-8424-424242424242',
        'spine-device',
        0,
        1700000000000,
        jsonb_build_object('animalId','44444444-4444-4444-8444-444444444443','tag','DOE-01','name','Nala','sex','FEMALE')
    )->>'code',
    'ACCEPTED',
    'doe can be registered before kidding'
);

select is(
    public.goat_record_kidding_v1(
        '43434343-4343-4434-8434-434343434344',
        '42424242-4242-4424-8424-424242424242',
        'spine-device',
        1,
        1700000001000,
        jsonb_build_object(
            'kiddingId','45454545-4545-4454-8454-454545454541',
            'damAnimalId','44444444-4444-4444-8444-444444444443',
            'bornCount',2,
            'liveCount',2,
            'deadCount',0,
            'occurredEpochDay',19723
        )
    )->>'code',
    'ACCEPTED',
    'kidding is recorded on the doe stream'
);

select is(
    public.health_record_observation_v1(
        '43434343-4343-4434-8434-434343434345',
        '42424242-4242-4424-8424-424242424242',
        'spine-device',
        0,
        1700000002000,
        jsonb_build_object(
            'observationId','46464646-4646-4464-8464-464646464641',
            'speciesCode','goat',
            'animalId','44444444-4444-4444-8444-444444444443',
            'signs','pale eyelids',
            'productName','ivermectin',
            'occurredAtEpochMillis',1700000002000
        )
    )->>'code',
    'VALIDATION_REJECTED',
    'health observation cannot carry a product name'
);

select is(
    public.rabbit_cage_create_v1(
        '43434343-4343-4434-8434-434343434346',
        '42424242-4242-4424-8424-424242424242',
        'spine-device',
        0,
        1700000003000,
        jsonb_build_object('cageId','47474747-4747-4474-8474-474747474741','code','Cage B','doeCapacity',11)
    )->>'code',
    'ACCEPTED',
    'rabbit cage can be created'
);

select is(
    public.rabbit_wave_create_v1(
        '43434343-4343-4434-8434-434343434347',
        '42424242-4242-4424-8424-424242424242',
        'spine-device',
        0,
        1700000004000,
        jsonb_build_object(
            'waveId','48484848-4848-4484-8484-484848484841',
            'cageId','47474747-4747-4474-8474-474747474741',
            'doeCount',11,
            'matingEpochDay',19723
        )
    )->>'code',
    'VALIDATION_REJECTED',
    'KudBat wave of 11 does is rejected without 11 nest boxes'
);

reset role;
insert into public.rabbit_nest_boxes(id, farm_id, cage_id, code)
select
    ('49494949-4949-4494-8494-4949494948' || lpad(g::text, 2, '0'))::uuid,
    '42424242-4242-4424-8424-424242424242',
    '47474747-4747-4474-8474-474747474741',
    'NB-' || lpad(g::text, 2, '0')
from generate_series(1, 11) as g;
set local role authenticated;
select set_config('request.jwt.claim.sub', '41414141-4141-4414-8414-414141414141', true);

select is(
    public.rabbit_wave_create_v1(
        '43434343-4343-4434-8434-434343434348',
        '42424242-4242-4424-8424-424242424242',
        'spine-device',
        0,
        1700000005000,
        jsonb_build_object(
            'waveId','48484848-4848-4484-8484-484848484842',
            'cageId','47474747-4747-4474-8474-474747474741',
            'doeCount',11,
            'matingEpochDay',19723
        )
    )->>'code',
    'ACCEPTED',
    'KudBat wave of 11 does is accepted when 11 nest boxes exist'
);

select is(
    (select nest_in_on - mating_on from public.rabbit_breeding_waves where id = '48484848-4848-4484-8484-484848484842'),
    28,
    'nest box place is mating plus 28 days'
);

select is(
    public.money_record_v1(
        '43434343-4343-4434-8434-434343434349',
        '42424242-4242-4424-8424-424242424242',
        'spine-device',
        0,
        1700000006000,
        jsonb_build_object(
            'recordId','50505050-5050-4505-8505-505050505051',
            'kind','expense',
            'categoryCode','feed',
            'amountMinor',1250,
            'currency','USD',
            'occurredEpochDay',19723
        )
    )->>'code',
    'ACCEPTED',
    'money is recorded as an append-only commercial event'
);

select * from finish();
rollback;
