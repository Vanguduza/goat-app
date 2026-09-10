begin;

create extension if not exists pgtap with schema extensions;
select plan(8);

insert into auth.users (id, email)
values ('51515151-5151-4515-8515-515151515151', 'ops-exp@test.invalid');

insert into public.farms (id, name)
values ('52525252-5252-4525-8525-525252525252', 'Ops Expansion Farm');

insert into public.farm_users (farm_id, user_id, role)
values (
    '52525252-5252-4525-8525-525252525252',
    '51515151-5151-4515-8515-515151515151',
    'owner'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '51515151-5151-4515-8515-515151515151', true);

select is(
    public.group_create_v1(
        '53535353-5353-4535-8535-535353535351',
        '52525252-5252-4525-8525-525252525252',
        'ops-device',
        0,
        1700000000000,
        jsonb_build_object('groupId','54545454-5454-4545-8545-545454545451','speciesCode','goat','name','Doe mob','headCount',12)
    )->>'code',
    'ACCEPTED',
    'animal group is created through the versioned RPC'
);

select is(
    public.paddock_create_v1(
        '53535353-5353-4535-8535-535353535352',
        '52525252-5252-4525-8525-525252525252',
        'ops-device',
        0,
        1700000001000,
        jsonb_build_object('paddockId','55555555-5555-4555-8555-555555555551','code','P1','displayName','East rest','waterSource','trough','shade',true)
    )->>'code',
    'ACCEPTED',
    'paddock is created through the versioned RPC'
);

select is(
    public.grazing_start_v1(
        '53535353-5353-4535-8535-535353535353',
        '52525252-5252-4525-8525-525252525252',
        'ops-device',
        0,
        1700000002000,
        jsonb_build_object('sessionId','56565656-5656-4565-8565-565656565651','paddockId','55555555-5555-4555-8555-555555555551','groupId','54545454-5454-4545-8545-545454545451','headCount',12,'enteredEpochDay',19723)
    )->>'code',
    'ACCEPTED',
    'one group can enter a paddock'
);

select is(
    public.grazing_start_v1(
        '53535353-5353-4535-8535-535353535354',
        '52525252-5252-4525-8525-525252525252',
        'ops-device',
        0,
        1700000003000,
        jsonb_build_object('sessionId','56565656-5656-4565-8565-565656565652','paddockId','55555555-5555-4555-8555-555555555551','groupId','54545454-5454-4545-8545-545454545451','headCount',8,'enteredEpochDay',19724)
    )->>'code',
    'VALIDATION_REJECTED',
    'overlapping open grazing on the same paddock is rejected'
);

select is(
    public.health_record_treatment_v1(
        '53535353-5353-4535-8535-535353535355',
        '52525252-5252-4525-8525-525252525252',
        'ops-device',
        0,
        1700000004000,
        jsonb_build_object(
            'treatmentId','57575757-5757-4575-8575-575757575751',
            'speciesCode','goat',
            'reason','pale eyelids',
            'productName','ivermectin',
            'occurredAtEpochMillis',1700000004000
        )
    )->>'code',
    'VALIDATION_REJECTED',
    'free-typed treatment product is rejected'
);

select is(
    public.formulary_item_create_v1(
        '53535353-5353-4535-8535-535353535356',
        '52525252-5252-4525-8525-525252525252',
        'ops-device',
        0,
        1700000005000,
        jsonb_build_object(
            'itemId','58585858-5858-4585-8585-585858585851',
            'productName','CDT label pack',
            'speciesCode','goat',
            'vetClass','vaccine',
            'meatWithdrawalDays',21,
            'vetApproved',true
        )
    )->>'code',
    'ACCEPTED',
    'vet-approved formulary item can be recorded'
);

select is(
    public.health_record_treatment_v1(
        '53535353-5353-4535-8535-535353535357',
        '52525252-5252-4525-8525-525252525252',
        'ops-device',
        0,
        1700000006000,
        jsonb_build_object(
            'treatmentId','57575757-5757-4575-8575-575757575752',
            'speciesCode','goat',
            'formularyItemId','58585858-5858-4585-8585-585858585851',
            'reason','prepartum CDT slot',
            'occurredAtEpochMillis',1700000006000
        )
    )->>'code',
    'ACCEPTED',
    'treatment against a vet-approved formulary item is accepted'
);

select is(
    public.sale_record_v1(
        '53535353-5353-4535-8535-535353535358',
        '52525252-5252-4525-8525-525252525252',
        'ops-device',
        0,
        1700000007000,
        jsonb_build_object(
            'saleId','59595959-5959-4595-8595-595959595951',
            'itemKind','live_goat',
            'quantityMilli',1000,
            'amountMinor',18500,
            'occurredEpochDay',19723
        )
    )->>'code',
    'ACCEPTED',
    'sale records income in integer minor units'
);

select * from finish();
rollback;
