import { type FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { dictionaryWordApi } from '../api';
import type { WordListProcessResult } from '../api';
import type { Dictionary, MetaWord, MetaWordEntry } from '../types';
import './AddWordListModal.css';

interface AddWordListModalProps {
  isOpen: boolean;
  onClose: () => void;
  dictionary: Dictionary;
  onSuccess?: () => void;
}

type ManualEntryMode = 'quick' | 'bulk' | 'json';

interface QuickEntryRow {
  id: string;
  word: string;
  translation: string;
  partOfSpeech: string;
  phonetic: string;
  definition: string;
  exampleSentence: string;
  difficulty: string;
  matchedMetaWordId?: number;
}

interface ParsedEntryResult {
  entries: MetaWordEntry[];
  errors: string[];
  duplicateCount: number;
  lineCount: number;
}

const INITIAL_QUICK_ROW_COUNT = 5;
const MAX_ENTRY_COUNT = 1000;
const QUICK_SUGGESTION_LIMIT = 8;
const QUICK_SUGGESTION_DEBOUNCE_MS = 180;
const BULK_EXAMPLE = `apple | 苹果 | noun | /ˈaepəl/ | a fruit that grows on trees | I ate an apple for breakfast. | 2
abandon | 放弃 | verb | /əˈbaendən/ | to leave something behind | They abandoned the plan. | 3
phrase | 短语 | noun |  | a group of words used together | Learn the phrase by heart. | 2`;
const JSON_EXAMPLE = `[
  {
    "word": "apple",
    "translation": "苹果",
    "partOfSpeech": "noun",
    "phonetic": "/ˈaepəl/",
    "definition": "a fruit that grows on trees",
    "exampleSentence": "I ate an apple for breakfast.",
    "difficulty": 2
  }
]`;

function createQuickEntryRow(): QuickEntryRow {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    word: '',
    translation: '',
    partOfSpeech: '',
    phonetic: '',
    definition: '',
    exampleSentence: '',
    difficulty: '',
    matchedMetaWordId: undefined,
  };
}

function createQuickRows(count: number): QuickEntryRow[] {
  return Array.from({ length: count }, () => createQuickEntryRow());
}

function trimToUndefined(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function parseDifficulty(rawValue: string | number | undefined, locationLabel: string): number | undefined {
  if (rawValue === undefined || rawValue === null || rawValue === '') {
    return undefined;
  }

  const value = typeof rawValue === 'number' ? rawValue : Number(rawValue);
  if (!Number.isInteger(value) || value < 1 || value > 5) {
    throw new Error(`${locationLabel} 的难度必须是 1-5 的整数`);
  }

  return value;
}

function buildEntry(
  fields: {
    word?: string;
    translation?: string;
    partOfSpeech?: string;
    phonetic?: string;
    definition?: string;
    exampleSentence?: string;
    difficulty?: string | number;
  },
  locationLabel: string,
): MetaWordEntry | null {
  const word = trimToUndefined(fields.word);
  const hasOtherContent = Boolean(
    trimToUndefined(fields.translation)
      || trimToUndefined(fields.partOfSpeech)
      || trimToUndefined(fields.phonetic)
      || trimToUndefined(fields.definition)
      || trimToUndefined(fields.exampleSentence)
      || (fields.difficulty !== undefined && fields.difficulty !== null && String(fields.difficulty).trim() !== ''),
  );

  if (!word) {
    if (hasOtherContent) {
      throw new Error(`${locationLabel} 缺少单词内容`);
    }
    return null;
  }

  const difficulty = parseDifficulty(fields.difficulty, locationLabel);
  return {
    word,
    translation: trimToUndefined(fields.translation),
    partOfSpeech: trimToUndefined(fields.partOfSpeech),
    phonetic: trimToUndefined(fields.phonetic),
    definition: trimToUndefined(fields.definition),
    exampleSentence: trimToUndefined(fields.exampleSentence),
    difficulty: difficulty ?? 2,
  };
}

function dedupeEntries(entries: MetaWordEntry[]) {
  const uniqueEntries = new Map<string, MetaWordEntry>();
  entries.forEach((entry) => {
    uniqueEntries.set(entry.word.trim().toLowerCase(), entry);
  });
  return Array.from(uniqueEntries.values());
}

function removeRecordKey<T>(record: Record<string, T>, key: string) {
  if (!(key in record)) {
    return record;
  }

  const nextRecord = { ...record };
  delete nextRecord[key];
  return nextRecord;
}

function buildSuggestionSummary(suggestion: MetaWord) {
  const summaryParts = [
    suggestion.translation?.trim(),
    suggestion.partOfSpeech?.trim(),
    suggestion.phonetic?.trim(),
    suggestion.definition?.trim(),
  ].filter((part): part is string => Boolean(part));

  if (summaryParts.length === 0) {
    return '词元表中已有该单词，可直接加入当前词书';
  }

  return summaryParts.slice(0, 3).join(' · ');
}

function parseBulkText(input: string): ParsedEntryResult {
  const parsedEntries: MetaWordEntry[] = [];
  const errors: string[] = [];
  let lineCount = 0;

  input.split(/\r?\n/).forEach((rawLine, index) => {
    const line = rawLine.trim();
    if (!line) {
      return;
    }

    lineCount += 1;

    const separator = line.includes('\t') ? '\t' : '|';
    const columns = line.split(separator).map((part) => part.trim());

    try {
      const entry = buildEntry({
        word: columns[0],
        translation: columns[1],
        partOfSpeech: columns[2],
        phonetic: columns[3],
        definition: columns[4],
        exampleSentence: columns[5],
        difficulty: columns[6],
      }, `第 ${index + 1} 行`);

      if (entry) {
        parsedEntries.push(entry);
      }
    } catch (error) {
      errors.push(error instanceof Error ? error.message : `第 ${index + 1} 行解析失败`);
    }
  });

  const entries = dedupeEntries(parsedEntries);
  return {
    entries,
    errors,
    duplicateCount: Math.max(parsedEntries.length - entries.length, 0),
    lineCount,
  };
}

function parseJsonEntries(input: string): MetaWordEntry[] {
  if (!input.trim()) {
    throw new Error('请输入 JSON 内容');
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(input);
  } catch {
    throw new Error('JSON 格式错误，请检查语法');
  }

  if (!Array.isArray(parsed)) {
    throw new Error('输入必须是 JSON 数组');
  }

  const entries = parsed.map((item, index) => {
    if (!item || typeof item !== 'object') {
      throw new Error(`第 ${index + 1} 个条目必须是对象`);
    }

    const candidate = item as Record<string, unknown>;
    return buildEntry({
      word: typeof candidate.word === 'string' ? candidate.word : undefined,
      translation: typeof candidate.translation === 'string' ? candidate.translation : undefined,
      partOfSpeech: typeof candidate.partOfSpeech === 'string' ? candidate.partOfSpeech : undefined,
      phonetic: typeof candidate.phonetic === 'string' ? candidate.phonetic : undefined,
      definition: typeof candidate.definition === 'string' ? candidate.definition : undefined,
      exampleSentence: typeof candidate.exampleSentence === 'string' ? candidate.exampleSentence : undefined,
      difficulty: typeof candidate.difficulty === 'number' || typeof candidate.difficulty === 'string'
        ? candidate.difficulty
        : undefined,
    }, `第 ${index + 1} 个 JSON 条目`);
  }).filter((entry): entry is MetaWordEntry => entry !== null);

  return dedupeEntries(entries);
}

export function AddWordListModal({ isOpen, onClose, dictionary, onSuccess }: AddWordListModalProps) {
  const [mode, setMode] = useState<ManualEntryMode>('quick');
  const [quickRows, setQuickRows] = useState<QuickEntryRow[]>(() => createQuickRows(INITIAL_QUICK_ROW_COUNT));
  const [quickSuggestions, setQuickSuggestions] = useState<Record<string, MetaWord[]>>({});
  const [quickSuggestionLoading, setQuickSuggestionLoading] = useState<Record<string, boolean>>({});
  const [activeSuggestionRowId, setActiveSuggestionRowId] = useState<string | null>(null);
  const [bulkInput, setBulkInput] = useState('');
  const [jsonInput, setJsonInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<WordListProcessResult | null>(null);
  const quickSuggestionTimersRef = useRef<Record<string, number>>({});
  const quickSuggestionAbortControllersRef = useRef<Record<string, AbortController | null>>({});
  const quickSuggestionRequestIdsRef = useRef<Record<string, number>>({});

  const bulkPreview = useMemo(() => parseBulkText(bulkInput), [bulkInput]);
  const quickFilledCount = useMemo(
    () => quickRows.filter((row) => row.word.trim().length > 0).length,
    [quickRows],
  );

  useEffect(() => () => {
    Object.values(quickSuggestionTimersRef.current).forEach((timerId) => window.clearTimeout(timerId));
    Object.values(quickSuggestionAbortControllersRef.current).forEach((controller) => controller?.abort());
  }, []);

  const clearQuickSuggestionTimer = (rowId: string) => {
    const timerId = quickSuggestionTimersRef.current[rowId];
    if (timerId) {
      window.clearTimeout(timerId);
      delete quickSuggestionTimersRef.current[rowId];
    }
  };

  const abortQuickSuggestionRequest = (rowId: string) => {
    quickSuggestionAbortControllersRef.current[rowId]?.abort();
    delete quickSuggestionAbortControllersRef.current[rowId];
  };

  const clearQuickSuggestionState = (rowId: string) => {
    clearQuickSuggestionTimer(rowId);
    abortQuickSuggestionRequest(rowId);
    delete quickSuggestionRequestIdsRef.current[rowId];
    setQuickSuggestions((previous) => removeRecordKey(previous, rowId));
    setQuickSuggestionLoading((previous) => removeRecordKey(previous, rowId));
    setActiveSuggestionRowId((currentRowId) => (currentRowId === rowId ? null : currentRowId));
  };

  const resetQuickSuggestionState = () => {
    Object.keys(quickSuggestionTimersRef.current).forEach((rowId) => clearQuickSuggestionTimer(rowId));
    Object.keys(quickSuggestionAbortControllersRef.current).forEach((rowId) => abortQuickSuggestionRequest(rowId));
    quickSuggestionRequestIdsRef.current = {};
    setQuickSuggestions({});
    setQuickSuggestionLoading({});
    setActiveSuggestionRowId(null);
  };

  const loadQuickSuggestions = async (rowId: string, keyword: string) => {
    const trimmedKeyword = keyword.trim();
    if (!trimmedKeyword) {
      clearQuickSuggestionState(rowId);
      return;
    }

    abortQuickSuggestionRequest(rowId);

    const requestId = (quickSuggestionRequestIdsRef.current[rowId] ?? 0) + 1;
    quickSuggestionRequestIdsRef.current[rowId] = requestId;

    const controller = new AbortController();
    quickSuggestionAbortControllersRef.current[rowId] = controller;

    setQuickSuggestionLoading((previous) => ({
      ...previous,
      [rowId]: true,
    }));

    try {
      const suggestions = await dictionaryWordApi.getMetaWordSuggestions(
        dictionary.id,
        trimmedKeyword,
        QUICK_SUGGESTION_LIMIT,
        controller.signal,
      );

      if (quickSuggestionRequestIdsRef.current[rowId] !== requestId) {
        return;
      }

      setQuickSuggestions((previous) => ({
        ...previous,
        [rowId]: suggestions,
      }));
    } catch (suggestionError) {
      if (!(suggestionError instanceof DOMException && suggestionError.name === 'AbortError')) {
        console.error('Failed to load meta word suggestions:', suggestionError);
      }

      if (quickSuggestionRequestIdsRef.current[rowId] !== requestId) {
        return;
      }

      setQuickSuggestions((previous) => removeRecordKey(previous, rowId));
    } finally {
      if (quickSuggestionRequestIdsRef.current[rowId] === requestId) {
        setQuickSuggestionLoading((previous) => ({
          ...previous,
          [rowId]: false,
        }));
      }
    }
  };

  const resetCurrentMode = () => {
    if (mode === 'quick') {
      setQuickRows(createQuickRows(INITIAL_QUICK_ROW_COUNT));
      resetQuickSuggestionState();
      return;
    }

    if (mode === 'bulk') {
      setBulkInput('');
      return;
    }

    setJsonInput('');
  };

  const handleModeChange = (nextMode: ManualEntryMode) => {
    setMode(nextMode);
    setError(null);
    setResult(null);
    if (nextMode !== 'quick') {
      resetQuickSuggestionState();
    }
  };

  const handleQuickRowChange = (rowId: string, field: keyof Omit<QuickEntryRow, 'id'>, value: string) => {
    setQuickRows((previousRows) => previousRows.map((row) => (
      row.id === rowId ? { ...row, [field]: value } : row
    )));
    setResult(null);
    setError(null);
  };

  const handleQuickWordChange = (rowId: string, value: string) => {
    setQuickRows((previousRows) => previousRows.map((row) => (
      row.id === rowId
        ? {
          ...row,
          word: value,
          matchedMetaWordId: undefined,
        }
        : row
    )));
    setResult(null);
    setError(null);
    setActiveSuggestionRowId(rowId);

    clearQuickSuggestionTimer(rowId);
    abortQuickSuggestionRequest(rowId);
    delete quickSuggestionRequestIdsRef.current[rowId];
    setQuickSuggestions((previous) => removeRecordKey(previous, rowId));

    const trimmedValue = value.trim();
    if (!trimmedValue) {
      clearQuickSuggestionState(rowId);
      return;
    }

    setQuickSuggestionLoading((previous) => ({
      ...previous,
      [rowId]: true,
    }));

    quickSuggestionTimersRef.current[rowId] = window.setTimeout(() => {
      void loadQuickSuggestions(rowId, trimmedValue);
    }, QUICK_SUGGESTION_DEBOUNCE_MS);
  };

  const handleApplySuggestion = (rowId: string, suggestion: MetaWord) => {
    clearQuickSuggestionState(rowId);
    setQuickRows((previousRows) => previousRows.map((row) => (
      row.id === rowId
        ? {
          ...row,
          word: suggestion.word,
          translation: suggestion.translation ?? row.translation,
          partOfSpeech: suggestion.partOfSpeech ?? row.partOfSpeech,
          phonetic: suggestion.phonetic ?? row.phonetic,
          definition: suggestion.definition ?? row.definition,
          exampleSentence: suggestion.exampleSentence ?? row.exampleSentence,
          difficulty: suggestion.difficulty !== undefined ? String(suggestion.difficulty) : row.difficulty,
          matchedMetaWordId: suggestion.id,
        }
        : row
    )));
    setResult(null);
    setError(null);
  };

  const handleQuickWordBlur = (rowId: string) => {
    window.setTimeout(() => {
      setActiveSuggestionRowId((currentRowId) => (currentRowId === rowId ? null : currentRowId));
    }, 120);
  };

  const handleRemoveQuickRow = (rowId: string) => {
    clearQuickSuggestionState(rowId);
    setQuickRows((previousRows) => {
      if (previousRows.length === 1) {
        return previousRows;
      }
      return previousRows.filter((row) => row.id !== rowId);
    });
  };

  const handleAddQuickRow = () => {
    setQuickRows((previousRows) => [...previousRows, createQuickEntryRow()]);
  };

  const handleFillBulkExample = () => {
    setBulkInput(BULK_EXAMPLE);
    setError(null);
    setResult(null);
  };

  const handleFillJsonExample = () => {
    setJsonInput(JSON_EXAMPLE);
    setError(null);
    setResult(null);
  };

  const handleFormatJson = () => {
    try {
      if (!jsonInput.trim()) {
        setError('请输入 JSON 内容');
        return;
      }

      const parsed = JSON.parse(jsonInput);
      setJsonInput(JSON.stringify(parsed, null, 2));
      setError(null);
    } catch (formatError) {
      if (formatError instanceof SyntaxError) {
        setError('JSON 格式错误，请检查引号、逗号和括号是否完整。');
        return;
      }

      setError('JSON 格式化失败');
    }
  };

  const buildQuickEntries = () => {
    const entries = quickRows.map((row, index) => buildEntry({
      word: row.word,
      translation: row.translation,
      partOfSpeech: row.partOfSpeech,
      phonetic: row.phonetic,
      definition: row.definition,
      exampleSentence: row.exampleSentence,
      difficulty: row.difficulty,
    }, `第 ${index + 1} 行`)).filter((entry): entry is MetaWordEntry => entry !== null);

    return dedupeEntries(entries);
  };

  const resolveEntriesForSubmission = () => {
    if (mode === 'quick') {
      return buildQuickEntries();
    }

    if (mode === 'bulk') {
      if (bulkPreview.errors.length > 0) {
        throw new Error(bulkPreview.errors.slice(0, 3).join('；'));
      }
      return bulkPreview.entries;
    }

    return parseJsonEntries(jsonInput);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const entries = resolveEntriesForSubmission();

      if (entries.length === 0) {
        throw new Error('请至少录入一个单词');
      }

      if (entries.length > MAX_ENTRY_COUNT) {
        throw new Error(`每次最多添加 ${MAX_ENTRY_COUNT} 个词条`);
      }

      const response = await dictionaryWordApi.addWordList(dictionary.id, entries);
      setResult(response);
      resetCurrentMode();
      onSuccess?.();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '添加单词失败');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setMode('quick');
    setQuickRows(createQuickRows(INITIAL_QUICK_ROW_COUNT));
    resetQuickSuggestionState();
    setBulkInput('');
    setJsonInput('');
    setError(null);
    setResult(null);
    onClose();
  };

  const submitLabel = {
    quick: '保存当前录入',
    bulk: '解析并添加',
    json: '导入 JSON',
  }[mode];

  const canSubmit = mode === 'quick'
    ? quickFilledCount > 0
    : mode === 'bulk'
      ? bulkInput.trim().length > 0
      : jsonInput.trim().length > 0;

  if (!isOpen) {
    return null;
  }

  return (
    <div className="modal-overlay">
      <div className="modal modal--wide manual-entry-modal">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">手动录入单词</h2>
            <p className="manual-entry-modal__subtitle">
              当前辞书：<strong>{dictionary.name}</strong>
            </p>
          </div>
          <button className="modal__close" onClick={handleClose} type="button" aria-label="关闭">
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit} className="modal__form">
          <div className="manual-entry-modal__tabs" role="tablist" aria-label="录入方式">
            <button
              type="button"
              className={`manual-entry-modal__tab ${mode === 'quick' ? 'manual-entry-modal__tab--active' : ''}`}
              onClick={() => handleModeChange('quick')}
            >
              快速录入
            </button>
            <button
              type="button"
              className={`manual-entry-modal__tab ${mode === 'bulk' ? 'manual-entry-modal__tab--active' : ''}`}
              onClick={() => handleModeChange('bulk')}
            >
              批量粘贴
            </button>
            <button
              type="button"
              className={`manual-entry-modal__tab ${mode === 'json' ? 'manual-entry-modal__tab--active' : ''}`}
              onClick={() => handleModeChange('json')}
            >
              JSON 高级版
            </button>
          </div>

          <div className="manual-entry-modal__summary">
            {mode === 'quick' && (
              <>
                <span className="manual-entry-modal__summary-chip">逐行填写，适合边查边录</span>
                <span className="manual-entry-modal__summary-chip">{quickFilledCount} 行待提交</span>
              </>
            )}
            {mode === 'bulk' && (
              <>
                <span className="manual-entry-modal__summary-chip">支持 `|` 或制表符分列</span>
                <span className="manual-entry-modal__summary-chip">{bulkPreview.entries.length} 条已解析</span>
                {bulkPreview.duplicateCount > 0 && (
                  <span className="manual-entry-modal__summary-chip">{bulkPreview.duplicateCount} 条已自动去重</span>
                )}
              </>
            )}
            {mode === 'json' && (
              <>
                <span className="manual-entry-modal__summary-chip">适合从 AI 或脚本生成后直接粘贴</span>
                <span className="manual-entry-modal__summary-chip">沿用现有 JSON 数据结构</span>
              </>
            )}
          </div>

          {mode === 'quick' && (
            <>
              <div className="form__hint manual-entry-modal__hint-box">
                建议至少填写“单词 + 中文释义”。系统会自动按单词去重，已存在的词会补充信息并加入当前辞书。
              </div>

              <div className="manual-entry-modal__toolbar">
                <button type="button" className="btn btn--secondary" onClick={handleAddQuickRow} disabled={loading}>
                  新增一行
                </button>
                <button
                  type="button"
                  className="btn btn--secondary"
                  onClick={() => setQuickRows((previousRows) => [...previousRows, ...createQuickRows(5)])}
                  disabled={loading}
                >
                  再加 5 行
                </button>
                <button type="button" className="btn btn--secondary" onClick={resetCurrentMode} disabled={loading}>
                  清空
                </button>
              </div>

              <div className="manual-entry-modal__rows">
                {quickRows.map((row, index) => (
                  <div key={row.id} className="manual-entry-row">
                    <div className="manual-entry-row__index">{index + 1}</div>
                    <div className="manual-entry-row__body">
                      <div className="manual-entry-row__grid">
                        <div className="manual-entry-row__word-field">
                          <input
                            className="form__input"
                            placeholder="单词 *（输入时自动联想词元表）"
                            value={row.word}
                            onChange={(event) => handleQuickWordChange(row.id, event.target.value)}
                            onFocus={() => setActiveSuggestionRowId(row.id)}
                            onBlur={() => handleQuickWordBlur(row.id)}
                            disabled={loading}
                            aria-label={`第 ${index + 1} 行单词`}
                            autoComplete="off"
                          />
                          {row.matchedMetaWordId && (
                            <div className="manual-entry-row__match-hint">
                              已从词元表匹配到该单词，释义信息已自动回填。
                            </div>
                          )}
                          {activeSuggestionRowId === row.id
                            && row.word.trim().length > 0
                            && (quickSuggestionLoading[row.id] || quickSuggestions[row.id] !== undefined) && (
                            <div className="manual-entry-row__suggestions" role="listbox" aria-label={`第 ${index + 1} 行候选单词`}>
                              {quickSuggestionLoading[row.id] ? (
                                <div className="manual-entry-row__suggestion-empty">正在匹配词元表…</div>
                              ) : (quickSuggestions[row.id]?.length ?? 0) > 0 ? (
                                quickSuggestions[row.id].map((suggestion) => (
                                  <button
                                    key={suggestion.id}
                                    type="button"
                                    className="manual-entry-row__suggestion"
                                    onMouseDown={(event) => {
                                      event.preventDefault();
                                      handleApplySuggestion(row.id, suggestion);
                                    }}
                                  >
                                    <span className="manual-entry-row__suggestion-word">{suggestion.word}</span>
                                    <span className="manual-entry-row__suggestion-meta">
                                      {buildSuggestionSummary(suggestion)}
                                    </span>
                                  </button>
                                ))
                              ) : (
                                <div className="manual-entry-row__suggestion-empty">
                                  词元表里暂时没有这个前缀的候选词，你也可以继续手动录入。
                                </div>
                              )}
                            </div>
                          )}
                        </div>
                        <input
                          className="form__input"
                          placeholder="中文释义"
                          value={row.translation}
                          onChange={(event) => handleQuickRowChange(row.id, 'translation', event.target.value)}
                          disabled={loading}
                          aria-label={`第 ${index + 1} 行中文释义`}
                        />
                        <input
                          className="form__input"
                          placeholder="词性"
                          value={row.partOfSpeech}
                          onChange={(event) => handleQuickRowChange(row.id, 'partOfSpeech', event.target.value)}
                          disabled={loading}
                          aria-label={`第 ${index + 1} 行词性`}
                        />
                        <input
                          className="form__input"
                          placeholder="音标"
                          value={row.phonetic}
                          onChange={(event) => handleQuickRowChange(row.id, 'phonetic', event.target.value)}
                          disabled={loading}
                          aria-label={`第 ${index + 1} 行音标`}
                        />
                        <input
                          className="form__input"
                          placeholder="英文释义"
                          value={row.definition}
                          onChange={(event) => handleQuickRowChange(row.id, 'definition', event.target.value)}
                          disabled={loading}
                          aria-label={`第 ${index + 1} 行英文释义`}
                        />
                        <input
                          className="form__input"
                          placeholder="例句"
                          value={row.exampleSentence}
                          onChange={(event) => handleQuickRowChange(row.id, 'exampleSentence', event.target.value)}
                          disabled={loading}
                          aria-label={`第 ${index + 1} 行例句`}
                        />
                        <input
                          className="form__input"
                          placeholder="难度 1-5"
                          value={row.difficulty}
                          onChange={(event) => handleQuickRowChange(row.id, 'difficulty', event.target.value)}
                          disabled={loading}
                          aria-label={`第 ${index + 1} 行难度`}
                        />
                      </div>
                    </div>
                    <button
                      type="button"
                      className="manual-entry-row__remove"
                      onClick={() => handleRemoveQuickRow(row.id)}
                      disabled={loading || quickRows.length === 1}
                    >
                      移除
                    </button>
                  </div>
                ))}
              </div>
            </>
          )}

          {mode === 'bulk' && (
            <>
              <div className="form__hint manual-entry-modal__hint-box">
                每行一条记录，推荐顺序：`单词 | 中文释义 | 词性 | 音标 | 英文释义 | 例句 | 难度`。如果你是从表格里复制，直接粘贴制表符分列也可以。
              </div>

              <div className="manual-entry-modal__toolbar">
                <button type="button" className="btn btn--secondary" onClick={handleFillBulkExample} disabled={loading}>
                  填入示例
                </button>
                <button type="button" className="btn btn--secondary" onClick={resetCurrentMode} disabled={loading}>
                  清空
                </button>
              </div>

              <textarea
                value={bulkInput}
                onChange={(event) => {
                  setBulkInput(event.target.value);
                  setError(null);
                  setResult(null);
                }}
                className="form__input manual-entry-modal__textarea"
                placeholder={BULK_EXAMPLE}
                rows={11}
                disabled={loading}
              />

              {bulkPreview.errors.length > 0 && (
                <div className="form__error">
                  <strong>以下内容暂时无法解析：</strong>
                  <div>{bulkPreview.errors.slice(0, 5).join('；')}</div>
                </div>
              )}

              {bulkPreview.entries.length > 0 && (
                <div className="manual-entry-modal__preview">
                  <div className="manual-entry-modal__preview-header">
                    <h3 className="form__result-title">解析预览</h3>
                    <span className="manual-entry-modal__preview-meta">
                      共 {bulkPreview.lineCount} 行，成功解析 {bulkPreview.entries.length} 条
                    </span>
                  </div>
                  <div className="manual-entry-modal__preview-table">
                    {bulkPreview.entries.slice(0, 6).map((entry) => (
                      <div key={entry.word.toLowerCase()} className="manual-entry-modal__preview-row">
                        <strong>{entry.word}</strong>
                        <span>{entry.translation || '未填中文释义'}</span>
                        <span>{entry.partOfSpeech || '未填词性'}</span>
                        <span>难度 {entry.difficulty ?? 2}</span>
                      </div>
                    ))}
                  </div>
                  {bulkPreview.entries.length > 6 && (
                    <p className="manual-entry-modal__preview-more">
                      仅展示前 6 条，提交时会完整处理全部解析结果。
                    </p>
                  )}
                </div>
              )}
            </>
          )}

          {mode === 'json' && (
            <>
              <div className="form__hint manual-entry-modal__hint-box">
                适合已经有结构化数据时直接粘贴。字段沿用现有接口：`word`、`translation`、`partOfSpeech`、`phonetic`、`definition`、`exampleSentence`、`difficulty`。
              </div>

              <div className="manual-entry-modal__toolbar">
                <button type="button" className="btn btn--secondary" onClick={handleFillJsonExample} disabled={loading}>
                  填入示例
                </button>
                <button type="button" className="btn btn--secondary" onClick={handleFormatJson} disabled={loading}>
                  格式化 JSON
                </button>
                <button type="button" className="btn btn--secondary" onClick={resetCurrentMode} disabled={loading}>
                  清空
                </button>
              </div>

              <textarea
                value={jsonInput}
                onChange={(event) => {
                  setJsonInput(event.target.value);
                  setError(null);
                  setResult(null);
                }}
                className="form__input manual-entry-modal__textarea manual-entry-modal__textarea--code"
                placeholder={JSON_EXAMPLE}
                rows={12}
                disabled={loading}
              />
            </>
          )}

          {error && (
            <div className="form__error">
              {error}
            </div>
          )}

          {result && (
            <div className="form__result">
              <h3 className="form__result-title">处理结果</h3>
              <div className="form__result-stats">
                <div className="form__result-stat">
                  <span className="form__result-label">总词条</span>
                  <span className="form__result-value">{result.total}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">已存在元词</span>
                  <span className="form__result-value">{result.existed}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">新建元词</span>
                  <span className="form__result-value">{result.created}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">加入当前辞书</span>
                  <span className="form__result-value">{result.added}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">失败</span>
                  <span className="form__result-value">{result.failed}</span>
                </div>
              </div>
            </div>
          )}

          <div className="modal__footer">
            <button type="button" className="btn btn--secondary" onClick={handleClose} disabled={loading}>
              关闭
            </button>
            <button type="submit" className="btn btn--primary" disabled={loading || !canSubmit}>
              {loading ? '处理中...' : submitLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
