begin;

create extension if not exists pgtap with schema extensions;
select plan(4);

insert into auth.users (id, email)
values ('71717171-7171-4717-8717-717171717171', 'nest@test.invalid');
insert into public.farms (id, name)
values ('72727272-7272-4727-8727-727272727272', 'Nest Farm');
insert into public.farm_users (farm_id, user_id, role)
values ('72727272-7272-4727-8727-727272727272','71717171-7171-4717-8717-717171717171','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', '71717171-7171-4717-8717-717171717171', true);

select is(
    public.rabbit_cage_create_v1(
        '73737373-7373-4737-8737-737373737371',
        '72727272-7272-4727-8727-727272727272',
        'nest-device', 0, 1700000000000,
        jsonb_build_object('cageId','74747474-7474-4747-8747-747474747471','code','A','doeCapacity',11)
    )->>'code',
    'ACCEPTED',
    'cage exists before nest box status changes'
);

select is(
    public.rabbit_nest_box_create_v1(
        '73737373-7373-4737-8737-737373737372',
        '72727272-7272-4727-8727-727272727272',
        'nest-device', 0, 1700000001000,
        jsonb_build_object('nestBoxId','75757575-7575-4757-8757-757575757571','cageId','74747474-7474-4747-8747-747474747471','code','N1')
    )->>'code',
    'ACCEPTED',
    'nest box starts available'
);

select is(
    public.rabbit_nest_box_set_status_v1(
        '73737373-7373-4737-8737-737373737373',
        '72727272-7272-4727-8727-727272727272',
        'nest-device', 0, 1700000002000,
        jsonb_build_object('nestBoxId','75757575-7575-4757-8757-757575757571','status','dirty')
    )->>'code',
    'VALIDATION_REJECTED',
    'available cannot jump to dirty'
);

select is(
    public.cattle_record_bcs_v1(
        '73737373-7373-4737-8737-737373737374',
        '72727272-7272-4727-8727-727272727272',
        'nest-device', 0, 1700000003000,
        jsonb_build_object('scoreId','76767676-7676-4767-8767-767676767671','animalId','77777777-7777-4777-8777-777777777771','scale','1_5','scoreTenths',25,'occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'BCS without a cow is rejected'
);

select * from finish();
rollback;
