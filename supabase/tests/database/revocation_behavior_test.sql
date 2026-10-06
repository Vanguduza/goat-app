begin;

create extension if not exists pgtap with schema extensions;
select plan(4);

insert into auth.users (id, email)
values ('15151515-1515-4515-8515-151515151515', 'revoked@test.invalid');

insert into public.farms (id, name)
values ('16161616-1616-4616-8616-161616161616', 'Revocation Farm');

insert into public.farm_users (farm_id, user_id, role)
values (
    '16161616-1616-4616-8616-161616161616',
    '15151515-1515-4515-8515-151515151515',
    'owner'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '15151515-1515-4515-8515-151515151515', true);

select is(
    public.goat_register_v1(
        '17171717-1717-4717-8717-171717171717',
        '16161616-1616-4616-8616-161616161616',
        'device-before-revoke',
        0,
        1700000000000,
        jsonb_build_object(
            'animalId', '18181818-1818-4818-8818-181818181818',
            'tag', 'REV-001',
            'name', 'Before revoke',
            'sex', 'FEMALE'
        )
    )->>'code',
    'ACCEPTED',
    'member can commit authoritative work before revocation'
);

reset role;
delete from public.farm_users
where farm_id = '16161616-1616-4616-8616-161616161616'
  and user_id = '15151515-1515-4515-8515-151515151515';

set local role authenticated;
select set_config('request.jwt.claim.sub', '15151515-1515-4515-8515-151515151515', true);

select is(
    public.goat_record_weight_v1(
        '19191919-1919-4919-8919-191919191919',
        '16161616-1616-4616-8616-161616161616',
        'device-after-revoke',
        1,
        1700000001000,
        jsonb_build_object(
            'animalId', '18181818-1818-4818-8818-181818181818',
            'measurementId', '20202020-2020-4020-8020-202020202020',
            'weightGrams', 31000,
            'measuredAtEpochMillis', 1700000001000
        )
    )->>'code',
    'AUTH_REJECTED',
    'revoked member pending mutation is rejected by the authoritative RPC'
);

select is(
    (select count(*)::bigint
     from public.pull_changes_v1('16161616-1616-4616-8616-161616161616', 0, 200)),
    0::bigint,
    'revoked member cannot pull prior farm history'
);

reset role;

select is(
    (select count(*)::bigint from public.domain_events
     where farm_id = '16161616-1616-4616-8616-161616161616'),
    1::bigint,
    'rejected post-revocation mutation leaves authoritative history unchanged'
);

select * from finish();
rollback;
