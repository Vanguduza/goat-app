-- Correlate one client mutation through receipt -> authoritative event -> async search work.
-- Raw search queue access remains service-only; authenticated farm members receive a safe trace RPC.

alter table public.search_index_jobs
    add column if not exists source_event_id uuid references public.domain_events(event_id),
    add column if not exists origin text not null default 'command';

alter table public.search_index_jobs
    drop constraint if exists search_index_jobs_origin_check;

alter table public.search_index_jobs
    add constraint search_index_jobs_origin_check
    check (origin in ('command','rebuild','repair'));

create index if not exists search_jobs_source_event_idx
    on public.search_index_jobs(source_event_id, job_id);

-- Backfill deterministically from the canonical event stream/version identity.
update public.search_index_jobs j
set source_event_id = e.event_id
from public.domain_events e
where j.source_event_id is null
  and e.farm_id = j.farm_id
  and e.aggregate_type = j.entity_type
  and e.aggregate_id = j.entity_id
  and e.stream_version = j.projection_version;

create or replace function public.attach_search_job_source_v1()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if new.source_event_id is null then
        select e.event_id
        into new.source_event_id
        from public.domain_events e
        where e.farm_id = new.farm_id
          and e.aggregate_type = new.entity_type
          and e.aggregate_id = new.entity_id
          and e.stream_version = new.projection_version
        order by e.change_cursor desc
        limit 1;
    end if;
    return new;
end;
$$;

drop trigger if exists search_index_jobs_attach_source on public.search_index_jobs;
create trigger search_index_jobs_attach_source
before insert on public.search_index_jobs
for each row execute function public.attach_search_job_source_v1();

create or replace function public.mutation_trace_v1(
    p_farm_id uuid,
    p_mutation_id uuid
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, auth
as $$
declare
    v_command_name text;
    v_applied_at timestamptz;
    v_event_id uuid;
    v_event_type text;
    v_stream_version bigint;
    v_change_cursor bigint;
    v_recorded_at timestamptz;
    v_device_id text;
    v_jobs jsonb;
begin
    if auth.uid() is null or not public.is_farm_member(p_farm_id) then
        return jsonb_build_object('code','AUTH_REJECTED','safeMessage','Farm access denied');
    end if;

    select r.command_name,
           r.applied_at,
           e.event_id,
           e.event_type,
           e.stream_version,
           e.change_cursor,
           e.recorded_at,
           e.device_id
    into v_command_name,
         v_applied_at,
         v_event_id,
         v_event_type,
         v_stream_version,
         v_change_cursor,
         v_recorded_at,
         v_device_id
    from public.command_receipts r
    join public.domain_events e on e.event_id = r.event_id
    where r.farm_id = p_farm_id
      and r.mutation_id = p_mutation_id;

    if not found then
        return jsonb_build_object('code','NOT_FOUND','safeMessage','Mutation not found for this farm');
    end if;

    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'jobId', j.job_id,
                'origin', j.origin,
                'state', j.state,
                'attempts', j.attempts,
                'projectionVersion', j.projection_version,
                'meiliTaskUid', j.meili_task_uid,
                'createdAt', j.created_at,
                'completedAt', j.completed_at,
                'errorPresent', j.last_error is not null
            ) order by j.job_id
        ),
        '[]'::jsonb
    )
    into v_jobs
    from public.search_index_jobs j
    where j.source_event_id = v_event_id;

    return jsonb_build_object(
        'code','FOUND',
        'mutationId',p_mutation_id,
        'commandName',v_command_name,
        'appliedAt',v_applied_at,
        'event',jsonb_build_object(
            'eventId',v_event_id,
            'eventType',v_event_type,
            'streamVersion',v_stream_version,
            'changeCursor',v_change_cursor,
            'recordedAt',v_recorded_at,
            'deviceId',v_device_id
        ),
        'searchJobs',v_jobs
    );
end;
$$;

revoke all on function public.mutation_trace_v1(uuid,uuid) from public, anon;
grant execute on function public.mutation_trace_v1(uuid,uuid) to authenticated;
