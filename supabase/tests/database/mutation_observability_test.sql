begin;

create extension if not exists pgtap with schema extensions;
select plan(11);

insert into auth.users (id, email)
values
    ('24242424-2424-4424-8424-242424242424', 'trace-a@test.invalid'),
    ('25252525-2525-4525-8525-252525252525', 'trace-b@test.invalid');

insert into public.farms (id, name)
values
    ('26262626-2626-4626-8626-262626262626', 'Trace Farm A'),
    ('27272727-2727-4727-8727-272727272727', 'Trace Farm B');

insert into public.farm_users (farm_id, user_id, role)
values
    ('26262626-2626-4626-8626-262626262626', '24242424-2424-4424-8424-242424242424', 'owner'),
    ('27272727-2727-4727-8727-272727272727', '25252525-2525-4525-8525-252525252525', 'owner');

set local role authenticated;
select set_config('request.jwt.claim.sub', '24242424-2424-4424-8424-242424242424', true);

select is(
    public.goat_register_v1(
        '28282828-2828-4828-8828-282828282828',
        '26262626-2626-4626-8626-262626262626',
        'trace-device-a',
        0,
        1700000000000,
        jsonb_build_object(
            'animalId', '29292929-2929-4929-8929-292929292929',
            'tag', 'TRACE-001',
            'name', 'Trace Goat',
            'sex', 'FEMALE'
        )
    )->>'code',
    'ACCEPTED',
    'trace fixture mutation is accepted through the authoritative RPC'
);

reset role;

select ok(
    (select source_event_id is not null
     from public.search_index_jobs
     where farm_id = '26262626-2626-4626-8626-262626262626'
       and entity_id = '29292929-2929-4929-8929-292929292929'
     order by job_id
     limit 1),
    'command-driven search job is explicitly correlated to its authoritative event'
);

select is(
    (select origin
     from public.search_index_jobs
     where farm_id = '26262626-2626-4626-8626-262626262626'
       and entity_id = '29292929-2929-4929-8929-292929292929'
     order by job_id
     limit 1),
    'command',
    'ordinary authoritative mutation search work is labelled command origin'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '24242424-2424-4424-8424-242424242424', true);

select is(
    public.mutation_trace_v1(
        '26262626-2626-4626-8626-262626262626',
        '28282828-2828-4828-8828-282828282828'
    )->>'code',
    'FOUND',
    'farm member can obtain a safe mutation trace'
);

select is(
    public.mutation_trace_v1(
        '26262626-2626-4626-8626-262626262626',
        '28282828-2828-4828-8828-282828282828'
    )->>'commandName',
    'goat.register.v1',
    'trace identifies the accepted command receipt'
);

select is(
    public.mutation_trace_v1(
        '26262626-2626-4626-8626-262626262626',
        '28282828-2828-4828-8828-282828282828'
    )->'event'->>'eventType',
    'goat.registered.v1',
    'trace identifies the authoritative domain event'
);

select ok(
    (public.mutation_trace_v1(
        '26262626-2626-4626-8626-262626262626',
        '28282828-2828-4828-8828-282828282828'
    )->'searchJobs') @> '[{"origin":"command","state":"pending","projectionVersion":1}]'::jsonb,
    'trace exposes the safe async search state for the mutation'
);

reset role;

insert into public.search_index_jobs(
    farm_id, entity_type, entity_id, operation, projection_version, state, origin
) values (
    '26262626-2626-4626-8626-262626262626',
    'animal',
    '29292929-2929-4929-8929-292929292929',
    'upsert',
    1,
    'pending',
    'rebuild'
);

select ok(
    (select source_event_id is not null
     from public.search_index_jobs
     where farm_id = '26262626-2626-4626-8626-262626262626'
       and entity_id = '29292929-2929-4929-8929-292929292929'
       and origin = 'rebuild'
     order by job_id desc
     limit 1),
    'rebuild work is also correlated to the authoritative projection source event'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '24242424-2424-4424-8424-242424242424', true);

select is(
    jsonb_array_length(
        public.mutation_trace_v1(
            '26262626-2626-4626-8626-262626262626',
            '28282828-2828-4828-8828-282828282828'
        )->'searchJobs'
    ),
    2,
    'trace can distinguish all search work associated with the same authoritative event'
);

select is(
    public.mutation_trace_v1(
        '26262626-2626-4626-8626-262626262626',
        '30303030-3030-4030-8030-303030303030'
    )->>'code',
    'NOT_FOUND',
    'unknown mutation within an authorized farm does not fabricate trace evidence'
);

select set_config('request.jwt.claim.sub', '25252525-2525-4525-8525-252525252525', true);

select is(
    public.mutation_trace_v1(
        '26262626-2626-4626-8626-262626262626',
        '28282828-2828-4828-8828-282828282828'
    )->>'code',
    'AUTH_REJECTED',
    'member of another farm cannot inspect mutation observability data'
);

select * from finish();
rollback;
