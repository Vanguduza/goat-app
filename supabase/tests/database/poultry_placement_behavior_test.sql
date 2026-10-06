begin;

create extension if not exists pgtap with schema extensions;
select plan(2);

insert into auth.users (id, email)
values ('b1b1b1b1-b1b1-41b1-81b1-b1b1b1b1b1b1', 'place@test.invalid');
insert into public.farms (id, name)
values ('b2b2b2b2-b2b2-42b2-82b2-b2b2b2b2b2b2', 'Place Farm');
insert into public.farm_users (farm_id, user_id, role)
values ('b2b2b2b2-b2b2-42b2-82b2-b2b2b2b2b2b2','b1b1b1b1-b1b1-41b1-81b1-b1b1b1b1b1b1','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b1b1b1b1-b1b1-41b1-81b1-b1b1b1b1b1b1', true);

select is(
    public.poultry_flock_place_v1(
        'b3b3b3b3-b3b3-43b3-83b3-b3b3b3b3b3b1',
        'b2b2b2b2-b2b2-42b2-82b2-b2b2b2b2b2b2',
        'place-device', 0, 1700000000000,
        jsonb_build_object(
            'placementId','b4b4b4b4-b4b4-44b4-84b4-b4b4b4b4b4b1',
            'groupId','b5b5b5b5-b5b5-45b5-85b5-b5b5b5b5b5b1',
            'houseId','b6b6b6b6-b6b6-46b6-86b6-b6b6b6b6b6b1',
            'poultryKindCode','chicken',
            'headCount',40,
            'occurredEpochDay',19723
        )
    )->>'code',
    'VALIDATION_REJECTED',
    'placement without a flock and house is rejected'
);

select is(
    public.poultry_record_biosecurity_v1(
        'b3b3b3b3-b3b3-43b3-83b3-b3b3b3b3b3b2',
        'b2b2b2b2-b2b2-42b2-82b2-b2b2b2b2b2b2',
        'place-device', 0, 1700000001000,
        jsonb_build_object('walkId','b7b7b7b7-b7b7-47b7-87b7-b7b7b7b7b7b1','findings','','occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'biosecurity walk needs findings and a house or flock'
);

select * from finish();
rollback;
