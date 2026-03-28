import { createMemo, createResource, createSignal, For, Show } from "solid-js";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { enumLabel, formatDateTime, formatNumber } from "@/lib/format";

export function ImportCenterPage() {
    const auth = useAuth();
    const [feedback, setFeedback] = createSignal("");
    const isAdmin = createMemo(() => auth.user()?.role === "ADMIN");
    const [selectedBatchId, setSelectedBatchId] = createSignal<string | null>(null);

    const [latestBatch, { refetch: refetchLatest }] = createResource(
        () => auth.user(),
        async (user) => {
            if (!user || user.role !== "ADMIN") {
                return null;
            }
            const batch = await api.getLatestImportBatch().catch(() => null);
            if (batch && !selectedBatchId()) {
                setSelectedBatchId(batch.jobId);
            }
            return batch;
        },
    );

    const [batchDetails, { refetch: refetchDetails }] = createResource(selectedBatchId, async (batchId) => {
        if (!batchId) {
            return null;
        }
        const [job, files, conflicts] = await Promise.all([
            api.getImportBatch(batchId),
            api.getImportBatchFiles(batchId),
            api.getImportBatchConflicts(batchId),
        ]);
        return { job, files, conflicts };
    });

    const mutate = async (runner: () => Promise<{ jobId: string }>, successMessage: string) => {
        const batch = await runner();
        setFeedback(successMessage);
        setSelectedBatchId(batch.jobId);
        await refetchLatest();
        await refetchDetails();
    };

    if (!isAdmin()) {
        return (
            <section class="space-y-6">
                <PageHeader eyebrow="Imports" title="导入中心" description="该页面仅对管理员开放。" />
                <EmptyState title="没有访问权限" description="老师角色不参与词书批量导入流程。" />
            </section>
        );
    }

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Imports"
                title="批量导入中心"
                description="触发 books 目录扫描，追踪导入状态，并在冲突评审后完成发布。"
                actions={
                    <div class="flex items-center gap-3">
                        <Button variant="outline" onClick={() => void refetchLatest()}>
                            刷新
                        </Button>
                        <Button onClick={() => void mutate(() => api.createImportBatch(), "已创建新的导入批次。")}>
                            新建导入批次
                        </Button>
                    </div>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>

            <div class="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
                <Card>
                    <CardHeader>
                        <CardTitle>最近批次</CardTitle>
                        <CardDescription>优先从最近一次批次进入排查与发布。</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <Show
                            when={latestBatch()}
                            fallback={<EmptyState title="暂无批次" description="点击右上角按钮启动一次新的导入任务。" />}
                        >
                            {(batch) => (
                                <button
                                    class="w-full rounded-3xl border border-primary/20 bg-primary/5 p-5 text-left"
                                    onClick={() => setSelectedBatchId(batch().jobId)}
                                >
                                    <div class="flex items-start justify-between gap-4">
                                        <div>
                                            <p class="font-medium text-foreground">{batch().jobId}</p>
                                            <p class="mt-1 text-sm text-muted-foreground">{formatDateTime(batch().createdAt)}</p>
                                        </div>
                                        <Badge variant="outline">{batch().status}</Badge>
                                    </div>
                                    <div class="mt-4 grid gap-3 md:grid-cols-2">
                                        <div class="rounded-2xl border border-border/60 bg-background/60 p-4">
                                            <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">文件</p>
                                            <p class="mt-2 font-display text-2xl font-semibold">{formatNumber(batch().totalFiles)}</p>
                                        </div>
                                        <div class="rounded-2xl border border-border/60 bg-background/60 p-4">
                                            <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">冲突</p>
                                            <p class="mt-2 font-display text-2xl font-semibold">{formatNumber(batch().conflictCount)}</p>
                                        </div>
                                    </div>
                                </button>
                            )}
                        </Show>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <div class="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <CardTitle>{batchDetails()?.job.jobId || "选择批次查看详情"}</CardTitle>
                                <CardDescription>批次详情、文件分布和冲突统计会显示在这里。</CardDescription>
                            </div>
                            <Show when={batchDetails()}>
                                {(details) => (
                                    <div class="flex flex-wrap gap-2">
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={() => void mutate(() => api.autoMergeImportBatch(details().job.jobId), "已启动自动合并。")}
                                        >
                                            自动合并
                                        </Button>
                                        <Button
                                            size="sm"
                                            onClick={() => void mutate(() => api.publishImportBatch(details().job.jobId), "已启动发布。")}
                                        >
                                            发布
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="destructive"
                                            onClick={() => void mutate(() => api.discardImportBatch(details().job.jobId), "批次已丢弃。")}
                                        >
                                            丢弃
                                        </Button>
                                    </div>
                                )}
                            </Show>
                        </div>
                    </CardHeader>
                    <CardContent class="space-y-5">
                        <Show when={batchDetails()} fallback={<p class="text-sm text-muted-foreground">尚未选择任何导入批次。</p>}>
                            {(details) => (
                                <>
                                    <div class="grid gap-4 md:grid-cols-4">
                                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                            状态：<span class="font-medium">{enumLabel(details().job.status)}</span>
                                        </div>
                                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                            处理行数：<span class="font-medium">{formatNumber(details().job.processedRows)}</span>
                                        </div>
                                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                            成功：<span class="font-medium">{formatNumber(details().job.successRows)}</span>
                                        </div>
                                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                            失败：<span class="font-medium">{formatNumber(details().job.failedRows)}</span>
                                        </div>
                                    </div>

                                    <div class="space-y-3">
                                        <p class="text-sm font-medium text-foreground">文件列表</p>
                                        <Show when={details().files.length > 0} fallback={<p class="text-sm text-muted-foreground">暂无文件统计。</p>}>
                                            <div class="space-y-3">
                                                <For each={details().files.slice(0, 8)}>
                                                    {(file) => (
                                                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                            <div class="flex items-center justify-between gap-4">
                                                                <div>
                                                                    <p class="font-medium text-foreground">{file.fileName}</p>
                                                                    <p class="mt-1 text-sm text-muted-foreground">{file.dictionaryName || "未绑定词书名"}</p>
                                                                </div>
                                                                <Badge variant="outline">{file.status}</Badge>
                                                            </div>
                                                        </div>
                                                    )}
                                                </For>
                                            </div>
                                        </Show>
                                    </div>

                                    <div class="space-y-3">
                                        <p class="text-sm font-medium text-foreground">冲突摘要</p>
                                        <Show when={details().conflicts.length > 0} fallback={<p class="text-sm text-muted-foreground">当前批次没有冲突。</p>}>
                                            <div class="space-y-3">
                                                <For each={details().conflicts.slice(0, 10)}>
                                                    {(conflict) => (
                                                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                            <div class="flex items-center justify-between gap-4">
                                                                <div>
                                                                    <p class="font-medium text-foreground">{conflict.displayWord}</p>
                                                                    <p class="mt-1 text-sm text-muted-foreground">{conflict.dictionaryNames.join(" / ")}</p>
                                                                </div>
                                                                <Badge variant={conflict.resolution ? "success" : "warning"}>
                                                                    {conflict.resolution || conflict.conflictType}
                                                                </Badge>
                                                            </div>
                                                        </div>
                                                    )}
                                                </For>
                                            </div>
                                        </Show>
                                    </div>
                                </>
                            )}
                        </Show>
                    </CardContent>
                </Card>
            </div>
        </section>
    );
}
