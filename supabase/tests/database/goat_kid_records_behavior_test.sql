begin;

create extension if not exists pgtap with schema extensions;
select plan(2);

insert into auth.users (id, email)
values ('e1e1e1e1-e1e1-41e1-81e1-e1e1e1e1e1e1', 'kids@test.invalid');
insert into public.farms (id, name)
values ('e2e2e2e2-e2e2-42e2-82e2-e2e2e2e2e2e2', 'Kid Farm');
insert into public.farm_users (farm_id, user_id, role)
values ('e2e2e2e2-e2e2-42e2-82e2-e2e2e2e2e2e2','e1e1e1e1-e1e1-41e1-81e1-e1e1e1e1e1e1','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'e1e1e1e1-e1e1-41e1-81e1-e1e1e1e1e1e1', true);

select is(
    public.goat_register_kid_v1(
        'e3e3e3e3-e3e3-43e3-83e3-e3e3e3e3e3e1',
        'e2e2e2e2-e2e2-42e2-82e2-e2e2e2e2e2e2',
        'kid-device', 0, 1700000000000,
        jsonb_build_object('animalId','e4e4e4e4-e4e4-44e4-84e4-e4e4e4e4e4e1','kiddingId','e5e5e5e5-e5e5-45e5-85e5-e5e5e5e5e5e1','tag','NALA-K1','sex','FEMALE')
    )->>'code',
    'VALIDATION_REJECTED',
    'kid without a kidding is rejected'
);

select is(
    public.goat_register_kid_v1(
        'e3e3e3e3-e3e3-43e3-83e3-e3e3e3e3e3e2',
        'e2e2e2e2-e2e2-42e2-82e2-e2e2e2e2e2e2',
        'kid-device', 0, 1700000001000,
        jsonb_build_object('animalId','e4e4e4e4-e4e4-44e4-84e4-e4e4e4e4e4e2','kiddingId','e5e5e5e5-e5e5-45e5-85e5-e5e5e5e5e5e1','tag','','sex','FEMALE')
    )->>'code',
    'VALIDATION_REJECTED',
    'kid without a tag is rejected'
);

select * from finish();
rollback;
