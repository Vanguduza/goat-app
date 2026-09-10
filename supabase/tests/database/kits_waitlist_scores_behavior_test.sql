begin;

create extension if not exists pgtap with schema extensions;
select plan(5);

insert into auth.users (id, email)
values ('81818181-8181-4818-8818-818181818181', 'wait@test.invalid');
insert into public.farms (id, name)
values ('82828282-8282-4828-8828-828282828282', 'Waitlist Farm');
insert into public.farm_users (farm_id, user_id, role)
values ('82828282-8282-4828-8828-828282828282','81818181-8181-4818-8818-818181818181','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', '81818181-8181-4818-8818-818181818181', true);

select is(
    public.rabbit_waitlist_enqueue_v1(
        '83838383-8383-4838-8838-838383838381',
        '82828282-8282-4828-8828-828282828282',
        'wait-device', 0, 1700000000000,
        jsonb_build_object('waitlistId','84848484-8484-4848-8848-848484848481','contactName','Nala buyer','qty',2)
    )->>'code',
    'ACCEPTED',
    'waitlist enqueue is accepted'
);

select is(
    public.rabbit_waitlist_fulfill_v1(
        '83838383-8383-4838-8838-838383838382',
        '82828282-8282-4828-8828-828282828282',
        'wait-device', 0, 1700000001000,
        jsonb_build_object('waitlistId','84848484-8484-4848-8848-848484848481','kitId','85858585-8585-4858-8858-858585858581')
    )->>'code',
    'VALIDATION_REJECTED',
    'fulfill without a sale_pet kit is rejected'
);

select is(
    public.goat_record_bcs_v1(
        '83838383-8383-4838-8838-838383838383',
        '82828282-8282-4828-8828-828282828282',
        'wait-device', 0, 1700000002000,
        jsonb_build_object('scoreId','86868686-8686-4868-8868-868686868681','animalId','87878787-8787-4878-8878-878787878781','scoreTenths',30,'occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'goat BCS without a goat is rejected'
);

select is(
    public.cattle_record_scc_v1(
        '83838383-8383-4838-8838-838383838384',
        '82828282-8282-4828-8828-828282828282',
        'wait-device', 0, 1700000003000,
        jsonb_build_object('recordId','88888888-8888-4888-8888-888888888881','animalId','89898989-8989-4898-8898-898989898981','cellsPerMl',250000,'occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'SCC without a cow is rejected'
);

select is(
    public.sheep_record_shearing_v1(
        '83838383-8383-4838-8838-838383838385',
        '82828282-8282-4828-8828-828282828282',
        'wait-device', 0, 1700000004000,
        jsonb_build_object('eventId','90909090-9090-4909-8909-909090909091','kind','clip','occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'shearing kind must be shearing, crutching, or classing'
);

select * from finish();
rollback;
