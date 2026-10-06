begin;

create extension if not exists pgtap with schema extensions;
select plan(6);

insert into auth.users (id, email)
values ('61616161-6161-4616-8616-616161616161', 'life@test.invalid');
insert into public.farms (id, name)
values ('62626262-6262-4626-8626-626262626262', 'Lifecycle Farm');
insert into public.farm_users (farm_id, user_id, role)
values ('62626262-6262-4626-8626-626262626262','61616161-6161-4616-8616-616161616161','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', '61616161-6161-4616-8616-616161616161', true);

select is(
    public.group_create_v1(
        '63636363-6363-4636-8636-636363636361',
        '62626262-6262-4626-8626-626262626262',
        'life-device', 0, 1700000000000,
        jsonb_build_object('groupId','64646464-6464-4646-8646-646464646461','speciesCode','sheep','name','Scan mob','headCount',40)
    )->>'code',
    'ACCEPTED',
    'sheep mob exists before joining'
);

select is(
    public.sheep_record_joining_v1(
        '63636363-6363-4636-8636-636363636362',
        '62626262-6262-4626-8626-626262626262',
        'life-device', 0, 1700000001000,
        jsonb_build_object('joiningId','65656565-6565-4656-8656-656565656561','groupId','64646464-6464-4646-8646-646464646461','startedEpochDay',19723)
    )->>'code',
    'ACCEPTED',
    'sheep joining records the mob and drafts scan/lambing tasks'
);

select is(
    (select count(*) from public.farm_tasks where farm_id = '62626262-6262-4626-8626-626262626262' and module_code = 'sheep'),
    4::bigint,
    'joining creates four sheep lifecycle tasks'
);

select is(
    public.cattle_record_pd_v1(
        '63636363-6363-4636-8636-636363636363',
        '62626262-6262-4626-8626-626262626262',
        'life-device', 0, 1700000002000,
        jsonb_build_object('pdId','66666666-6666-4666-8666-666666666661','animalId','67676767-6767-4676-8676-676767676761','result','pregnant','occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'cattle PD without a cow is rejected'
);

select is(
    public.rabbit_record_foster_v1(
        '63636363-6363-4636-8636-636363636364',
        '62626262-6262-4626-8626-626262626262',
        'life-device', 0, 1700000003000,
        jsonb_build_object('fosterId','68686868-6868-4686-8686-686868686861','fromWaveId','69696969-6969-4696-8696-696969696961','toWaveId','69696969-6969-4696-8696-696969696961','kitCount',2,'occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'foster into the same wave is rejected'
);

select is(
    public.supplier_create_v1(
        '63636363-6363-4636-8636-636363636365',
        '62626262-6262-4626-8626-626262626262',
        'life-device', 0, 1700000004000,
        jsonb_build_object('supplierId','70707070-7070-4707-8707-707070707071','name','Feed mill')
    )->>'code',
    'ACCEPTED',
    'procurement supplier is created through the versioned RPC'
);

select * from finish();
rollback;
