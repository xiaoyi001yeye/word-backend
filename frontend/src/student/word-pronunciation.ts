import type { PronunciationAccent } from './student-workspace-state';

export interface WordPronunciationDetail {
  word: string;
  ukPhonetic: string;
  usPhonetic: string;
  ukSpeechUrl: string;
  usSpeechUrl: string;
}

interface WordPronunciationServiceDependencies {
  fetchJson: (url: string) => Promise<unknown>;
  playAudio: (url: string) => Promise<void>;
}

export function parseWordPronunciationDetail(payload: unknown): WordPronunciationDetail | null {
  if (!isRecord(payload)) {
    return null;
  }

  const data = payload.data;
  if (!isRecord(data)) {
    return null;
  }

  return {
    word: stringValue(data.word),
    ukPhonetic: stringValue(data.ukphone),
    usPhonetic: stringValue(data.usphone),
    ukSpeechUrl: normalizeSpeechUrl(stringValue(data.ukspeech)),
    usSpeechUrl: normalizeSpeechUrl(stringValue(data.usspeech)),
  };
}

export function buildYoudaoAudioUrl(word: string, accent: PronunciationAccent): string {
  const type = accent === 'UK' ? '1' : '2';
  return `https://dict.youdao.com/dictvoice?audio=${encodeURIComponent(word.trim())}&type=${type}`;
}

export class WordPronunciationAudioService {
  private readonly dependencies: WordPronunciationServiceDependencies;
  private readonly detailCache = new Map<string, WordPronunciationDetail | null>();
  private readonly pendingDetails = new Map<string, Promise<WordPronunciationDetail | null>>();

  constructor(dependencies: WordPronunciationServiceDependencies) {
    this.dependencies = dependencies;
  }

  async fetchWordDetail(word: string): Promise<WordPronunciationDetail | null> {
    const normalizedWord = word.trim();
    if (!normalizedWord) {
      return null;
    }

    const cacheKey = normalizedWord.toLowerCase();
    if (this.detailCache.has(cacheKey)) {
      return this.detailCache.get(cacheKey) ?? null;
    }

    const pending = this.pendingDetails.get(cacheKey);
    if (pending) {
      return pending;
    }

    const request = this.fetchRemoteWordDetail(normalizedWord).finally(() => {
      this.pendingDetails.delete(cacheKey);
    });
    this.pendingDetails.set(cacheKey, request);

    const detail = await request;
    this.detailCache.set(cacheKey, detail);
    return detail;
  }

  async playWord(word: string, accent: PronunciationAccent): Promise<void> {
    const normalizedWord = word.trim();
    if (!normalizedWord) {
      throw new Error('当前单词为空，无法播放发音。');
    }

    const detail = await this.fetchWordDetail(normalizedWord);
    const resolvedWord = detail?.word || normalizedWord;
    const directUrl = speechUrlFor(detail, accent);
    const fallbackUrl = buildYoudaoAudioUrl(resolvedWord, accent);

    if (directUrl) {
      try {
        await this.dependencies.playAudio(directUrl);
        return;
      } catch {
        // Broken third-party clips should not block the stable word endpoint.
      }
    }

    await this.dependencies.playAudio(fallbackUrl);
  }

  private async fetchRemoteWordDetail(word: string): Promise<WordPronunciationDetail | null> {
    const url = `https://v2.xxapi.cn/api/englishwords?word=${encodeURIComponent(word)}`;
    try {
      return parseWordPronunciationDetail(await this.dependencies.fetchJson(url));
    } catch {
      return null;
    }
  }
}

function speechUrlFor(
  detail: WordPronunciationDetail | null | undefined,
  accent: PronunciationAccent,
): string {
  if (!detail) {
    return '';
  }

  const primary = accent === 'UK' ? detail.ukSpeechUrl : detail.usSpeechUrl;
  if (primary) {
    return primary;
  }

  return accent === 'UK' ? detail.usSpeechUrl : detail.ukSpeechUrl;
}

function normalizeSpeechUrl(value: string): string {
  const normalized = value.replaceAll('\\u0026', '&').trim();
  if (!normalized) {
    return '';
  }

  try {
    const url = new URL(normalized);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') {
      return '';
    }

    if (url.searchParams.has('audio') && !url.searchParams.get('audio')?.trim()) {
      return '';
    }

    return normalized;
  } catch {
    return '';
  }
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
