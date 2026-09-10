begin;

create extension if not exists pgtap with schema extensions;
select plan(16);

select has_table('public', 'farms', 'farms table exists');
select has_table('public', 'farm_users', 'farm membership table exists');
select has_table('public', 'animals', 'animals table exists');
select has_table('public', 'measurements', 'measurements table exists');
select has_table('public', 'domain_events', 'domain event ledger exists');
select has_table('public', 'command_receipts', 'idempotency receipts exist');
select has_table('public', 'search_index_jobs', 'search indexing outbox exists');

select ok(
    (select relrowsecurity from pg_class where oid = 'public.animals'::regclass),
    'animals has RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.measurements'::regclass),
    'measurements has RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.domain_events'::regclass),
    'domain_events has RLS enabled'
);

select has_index('public', 'animals', 'animals_farm_species_status_idx', 'animal tenant/species/status index exists');
select has_index('public', 'domain_events', 'domain_events_farm_cursor_idx', 'pull cursor index exists');

select has_function('public', 'goat_register_v1', array['uuid','uuid','text','bigint','bigint','jsonb'], 'register RPC exists');
select has_function('public', 'goat_record_weight_v1', array['uuid','uuid','text','bigint','bigint','jsonb'], 'weight RPC exists');
select has_function('public', 'pull_changes_v1', array['uuid','bigint','integer'], 'cursor pull RPC exists');

select ok(
    exists (
        select 1
        from pg_constraint c
        join pg_class t on t.oid = c.conrelid
        where t.relname = 'measurements'
          and c.contype = 'f'
          and pg_get_constraintdef(c.oid) like '%FOREIGN KEY (farm_id, animal_id)%'
    ),
    'measurements enforce farm-aware animal relationship'
);

select * from finish();
rollback;
