# Student Workspace First Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a production student workspace with an aggregated multi-plan dashboard, real study submission semantics, responsive student navigation, and syllable reading with managed AI backfill.

**Architecture:** Add a student-specific dashboard service that composes existing study-plan tasks and remains the authority after every submission. Keep student UI in focused React components selected by the authenticated role, while preserving the existing teacher workspace. Store optional syllable metadata as JSONB on `meta_words`; isolate AI backfill behind an admin-only service and endpoint.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Data JPA, PostgreSQL JSONB/Flyway, JUnit 5/Mockito, React 19, TypeScript 5.9, Vite 7, Vitest/Testing Library, Docker Compose.

## Global Constraints

- Student tasks aggregate across active published plans and sort by task type, due date, plan publication time, and task item ID.
- Duplicate words from separate plans remain separate queue items.
- `INCORRECT` records an attempt without completing the task; `CORRECT` and `SKIPPED` complete it.
- Missing syllable data never blocks ordinary study.
- Audio prefers accent-specific URLs and falls back to browser speech synthesis; default accent is US and is stored locally.
- First slice excludes free study, global word memory, wrong/favorite lists, exams, self-practice, analytics, and editable profile.
- Student mobile navigation is bottom-aligned; desktop navigation is a left rail.
- Existing teacher/admin behavior must not regress.
- Any change under `frontend/` requires `docker-compose up -d --build frontend`; any change under `admin-frontend/` also requires rebuilding `admin-frontend`.

---

### Task 1: Persist and Transport Syllable Metadata

**Files:**
- Create: `src/main/resources/db/migration/V22__add_meta_word_syllable_detail.sql`
- Create: `src/main/java/com/example/words/model/SyllableDetail.java`
- Create: `src/main/java/com/example/words/model/SyllableSegment.java`
- Create: `src/main/java/com/example/words/dto/SyllableDetailDto.java`
- Create: `src/main/java/com/example/words/dto/SyllableSegmentDto.java`
- Modify: `src/main/java/com/example/words/model/MetaWord.java`
- Modify: `src/main/java/com/example/words/dto/MetaWordEntryDtoV2.java`
- Modify: `src/main/java/com/example/words/service/DictionaryWordService.java`
- Modify: `src/main/java/com/example/words/service/MetaWordService.java`
- Modify: `src/main/java/com/example/words/service/AiPromptService.java`
- Test: `src/test/java/com/example/words/service/DictionaryWordServiceTest.java`

**Interfaces:**
- Produces: `MetaWord.getSyllableDetail(): SyllableDetail` and `MetaWordEntryDtoV2.getSyllableDetail(): SyllableDetailDto`.
- `SyllableSegment` fields: `text`, `ukPhonetic`, `usPhonetic`, `ukAudioUrl`, `usAudioUrl`.

- [ ] **Step 1: Write a failing import propagation test**

Add a test that submits `MetaWordEntryDtoV2` with one `SyllableSegmentDto`, processes the word, and asserts the saved `MetaWord.syllableDetail.segments[0].text` equals `re`.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./mvnw test -Dtest=DictionaryWordServiceTest`

Expected: compilation or assertion failure because syllable DTO/model fields do not exist.

- [ ] **Step 3: Add migration, model, DTO, and mapping**

Migration:

```sql
ALTER TABLE meta_words ADD COLUMN IF NOT EXISTS syllable_detail JSONB;
COMMENT ON COLUMN meta_words.syllable_detail IS 'Ordered syllable spelling, IPA, and optional UK/US audio';
```

Model field:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "syllable_detail", columnDefinition = "jsonb")
private SyllableDetail syllableDetail;
```

Update V2 import mapping and the AI schema prompt so future generated entries carry `syllableDetail`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `./mvnw test -Dtest=DictionaryWordServiceTest`

Expected: all `DictionaryWordServiceTest` tests pass.

- [ ] **Step 5: Commit the task**

```bash
git add src/main/resources/db/migration/V22__add_meta_word_syllable_detail.sql src/main/java/com/example/words/model src/main/java/com/example/words/dto src/main/java/com/example/words/service src/test/java/com/example/words/service/DictionaryWordServiceTest.java
git commit -m "feat: persist syllable metadata"
```

### Task 2: Aggregate Student Dashboard and Correct Retry Semantics

**Files:**
- Create: `src/main/java/com/example/words/dto/StudentDashboardResponse.java`
- Create: `src/main/java/com/example/words/dto/StudentDashboardTaskItemResponse.java`
- Create: `src/main/java/com/example/words/dto/StudentDashboardRecordRequest.java`
- Create: `src/main/java/com/example/words/dto/StudentDashboardReminderResponse.java`
- Create: `src/main/java/com/example/words/service/StudentDashboardService.java`
- Create: `src/main/java/com/example/words/controller/StudentDashboardController.java`
- Modify: `src/main/java/com/example/words/service/StudyPlanService.java`
- Modify: `src/main/java/com/example/words/dto/StudyTaskItemResponse.java`
- Modify: `src/main/java/com/example/words/repository/StudyPlanRepository.java`
- Test: `src/test/java/com/example/words/service/StudentDashboardServiceTest.java`
- Test: `src/test/java/com/example/words/service/StudyPlanServiceTest.java`

**Interfaces:**
- Produces: `StudentDashboardService.getDashboard(AppUser)` and `record(StudentDashboardRecordRequest, AppUser)`.
- `StudentDashboardRecordRequest` contains `studentStudyPlanId` plus every field in `RecordStudyRequest`.
- Dashboard queue items expose plan identity, task item identity, word details, syllables, due date, and attempt count.

- [ ] **Step 1: Write failing aggregation and ordering tests**

Cover two active plans with overdue/review/new items and a duplicate meta word. Assert the queue order and that duplicate words remain separate by `studentStudyPlanId`.

- [ ] **Step 2: Write a failing retry semantics test**

Add `recordStudyShouldKeepIncorrectTaskPending` to `StudyPlanServiceTest`; assert `INCORRECT` saves a record but leaves `completedAt` null and `completedCount` unchanged. Preserve existing tests for `CORRECT`; add `SKIPPED` completion coverage.

- [ ] **Step 3: Run focused tests and verify RED**

Run: `./mvnw test -Dtest=StudentDashboardServiceTest,StudyPlanServiceTest`

Expected: dashboard types are absent and existing `INCORRECT` behavior completes the item.

- [ ] **Step 4: Implement retry semantics and dashboard aggregation**

Use existing `getTodayTask` generation per plan. For `INCORRECT`, update progress and record attention, but do not set `StudyDayTaskItem.completedAt`; sort the returned dashboard queue with a comparator over task priority, due date, plan publication time, and task item ID. Compute summary counts from all generated daily tasks and return only plan-related reminders.

- [ ] **Step 5: Add student-only controller routes**

```java
@RestController
@RequestMapping("/api/students/me/dashboard")
@PreAuthorize("hasRole('STUDENT')")
class StudentDashboardController {
    private final StudentDashboardService dashboardService;
    private final CurrentUserService currentUserService;

    StudentDashboardController(
            StudentDashboardService dashboardService,
            CurrentUserService currentUserService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    ResponseEntity<StudentDashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard(currentUserService.getCurrentUser()));
    }

    @PostMapping("/records")
    ResponseEntity<StudentDashboardResponse> record(
            @Valid @RequestBody StudentDashboardRecordRequest request) {
        return ResponseEntity.ok(dashboardService.record(request, currentUserService.getCurrentUser()));
    }
}
```

- [ ] **Step 6: Run focused and full backend tests**

Run: `./mvnw test -Dtest=StudentDashboardServiceTest,StudyPlanServiceTest`

Then: `./mvnw test`

Expected: all tests pass.

- [ ] **Step 7: Commit the task**

```bash
git add src/main/java/com/example/words/controller/StudentDashboardController.java src/main/java/com/example/words/dto src/main/java/com/example/words/service/StudentDashboardService.java src/main/java/com/example/words/service/StudyPlanService.java src/main/java/com/example/words/repository/StudyPlanRepository.java src/test/java/com/example/words/service
git commit -m "feat: add aggregated student dashboard"
```

### Task 3: Add Validated Admin Syllable Backfill

**Files:**
- Create: `src/main/java/com/example/words/dto/SyllableBackfillResponse.java`
- Create: `src/main/java/com/example/words/dto/SyllableBackfillFailureResponse.java`
- Create: `src/main/java/com/example/words/service/SyllableBackfillService.java`
- Create: `src/main/java/com/example/words/controller/SyllableBackfillController.java`
- Modify: `src/main/java/com/example/words/repository/MetaWordRepository.java`
- Modify: `src/main/java/com/example/words/service/AiGenerationService.java`
- Test: `src/test/java/com/example/words/service/SyllableBackfillServiceTest.java`

**Interfaces:**
- Produces: `SyllableBackfillService.backfillPublishedPlanWords(): SyllableBackfillResponse`.
- Validation normalizes and concatenates segment text, then compares it with `WordNormalizationUtils.normalize(metaWord.word)`.

- [ ] **Step 1: Write failing validation tests**

Cover a valid `re + sil + ient` response, a mismatched concatenation, one AI failure, and exclusion of words outside published plans.

- [ ] **Step 2: Run the test and verify RED**

Run: `./mvnw test -Dtest=SyllableBackfillServiceTest`

Expected: service does not exist.

- [ ] **Step 3: Implement bounded backfill and admin endpoint**

Add `POST /api/admin/syllables/backfill` with `hasRole('ADMIN')`. Process candidates independently, save valid results, and return attempted, updated, skipped, and failure details without aborting the batch.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `./mvnw test -Dtest=SyllableBackfillServiceTest`

Expected: all backfill tests pass.

- [ ] **Step 5: Commit the task**

```bash
git add src/main/java/com/example/words/controller/SyllableBackfillController.java src/main/java/com/example/words/dto src/main/java/com/example/words/repository/MetaWordRepository.java src/main/java/com/example/words/service src/test/java/com/example/words/service/SyllableBackfillServiceTest.java
git commit -m "feat: backfill syllables for active plans"
```

### Task 4: Add Frontend Contracts, Test Harness, and Student Role Split

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/index.ts`
- Modify: `frontend/src/App.tsx`
- Create: `frontend/src/student/student-workspace-state.ts`
- Test: `frontend/src/student/student-workspace-state.test.ts`

**Interfaces:**
- Produces: `studentDashboardApi.get()` and `studentDashboardApi.record(payload)`.
- Produces pure helpers `dashboardEmptyState`, `taskTypeLabel`, and `nextAccent` for component use.

- [ ] **Step 1: Install Vitest and write failing pure-state tests**

Run: `npm install -D vitest @testing-library/react @testing-library/jest-dom jsdom`

Add `"test": "vitest run"` and tests for `尚未安排学习任务`, `今日任务已完成`, task labels, and US/UK accent switching.

- [ ] **Step 2: Run tests and verify RED**

Run: `npm test -- student-workspace-state.test.ts`

Expected: helper module does not exist.

- [ ] **Step 3: Add types, API methods, helpers, and role split**

In `App.tsx`, render `<StudentWorkspace user={currentUser} dictionaries={dictionaries} onSignOut={handleSignOut} />` for `STUDENT` before the existing teacher workspace markup. Do not duplicate authentication bootstrapping.

- [ ] **Step 4: Run tests, typecheck, and build**

Run: `npm test && npm run build`

Expected: tests and build pass.

- [ ] **Step 5: Commit the task**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/App.tsx frontend/src/api/index.ts frontend/src/types/index.ts frontend/src/student
git commit -m "feat: add student workspace contracts"
```

### Task 5: Build the Responsive Student Workspace and Syllable Reader

**Files:**
- Create: `frontend/src/student/StudentWorkspace.tsx`
- Create: `frontend/src/student/StudentDashboardHome.tsx`
- Create: `frontend/src/student/StudentStudySession.tsx`
- Create: `frontend/src/student/SyllableReader.tsx`
- Create: `frontend/src/student/StudentLibrary.tsx`
- Create: `frontend/src/student/StudentProfile.tsx`
- Create: `frontend/src/student/student-workspace.css`
- Test: `frontend/src/student/StudentWorkspace.test.tsx`
- Test: `frontend/src/student/SyllableReader.test.tsx`

**Interfaces:**
- `StudentWorkspaceProps`: authenticated `User`, assigned `Dictionary[]`, and `onSignOut`.
- `SyllableReaderProps`: `word`, optional `phoneticDetail`, optional `syllableDetail`.
- `StudentStudySession` submits authoritative attention payloads and replaces local dashboard state with the server response.

- [ ] **Step 1: Write failing component tests**

Test student home summary, both empty-state copies, teacher/admin controls absence, `INCORRECT` submission, disabled buttons while submitting, missing syllable fallback, US/UK switching, audio URL preference, and speech synthesis fallback.

- [ ] **Step 2: Run component tests and verify RED**

Run: `npm test -- StudentWorkspace.test.tsx SyllableReader.test.tsx`

Expected: components do not exist.

- [ ] **Step 3: Implement focused components**

Use the approved visual direction: neutral surfaces, rust primary actions, blue/green status accents, Phosphor icons, 8px card radii, mobile bottom navigation, and desktop left rail. Reuse the existing student dictionary APIs and word detail components without exposing management actions.

- [ ] **Step 4: Implement audio behavior**

Prefer `usAudioUrl`/`ukAudioUrl` with `HTMLAudioElement`; on playback failure or missing URL, use `SpeechSynthesisUtterance`. Cancel previous audio/speech before each action. Persist accent under `student-pronunciation-accent`.

- [ ] **Step 5: Run tests, lint, and build**

Run: `npm test && npm run lint && npm run build`

Expected: all commands pass.

- [ ] **Step 6: Commit the task**

```bash
git add frontend/src/student frontend/src/App.tsx frontend/src/App.css
git commit -m "feat: build responsive student workspace"
```

### Task 6: Add Admin Backfill Control

**Files:**
- Modify: `admin-frontend/src/api/ai-configs.ts`
- Modify: `admin-frontend/src/types/api.ts`
- Modify: `admin-frontend/src/pages/ai-configs-page.tsx`
- Test: `admin-frontend/src/pages/ai-configs-page.test.tsx`

**Interfaces:**
- Produces admin API method `backfillPublishedPlanSyllables()` and renders aggregate success/failure feedback.

- [ ] **Step 1: Write a failing page test**

Assert an admin can click `补全已发布计划音节`, the API is called once, and attempted/updated/failure counts render. Assert non-admin roles do not see the action.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `npm test -- ai-configs-page.test.tsx`

Expected: action and API method are missing.

- [ ] **Step 3: Implement API and control**

Add a clearly labeled command area to the AI config page, disable the button while running, and render individual failures without nesting cards.

- [ ] **Step 4: Run admin tests and build**

Run: `npm test && npm run build`

Expected: tests and build pass.

- [ ] **Step 5: Commit the task**

```bash
git add admin-frontend/src/api/ai-configs.ts admin-frontend/src/types/api.ts admin-frontend/src/pages/ai-configs-page.tsx admin-frontend/src/pages/ai-configs-page.test.tsx
git commit -m "feat(admin): add syllable backfill control"
```

### Task 7: Full Verification, Docker Rebuild, and Visual QA

**Files:**
- Modify: `docs/superpowers/prototypes/student-post-login-mobile/design-qa.md`
- Create: `docs/superpowers/prototypes/student-post-login-mobile/artifacts/production-student-home-390x844.jpg`
- Create: `docs/superpowers/prototypes/student-post-login-mobile/artifacts/production-student-study-390x844.jpg`
- Create: `docs/superpowers/prototypes/student-post-login-mobile/artifacts/production-student-desktop.jpg`

- [ ] **Step 1: Run the complete verification suite**

Run:

```bash
./mvnw test
(cd frontend && npm test && npm run lint && npm run build)
(cd admin-frontend && npm test && npm run build)
```

Expected: every command exits 0.

- [ ] **Step 2: Rebuild required Docker services**

Run: `docker-compose up -d --build frontend admin-frontend`

Expected: both services report running and the backend remains healthy.

- [ ] **Step 3: Browser-test authenticated student flows**

At 390x844 and 320x740 verify login role routing, dashboard counts, empty states, queue ordering, retry-to-tail behavior, skip/correct completion, US/UK switch, audio fallback, library, profile, and logout. At desktop verify left rail and no horizontal overflow.

- [ ] **Step 4: Capture screenshots and update design QA**

Compare production screenshots with the selected Daily Mission Hub visual and the syllable-reading reference. Fix all P0/P1/P2 issues; `design-qa.md` must end with `final result: passed`.

- [ ] **Step 5: Final regression check**

Verify an authenticated teacher still sees the existing teacher workspace and an admin still redirects to `/admin/`.
