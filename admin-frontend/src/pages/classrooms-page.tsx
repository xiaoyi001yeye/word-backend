import { createMemo, createResource, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import type { ClassroomResponse, UserResponse } from "@/types/api";

interface ClassroomPageData {
    classrooms: ClassroomResponse[];
    teachers: UserResponse[];
    students: UserResponse[];
    membersByClassroom: Record<number, UserResponse[]>;
}

export function ClassroomsPage() {
    const auth = useAuth();
    const isAdmin = createMemo(() => auth.user()?.role === "ADMIN");
    const [feedback, setFeedback] = createSignal("");
    const [form, setForm] = createStore({
        name: "",
        description: "",
        teacherId: "",
    });
    const [selectedStudent, setSelectedStudent] = createStore<Record<number, string>>({});

    const [pageData, { refetch }] = createResource(
        () => auth.user(),
        async (user): Promise<ClassroomPageData | null> => {
            if (!user) {
                return null;
            }

            const [classrooms, students, allUsers] = await Promise.all([
                api.listClassrooms(),
                api.listStudents(),
                user.role === "ADMIN" ? api.listUsers() : Promise.resolve<UserResponse[]>([]),
            ]);

            const members = await Promise.all(
                classrooms.map(async (classroom) => [classroom.id, await api.getClassroomStudents(classroom.id)] as const),
            );

            return {
                classrooms,
                teachers: user.role === "ADMIN" ? allUsers.filter((item) => item.role === "TEACHER") : [],
                students,
                membersByClassroom: Object.fromEntries(members),
            };
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
                api.createClassroom({
                    name: form.name.trim(),
                    description: form.description.trim() || undefined,
                    teacherId: form.teacherId ? Number(form.teacherId) : undefined,
                }),
            "班级已创建。",
        );
        setForm({
            name: "",
            description: "",
            teacherId: "",
        });
    };

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Classrooms"
                title="班级管理"
                description="创建班级、绑定老师、维护班级内学生名单。老师只能管理自己负责的班级。"
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
                        <CardContent class="p-6 text-sm text-muted-foreground">正在加载班级数据...</CardContent>
                    </Card>
                }
            >
                {(data) => (
                    <>
                        <Card>
                            <CardHeader>
                                <CardTitle>新建班级</CardTitle>
                                <CardDescription>管理员可指定老师，老师创建时默认归自己管理。</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <form class="grid gap-4" onSubmit={handleCreate}>
                                    <div class="grid gap-4 md:grid-cols-2">
                                        <div class="space-y-2">
                                            <Label>班级名称</Label>
                                            <Input value={form.name} onInput={(event) => setForm("name", event.currentTarget.value)} />
                                        </div>
                                        <Show when={isAdmin()}>
                                            <div class="space-y-2">
                                                <Label>负责老师</Label>
                                                <select
                                                    class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                    value={form.teacherId}
                                                    onChange={(event) => setForm("teacherId", event.currentTarget.value)}
                                                >
                                                    <option value="">选择老师</option>
                                                    <For each={data().teachers}>
                                                        {(teacher) => <option value={teacher.id}>{teacher.displayName}</option>}
                                                    </For>
                                                </select>
                                            </div>
                                        </Show>
                                    </div>
                                    <div class="space-y-2">
                                        <Label>描述</Label>
                                        <Textarea value={form.description} onInput={(event) => setForm("description", event.currentTarget.value)} />
                                    </div>
                                    <Button class="w-full md:w-auto" type="submit">创建班级</Button>
                                </form>
                            </CardContent>
                        </Card>

                        <Show when={data().classrooms.length > 0} fallback={<EmptyState title="还没有班级" description="先创建班级，再把学生加进去。" />}>
                            <div class="grid gap-5 xl:grid-cols-2">
                                <For each={data().classrooms}>
                                    {(classroom) => (
                                        <Card>
                                            <CardHeader>
                                                <div class="flex items-start justify-between gap-4">
                                                    <div>
                                                        <CardTitle>{classroom.name}</CardTitle>
                                                        <CardDescription>{classroom.description || "暂无班级说明"}</CardDescription>
                                                    </div>
                                                    <Badge variant="outline">{classroom.studentCount} 人</Badge>
                                                </div>
                                            </CardHeader>
                                            <CardContent class="space-y-4">
                                                <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm text-muted-foreground">
                                                    负责老师：<span class="font-medium text-foreground">{classroom.teacherName}</span>
                                                </div>

                                                <div class="space-y-3">
                                                    <div class="flex items-center justify-between gap-3">
                                                        <p class="text-sm font-medium text-foreground">班级学生</p>
                                                        <Button
                                                            size="sm"
                                                            variant="destructive"
                                                            onClick={() =>
                                                                void mutateWithRefetch(
                                                                    () => api.deleteClassroom(classroom.id),
                                                                    `已删除班级 ${classroom.name}。`,
                                                                )
                                                            }
                                                        >
                                                            删除班级
                                                        </Button>
                                                    </div>

                                                    <Show
                                                        when={(data().membersByClassroom[classroom.id] || []).length > 0}
                                                        fallback={<p class="text-sm text-muted-foreground">该班级还没有学生。</p>}
                                                    >
                                                        <div class="flex flex-wrap gap-2">
                                                            <For each={data().membersByClassroom[classroom.id] || []}>
                                                                {(student) => (
                                                                    <div class="flex items-center gap-2 rounded-full border border-border/70 bg-background px-3 py-1.5 text-sm">
                                                                        <span>{student.displayName}</span>
                                                                        <button
                                                                            class="text-muted-foreground transition hover:text-destructive"
                                                                            onClick={() =>
                                                                                void mutateWithRefetch(
                                                                                    () => api.removeStudentFromClassroom(classroom.id, student.id),
                                                                                    `已将 ${student.displayName} 移出 ${classroom.name}。`,
                                                                                )
                                                                            }
                                                                        >
                                                                            ×
                                                                        </button>
                                                                    </div>
                                                                )}
                                                            </For>
                                                        </div>
                                                    </Show>
                                                </div>

                                                <div class="grid gap-3 md:grid-cols-[1fr_auto]">
                                                    <select
                                                        class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                        value={selectedStudent[classroom.id] || ""}
                                                        onChange={(event) => setSelectedStudent(classroom.id, event.currentTarget.value)}
                                                    >
                                                        <option value="">选择学生加入班级</option>
                                                        <For each={data().students}>
                                                            {(student) => <option value={student.id}>{student.displayName}</option>}
                                                        </For>
                                                    </select>
                                                    <Button
                                                        onClick={() => {
                                                            const studentId = selectedStudent[classroom.id];
                                                            if (!studentId) {
                                                                return;
                                                            }
                                                            void mutateWithRefetch(
                                                                () => api.addStudentToClassroom(classroom.id, Number(studentId)),
                                                                `已将学生加入 ${classroom.name}。`,
                                                            );
                                                        }}
                                                    >
                                                        添加学生
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
