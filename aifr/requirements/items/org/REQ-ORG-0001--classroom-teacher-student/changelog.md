# REQ-ORG-0001 Changelog

## 1.1.0

```yaml
version_update:
  requirement_id: REQ-ORG-0001
  from_version: "1.0.0"
  to_version: "1.1.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增班级 CRUD、成员维护、分页、数据约束和级联删除的明确规则，并将原合并需求拆分为独立关联需求。

change_set:
  added_rules:
    - RULE-005
    - RULE-006
    - RULE-007
  modified_rules:
    - RULE-001
    - RULE-002
    - RULE-003
    - RULE-004
  removed_rules: []
  added_scenarios:
    - SCN-003
    - SCN-004
    - SCN-005
  modified_scenarios:
    - SCN-001
    - SCN-002
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-004
    - AC-005
    - AC-006
  modified_acceptance_criteria:
    - AC-001
    - AC-002
    - AC-003
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules:
      - RULE-001
      - RULE-002
    clarified_scenarios:
      - SCN-001
      - SCN-002
    clarified_acceptance_criteria:
      - AC-001
    terminology_changes:
      - 将原“班级与师生关系管理”拆分为班级基础管理、师生责任范围、班级辞书分配和班级学习计划范围。
  semantic_summary:
    - 班级基础管理需求现在只覆盖班级 CRUD、成员维护和班级响应。
    - 师生责任范围、班级辞书分配和学习计划范围迁出为独立需求。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0002
      - REQ-ORG-0003
      - REQ-STUDY-0002
      - REQ-DICT-0001
  code_impact:
    level: medium
    reason: 文档反映现有代码行为，未要求实现变更。
    code_search_hints:
      - ClassroomService
      - ClassroomController
      - ClassroomMemberRepository
      - V10__add_classroom_support.sql
  test_impact:
    level: medium
    reason: 现有后端缺少直接覆盖 ClassroomService 的单元测试。
    recommended_tests:
      - ClassroomServiceTest#createClassroomShouldResolveTeacherByRole
      - ClassroomServiceTest#addStudentShouldBeIdempotent
      - ClassroomServiceTest#teacherCannotManageOtherTeacherClassroom
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```
