import { createResource, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { compactFileSize, formatDateTime } from "@/lib/format";
import type { ClassroomResponse, Dictionary } from "@/types/api";

interface DictionariesPageData {
    dictionaries: Dictionary[];
    classrooms: ClassroomResponse[];
}

export function DictionariesPage() {
    const auth = useAuth();
    const [feedback, setFeedback] = createSignal("");
    const [form, setForm] = createStore({
        name: "",
        category: "",
    });
    const [selectedClassroom, setSelectedClassroom] = createStore<Record<number, string>>({});

    const [pageData, { refetch }] = createResource(
        () => auth.user(),
        async (user): Promise<DictionariesPageData | null> => {
            if (!user) {
                return null;
            }
            const [dictionaries, classrooms] = await Promise.all([api.listDictionaries(), api.listClassrooms()]);
            return { dictionaries, classrooms };
        },
    );

    const mutateWithRefetch = async (runner: () => Promise<unknown>, successMessage: string) => {
        setFeedback("");
        await runner();
        setFeedback(successMessage);
        await refetch();
    };

    const handleCreate = async (event: SubmitEvent) => {
        event.preventDefault();
        await mutateWithRefetch(
            () =>
                api.createDictionary({
                    name: form.name.trim(),
                    category: form.category.trim() || undefined,
                }),
            "词书已创建。",
        );
        setForm({ name: "", category: "" });
    };

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Dictionaries"
                title="词书资源"
                description="集中查看可见词书，手动创建老师词书，并将词书分配到班级。"
                actions={
                    <Button variant="outline" onClick={() => void refetch()}>
                        刷新
                    </Button>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>

            <Show
                when={pageData()}
                fallback={
                    <Card>
                        <CardContent class="p-6 text-sm text-muted-foreground">正在加载词书数据...</CardContent>
                    </Card>
                }
            >
                {(data) => (
                    <>
                        <Card>
                            <CardHeader>
                                <CardTitle>新建词书</CardTitle>
                                <CardDescription>适合老师创建自己的专题词书，也适合管理员补充系统资源。</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <form class="grid gap-4 md:grid-cols-[1fr_220px_auto]" onSubmit={handleCreate}>
                                    <div class="space-y-2">
                                        <Label>词书名称</Label>
                                        <Input value={form.name} onInput={(event) => setForm("name", event.currentTarget.value)} />
                                    </div>
                                    <div class="space-y-2">
                                        <Label>分类</Label>
                                        <Input placeholder="例如：高考 / 考研 / 自定义" value={form.category} onInput={(event) => setForm("category", event.currentTarget.value)} />
                                    </div>
                                    <div class="flex items-end">
                                        <Button class="w-full md:w-auto" type="submit">创建词书</Button>
                                    </div>
                                </form>
                            </CardContent>
                        </Card>

                        <Show when={data().dictionaries.length > 0} fallback={<EmptyState title="暂无可见词书" description="可以先手动创建，也可以到导入中心触发批量导入。" />}>
                            <div class="grid gap-5 xl:grid-cols-2">
                                <For each={data().dictionaries}>
                                    {(dictionary) => (
                                        <Card>
                                            <CardHeader>
                                                <div class="flex items-start justify-between gap-4">
                                                    <div>
                                                        <CardTitle>{dictionary.name}</CardTitle>
                                                        <CardDescription>{dictionary.category || "未分类词书"}</CardDescription>
                                                    </div>
                                                    <Badge variant="outline">{dictionary.scopeType || "SYSTEM"}</Badge>
                                                </div>
                                            </CardHeader>
                                            <CardContent class="space-y-4">
                                                <div class="grid gap-3 sm:grid-cols-3">
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">Words</p>
                                                        <p class="mt-2 font-display text-2xl font-semibold">{dictionary.wordCount || 0}</p>
                                                    </div>
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">Entries</p>
                                                        <p class="mt-2 font-display text-2xl font-semibold">{dictionary.entryCount || 0}</p>
                                                    </div>
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">Size</p>
                                                        <p class="mt-2 font-display text-2xl font-semibold">{compactFileSize(dictionary.fileSize)}</p>
                                                    </div>
                                                </div>

                                                <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm text-muted-foreground">
                                                    更新时间：{formatDateTime(dictionary.updatedAt || dictionary.createdAt)}
                                                </div>

                                                <div class="grid gap-3 md:grid-cols-[1fr_auto]">
                                                    <select
                                                        class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                        value={selectedClassroom[dictionary.id] || ""}
                                                        onChange={(event) => setSelectedClassroom(dictionary.id, event.currentTarget.value)}
                                                    >
                                                        <option value="">选择班级后分配</option>
                                                        <For each={data().classrooms}>
                                                            {(classroom) => <option value={classroom.id}>{classroom.name}</option>}
                                                        </For>
                                                    </select>
                                                    <Button
                                                        onClick={() => {
                                                            const classroomId = selectedClassroom[dictionary.id];
                                                            if (!classroomId) {
                                                                return;
                                                            }
                                                            void mutateWithRefetch(
                                                                () => api.assignDictionaryToClassrooms(dictionary.id, [Number(classroomId)]),
                                                                "已将词书分配到班级。",
                                                            );
                                                        }}
                                                    >
                                                        分配到班级
                                                    </Button>
                                                </div>
                                            </CardContent>
                                        </Card>
                                    )}
                                </For>
                            </div>
                        </Show>
                    </>
                )}
            </Show>
        </section>
    );
}
