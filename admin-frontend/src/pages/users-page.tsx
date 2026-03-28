import { createMemo, createResource, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRoot,
    TableRow,
} from "@/components/ui/table";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { enumLabel, formatDateTime } from "@/lib/format";
import type { UserResponse, UserRole, UserStatus } from "@/types/api";

interface AdminUserPageData {
    users: UserResponse[];
    teachers: UserResponse[];
    students: UserResponse[];
    teacherStudentMap: Record<number, UserResponse[]>;
}

interface TeacherUserPageData {
    students: UserResponse[];
}

const userRoles: UserRole[] = ["ADMIN", "TEACHER", "STUDENT"];
const userStatuses: UserStatus[] = ["ACTIVE", "DISABLED", "LOCKED"];

export function UsersPage() {
    const auth = useAuth();
    const isAdmin = createMemo(() => auth.user()?.role === "ADMIN");
    const [feedback, setFeedback] = createSignal("");
    const [createForm, setCreateForm] = createStore({
        username: "",
        password: "",
        displayName: "",
        email: "",
        phone: "",
        role: "TEACHER" as UserRole,
    });
    const [assignmentForm, setAssignmentForm] = createStore({
        teacherId: "",
        studentId: "",
    });

    const [pageData, { refetch }] = createResource(
        () => auth.user(),
        async (user): Promise<AdminUserPageData | TeacherUserPageData | null> => {
            if (!user) {
                return null;
            }

            if (user.role === "ADMIN") {
                const users = await api.listUsers();
                const teachers = users.filter((item) => item.role === "TEACHER");
                const students = users.filter((item) => item.role === "STUDENT");
                const relations = await Promise.all(
                    teachers.map(async (teacher) => [teacher.id, await api.listTeacherStudents(teacher.id)] as const),
                );

                return {
                    users,
                    teachers,
                    students,
                    teacherStudentMap: Object.fromEntries(relations),
                };
            }

            return {
                students: await api.listMyStudents(),
            };
        },
    );

    const mutateWithRefetch = async (runner: () => Promise<unknown>, successMessage: string) => {
        setFeedback("");
        await runner();
        setFeedback(successMessage);
        await refetch();
    };

    const handleCreateUser = async (event: SubmitEvent) => {
        event.preventDefault();
        await mutateWithRefetch(
            () =>
                api.createUser({
                    username: createForm.username.trim(),
                    password: createForm.password,
                    displayName: createForm.displayName.trim(),
                    email: createForm.email.trim() || undefined,
                    phone: createForm.phone.trim() || undefined,
                    role: createForm.role,
                }),
            "用户已创建。",
        );

        setCreateForm({
            username: "",
            password: "",
            displayName: "",
            email: "",
            phone: "",
            role: "TEACHER",
        });
    };

    const handleAssignStudent = async (event: SubmitEvent) => {
        event.preventDefault();
        if (!assignmentForm.teacherId || !assignmentForm.studentId) {
            return;
        }
        await mutateWithRefetch(
            () => api.assignStudentToTeacher(Number(assignmentForm.teacherId), Number(assignmentForm.studentId)),
            "学生已分配给老师。",
        );
    };

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Users"
                title={isAdmin() ? "用户与师生关系" : "我的学生"}
                description={
                    isAdmin()
                        ? "管理员可以创建账号、调整角色状态，并直接维护老师与学生的对应关系。"
                        : "老师在这里快速查看自己负责的学生列表。"
                }
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
                        <CardContent class="p-6 text-sm text-muted-foreground">正在加载用户数据...</CardContent>
                    </Card>
                }
            >
                {(data) => (
                    <div class="space-y-6">
                        <Show when={isAdmin()}>
                            <div class="grid gap-6 xl:grid-cols-[0.8fr_1.2fr]">
                                <Card>
                                    <CardHeader>
                                        <CardTitle>创建账号</CardTitle>
                                        <CardDescription>给管理员、老师或学生开通新的后台入口。</CardDescription>
                                    </CardHeader>
                                    <CardContent>
                                        <form class="grid gap-4" onSubmit={handleCreateUser}>
                                            <div class="grid gap-4 md:grid-cols-2">
                                                <div class="space-y-2">
                                                    <Label>用户名</Label>
                                                    <Input value={createForm.username} onInput={(event) => setCreateForm("username", event.currentTarget.value)} />
                                                </div>
                                                <div class="space-y-2">
                                                    <Label>姓名</Label>
                                                    <Input value={createForm.displayName} onInput={(event) => setCreateForm("displayName", event.currentTarget.value)} />
                                                </div>
                                            </div>
                                            <div class="grid gap-4 md:grid-cols-2">
                                                <div class="space-y-2">
                                                    <Label>密码</Label>
                                                    <Input type="password" value={createForm.password} onInput={(event) => setCreateForm("password", event.currentTarget.value)} />
                                                </div>
                                                <div class="space-y-2">
                                                    <Label>角色</Label>
                                                    <select
                                                        class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                        value={createForm.role}
                                                        onChange={(event) => setCreateForm("role", event.currentTarget.value as UserRole)}
                                                    >
                                                        <For each={userRoles}>{(role) => <option value={role}>{role}</option>}</For>
                                                    </select>
                                                </div>
                                            </div>
                                            <div class="grid gap-4 md:grid-cols-2">
                                                <div class="space-y-2">
                                                    <Label>邮箱</Label>
                                                    <Input value={createForm.email} onInput={(event) => setCreateForm("email", event.currentTarget.value)} />
                                                </div>
                                                <div class="space-y-2">
                                                    <Label>手机号</Label>
                                                    <Input value={createForm.phone} onInput={(event) => setCreateForm("phone", event.currentTarget.value)} />
                                                </div>
                                            </div>
                                            <Button type="submit">创建账号</Button>
                                        </form>
                                    </CardContent>
                                </Card>

                                <Card>
                                    <CardHeader>
                                        <CardTitle>师生分配</CardTitle>
                                        <CardDescription>适合管理员快速建立老师负责学生的关系网。</CardDescription>
                                    </CardHeader>
                                    <CardContent class="space-y-5">
                                        <form class="grid gap-4 md:grid-cols-[1fr_1fr_auto]" onSubmit={handleAssignStudent}>
                                            <div class="space-y-2">
                                                <Label>老师</Label>
                                                <select
                                                    class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                    value={assignmentForm.teacherId}
                                                    onChange={(event) => setAssignmentForm("teacherId", event.currentTarget.value)}
                                                >
                                                    <option value="">选择老师</option>
                                                    <For each={(data() as AdminUserPageData).teachers}>
                                                        {(teacher) => <option value={teacher.id}>{teacher.displayName}</option>}
                                                    </For>
                                                </select>
                                            </div>
                                            <div class="space-y-2">
                                                <Label>学生</Label>
                                                <select
                                                    class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                    value={assignmentForm.studentId}
                                                    onChange={(event) => setAssignmentForm("studentId", event.currentTarget.value)}
                                                >
                                                    <option value="">选择学生</option>
                                                    <For each={(data() as AdminUserPageData).students}>
                                                        {(student) => <option value={student.id}>{student.displayName}</option>}
                                                    </For>
                                                </select>
                                            </div>
                                            <div class="flex items-end">
                                                <Button class="w-full md:w-auto" type="submit">分配</Button>
                                            </div>
                                        </form>

                                        <div class="grid gap-4 lg:grid-cols-2">
                                            <For each={(data() as AdminUserPageData).teachers}>
                                                {(teacher) => (
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                        <div class="mb-3 flex items-center justify-between gap-3">
                                                            <div>
                                                                <p class="font-medium text-foreground">{teacher.displayName}</p>
                                                                <p class="text-sm text-muted-foreground">{teacher.username}</p>
                                                            </div>
                                                            <Badge variant="outline">
                                                                {((data() as AdminUserPageData).teacherStudentMap[teacher.id] || []).length} 人
                                                            </Badge>
                                                        </div>
                                                        <div class="space-y-2">
                                                            <Show
                                                                when={((data() as AdminUserPageData).teacherStudentMap[teacher.id] || []).length > 0}
                                                                fallback={<p class="text-sm text-muted-foreground">暂无已分配学生。</p>}
                                                            >
                                                                <For each={(data() as AdminUserPageData).teacherStudentMap[teacher.id] || []}>
                                                                    {(student) => (
                                                                        <div class="flex items-center justify-between gap-3 rounded-xl border border-border/70 px-3 py-2">
                                                                            <div>
                                                                                <p class="text-sm font-medium text-foreground">{student.displayName}</p>
                                                                                <p class="text-xs text-muted-foreground">{student.username}</p>
                                                                            </div>
                                                                            <Button
                                                                                size="sm"
                                                                                variant="ghost"
                                                                                onClick={() =>
                                                                                    void mutateWithRefetch(
                                                                                        () => api.removeStudentFromTeacher(teacher.id, student.id),
                                                                                        "师生关系已移除。",
                                                                                    )
                                                                                }
                                                                            >
                                                                                移除
                                                                            </Button>
                                                                        </div>
                                                                    )}
                                                                </For>
                                                            </Show>
                                                        </div>
                                                    </div>
                                                )}
                                            </For>
                                        </div>
                                    </CardContent>
                                </Card>
                            </div>
                        </Show>

                        <Card>
                            <CardHeader>
                                <CardTitle>{isAdmin() ? "用户清单" : "我的学生清单"}</CardTitle>
                                <CardDescription>
                                    {isAdmin()
                                        ? "角色和状态支持直接切换并即时提交。"
                                        : "这里只显示当前老师账号下已绑定的学生。"}
                                </CardDescription>
                            </CardHeader>
                            <CardContent>
                                <Show
                                    when={isAdmin() ? (data() as AdminUserPageData).users.length > 0 : (data() as TeacherUserPageData).students.length > 0}
                                    fallback={<EmptyState title="暂无数据" description="先创建账号或建立师生关系，列表才会出现内容。" />}
                                >
                                    <Table>
                                        <TableRoot>
                                            <TableHead>
                                                <tr>
                                                    <TableHeaderCell>用户</TableHeaderCell>
                                                    <TableHeaderCell>角色</TableHeaderCell>
                                                    <TableHeaderCell>状态</TableHeaderCell>
                                                    <TableHeaderCell>最近登录</TableHeaderCell>
                                                    <Show when={isAdmin()}>
                                                        <TableHeaderCell>标记</TableHeaderCell>
                                                    </Show>
                                                </tr>
                                            </TableHead>
                                            <TableBody>
                                                <For each={isAdmin() ? (data() as AdminUserPageData).users : (data() as TeacherUserPageData).students}>
                                                    {(user) => (
                                                        <TableRow>
                                                            <TableCell>
                                                                <div>
                                                                    <p class="font-medium text-foreground">{user.displayName}</p>
                                                                    <p class="text-xs text-muted-foreground">{user.username}</p>
                                                                </div>
                                                            </TableCell>
                                                            <TableCell>
                                                                <Show
                                                                    when={isAdmin()}
                                                                    fallback={<Badge variant="outline">{enumLabel(user.role)}</Badge>}
                                                                >
                                                                    <select
                                                                        class="h-10 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                                        value={user.role}
                                                                        onChange={(event) =>
                                                                            void mutateWithRefetch(
                                                                                () => api.updateUserRole(user.id, event.currentTarget.value),
                                                                                `已更新 ${user.displayName} 的角色。`,
                                                                            )
                                                                        }
                                                                    >
                                                                        <For each={userRoles}>{(role) => <option value={role}>{role}</option>}</For>
                                                                    </select>
                                                                </Show>
                                                            </TableCell>
                                                            <TableCell>
                                                                <Show
                                                                    when={isAdmin()}
                                                                    fallback={<Badge variant="outline">{enumLabel(user.status)}</Badge>}
                                                                >
                                                                    <select
                                                                        class="h-10 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                                        value={user.status}
                                                                        onChange={(event) =>
                                                                            void mutateWithRefetch(
                                                                                () => api.updateUserStatus(user.id, event.currentTarget.value),
                                                                                `已更新 ${user.displayName} 的状态。`,
                                                                            )
                                                                        }
                                                                    >
                                                                        <For each={userStatuses}>{(status) => <option value={status}>{status}</option>}</For>
                                                                    </select>
                                                                </Show>
                                                            </TableCell>
                                                            <TableCell>{formatDateTime(user.lastLoginAt)}</TableCell>
                                                            <Show when={isAdmin()}>
                                                                <TableCell>
                                                                    <Badge variant={user.role === "STUDENT" ? "secondary" : "default"}>
                                                                        {enumLabel(user.role)}
                                                                    </Badge>
                                                                </TableCell>
                                                            </Show>
                                                        </TableRow>
                                                    )}
                                                </For>
                                            </TableBody>
                                        </TableRoot>
                                    </Table>
                                </Show>
                            </CardContent>
                        </Card>
                    </div>
                )}
            </Show>
        </section>
    );
}
