import { fireEvent, render, screen } from "@solidjs/testing-library";
import { createSignal } from "solid-js";
import { describe, expect, it, vi } from "vitest";
import { SearchableDictionarySelect } from "@/components/study-plans/searchable-dictionary-select";

const dictionaries = [
    { id: 7, name: "CET-4 核心词汇", wordCount: 1200 },
    { id: 8, name: "考研英语高频词", wordCount: 860 },
];

function SelectorHarness() {
    const [value, setValue] = createSignal("");
    return (
        <SearchableDictionarySelect
            dictionaries={dictionaries}
            value={value()}
            onChange={setValue}
        />
    );
}

function MissingCountSelectorHarness() {
    const [value, setValue] = createSignal("");
    return (
        <SearchableDictionarySelect
            dictionaries={[{ id: 9, name: "未统计词书", wordCount: null }]}
            value={value()}
            onChange={setValue}
        />
    );
}

describe("SearchableDictionarySelect", () => {
    it("filters dictionary names case-insensitively and selects a result", async () => {
        render(() => <SelectorHarness />);

        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        fireEvent.input(screen.getByPlaceholderText("搜索词书名称"), { target: { value: "cet-4" } });

        expect(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" })).toBeInTheDocument();
        expect(screen.queryByRole("option", { name: "考研英语高频词 860 词" })).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" }));

        expect(screen.getByRole("button", { name: "CET-4 核心词汇，1200 词" })).toBeInTheDocument();
        expect(screen.queryByPlaceholderText("搜索词书名称")).not.toBeInTheDocument();
    });

    it("shows an empty result and restores options when search is cleared", () => {
        render(() => <SelectorHarness />);

        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        fireEvent.input(screen.getByPlaceholderText("搜索词书名称"), { target: { value: "托福" } });

        expect(screen.getByText("没有匹配的词书")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "清空词书搜索" }));
        expect(screen.getAllByRole("option")).toHaveLength(2);
    });

    it("closes on Escape and returns focus to the trigger", () => {
        render(() => <SelectorHarness />);
        const trigger = screen.getByRole("button", { name: "选择词书" });

        fireEvent.click(trigger);
        fireEvent.keyDown(screen.getByPlaceholderText("搜索词书名称"), { key: "Escape" });

        expect(screen.queryByPlaceholderText("搜索词书名称")).not.toBeInTheDocument();
        expect(trigger).toHaveFocus();
    });

    it("disables selection when no dictionaries are available", () => {
        const onChange = vi.fn();
        render(() => <SearchableDictionarySelect dictionaries={[]} value="" onChange={onChange} />);

        expect(screen.getByRole("button", { name: "暂无可选词书" })).toBeDisabled();
    });

    it("uses zero as the fallback for a missing word count", () => {
        render(() => <MissingCountSelectorHarness />);

        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        const option = screen.getByRole("option", { name: "未统计词书 0 词" });
        expect(option).toBeInTheDocument();

        fireEvent.click(option);
        expect(screen.getByRole("button", { name: "未统计词书，0 词" })).toBeInTheDocument();
    });
});
