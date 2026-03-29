const TOKEN_KEY = "word-atelier-admin-token";

export const getStoredToken = () => window.localStorage.getItem(TOKEN_KEY);

export const setStoredToken = (token: string) => {
    window.localStorage.setItem(TOKEN_KEY, token);
};

export const clearStoredToken = () => {
    window.localStorage.removeItem(TOKEN_KEY);
};
