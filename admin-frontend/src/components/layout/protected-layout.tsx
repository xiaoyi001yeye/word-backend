import type { JSX } from "solid-js";
import { Show } from "solid-js";
import { Navigate } from "@solidjs/router";
import { AppShell } from "@/components/layout/app-shell";
import { useAuth } from "@/features/auth/auth-context";

export function ProtectedLayout(props: { children?: JSX.Element }) {
    const auth = useAuth();

    return (
        <Show
            when={auth.ready()}
            fallback={
                <div class="flex min-h-screen items-center justify-center bg-[linear-gradient(180deg,_#fffdf8_0%,_#e8f1f0_100%)]">
                    <div class="space-y-3 text-center">
                        <div class="mx-auto h-12 w-12 animate-pulse rounded-2xl bg-primary/20" />
                        <p class="text-sm uppercase tracking-[0.2em] text-muted-foreground">正在校验登录态</p>
                    </div>
                </div>
            }
        >
            <Show
                when={auth.user()}
                fallback={<Navigate href="/login" />}
            >
                <AppShell>{props.children}</AppShell>
            </Show>
        </Show>
    );
}
