import { clearStoredToken, getStoredToken } from "@/lib/session";
import type {
    ApiErrorPayload,
    BooksImportBatchFileResponse,
    BooksImportConflictResponse,
    BooksImportJobResponse,
    ClassroomResponse,
    Dictionary,
    LoginResponse,
    QuoteResponse,
    StudyPlanOverviewResponse,
    StudyPlanResponse,
    StudyPlanStudentSummaryResponse,
    UserResponse,
} from "@/types/api";

export class ApiError extends Error {
    status: number;
    details: string[];

    constructor(message: string, status: number, details: string[] = []) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.details = details;
    }
}

type RequestOptions = Omit<RequestInit, "body"> & {
    body?: BodyInit | object | null;
};

const buildHeaders = (headers?: HeadersInit) => {
    const nextHeaders = new Headers(headers);
    const token = getStoredToken();
    if (token) {
        nextHeaders.set("Authorization", `Bearer ${token}`);
    }
    return nextHeaders;
};

const parsePayload = async (response: Response) => {
    const contentType = response.headers.get("content-type");
    if (response.status === 204) {
        return null;
    }
    if (contentType?.includes("application/json")) {
        return response.json();
    }
    const text = await response.text();
    return text ? { message: text } : null;
};

const toApiError = (status: number, payload: ApiErrorPayload | null) => {
    if (status === 401) {
        clearStoredToken();
    }
    return new ApiError(
        payload?.message ?? payload?.error ?? "请求失败",
        status,
        payload?.details ?? [],
    );
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const headers = buildHeaders(options.headers);
    let body = options.body ?? null;

    if (body && !(body instanceof FormData) && typeof body !== "string") {
        headers.set("Content-Type", "application/json");
        body = JSON.stringify(body);
    }

    const response = await fetch(path, {
        ...options,
        headers,
        body,
        credentials: "include",
    });

    const payload = await parsePayload(response);
    if (!response.ok) {
        throw toApiError(response.status, payload as ApiErrorPayload | null);
    }

    return payload as T;
}

export const api = {
    authQuote: () => request<QuoteResponse>("/api/auth/quote"),
    authMe: () => request<UserResponse>("/api/auth/me"),
    login: (payload: { username: string; password: string }) =>
        request<LoginResponse>("/api/auth/login", { method: "POST", body: payload }),
    logout: () => request<void>("/api/auth/logout", { method: "POST" }),

    listUsers: () => request<UserResponse[]>("/api/users"),
    listStudents: () => request<UserResponse[]>("/api/users/students"),
    createUser: (payload: {
        username: string;
        password: string;
        displayName: string;
        email?: string;
        phone?: string;
        role: string;
    }) => request<UserResponse>("/api/users", { method: "POST", body: payload }),
    updateUserRole: (id: number, role: string) =>
        request<UserResponse>(`/api/users/${id}/role`, { method: "PATCH", body: { role } }),
    updateUserStatus: (id: number, status: string) =>
        request<UserResponse>(`/api/users/${id}/status`, { method: "PATCH", body: { status } }),

    listTeacherStudents: (teacherId: number) => request<UserResponse[]>(`/api/teachers/${teacherId}/students`),
    listMyStudents: () => request<UserResponse[]>("/api/teachers/me/students"),
    assignStudentToTeacher: (teacherId: number, studentId: number) =>
        request<void>(`/api/teachers/${teacherId}/students/${studentId}`, { method: "POST" }),
    removeStudentFromTeacher: (teacherId: number, studentId: number) =>
        request<void>(`/api/teachers/${teacherId}/students/${studentId}`, { method: "DELETE" }),

    listClassrooms: () => request<ClassroomResponse[]>("/api/classrooms"),
    createClassroom: (payload: { name: string; description?: string; teacherId?: number | null }) =>
        request<ClassroomResponse>("/api/classrooms", { method: "POST", body: payload }),
    deleteClassroom: (classroomId: number) =>
        request<{ message: string; id: number }>(`/api/classrooms/${classroomId}`, { method: "DELETE" }),
    getClassroomStudents: (classroomId: number) => request<UserResponse[]>(`/api/classrooms/${classroomId}/students`),
    addStudentToClassroom: (classroomId: number, studentId: number) =>
        request<void>(`/api/classrooms/${classroomId}/students/${studentId}`, { method: "POST" }),
    removeStudentFromClassroom: (classroomId: number, studentId: number) =>
        request<void>(`/api/classrooms/${classroomId}/students/${studentId}`, { method: "DELETE" }),

    listDictionaries: () => request<Dictionary[]>("/api/dictionaries"),
    createDictionary: (payload: { name: string; category?: string; scopeType?: string | null }) =>
        request<Dictionary>("/api/dictionaries", { method: "POST", body: payload }),
    assignDictionaryToClassrooms: (dictionaryId: number, classroomIds: number[]) =>
        request<{ message: string; assignedCount: number }>(`/api/dictionaries/${dictionaryId}/assign/classrooms`, {
            method: "POST",
            body: { classroomIds },
        }),

    listStudyPlans: () => request<StudyPlanResponse[]>("/api/study-plans"),
    createStudyPlan: (payload: Record<string, unknown>) =>
        request<StudyPlanResponse>("/api/study-plans", { method: "POST", body: payload }),
    publishStudyPlan: (planId: number) =>
        request<StudyPlanResponse>(`/api/study-plans/${planId}/publish`, { method: "POST" }),
    getStudyPlanOverview: (planId: number) =>
        request<StudyPlanOverviewResponse>(`/api/study-plans/${planId}/overview`),
    getStudyPlanStudents: (planId: number) =>
        request<StudyPlanStudentSummaryResponse[]>(`/api/study-plans/${planId}/students`),

    createImportBatch: () => request<BooksImportJobResponse>("/api/books-import/batches", { method: "POST" }),
    getLatestImportBatch: () => request<BooksImportJobResponse>("/api/books-import/batches/latest"),
    getImportBatch: (batchId: string) => request<BooksImportJobResponse>(`/api/books-import/batches/${batchId}`),
    getImportBatchFiles: (batchId: string) =>
        request<BooksImportBatchFileResponse[]>(`/api/books-import/batches/${batchId}/files`),
    getImportBatchConflicts: (batchId: string) =>
        request<BooksImportConflictResponse[]>(`/api/books-import/batches/${batchId}/conflicts`),
    autoMergeImportBatch: (batchId: string) =>
        request<BooksImportJobResponse>(`/api/books-import/batches/${batchId}/auto-merge`, { method: "POST" }),
    publishImportBatch: (batchId: string) =>
        request<BooksImportJobResponse>(`/api/books-import/batches/${batchId}/publish`, { method: "POST" }),
    discardImportBatch: (batchId: string) =>
        request<BooksImportJobResponse>(`/api/books-import/batches/${batchId}/discard`, { method: "POST" }),
};
