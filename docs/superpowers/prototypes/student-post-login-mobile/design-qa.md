# Student Mobile Prototype Design QA

source visual truth path: `/Users/wyn/.codex/generated_images/019eea9c-25b9-78e2-9980-fb5fdff76639/ig_0209848c4afbf5a4016a37f8d4754c819aaaa1536f07e1eef0.png`

implementation screenshot paths:
- `artifacts/home-390x844.jpg`
- `artifacts/home-320x740.jpg`
- `artifacts/study-390x844.jpg`
- `artifacts/self-test-390x844.jpg`
- `artifacts/formal-exams-390x844.jpg`
- `artifacts/stats-390x844.jpg`

viewports: 390 x 844 primary mobile target; 320 x 740 narrow-phone validation

states reviewed: dashboard, planned study, free study, self-practice setup and question, formal exam list and question, assigned dictionaries, wrong words, favorites, analytics, profile editing, and password change

## Comparison Evidence

Full-view comparison: the implemented dashboard preserves the selected Daily Mission Hub hierarchy: editorial greeting, role marker, dominant today-task block, 8/14 completion summary, 57% progress ring, overdue/today/new-word split, primary learning action, reminders, shortcuts, and fixed four-item navigation. The implementation uses a quieter neutral surface and 8px cards while retaining the source visual's rust, blue, and green state hierarchy.

Focused-region comparison: the mission summary proportions, serif display type, monospace labels, outline icon treatment, progress ring, reminder rows, and bottom navigation align with the selected visual direction. The study card and analytics screens extend the same typography, spacing, and status-color system without introducing a second visual language.

## Findings

- No open P0, P1, or P2 visual issues.
- The 390px viewport has no horizontal overflow; the phone frame and fixed navigation exactly match the viewport bounds.
- The 320px narrow-phone viewport has no horizontal overflow or off-canvas elements.
- Navigation resets the internal scroll position to the top on every screen transition.
- Browser console inspection returned no warnings or errors.

## Interaction Verification

- Planned study records `我会了`, `再学一次`, and `先跳过` feedback; errors enter the automatic wrong-word state.
- Free study is limited to assigned dictionary entry points and is labeled `不计计划进度`.
- Self-practice supports assigned-dictionary choice and 10/20/30-question configuration, and is labeled as excluded from teacher evaluation.
- Formal exams are separately labeled as included in teacher evaluation.
- Wrong words and favorites are separate views, while a word can appear in both.
- Analytics supports 30-day calendar details and source filters for plan, free study, formal exam, and self-practice.
- Profile fields are editable; password change validates old/new/confirmation fields and shows the required logout handoff state.

patches made since previous QA pass: completed all visible prototype controls, added assessment and password flows, added responsive scroll reset and safe-area handling, removed external font dependency, aligned cards to 8px radii, and captured implementation evidence through the in-app browser.

final result: passed
