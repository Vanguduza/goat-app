begin;

create extension if not exists pgtap with schema extensions;
select plan(6);

insert into public.farms (id, name)
values ('11111111-1111-4111-8111-111111111111', 'Search Claim Farm');

insert into public.search_index_jobs (
    farm_id, entity_type, entity_id, operation, projection_version, state, next_attempt_at, lease_until
) values
    (
        '11111111-1111-4111-8111-111111111111', 'animal',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'upsert', 1,
        'pending', null, null
    ),
    (
        '11111111-1111-4111-8111-111111111111', 'animal',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'upsert', 2,
        'retry', now() + interval '1 hour', null
    ),
    (
        '11111111-1111-4111-8111-111111111111', 'animal',
        'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'upsert', 3,
        'dispatching', null, now() - interval '1 minute'
    );

select ok(
    not has_function_privilege(
        'authenticated',
        'public.claim_search_index_jobs_v1(integer,integer)',
        'EXECUTE'
    ),
    'authenticated clients cannot claim worker-owned search jobs'
);

select is(
    (select count(*)::bigint from public.claim_search_index_jobs_v1(10, 120)),
    2::bigint,
    'first claim atomically acquires due pending work and an expired dispatch lease'
);

select is(
    (select count(*)::bigint
     from public.search_index_jobs
     where state = 'dispatching'
       and lease_until > now()),
    2::bigint,
    'claimed jobs receive active dispatch leases'
);

select is(
    (select count(*)::bigint from public.claim_search_index_jobs_v1(10, 120)),
    0::bigint,
    'a second claimer cannot acquire already leased rows'
);

select is(
    (select state from public.search_index_jobs
     where entity_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
    'retry',
    'future retry work is not claimed early'
);

update public.search_index_jobs
set lease_until = now() - interval '1 second'
where entity_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

select is(
    (select count(*)::bigint from public.claim_search_index_jobs_v1(10, 120)),
    1::bigint,
    'an expired dispatch lease is safely reclaimable after worker interruption'
);

select * from finish();
rollback;
