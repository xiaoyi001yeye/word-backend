import type { Dictionary, DictionaryWord, MetaWord } from '../types';

const API_BASE = '/api';

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
    },
    ...options,
  });
  if (!response.ok) {
    throw new Error(`API Error: ${response.status}`);
  }
  return response.json();
}

export const dictionaryApi = {
  getAll: () => fetchJson<Dictionary[]>(`${API_BASE}/dictionaries`),
  getById: (id: number) => fetchJson<Dictionary>(`${API_BASE}/dictionaries/${id}`),
  getByCategory: (category: string) => fetchJson<Dictionary[]>(`${API_BASE}/dictionaries/category/${category}`),
  importDictionaries: () => fetchJson<{ message: string; count: number }>(`${API_BASE}/dictionaries/import`, { method: 'POST' }),
  deleteAll: () => fetch(`${API_BASE}/dictionaries`, { method: 'DELETE' }),
};

export const metaWordApi = {
  getAll: () => fetchJson<MetaWord[]>(`${API_BASE}/meta-words`),
  getById: (id: number) => fetchJson<MetaWord>(`${API_BASE}/meta-words/${id}`),
  getByWord: (word: string) => fetchJson<MetaWord>(`${API_BASE}/meta-words/word/${word}`),
  search: (prefix: string) => fetchJson<MetaWord[]>(`${API_BASE}/meta-words/search?prefix=${encodeURIComponent(prefix)}`),
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
  getByWord: (metaWordId: number) => fetchJson<DictionaryWord[]>(`${API_BASE}/dictionary-words/word/${metaWordId}`),
  addWord: (dictionaryId: number, metaWordId: number) => fetchJson<DictionaryWord>(`${API_BASE}/dictionary-words/${dictionaryId}/${metaWordId}`, { method: 'POST' }),
  removeByDictionary: (dictionaryId: number) => fetch(`${API_BASE}/dictionary-words/dictionary/${dictionaryId}`, { method: 'DELETE' }),
};
