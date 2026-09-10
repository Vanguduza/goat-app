begin;

create extension if not exists pgtap with schema extensions;
select plan(6);

insert into auth.users (id, email)
values ('91919191-9191-4919-8919-919191919191', 'hatch@test.invalid');
insert into public.farms (id, name)
values ('92929292-9292-4929-8929-929292929292', 'Hatch Farm');
insert into public.farm_users (farm_id, user_id, role)
values ('92929292-9292-4929-8929-929292929292','91919191-9191-4919-8919-919191919191','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', '91919191-9191-4919-8919-919191919191', true);

select is(
    public.poultry_hatch_set_v1(
        '93939393-9393-4939-8939-939393939391',
        '92929292-9292-4929-8929-929292929292',
        'hatch-device', 0, 1700000000000,
        jsonb_build_object(
            'hatchId','94949494-9494-4949-8949-949494949491',
            'poultryKindCode','chicken',
            'eggsSet',12,
            'setEpochDay',19723,
            'candleTaskId','95959595-9595-4959-8959-959595959591',
            'lockTaskId','95959595-9595-4959-8959-959595959592',
            'hatchTaskId','95959595-9595-4959-8959-959595959593'
        )
    )->>'code',
    'ACCEPTED',
    'chicken hatch set is accepted'
);

select is(
    (select incubation_days from public.poultry_hatches where id = '94949494-9494-4949-8949-949494949491'),
    21,
    'chicken incubation is 21 days'
);

select is(
    public.poultry_hatch_record_v1(
        '93939393-9393-4939-8939-939393939392',
        '92929292-9292-4929-8929-929292929292',
        'hatch-device', 0, 1700000001000,
        jsonb_build_object('hatchId','94949494-9494-4949-8949-949494949491','hatched',10,'culls',0,'occurredEpochDay',19744)
    )->>'code',
    'VALIDATION_REJECTED',
    'hatch record before candling is rejected'
);

select is(
    public.poultry_hatch_set_v1(
        '93939393-9393-4939-8939-939393939393',
        '92929292-9292-4929-8929-929292929292',
        'hatch-device', 0, 1700000002000,
        jsonb_build_object(
            'hatchId','94949494-9494-4949-8949-949494949492',
            'poultryKindCode','farm_defined',
            'eggsSet',8,
            'setEpochDay',19723,
            'candleTaskId','95959595-9595-4959-8959-959595959594',
            'lockTaskId','95959595-9595-4959-8959-959595959595',
            'hatchTaskId','95959595-9595-4959-8959-959595959596'
        )
    )->>'code',
    'VALIDATION_REJECTED',
    'farm_defined hatch needs incubation days'
);

select is(
    public.sheep_record_flystrike_v1(
        '93939393-9393-4939-8939-939393939394',
        '92929292-9292-4929-8929-929292929292',
        'hatch-device', 0, 1700000003000,
        jsonb_build_object('scoreId','96969696-9696-4969-8969-969696969691','animalId','97979797-9797-4979-8979-979797979791','score',6,'occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'flystrike score must be 0 to 5'
);

select is(
    public.goat_record_scc_v1(
        '93939393-9393-4939-8939-939393939395',
        '92929292-9292-4929-8929-929292929292',
        'hatch-device', 0, 1700000004000,
        jsonb_build_object('recordId','98989898-9898-4989-8989-989898989891','animalId','99999999-9999-4999-8999-999999999991','cellsPerMl',250000,'occurredEpochDay',19723)
    )->>'code',
    'VALIDATION_REJECTED',
    'goat SCC without a goat is rejected'
);

select * from finish();
rollback;
