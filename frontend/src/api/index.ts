import type {
  Dictionary,
  DictionaryWord,
  Exam,
  ExamAnswer,
  ExamHistoryItem,
  ExamSubmissionResult,
  LoginResponse,
  MetaWord,
  MetaWordEntry,
  Page,
  User,
} from '../types';

export interface WordListProcessResult {
  message: string;
  dictionaryId: number;
  total: number;
  existed: number;
  created: number;
  added: number;
  failed: number;
}

const API_BASE = '/api';
const TOKEN_STORAGE_KEY = 'word_atelier_token';

let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

export function getStoredToken() {
  return window.localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function storeToken(token: string) {
  window.localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

export function clearStoredToken() {
  window.localStorage.removeItem(TOKEN_STORAGE_KEY);
}

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const isFormData = options?.body instanceof FormData;
  const headers = new Headers(options?.headers ?? {});
  const token = getStoredToken();

  if (!isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });
  if (!response.ok) {
    let message = `API Error: ${response.status}`;

    try {
      const errorBody = await response.json();
      message = errorBody.message || errorBody.error || message;
    } catch {
      // Ignore non-JSON error bodies.
    }

    if (response.status === 401) {
      clearStoredToken();
      unauthorizedHandler?.();
    }

    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export const authApi = {
  login: (username: string, password: string) => fetchJson<LoginResponse>(`${API_BASE}/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  }),
  me: () => fetchJson<User>(`${API_BASE}/auth/me`),
};

export const dictionaryApi = {
  getAll: () => fetchJson<Dictionary[]>(`${API_BASE}/dictionaries`),
  getById: (id: number) => fetchJson<Dictionary>(`${API_BASE}/dictionaries/${id}`),
  getByCategory: (category: string) => fetchJson<Dictionary[]>(`${API_BASE}/dictionaries/category/${category}`),
  create: (dictionary: Omit<Dictionary, 'id'>) => fetchJson<Dictionary>(`${API_BASE}/dictionaries`, {
    method: 'POST',
    body: JSON.stringify(dictionary),
  }),
  importDictionaries: () => fetchJson<{ message: string; count: number }>(`${API_BASE}/dictionaries/import`, { method: 'POST' }),
  deleteAll: () => fetch(`${API_BASE}/dictionaries`, { method: 'DELETE' }),
  deleteById: (id: number) => fetchJson<{ message: string; id: number }>(`${API_BASE}/dictionaries/${id}`, { method: 'DELETE' }),
  deleteUserCreated: () => fetchJson<{ message: string; deletedCount: number }>(`${API_BASE}/dictionaries/user-created`, { method: 'DELETE' }),
  assignStudents: (id: number, studentIds: number[]) => fetchJson<{ message: string; dictionaryId: number; assignedCount: number }>(
    `${API_BASE}/dictionaries/${id}/assign/students`,
    {
      method: 'POST',
      body: JSON.stringify({ studentIds }),
    },
  ),
};

export const metaWordApi = {
  getAll: () => fetchJson<MetaWord[]>(`${API_BASE}/meta-words`),
  getById: (id: number) => fetchJson<MetaWord>(`${API_BASE}/meta-words/${id}`),
  getByWord: (word: string) => fetchJson<MetaWord>(`${API_BASE}/meta-words/word/${word}`),
  search: (keyword: string, dictionaryId?: number, page?: number, size?: number) => {
    const requestBody = {
      keyword,
      dictionaryId,
      page,
      size
    };
    
    return fetchJson<Page<MetaWord>>(`${API_BASE}/meta-words/search`, {
      method: 'POST',
      body: JSON.stringify(requestBody),
    });
  },
  getByDifficulty: (difficulty: number) => fetchJson<MetaWord[]>(`${API_BASE}/meta-words/difficulty/${difficulty}`),
  create: (word: Omit<MetaWord, 'id'>) => fetchJson<MetaWord>(`${API_BASE}/meta-words`, {
    method: 'POST',
    body: JSON.stringify(word),
  }),
  deleteAll: () => fetch(`${API_BASE}/meta-words`, { method: 'DELETE' }),
  import: () => fetchJson<{ message: string; count: number }>(`${API_BASE}/meta-words/import`, { method: 'POST' }),
};

export const dictionaryWordApi = {
  getByDictionary: (dictionaryId: number) => fetchJson<DictionaryWord[]>(`${API_BASE}/dictionary-words/dictionary/${dictionaryId}`),
  getWordsByDictionary: (dictionaryId: number, page: number = 1, size: number = 10) => fetchJson<Page<MetaWord>>(`${API_BASE}/dictionary-words/dictionary/${dictionaryId}/words?page=${page}&size=${size}`),
  getByWord: (metaWordId: number) => fetchJson<DictionaryWord[]>(`${API_BASE}/dictionary-words/word/${metaWordId}`),
  addWord: (dictionaryId: number, metaWordId: number) => fetchJson<DictionaryWord>(`${API_BASE}/dictionary-words/${dictionaryId}/${metaWordId}`, { method: 'POST' }),
  removeByDictionary: (dictionaryId: number) => fetch(`${API_BASE}/dictionary-words/dictionary/${dictionaryId}`, { method: 'DELETE' }),
  addWordList: (dictionaryId: number, words: MetaWordEntry[]) => fetchJson<WordListProcessResult>(`${API_BASE}/dictionary-words/${dictionaryId}/words/list`, {
    method: 'POST',
    body: JSON.stringify({ words }),
  }),
  importCsv: (dictionaryId: number, formData: FormData) => fetchJson<WordListProcessResult>(`${API_BASE}/dictionary-words/${dictionaryId}/words/import-csv`, {
    method: 'POST',
    body: formData,
  }),
  importJson: (dictionaryId: number, jsonData: string) => fetchJson<WordListProcessResult>(`${API_BASE}/dictionary-words/${dictionaryId}/words/import-json`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: jsonData,
  }),
};

export const examApi = {
  create: (dictionaryId: number, questionCount: number, targetUserId: number) => fetchJson<Exam>(`${API_BASE}/exams`, {
    method: 'POST',
    body: JSON.stringify({ dictionaryId, questionCount, targetUserId }),
  }),
  getHistory: (dictionaryId?: number) => {
    const query = dictionaryId ? `?dictionaryId=${dictionaryId}` : '';
    return fetchJson<ExamHistoryItem[]>(`${API_BASE}/exams/history${query}`);
  },
  getById: (examId: number) => fetchJson<Exam>(`${API_BASE}/exams/${examId}`),
  getResult: (examId: number) => fetchJson<ExamSubmissionResult>(`${API_BASE}/exams/${examId}/result`),
  submit: (examId: number, answers: ExamAnswer[]) => fetchJson<ExamSubmissionResult>(`${API_BASE}/exams/${examId}/submit`, {
    method: 'POST',
    body: JSON.stringify({ answers }),
  }),
};

export const userApi = {
  getAll: () => fetchJson<User[]>(`${API_BASE}/users`),
};

export const teacherApi = {
  getMyStudents: () => fetchJson<User[]>(`${API_BASE}/teachers/me/students`),
};

export const studentApi = {
  getMyDictionaries: () => fetchJson<Dictionary[]>(`${API_BASE}/students/me/dictionaries`),
};
