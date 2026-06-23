# REQ-MEM-0001 Changelog

## 1.1.0 - 2026-06-23

```yaml
version_update:
  requirement_id: REQ-MEM-0001
  from_version: "1.0.0"
  to_version: "1.1.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增班级分配词书收藏边界和验收场景，不改变已有直接分配收藏行为

change_set:
  added_rules:
    - RULE-006
  modified_rules:
    - RULE-001
    - RULE-005
  removed_rules: []
  added_scenarios:
    - SCN-003
    - SCN-004
    - SCN-005
  modified_scenarios: []
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-004
    - AC-005
  modified_acceptance_criteria:
    - AC-003
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 将“已分配词书”明确拆分为“直接分配或班级分配词书”
  semantic_summary:
    - 学生可收藏通过班级分配获得的词书单词
    - SKIPPED 明确按错词处理
    - 取消收藏不得清除 autoWrong

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-DICT-0001
      - REQ-ORG-0001
  code_impact:
    level: medium
    reason: 收藏权限需要识别班级成员和班级辞书分配关系
    code_search_hints:
      - StudentWordMemoryService
      - ClassroomMemberRepository
      - ClassroomDictionaryAssignmentRepository
  test_impact:
    level: medium
    reason: 需要覆盖跳过入错词、班级分配可收藏、取消收藏不清错词和列表映射
    recommended_tests:
      - recordPlanStudyTreatsSkippedWordAsAutoWrong
      - updateFavoriteCreatesMemoryForClassroomAssignedVisibleWord
      - updateFavoriteKeepsAutoWrongStateWhenRemovingFavorite
      - listWrongWordsReturnsAutoWrongMemoriesWithWordDetails
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```
