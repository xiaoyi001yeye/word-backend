import { fireEvent, render, screen } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DictionariesPage } from "@/pages/dictionaries-page";
import { api } from "@/lib/api";

vi.mock("@/lib/api", () => ({
    api: {
        listDictionaries: vi.fn(),
        listDictionaryEntriesPage: vi.fn(),
        getMetaWord: vi.fn(),
        listDictionaryMetaWordSuggestions: vi.fn(),
        addDictionaryWordList: vi.fn(),
        generateDictionaryWordWithAi: vi.fn(),
        createDictionary: vi.fn(),
        renameDictionary: vi.fn(),
        deleteDictionary: vi.fn(),
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
        vi.mocked(api.getMetaWord).mockResolvedValue({
            id: 21,
            word: "apple",
            normalizedWord: "apple",
            phonetic: "/ˈæp.əl/",
            phoneticDetail: { uk: "/ˈæp.əl/", us: "/ˈæp.əl/" },
            syllableDetail: {
                segments: [
                    { text: "ap", ukPhonetic: "/æp/", usPhonetic: "/æp/" },
                    { text: "ple", ukPhonetic: "/əl/", usPhonetic: "/əl/" },
                ],
            },
            partOfSpeech: "n.",
            partOfSpeechDetail: [
                {
                    pos: "n.",
                    definitions: [
                        {
                            definition: "a round fruit",
                            translation: "苹果",
                            exampleSentences: [
                                { sentence: "I ate an apple.", translation: "我吃了一个苹果。" },
                            ],
                        },
                    ],
                    synonyms: ["fruit"],
                    antonyms: [],
                },
            ],
            learningDetail: {
                learningMaterial: "able + -ity\nknowability 可知性\nchangeability 可变性\nreadability 可读性\nusability 可用性",
                memoryHint: "ability = able + -ity：能够做事的能力。",
                samePatternWords: [
                    {
                        word: "knowability",
                        translation: "可知性",
                        rootBreakdown: "know（知道）+ able（能够）+ -ity（名词后缀）",
                    },
                ],
                examPhrases: ["have the ability to do sth"],
                wordFamily: [
                    { word: "able", pos: "adj.", translation: "能够的" },
                ],
                confusableWords: [
                    { word: "capacity", distinction: "ability 强调能力；capacity 更强调容量或潜能。" },
                ],
            },
            difficulty: 2,
        });
        vi.mocked(api.generateDictionaryWordWithAi).mockResolvedValue({
            dictionaryId: 7,
            metaWordId: 1,
            configId: 1,
            providerName: "OpenAI",
            modelName: "gpt-4o-mini",
            word: "apple",
            total: 1,
            existed: 1,
            created: 0,
            added: 0,
            failed: 0,
        });
        vi.mocked(api.addDictionaryWordList).mockResolvedValue({
            total: 0,
            existed: 0,
            created: 0,
            added: 0,
            failed: 0,
        });
        vi.mocked(api.renameDictionary).mockResolvedValue({
            id: 7,
            name: "新词书名称",
        });
        vi.mocked(api.deleteDictionary).mockResolvedValue({ message: "deleted", id: 7 });
    });

    it("opens the add-word dialog when clicking the add-word button", async () => {
        render(() => <DictionariesPage />);

        expect(await screen.findByText("词书单词表")).toBeInTheDocument();

        fireEvent.click(await screen.findByRole("button", { name: "添加单词" }));

        expect(await screen.findByRole("dialog")).toBeInTheDocument();
        expect(screen.getByText("手动录入单词")).toBeInTheDocument();
    });

    it("shows word ai action for dictionary entries", async () => {
        vi.mocked(api.listDictionaryEntriesPage).mockResolvedValue({
            content: [
                {
                    entryId: 11,
                    dictionaryId: 7,
                    metaWordId: 21,
                    word: "apple",
                    translation: "苹果",
                    phonetic: "/ˈæp.əl/",
                    definition: "a fruit",
                    chapterTagId: 1,
                    chapterDisplayPath: "默认章节",
                    entryOrder: 1,
                },
            ],
            totalElements: 1,
            totalPages: 1,
            size: 20,
            number: 0,
            numberOfElements: 1,
            first: true,
            last: true,
            empty: false,
        });

        render(() => <DictionariesPage />);

        expect(await screen.findByText("apple")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "单词AI" })).toBeInTheDocument();
    });

    it("shows structured word arrays as nested form fields", async () => {
        vi.mocked(api.listDictionaryEntriesPage).mockResolvedValue({
            content: [
                {
                    entryId: 11,
                    dictionaryId: 7,
                    metaWordId: 21,
                    word: "apple",
                    translation: "苹果",
                    entryOrder: 1,
                },
            ],
            totalElements: 1,
            totalPages: 1,
            size: 20,
            number: 0,
            numberOfElements: 1,
            first: true,
            last: true,
            empty: false,
        });

        render(() => <DictionariesPage />);
        fireEvent.click(await screen.findByRole("button", { name: "查看详细数据" }));

        expect(api.getMetaWord).toHaveBeenCalledWith(21);
        expect(await screen.findByText("词性与释义")).toBeInTheDocument();
        expect(screen.getByLabelText("中文释义")).toHaveValue("苹果");
        expect(screen.getByLabelText("英文例句 1")).toHaveValue("I ate an apple.");
        expect(screen.getByLabelText("第 1 段拼写")).toHaveValue("ap");
        expect((screen.getByLabelText("完整学习材料") as HTMLTextAreaElement).value).toContain("usability 可用性");
        expect(screen.getByLabelText("记忆提示（AI 学习补充）")).toHaveValue("ability = able + -ity：能够做事的能力。");
        expect(screen.getByLabelText("同构词 1")).toHaveValue("knowability");
        expect((screen.getByLabelText("字根拆解 1") as HTMLTextAreaElement).value).toContain("名词后缀");
        expect(screen.getByText("have the ability to do sth")).toBeInTheDocument();
        expect(screen.getByLabelText("派生词 1")).toHaveValue("able");
        expect(screen.getByLabelText("易混词 1")).toHaveValue("capacity");

        fireEvent.click(screen.getByRole("button", { name: "展开全部学习材料" }));
        expect(screen.getByRole("button", { name: "收起学习材料" })).toBeInTheDocument();
    });

    it("generates and refreshes the structured detail from its AI button", async () => {
        vi.mocked(api.listDictionaryEntriesPage).mockResolvedValue({
            content: [
                {
                    entryId: 11,
                    dictionaryId: 7,
                    metaWordId: 21,
                    word: "apple",
                    translation: "苹果",
                    entryOrder: 1,
                },
            ],
            totalElements: 1,
            totalPages: 1,
            size: 20,
            number: 0,
            numberOfElements: 1,
            first: true,
            last: true,
            empty: false,
        });

        render(() => <DictionariesPage />);
        fireEvent.click(await screen.findByRole("button", { name: "查看详细数据" }));
        await screen.findByText("词性与释义");

        fireEvent.click(screen.getByRole("button", { name: "AI 自动补全" }));

        expect(api.generateDictionaryWordWithAi).toHaveBeenCalledWith(7, {
            metaWordId: 21,
            word: "apple",
        });
        expect(await screen.findByText("单词AI已更新元单词数据：apple")).toBeInTheDocument();
        expect(api.getMetaWord).toHaveBeenCalledTimes(2);
    });

    it("shows explicit empty states when learning detail arrays are unavailable", async () => {
        vi.mocked(api.listDictionaryEntriesPage).mockResolvedValue({
            content: [
                { entryId: 11, dictionaryId: 7, metaWordId: 21, word: "apple", entryOrder: 1 },
            ],
            totalElements: 1,
            totalPages: 1,
            size: 20,
            number: 0,
            numberOfElements: 1,
            first: true,
            last: true,
            empty: false,
        });
        vi.mocked(api.getMetaWord).mockResolvedValue({ id: 21, word: "apple" });

        render(() => <DictionariesPage />);
        fireEvent.click(await screen.findByRole("button", { name: "查看详细数据" }));

        expect(await screen.findByText("教材来源与学习拓展")).toBeInTheDocument();
        expect(screen.getByLabelText("完整学习材料")).toHaveValue("未填写");
        expect(screen.getByLabelText("记忆提示（AI 学习补充）")).toHaveValue("未填写");
        expect(screen.getByText("暂无同构词")).toBeInTheDocument();
        expect(screen.getByText("暂无推荐搭配")).toBeInTheDocument();
        expect(screen.getByText("暂无常用派生词")).toBeInTheDocument();
        expect(screen.getByText("暂无易混词提示")).toBeInTheDocument();
    });

    it("renames a dictionary without changing its identity", async () => {
        render(() => <DictionariesPage />);

        fireEvent.click(await screen.findByRole("button", { name: "重命名词书" }));
        const input = screen.getByLabelText("词书名称");
        fireEvent.input(input, { target: { value: "新词书名称" } });
        fireEvent.click(screen.getByRole("button", { name: "保存名称" }));

        expect(api.renameDictionary).toHaveBeenCalledWith(7, "新词书名称");
        expect(await screen.findByText("词书已重命名为“新词书名称”，现有关联保持不变。")).toBeInTheDocument();
    });

    it("asks for confirmation before deleting a dictionary", async () => {
        render(() => <DictionariesPage />);

        fireEvent.click(await screen.findByRole("button", { name: "删除词书" }));
        expect(screen.getByText(/服务器会在删除前再次检查/)).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "确认删除" }));

        expect(api.deleteDictionary).toHaveBeenCalledWith(7);
        expect(await screen.findByText("词书“测试词书”已删除。")).toBeInTheDocument();
    });
});
