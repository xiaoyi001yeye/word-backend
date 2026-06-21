import { createEffect, createMemo, createSignal, For, Show } from "solid-js";
import { Input } from "@/components/ui/input";
import type { Dictionary } from "@/types/api";

interface SearchableDictionarySelectProps {
    dictionaries: Dictionary[];
    value: string;
    onChange: (value: string) => void;
}

export function SearchableDictionarySelect(props: SearchableDictionarySelectProps) {
    const [open, setOpen] = createSignal(false);
    const [keyword, setKeyword] = createSignal("");
    let triggerRef!: HTMLButtonElement;
    let searchInputRef!: HTMLInputElement;

    const selectedDictionary = createMemo(() =>
        props.dictionaries.find((dictionary) => String(dictionary.id) === props.value),
    );
    const filteredDictionaries = createMemo(() => {
        const normalizedKeyword = keyword().trim().toLocaleLowerCase("zh-CN");
        if (!normalizedKeyword) {
            return props.dictionaries;
        }
        return props.dictionaries.filter((dictionary) =>
            dictionary.name.toLocaleLowerCase("zh-CN").includes(normalizedKeyword),
        );
    });

    createEffect(() => {
        if (open()) {
            queueMicrotask(() => searchInputRef?.focus());
        }
    });

    const close = (restoreFocus = false) => {
        setOpen(false);
        setKeyword("");
        if (restoreFocus) {
            triggerRef?.focus();
        }
    };

    const toggle = () => {
        if (open()) {
            close(true);
            return;
        }
        setKeyword("");
        setOpen(true);
    };

    const selectDictionary = (dictionary: Dictionary) => {
        props.onChange(String(dictionary.id));
        close(true);
    };

    return (
        <div
            class="relative"
            onFocusOut={(event) => {
                if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
                    close();
                }
            }}
        >
            <button
                ref={triggerRef}
                type="button"
                class={
                    "flex h-11 w-full items-center justify-between rounded-lg border border-input bg-background/70 " +
                    "px-3 text-left text-sm disabled:cursor-not-allowed disabled:opacity-60"
                }
                aria-expanded={open()}
                aria-haspopup="listbox"
                aria-label={
                    selectedDictionary()
                        ? `${selectedDictionary()!.name}，${selectedDictionary()!.wordCount || 0} 词`
                        : props.dictionaries.length > 0
                            ? "选择词书"
                            : "暂无可选词书"
                }
                disabled={props.dictionaries.length === 0}
                onClick={toggle}
            >
                <Show
                    when={selectedDictionary()}
                    fallback={
                        <span class="text-muted-foreground">
                            {props.dictionaries.length > 0 ? "选择词书" : "暂无可选词书"}
                        </span>
                    }
                >
                    {(dictionary) => (
                        <span class="flex min-w-0 items-center gap-2">
                            <span class="truncate font-medium">{dictionary().name}</span>
                            <span class="shrink-0 text-xs text-muted-foreground">
                                {dictionary().wordCount || 0} 词
                            </span>
                        </span>
                    )}
                </Show>
                <span aria-hidden="true" class="text-muted-foreground">⌄</span>
            </button>

            <Show when={open()}>
                <div class="absolute z-50 mt-2 w-full rounded-xl border border-border bg-background p-3 shadow-xl">
                    <div class="flex gap-2">
                        <Input
                            ref={searchInputRef}
                            placeholder="搜索词书名称"
                            value={keyword()}
                            onInput={(event) => setKeyword(event.currentTarget.value)}
                            onKeyDown={(event) => {
                                if (event.key === "Escape") {
                                    event.preventDefault();
                                    close(true);
                                }
                            }}
                        />
                        <Show when={keyword()}>
                            <button
                                type="button"
                                class={
                                    "rounded-lg border border-border px-3 text-xs text-muted-foreground " +
                                    "hover:text-foreground"
                                }
                                aria-label="清空词书搜索"
                                onClick={() => {
                                    setKeyword("");
                                    searchInputRef?.focus();
                                }}
                            >
                                清空
                            </button>
                        </Show>
                    </div>

                    <div class="mt-3 max-h-64 space-y-1 overflow-y-auto" role="listbox" aria-label="词书列表">
                        <Show
                            when={filteredDictionaries().length > 0}
                            fallback={
                                <p class="px-3 py-6 text-center text-sm text-muted-foreground">
                                    没有匹配的词书
                                </p>
                            }
                        >
                            <For each={filteredDictionaries()}>
                                {(dictionary) => (
                                    <button
                                        type="button"
                                        role="option"
                                        aria-selected={String(dictionary.id) === props.value}
                                        aria-label={`${dictionary.name} ${dictionary.wordCount || 0} 词`}
                                        class={
                                            String(dictionary.id) === props.value
                                                ? "flex w-full items-center justify-between rounded-lg " +
                                                  "bg-primary/10 px-3 py-2.5 text-left text-sm text-primary"
                                                : "flex w-full items-center justify-between rounded-lg px-3 py-2.5 " +
                                                  "text-left text-sm hover:bg-muted/60"
                                        }
                                        onClick={() => selectDictionary(dictionary)}
                                    >
                                        <span class="truncate font-medium">{dictionary.name}</span>
                                        <span class="ml-3 shrink-0 text-xs text-muted-foreground">
                                            {dictionary.wordCount || 0} 词
                                        </span>
                                    </button>
                                )}
                            </For>
                        </Show>
                    </div>
                </div>
            </Show>
        </div>
    );
}
