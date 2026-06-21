import { fireEvent, render, screen, waitFor } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { StudyPlansPage } from "@/pages/study-plans-page";

vi.mock("@/lib/api", () => ({
    api: {
        listStudyPlans: vi.fn(),
        listClassrooms: vi.fn(),
        listDictionaries: vi.fn(),
        createStudyPlan: vi.fn(),
        getStudyPlanOverview: vi.fn(),
        getStudyPlanStudents: vi.fn(),
        publishStudyPlan: vi.fn(),
    },
}));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => ({
            id: 1,
            username: "admin",
            displayName: "Admin",
            role: "ADMIN",
            status: "ACTIVE",
        }),
    }),
}));

const createdPlan = {
    id: 99,
    name: "四级计划",
    teacherId: 1,
    dictionaryId: 7,
    dictionaryName: "CET-4 核心词汇",
    classroomIds: [],
    startDate: "2026-06-21",
    endDate: null,
    timezone: "Asia/Shanghai",
    dailyNewCount: 20,
    dailyReviewLimit: 60,
    reviewMode: "FIXED_INTERVAL",
    reviewIntervals: [1, 3, 7, 14],
    completionThreshold: 85,
    dailyDeadlineTime: "21:00",
    attentionTrackingEnabled: true,
    minFocusSecondsPerWord: 2,
    maxFocusSecondsPerWord: 18,
    longStayWarningSeconds: 25,
    idleTimeoutSeconds: 12,
    status: "DRAFT",
    studentCount: 0,
};

describe("StudyPlansPage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(api.listStudyPlans).mockResolvedValue([]);
        vi.mocked(api.listClassrooms).mockResolvedValue([]);
        vi.mocked(api.listDictionaries).mockResolvedValue([
            { id: 7, name: "CET-4 核心词汇", wordCount: 1200 },
            { id: 8, name: "考研英语高频词", wordCount: 860 },
        ]);
        vi.mocked(api.createStudyPlan).mockResolvedValue(createdPlan);
        vi.mocked(api.getStudyPlanOverview).mockResolvedValue({
            studyPlanId: 99,
            studyPlanName: "四级计划",
            status: "DRAFT",
            taskDate: "2026-06-21",
            totalStudents: 0,
            completedStudents: 0,
            notStartedStudents: 0,
            inProgressStudents: 0,
            missedStudents: 0,
            averageCompletionRate: 0,
            averageAttentionScore: 0,
        });
        vi.mocked(api.getStudyPlanStudents).mockResolvedValue([]);
    });

    it("submits the ID selected from the searchable dictionary picker", async () => {
        render(() => <StudyPlansPage />);

        fireEvent.input(await screen.findByLabelText("计划名称"), {
            target: { value: "四级计划" },
        });
        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        fireEvent.input(screen.getByPlaceholderText("搜索词书名称"), {
            target: { value: "CET-4" },
        });
        fireEvent.click(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" }));
        fireEvent.click(screen.getByRole("button", { name: "创建计划" }));

        await waitFor(() => {
            expect(api.createStudyPlan).toHaveBeenCalledWith(
                expect.objectContaining({ name: "四级计划", dictionaryId: 7 }),
            );
        });
    });
});
