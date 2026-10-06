begin;

create extension if not exists pgtap with schema extensions;
select plan(6);

insert into auth.users (id, email)
values ('c1c1c1c1-c1c1-41c1-81c1-c1c1c1c1c1c1', 'repro@test.invalid');
insert into public.farms (id, name)
values ('c2c2c2c2-c2c2-42c2-82c2-c2c2c2c2c2c2', 'Repro Farm');
insert into public.farm_users (farm_id, user_id, role)
values ('c2c2c2c2-c2c2-42c2-82c2-c2c2c2c2c2c2','c1c1c1c1-c1c1-41c1-81c1-c1c1c1c1c1c1','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'c1c1c1c1-c1c1-41c1-81c1-c1c1c1c1c1c1', true);

select is(
    public.goat_record_heat_v1(
        'c3c3c3c3-c3c3-43c3-83c3-c3c3c3c3c3c1',
        'c2c2c2c2-c2c2-42c2-82c2-c2c2c2c2c2c2',
        'repro-device', 0, 1700000000000,
        jsonb_build_object('heatId','c4c4c4c4-c4c4-44c4-84c4-c4c4c4c4c4c1','animalId','c5c5c5c5-c5c5-45c5-85c5-c5c5c5c5c5c1','occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'heat without a doe is rejected'
);

select is(
    public.goat_record_mating_v1(
        'c3c3c3c3-c3c3-43c3-83c3-c3c3c3c3c3c2',
        'c2c2c2c2-c2c2-42c2-82c2-c2c2c2c2c2c2',
        'repro-device', 0, 1700000001000,
        jsonb_build_object('matingId','c4c4c4c4-c4c4-44c4-84c4-c4c4c4c4c4c2','damId','c5c5c5c5-c5c5-45c5-85c5-c5c5c5c5c5c1','method','maybe','occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'mating method must be listed'
);

select is(
    public.inventory_lot_issue_v1(
        'c3c3c3c3-c3c3-43c3-83c3-c3c3c3c3c3c3',
        'c2c2c2c2-c2c2-42c2-82c2-c2c2c2c2c2c2',
        'repro-device', 0, 1700000002000,
        jsonb_build_object('itemId','c6c6c6c6-c6c6-46c6-86c6-c6c6c6c6c6c1','quantityMilli',1000)
    )->>'code',
    'VALIDATION_REJECTED',
    'FEFO issue without lot stock is rejected'
);

select is(
    public.official_record_movement_v1(
        'c3c3c3c3-c3c3-43c3-83c3-c3c3c3c3c3c4',
        'c2c2c2c2-c2c2-42c2-82c2-c2c2c2c2c2c2',
        'repro-device', 0, 1700000003000,
        jsonb_build_object('movementId','c7c7c7c7-c7c7-47c7-87c7-c7c7c7c7c7c1','animalId','c5c5c5c5-c5c5-45c5-85c5-c5c5c5c5c5c1','direction','on','occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'official movement without cattle or sheep is rejected'
);

select is(
    public.sheep_record_famacha_v1(
        'c3c3c3c3-c3c3-43c3-83c3-c3c3c3c3c3c5',
        'c2c2c2c2-c2c2-42c2-82c2-c2c2c2c2c2c2',
        'repro-device', 0, 1700000004000,
        jsonb_build_object('scoreId','c8c8c8c8-c8c8-48c8-88c8-c8c8c8c8c8c1','animalId','c5c5c5c5-c5c5-45c5-85c5-c5c5c5c5c5c1','score',3,'occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'sheep FAMACHA without a sheep is rejected'
);

select is(
    public.pedigree_link_v1(
        'c3c3c3c3-c3c3-43c3-83c3-c3c3c3c3c3c6',
        'c2c2c2c2-c2c2-42c2-82c2-c2c2c2c2c2c2',
        'repro-device', 0, 1700000005000,
        jsonb_build_object('linkId','c9c9c9c9-c9c9-49c9-89c9-c9c9c9c9c9c1','animalId','c5c5c5c5-c5c5-45c5-85c5-c5c5c5c5c5c1','parentId','c5c5c5c5-c5c5-45c5-85c5-c5c5c5c5c5c1','relationType','sire')
    )->>'code',
    'VALIDATION_REJECTED',
    'pedigree cannot link an animal to itself'
);

select * from finish();
rollback;
