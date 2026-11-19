-- SOCYTO publishing: private drafts become immutable client-visible snapshots
-- only after an authenticated administrator deliberately publishes an update.
-- Run after migrations 001–005.
create or replace function public.publish_project_progress(
  target_business_id uuid,
  target_project_id uuid,
  publication_note text default null
)
returns uuid language plpgsql security definer set search_path = ''
as $$
declare
  new_publication_id uuid;
  calculated_progress numeric(5,2);
  published_tasks jsonb;
begin
  if not public.is_business_administrator(target_business_id) then
    raise exception 'Not authorized to publish for this business' using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.projects p
    where p.business_id = target_business_id and p.project_id = target_project_id
  ) then
    raise exception 'Project does not belong to the selected business' using errcode = '42501';
  end if;
  if publication_note is not null and length(publication_note) > 4000 then
    raise exception 'Update note is too long' using errcode = '22023';
  end if;

  -- Serialize publications with requirement edits under the same project lock.
  perform 1 from public.projects p
  where p.business_id = target_business_id and p.project_id = target_project_id
  for update;

  select
    case when coalesce(sum(r.effort_score),0) = 0 then null
      else round(
        sum(r.effort_score * (case
          when r.tracking_type = 'checklist' then
            case when r.is_completed then 1.0 else 0.0 end
          else least(1.0, greatest(0.0,
            r.completed_quantity / nullif(r.required_quantity,0)))
        end)) / sum(r.effort_score) * 100.0, 2)
    end,
    coalesce(jsonb_agg(jsonb_build_object(
      'requirement_id',r.requirement_id,
      'requirement_name',r.requirement_name,
      'tracking_type',r.tracking_type,
      'effort_score',r.effort_score,
      'is_completed',r.is_completed,
      'required_quantity',r.required_quantity,
      'completed_quantity',r.completed_quantity,
      'unit_name',r.unit_name
    ) order by r.display_order,r.requirement_id)
      filter (where r.requirement_id is not null), '[]'::jsonb)
  into calculated_progress,published_tasks
  from public.project_requirements r
  where r.business_id = target_business_id and r.project_id = target_project_id;

  if jsonb_array_length(published_tasks) = 0 then
    raise exception 'Add work requirements before publishing' using errcode = '22023';
  end if;

  insert into public.project_publications (
    business_id,project_id,published_by_user_id,
    overall_progress_percent,published_requirements,public_update_note
  ) values (
    target_business_id,target_project_id,auth.uid(),
    calculated_progress,published_tasks,nullif(trim(publication_note),'')
  ) returning publication_id into new_publication_id;
  return new_publication_id;
end;
$$;

revoke all on function public.publish_project_progress(uuid,uuid,text) from public,anon;
grant execute on function public.publish_project_progress(uuid,uuid,text) to authenticated;

-- Clients can read publications only through existing RLS, and never edit one.
revoke insert,update,delete on public.project_publications from authenticated;
grant select on public.project_publications to authenticated;
