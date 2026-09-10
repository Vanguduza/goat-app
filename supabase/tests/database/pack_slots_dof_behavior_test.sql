begin;

create extension if not exists pgtap with schema extensions;
select plan(6);

insert into auth.users (id, email)
values ('d1d1d1d1-d1d1-41d1-81d1-d1d1d1d1d1d1', 'slots@test.invalid');
insert into public.farms (id, name)
values ('d2d2d2d2-d2d2-42d2-82d2-d2d2d2d2d2d2', 'Slots Farm');
insert into public.farm_users (farm_id, user_id, role)
values ('d2d2d2d2-d2d2-42d2-82d2-d2d2d2d2d2d2','d1d1d1d1-d1d1-41d1-81d1-d1d1d1d1d1d1','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'd1d1d1d1-d1d1-41d1-81d1-d1d1d1d1d1d1', true);

select is(
    public.health_pack_slot_add_v1(
        'd3d3d3d3-d3d3-43d3-83d3-d3d3d3d3d3d1',
        'd2d2d2d2-d2d2-42d2-82d2-d2d2d2d2d2d2',
        'slots-device', 0, 1700000000000,
        jsonb_build_object('slotId','d4d4d4d4-d4d4-44d4-84d4-d4d4d4d4d4d1','packId','d5d5d5d5-d5d5-45d5-85d5-d5d5d5d5d5d1','slotCode','cdt_prepartum','title','CDT prepartum','offsetDays',-28,'fromEvent','expected_birth')
    )->>'code',
    'VALIDATION_REJECTED',
    'slot without a vet-accepted pack is rejected'
);

select is(
    public.cattle_lot_place_v1(
        'd3d3d3d3-d3d3-43d3-83d3-d3d3d3d3d3d2',
        'd2d2d2d2-d2d2-42d2-82d2-d2d2d2d2d2d2',
        'slots-device', 0, 1700000001000,
        jsonb_build_object('placementId','d4d4d4d4-d4d4-44d4-84d4-d4d4d4d4d4d2','groupId','d5d5d5d5-d5d5-45d5-85d5-d5d5d5d5d5d2','headCount',12,'placedEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'lot place without a cattle lot is rejected'
);

select is(
    public.cattle_record_dof_v1(
        'd3d3d3d3-d3d3-43d3-83d3-d3d3d3d3d3d3',
        'd2d2d2d2-d2d2-42d2-82d2-d2d2d2d2d2d2',
        'slots-device', 0, 1700000002000,
        jsonb_build_object('recordId','d4d4d4d4-d4d4-44d4-84d4-d4d4d4d4d4d3','groupId','d5d5d5d5-d5d5-45d5-85d5-d5d5d5d5d5d2','daysOnFeed',-1,'occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'negative days on feed is rejected'
);

select is(
    public.goat_plan_lactation_v1(
        'd3d3d3d3-d3d3-43d3-83d3-d3d3d3d3d3d4',
        'd2d2d2d2-d2d2-42d2-82d2-d2d2d2d2d2d2',
        'slots-device', 0, 1700000003000,
        jsonb_build_object('planId','d4d4d4d4-d4d4-44d4-84d4-d4d4d4d4d4d4','animalId','d5d5d5d5-d5d5-45d5-85d5-d5d5d5d5d5d3','occurredEpochDay',19723,'checkTaskId','d6d6d6d6-d6d6-46d6-86d6-d6d6d6d6d6d1')
    )->>'code',
    'VALIDATION_REJECTED',
    'lactation plan without a doe is rejected'
);

select is(
    public.inventory_record_reorder_v1(
        'd3d3d3d3-d3d3-43d3-83d3-d3d3d3d3d3d5',
        'd2d2d2d2-d2d2-42d2-82d2-d2d2d2d2d2d2',
        'slots-device', 0, 1700000004000,
        jsonb_build_object('alertId','d4d4d4d4-d4d4-44d4-84d4-d4d4d4d4d4d5','itemId','d5d5d5d5-d5d5-45d5-85d5-d5d5d5d5d5d4','occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'reorder alert without stock below the point is rejected'
);

select is(
    public.group_census_v1(
        'd3d3d3d3-d3d3-43d3-83d3-d3d3d3d3d3d6',
        'd2d2d2d2-d2d2-42d2-82d2-d2d2d2d2d2d2',
        'slots-device', 0, 1700000005000,
        jsonb_build_object('censusId','d4d4d4d4-d4d4-44d4-84d4-d4d4d4d4d4d6','groupId','d5d5d5d5-d5d5-45d5-85d5-d5d5d5d5d5d2','headCount',8,'occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'census without a group is rejected'
);

select * from finish();
rollback;
