-- Make search projection dispatch single-claimer and crash-recoverable.
-- Indexer invocations must never independently read the same pending queue row and both dispatch it.

alter table public.search_index_jobs
    drop constraint if exists search_index_jobs_state_check;

alter table public.search_index_jobs
    add constraint search_index_jobs_state_check
    check (state in ('pending','dispatching','in_flight','retry','done','dead_letter'));

alter table public.search_index_jobs
    add column if not exists claimed_at timestamptz,
    add column if not exists lease_until timestamptz;

create index if not exists search_jobs_dispatch_lease_idx
    on public.search_index_jobs(state, lease_until, job_id);

create or replace function public.claim_search_index_jobs_v1(
    p_limit integer default 50,
    p_lease_seconds integer default 120
)
returns setof public.search_index_jobs
language plpgsql
security definer
set search_path = public
as $$
begin
    return query
    with candidates as (
        select j.job_id
        from public.search_index_jobs j
        where (
                j.state in ('pending','retry')
                and (j.next_attempt_at is null or j.next_attempt_at <= now())
              )
           or (
                j.state = 'dispatching'
                and j.lease_until is not null
                and j.lease_until <= now()
              )
        order by j.job_id
        for update skip locked
        limit least(greatest(p_limit, 1), 100)
    )
    update public.search_index_jobs j
    set state = 'dispatching',
        claimed_at = now(),
        lease_until = now() + make_interval(secs => least(greatest(p_lease_seconds, 15), 900)),
        last_error = case
            when j.state = 'dispatching' then coalesce(j.last_error, 'Recovered expired dispatch lease')
            else j.last_error
        end
    from candidates c
    where j.job_id = c.job_id
    returning j.*;
end;
$$;

revoke all on function public.claim_search_index_jobs_v1(integer,integer) from public, anon, authenticated;
grant execute on function public.claim_search_index_jobs_v1(integer,integer) to service_role;
