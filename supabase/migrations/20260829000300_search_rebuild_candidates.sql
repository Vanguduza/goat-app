create or replace function public.search_rebuild_candidates_v1(
    p_after_id uuid default null,
    p_limit integer default 500
)
returns table(
    farm_id uuid,
    entity_id uuid,
    projection_version bigint
)
language sql
stable
security definer
set search_path = public, auth
as $$
    select
        a.farm_id,
        a.id as entity_id,
        coalesce((
            select max(e.stream_version)
            from public.domain_events e
            where e.stream_id = 'animal:' || a.id::text
              and e.farm_id = a.farm_id
        ), 0)::bigint as projection_version
    from public.animals a
    where (p_after_id is null or a.id > p_after_id)
    order by a.id
    limit least(greatest(p_limit, 1), 1000);
$$;

revoke all on function public.search_rebuild_candidates_v1(uuid,integer) from public, anon, authenticated;
grant execute on function public.search_rebuild_candidates_v1(uuid,integer) to service_role;
