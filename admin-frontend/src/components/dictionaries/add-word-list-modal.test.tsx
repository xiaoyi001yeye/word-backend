import { fireEvent, render, screen, waitFor } from "@solidjs/testing-library";
import { createSignal } from "solid-js";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AddWordListModal } from "@/components/dictionaries/add-word-list-modal";
import { api } from "@/lib/api";

vi.mock("@/lib/api", () => ({
    api: {
        addDictionaryWordList: vi.fn(),
        listDictionaryMetaWordSuggestions: vi.fn(),
    },
}));

const dictionary = {
    id: 7,
    name: "测试词书",
    category: "测试",
    scopeType: "CUSTOM",
    ownerUserId: 1,
};

describe("AddWordListModal", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(api.listDictionaryMetaWordSuggestions).mockResolvedValue([]);
        vi.mocked(api.addDictionaryWordList).mockResolvedValue({
            total: 1,
            existed: 0,
            created: 1,
            added: 1,
            failed: 0,
        });
    });

    it("shows the dialog when isOpen changes from false to true", async () => {
        let setOpen!: (value: boolean) => void;

        const Wrapper = () => {
            const [open, updateOpen] = createSignal(false);
            setOpen = updateOpen;
            return (
                <AddWordListModal
                    dictionary={dictionary}
                    isOpen={open()}
                    onClose={vi.fn()}
                />
            );
        };

        render(() => <Wrapper />);

        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

        setOpen(true);

        expect(await screen.findByRole("dialog")).toBeInTheDocument();
        expect(screen.getByText("手动录入单词")).toBeInTheDocument();
    });

    it("submits bulk entries through the existing batch API", async () => {
        const onSuccess = vi.fn();

        render(() => (
            <AddWordListModal
                dictionary={dictionary}
                isOpen={true}
                onClose={vi.fn()}
                onSuccess={onSuccess}
            />
        ));

        fireEvent.click(screen.getByRole("button", { name: "批量粘贴" }));
        fireEvent.input(screen.getByRole("textbox"), {
            currentTarget: { value: "apple | 苹果 | noun" },
            target: { value: "apple | 苹果 | noun" },
        });
        fireEvent.click(screen.getByRole("button", { name: "解析并添加" }));

        await waitFor(() => {
            expect(api.addDictionaryWordList).toHaveBeenCalledWith(7, [
                {
                    word: "apple",
                    translation: "苹果",
                    partOfSpeech: "noun",
                    phonetic: undefined,
                    definition: undefined,
                    exampleSentence: undefined,
                    difficulty: 2,
                },
            ]);
        });

        await waitFor(() => {
            expect(onSuccess).toHaveBeenCalledTimes(1);
        });

        expect(screen.getByText("处理结果")).toBeInTheDocument();
    });
});
