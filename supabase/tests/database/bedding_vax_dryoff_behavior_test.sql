begin;

create extension if not exists pgtap with schema extensions;
select plan(3);

insert into auth.users (id, email)
values ('a1a1a1a1-a1a1-41a1-81a1-a1a1a1a1a1a1', 'bind@test.invalid');
insert into public.farms (id, name)
values ('a2a2a2a2-a2a2-42a2-82a2-a2a2a2a2a2a2', 'Bind Farm');
insert into public.farm_users (farm_id, user_id, role)
values ('a2a2a2a2-a2a2-42a2-82a2-a2a2a2a2a2a2','a1a1a1a1-a1a1-41a1-81a1-a1a1a1a1a1a1','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'a1a1a1a1-a1a1-41a1-81a1-a1a1a1a1a1a1', true);

select is(
    public.rabbit_bedding_bind_v1(
        'a3a3a3a3-a3a3-43a3-83a3-a3a3a3a3a3a1',
        'a2a2a2a2-a2a2-42a2-82a2-a2a2a2a2a2a2',
        'bind-device', 0, 1700000000000,
        jsonb_build_object('beddingItemId','a4a4a4a4-a4a4-44a4-84a4-a4a4a4a4a4a1','beddingQtyMilli',2000)
    )->>'code',
    'VALIDATION_REJECTED',
    'bedding bind without an inventory item is rejected'
);

select is(
    public.poultry_record_vaccination_v1(
        'a3a3a3a3-a3a3-43a3-83a3-a3a3a3a3a3a2',
        'a2a2a2a2-a2a2-42a2-82a2-a2a2a2a2a2a2',
        'bind-device', 0, 1700000001000,
        jsonb_build_object(
            'vaccinationId','a5a5a5a5-a5a5-45a5-85a5-a5a5a5a5a5a1',
            'groupId','a6a6a6a6-a6a6-46a6-86a6-a6a6a6a6a6a1',
            'poultryKindCode','chicken',
            'formularyItemId','a7a7a7a7-a7a7-47a7-87a7-a7a7a7a7a7a1',
            'occurredEpochDay',19723
        )
    )->>'code',
    'VALIDATION_REJECTED',
    'poultry vaccination without a flock and formulary item is rejected'
);

select is(
    public.cattle_record_dryoff_v1(
        'a3a3a3a3-a3a3-43a3-83a3-a3a3a3a3a3a3',
        'a2a2a2a2-a2a2-42a2-82a2-a2a2a2a2a2a2',
        'bind-device', 0, 1700000002000,
        jsonb_build_object('dryOffId','a8a8a8a8-a8a8-48a8-88a8-a8a8a8a8a8a1','animalId','a9a9a9a9-a9a9-49a9-89a9-a9a9a9a9a9a1','occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'dry-off without a cow is rejected'
);

select * from finish();
rollback;
