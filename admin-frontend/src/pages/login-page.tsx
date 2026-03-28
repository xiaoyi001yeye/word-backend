import { createEffect, createResource, createSignal, Show } from "solid-js";
import { useNavigate } from "@solidjs/router";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api } from "@/lib/api";
import { useAuth } from "@/features/auth/auth-context";

export function LoginPage() {
    const auth = useAuth();
    const navigate = useNavigate();
    const [username, setUsername] = createSignal("");
    const [password, setPassword] = createSignal("");
    const [submitting, setSubmitting] = createSignal(false);
    const [error, setError] = createSignal("");
    const [quote] = createResource(() => true, () => api.authQuote());

    createEffect(() => {
        if (auth.ready() && auth.user()) {
            void navigate("/", { replace: true });
        }
    });

    const handleSubmit = async (event: SubmitEvent) => {
        event.preventDefault();
        setSubmitting(true);
        setError("");

        try {
            await auth.login({
                username: username().trim(),
                password: password(),
            });
            void navigate("/", { replace: true });
        } catch (submitError) {
            setError(submitError instanceof Error ? submitError.message : "登录失败，请检查账号和密码");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div class="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(240,180,80,0.3),_transparent_24%),radial-gradient(circle_at_bottom_right,_rgba(47,125,116,0.26),_transparent_22%),linear-gradient(180deg,_#fdf8ed_0%,_#edf3f2_100%)]">
            <div class="mx-auto grid min-h-screen max-w-[1400px] gap-10 px-4 py-8 lg:grid-cols-[1.15fr_0.85fr] lg:px-6">
                <section class="flex flex-col justify-between rounded-[36px] border border-white/60 bg-[#0f2730] px-6 py-8 text-white shadow-haze md:px-10">
                    <div class="space-y-8">
                        <div class="space-y-4">
                            <p class="text-xs uppercase tracking-[0.22em] text-white/55">Word Atelier Admin</p>
                            <h1 class="max-w-xl font-display text-4xl font-semibold leading-tight tracking-tight md:text-6xl">
                                给管理员和老师一套真正能落地的后台编排台。
                            </h1>
                            <p class="max-w-lg text-base leading-7 text-white/68">
                                统一处理用户、班级、词书资源、学习计划和批量导入，让教学运营动作集中在一个独立前端里完成。
                            </p>
                        </div>

                        <div class="grid gap-4 md:grid-cols-3">
                            <div class="rounded-3xl border border-white/10 bg-white/5 p-5">
                                <p class="text-xs uppercase tracking-[0.18em] text-white/45">Users</p>
                                <p class="mt-4 font-display text-3xl font-semibold">Admin</p>
                                <p class="mt-2 text-sm text-white/60">角色和状态集中编排</p>
                            </div>
                            <div class="rounded-3xl border border-white/10 bg-white/5 p-5">
                                <p class="text-xs uppercase tracking-[0.18em] text-white/45">Classrooms</p>
                                <p class="mt-4 font-display text-3xl font-semibold">Teacher</p>
                                <p class="mt-2 text-sm text-white/60">班级与学生结构可视化</p>
                            </div>
                            <div class="rounded-3xl border border-white/10 bg-white/5 p-5">
                                <p class="text-xs uppercase tracking-[0.18em] text-white/45">Imports</p>
                                <p class="mt-4 font-display text-3xl font-semibold">Books</p>
                                <p class="mt-2 text-sm text-white/60">词书批量导入状态可追踪</p>
                            </div>
                        </div>
                    </div>

                    <Show when={quote()}>
                        <div class="mt-10 rounded-[28px] border border-white/10 bg-white/5 p-6">
                            <p class="text-sm leading-7 text-white/78">“{quote()?.content || "Knowledge begins in attention."}”</p>
                            <Show when={quote()?.translation}>
                                <p class="mt-3 text-sm text-white/55">{quote()?.translation}</p>
                            </Show>
                            <p class="mt-4 text-xs uppercase tracking-[0.2em] text-white/40">
                                {quote()?.author || "Daily Quote"}
                            </p>
                        </div>
                    </Show>
                </section>

                <section class="flex items-center justify-center">
                    <Card class="w-full max-w-xl border-white/60 bg-white/75">
                        <CardHeader>
                            <CardTitle>登录管理端</CardTitle>
                            <CardDescription>使用管理员或老师账号进入独立后台服务。</CardDescription>
                        </CardHeader>
                        <CardContent>
                            <form class="space-y-5" onSubmit={handleSubmit}>
                                <div class="space-y-2">
                                    <Label for="username">用户名</Label>
                                    <Input
                                        id="username"
                                        autocomplete="username"
                                        placeholder="例如：admin"
                                        value={username()}
                                        onInput={(event) => setUsername(event.currentTarget.value)}
                                    />
                                </div>
                                <div class="space-y-2">
                                    <Label for="password">密码</Label>
                                    <Input
                                        id="password"
                                        type="password"
                                        autocomplete="current-password"
                                        placeholder="请输入密码"
                                        value={password()}
                                        onInput={(event) => setPassword(event.currentTarget.value)}
                                    />
                                </div>
                                <Show when={error()}>
                                    <Alert class="border-destructive/30 bg-destructive/10 text-destructive">{error()}</Alert>
                                </Show>
                                <Button class="h-12 w-full text-base" disabled={submitting()} type="submit">
                                    {submitting() ? "正在登录..." : "进入后台"}
                                </Button>
                            </form>
                        </CardContent>
                    </Card>
                </section>
            </div>
        </div>
    );
}
