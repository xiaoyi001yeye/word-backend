import { fireEvent, render, screen } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { LoginPage } from "@/pages/login-page";

vi.mock("@solidjs/router", () => ({
    useNavigate: () => vi.fn(),
}));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        ready: () => true,
        user: () => null,
        login: vi.fn(),
    }),
}));

vi.mock("@/lib/api", () => ({
    api: {
        authQuote: vi.fn(),
    },
}));

describe("LoginPage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(api.authQuote).mockResolvedValue({
            content: "Knowledge begins in attention.",
            translation: "学习从不会使头脑疲惫。",
            author: "Leonardo da Vinci",
        });
    });

    it("toggles password visibility without changing the password value", () => {
        render(() => <LoginPage />);

        const passwordInput = screen.getByLabelText("密码");

        fireEvent.input(passwordInput, { target: { value: "secret-password" } });
        expect(passwordInput).toHaveAttribute("type", "password");

        fireEvent.click(screen.getByRole("button", { name: "显示密码" }));

        expect(passwordInput).toHaveAttribute("type", "text");
        expect(passwordInput).toHaveValue("secret-password");

        fireEvent.click(screen.getByRole("button", { name: "隐藏密码" }));

        expect(passwordInput).toHaveAttribute("type", "password");
        expect(passwordInput).toHaveValue("secret-password");
    });
});
