-- Historical archive operations only flipped study_plans.status to ARCHIVED and left the
-- study_plan_classrooms rows behind. Those stale links made classroom student add/remove fail with
-- 404 as well, because enrollment re-validates every plan linked to the classroom, and they also
-- blocked physical classroom deletion through ClassroomService#canPhysicallyDelete.
--
-- Scope: only links whose study plan is ARCHIVED or missing. Links to DRAFT/PUBLISHED/PAUSED/
-- COMPLETED plans, student study plans and learning records are untouched.
DELETE FROM study_plan_classrooms link
 WHERE NOT EXISTS (
     SELECT 1
       FROM study_plans plan
      WHERE plan.id = link.study_plan_id
        AND plan.status <> 'ARCHIVED'
 );
