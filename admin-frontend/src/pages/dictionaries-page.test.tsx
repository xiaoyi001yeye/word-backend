import { fireEvent, render, screen } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DictionariesPage } from "@/pages/dictionaries-page";
import { api } from "@/lib/api";

vi.mock("@/lib/api", () => ({
    api: {
        listDictionaries: vi.fn(),
        listDictionaryEntriesPage: vi.fn(),
        listDictionaryMetaWordSuggestions: vi.fn(),
        addDictionaryWordList: vi.fn(),
        createDictionary: vi.fn(),
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
        ready: () => true,
        login: vi.fn(),
        logout: vi.fn(),
        refresh: vi.fn(),
    }),
}));

describe("DictionariesPage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(api.listDictionaries).mockResolvedValue([
            {
                id: 7,
                name: "测试词书",
                category: "测试",
                scopeType: "CUSTOM",
                ownerUserId: 1,
                wordCount: 0,
                entryCount: 0,
                fileSize: 0,
                createdAt: "2026-03-29T00:00:00Z",
                updatedAt: "2026-03-29T00:00:00Z",
            },
        ]);
        vi.mocked(api.listDictionaryEntriesPage).mockResolvedValue({
            content: [],
            totalElements: 0,
            totalPages: 1,
            size: 20,
            number: 0,
            numberOfElements: 0,
            first: true,
            last: true,
            empty: true,
        });
        vi.mocked(api.listDictionaryMetaWordSuggestions).mockResolvedValue([]);
        vi.mocked(api.addDictionaryWordList).mockResolvedValue({
            total: 0,
            existed: 0,
            created: 0,
            added: 0,
            failed: 0,
        });
    });

    it("opens the add-word dialog when clicking the add-word button", async () => {
        render(() => <DictionariesPage />);

        expect(await screen.findByText("词书单词表")).toBeInTheDocument();

        fireEvent.click(await screen.findByRole("button", { name: "添加单词" }));

        expect(await screen.findByRole("dialog")).toBeInTheDocument();
        expect(screen.getByText("手动录入单词")).toBeInTheDocument();
    });
});
