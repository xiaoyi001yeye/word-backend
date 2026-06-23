# Student Word Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the student global card box, wrong-word list, and favorites list, then verify with two newly created students.

**Architecture:** Add persistent global memory and event tables behind a focused `StudentWordMemoryService`. Keep `StudyWordProgress` as the plan-scoped source of teacher task progress, and call the memory service from `StudyPlanService.recordStudy` after plan progress is updated.

**Tech Stack:** Spring Boot, Spring Data JPA, Flyway, PostgreSQL/H2 tests, React, TypeScript, Docker Compose

## Global Constraints

- Use TDD for behavior changes: write failing tests before production code.
- Do not touch unrelated untracked files already present in the worktree.
- Frontend changes under `frontend/` require rebuilding and restarting the unified `frontend` Docker container before completion.
- Student memory is global by `student_id + meta_word_id`; plan progress remains scoped by `student_study_plan_id`.

---

### Task 1: Backend Memory Model and Plan Sync

**Files:**
- Create: `src/main/resources/db/migration/V23__create_student_word_memories.sql`
- Create: `src/main/java/com/example/words/model/StudentWordMemory.java`
- Create: `src/main/java/com/example/words/model/StudentWordMemoryEvent.java`
- Create: `src/main/java/com/example/words/model/StudentWordMemorySourceType.java`
- Create: `src/main/java/com/example/words/repository/StudentWordMemoryRepository.java`
- Create: `src/main/java/com/example/words/repository/StudentWordMemoryEventRepository.java`
- Create: `src/main/java/com/example/words/service/StudentWordMemoryService.java`
- Modify: `src/main/java/com/example/words/service/StudyPlanService.java`
- Test: `src/test/java/com/example/words/service/StudentWordMemoryServiceTest.java`

**Interfaces:**
- Consumes: `StudyRecordResult`, `MetaWordRepository`, `DictionaryAssignmentRepository`, `DictionaryWordRepository`.
- Produces: `recordPlanStudy(Long studentId, Long metaWordId, Long sourceId, Long dictionaryId, StudyRecordResult result, LocalDateTime occurredAt)`.

- [ ] Write failing service tests for correct, incorrect, skipped, auto-wrong clearing, and unassigned favorite denial.
- [ ] Run `./mvnw test -Dtest=StudentWordMemoryServiceTest` and verify the expected compile/test failure.
- [ ] Add Flyway migration, entities, repositories, service, and inject the service into `StudyPlanService`.
- [ ] Run `./mvnw test -Dtest=StudentWordMemoryServiceTest` and verify the tests pass.

### Task 2: Backend Student Memory API

**Files:**
- Create: `src/main/java/com/example/words/controller/StudentWordMemoryController.java`
- Create: `src/main/java/com/example/words/dto/StudentWordMemoryResponse.java`
- Create: `src/main/java/com/example/words/dto/UpdateFavoriteRequest.java`
- Modify: `src/main/java/com/example/words/service/StudentWordMemoryService.java`
- Test: `src/test/java/com/example/words/service/StudentWordMemoryServiceTest.java`

**Interfaces:**
- Consumes: `listMemories(AppUser actor)`, `listWrongWords(AppUser actor)`, `listFavoriteWords(AppUser actor)`, `updateFavorite(Long metaWordId, boolean favorite, AppUser actor)`.
- Produces: student-only REST endpoints under `/api/students/me`.

- [ ] Write failing tests for list filtering and favorite update behavior.
- [ ] Run `./mvnw test -Dtest=StudentWordMemoryServiceTest` and verify the expected failure.
- [ ] Add DTOs and controller endpoints.
- [ ] Run `./mvnw test -Dtest=StudentWordMemoryServiceTest` and verify the tests pass.

### Task 3: Frontend Wrong/Favorite Tabs

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/index.ts`
- Modify: `frontend/src/student/StudentLibrary.tsx`
- Modify: `frontend/src/student/student-workspace.css`

**Interfaces:**
- Consumes: `studentWordMemoryApi.listWrongWords`, `listFavoriteWords`, `updateFavorite`.
- Produces: active `错词本` and `收藏` tabs plus favorite toggle in word detail.

- [ ] Add TypeScript types and API methods.
- [ ] Update `StudentLibrary` to load wrong/favorite lists and toggle favorites.
- [ ] Add focused CSS for memory cards and tab state.
- [ ] Run `npm run build` in `frontend/`.

### Task 4: Verification and Two Student Test Data

**Files:**
- Runtime database via existing API/SQL.

**Interfaces:**
- Consumes: user creation/login APIs and student memory endpoints.
- Produces: two new students with exercised card-box behavior.

- [ ] Run `./mvnw test`.
- [ ] Run `npm run build` in `frontend/`.
- [ ] Rebuild and restart Docker frontend with `docker-compose up -d --build frontend`.
- [ ] Create two students.
- [ ] Assign visible dictionary/plan data as needed.
- [ ] Submit learning records that produce wrong/favorite state.
- [ ] Verify wrong-word and favorite endpoints return expected results for each student independently.
