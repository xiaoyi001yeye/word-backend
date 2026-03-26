import { useState, useEffect, useCallback, useRef } from 'react';
import { dictionaryApi, examApi, metaWordApi } from './api';
import type { Dictionary, Exam, ExamHistoryItem, ExamSubmissionResult, MetaWord } from './types';
import { DictionaryCard } from './components/DictionaryCard';
import { WordList } from './components/WordList';
import { WordDetail } from './components/WordDetail';
import { SearchBox } from './components/SearchBox';
import { CreateDictionaryModal } from './components/CreateDictionaryModal';
import { AddWordListModal } from './components/AddWordListModal';
import { CsvImportModal } from './components/CsvImportModal';
import { CreateExamModal } from './components/CreateExamModal';
import { ExamHistoryModal } from './components/ExamHistoryModal';
import { ExamSessionModal } from './components/ExamSessionModal';
import './App.css';

function App() {
  const [dictionaries, setDictionaries] = useState<Dictionary[]>([]);
  const [selectedDictionary, setSelectedDictionary] = useState<Dictionary | null>(null);
  const [metaWords, setMetaWords] = useState<MetaWord[]>([]);
  const [selectedWord, setSelectedWord] = useState<MetaWord | null>(null);
  const [loading, setLoading] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [dictSearchQuery, setDictSearchQuery] = useState('');
  const [dictPage, setDictPage] = useState(1);
  const DICT_PAGE_SIZE = 5;
  const [wordPage, setWordPage] = useState(1);
  const WORD_PAGE_SIZE = 10;
  const [totalWords, setTotalWords] = useState(0);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showAddWordListModal, setShowAddWordListModal] = useState(false);
  const [showCsvImportModal, setShowCsvImportModal] = useState(false);
  const [showExamSetupModal, setShowExamSetupModal] = useState(false);
  const [showExamHistoryModal, setShowExamHistoryModal] = useState(false);
  const [dictionaryForAdd, setDictionaryForAdd] = useState<Dictionary | null>(null);
  const [dictionaryForCsvImport, setDictionaryForCsvImport] = useState<Dictionary | null>(null);
  const [activeExam, setActiveExam] = useState<Exam | null>(null);
  const [examResult, setExamResult] = useState<ExamSubmissionResult | null>(null);
  const [examAnswers, setExamAnswers] = useState<Record<number, string>>({});
  const [examLoading, setExamLoading] = useState(false);
  const [examError, setExamError] = useState<string | null>(null);
  const [examHistory, setExamHistory] = useState<ExamHistoryItem[]>([]);
  const isLoadingRef = useRef(false);
  const lastLoadedRef = useRef<{ dictId: number; page: number } | null>(null);
  const prevWordPageRef = useRef<number>(1);

  const loadDictionaries = useCallback(async () => {
    setLoading(true);
    try {
      const dicts = await dictionaryApi.getAll();
      setDictionaries(dicts);
    } catch (error) {
      console.error('Failed to load dictionaries:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleDictionaryCreated = useCallback((newDictionary: Dictionary) => {
    setDictionaries(prev => [newDictionary, ...prev]);
    setSelectedDictionary(newDictionary);
    setSelectedWord(null);
    setIsSearching(false);
    setSearchKeyword('');
    setWordPage(1);
  }, []);

  const handleDeleteDictionary = useCallback(async (id: number) => {
    if (!window.confirm('确定要删除这个辞书吗？此操作无法撤销。')) {
      return;
    }
    try {
      await dictionaryApi.deleteById(id);
      setDictionaries(prev => prev.filter(d => d.id !== id));
      if (selectedDictionary?.id === id) {
        setSelectedDictionary(null);
        setSelectedWord(null);
        setIsSearching(false);
        setSearchKeyword('');
        setWordPage(1);
      }
    } catch (error) {
      console.error('Failed to delete dictionary:', error);
      const errorMessage = error instanceof Error ? error.message : String(error);
      if (errorMessage.includes('400')) {
        alert('无法删除导入的辞书，只能删除用户创建的辞书。');
      } else {
        alert('删除辞书失败，请重试。');
      }
    }
  }, [selectedDictionary]);

  const handleAddWords = useCallback((dict: Dictionary) => {
    setDictionaryForAdd(dict);
    setShowAddWordListModal(true);
  }, []);

  const handleImportCsv = useCallback((dict: Dictionary) => {
    setDictionaryForCsvImport(dict);
    setShowCsvImportModal(true);
  }, []);

  const handleWordListAdded = useCallback(() => {
    if (dictionaryForAdd?.id === selectedDictionary?.id) {
      setWordPage(1);
      prevWordPageRef.current = 0;
      lastLoadedRef.current = null;
    }
  }, [dictionaryForAdd, selectedDictionary]);

  const handleCsvImported = useCallback(() => {
    if (dictionaryForCsvImport?.id === selectedDictionary?.id) {
      setWordPage(1);
      prevWordPageRef.current = 0;
      lastLoadedRef.current = null;
    }
  }, [dictionaryForCsvImport, selectedDictionary]);



  const performSearch = useCallback(async (keyword: string, page: number = 1, dictionaryId?: number) => {
    setLoading(true);
    try {
      const pageResult = await metaWordApi.search(keyword.trim(), dictionaryId, page - 1);
      setMetaWords(pageResult.content);
      setTotalWords(pageResult.totalElements);
      
      setWordPage(page);
      prevWordPageRef.current = 0;
      lastLoadedRef.current = dictionaryId ? { dictId: dictionaryId, page } : null;
    } catch (error) {
      console.error('Search failed:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (dictionaries.length === 0) {
      loadDictionaries();
    }
  }, [dictionaries.length, loadDictionaries]);

  useEffect(() => {
    if (isLoadingRef.current) return;
    
    console.log('[useEffect] wordPage:', wordPage, 'selectedDictionary:', selectedDictionary?.id, 'searchKeyword:', searchKeyword, 'isSearching:', isSearching);
    
    // Determine what to load based on current state
    if (isSearching && searchKeyword.trim()) {
      // Search mode: keyword search
      if (prevWordPageRef.current === wordPage && lastLoadedRef.current?.dictId === -1) {
        console.log('[useEffect] Skip search - already loaded');
        return;
      }
      prevWordPageRef.current = wordPage;
      lastLoadedRef.current = { dictId: -1, page: wordPage };
      
      performSearch(searchKeyword, wordPage);
    } else if (selectedDictionary && !isSearching) {
      // Dictionary browsing mode: load dictionary words
      if (prevWordPageRef.current === wordPage && lastLoadedRef.current?.dictId === selectedDictionary.id) {
        console.log('[useEffect] Skip dictionary - already loaded');
        return;
      }
      prevWordPageRef.current = wordPage;
      
      performSearch('', wordPage, selectedDictionary.id);
    }
  }, [wordPage, selectedDictionary, isSearching, searchKeyword, performSearch]);

  const handleSelectDictionary = useCallback((dict: Dictionary) => {
    setSelectedDictionary(dict);
    setSelectedWord(null);
    setIsSearching(false);
    setSearchKeyword('');
    setWordPage(1);
    // Don't set totalWords here - it will be set by performSearch
    lastLoadedRef.current = null;
    // The useEffect will trigger performSearch with dictionaryId
  }, []);



  const handleSearchClear = useCallback(() => {
    setIsSearching(false);
    setSearchKeyword('');
    setWordPage(1);
    prevWordPageRef.current = 0;
    lastLoadedRef.current = null;
  }, []);

  const handleSearchQueryChange = useCallback((query: string) => {
    setSearchKeyword(query);
    // Clear dictionary selection when user starts typing (if query is not empty)
    if (query.trim()) {
      setSelectedDictionary(null);
      setIsSearching(true); // Set searching mode when user types
    } else {
      setIsSearching(false); // Clear searching mode when query is empty
    }
  }, []);

  const handleSelectWord = useCallback((word: MetaWord) => {
    setSelectedWord(word);
  }, []);

  const handleOpenExamSetup = useCallback(() => {
    if (!selectedDictionary) {
      return;
    }
    setExamError(null);
    setShowExamSetupModal(true);
  }, [selectedDictionary]);

  const handleOpenExamHistory = useCallback(async () => {
    if (!selectedDictionary) {
      return;
    }

    setExamLoading(true);
    setExamError(null);
    setShowExamHistoryModal(true);
    try {
      const historyItems = await examApi.getHistory(selectedDictionary.id);
      setExamHistory(historyItems);
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '加载考试历史失败');
    } finally {
      setExamLoading(false);
    }
  }, [selectedDictionary]);

  const handleStartExam = useCallback(async (questionCount: number) => {
    if (!selectedDictionary) {
      return;
    }

    setExamLoading(true);
    setExamError(null);
    try {
      const exam = await examApi.create(selectedDictionary.id, questionCount);
      setActiveExam(exam);
      setExamAnswers({});
      setExamResult(null);
      setShowExamSetupModal(false);
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '生成考试失败');
    } finally {
      setExamLoading(false);
    }
  }, [selectedDictionary]);

  const handleSelectExamOption = useCallback((questionId: number, optionKey: string) => {
    setExamAnswers(prev => ({
      ...prev,
      [questionId]: optionKey,
    }));
  }, []);

  const handleSubmitExam = useCallback(async () => {
    if (!activeExam) {
      return;
    }

    setExamLoading(true);
    setExamError(null);
    try {
      const answers = Object.entries(examAnswers).map(([questionId, selectedOption]) => ({
        questionId: Number(questionId),
        selectedOption,
      }));
      const result = await examApi.submit(activeExam.examId, answers);
      setExamResult(result);
      if (selectedDictionary) {
        const historyItems = await examApi.getHistory(selectedDictionary.id);
        setExamHistory(historyItems);
      }
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '提交考试失败');
    } finally {
      setExamLoading(false);
    }
  }, [activeExam, examAnswers, selectedDictionary]);

  const handleViewHistoryResult = useCallback(async (examId: number) => {
    setExamLoading(true);
    setExamError(null);
    try {
      const [exam, result] = await Promise.all([
        examApi.getById(examId),
        examApi.getResult(examId),
      ]);
      setActiveExam(exam);
      setExamResult(result);
      setExamAnswers({});
      setShowExamHistoryModal(false);
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '加载考试详情失败');
    } finally {
      setExamLoading(false);
    }
  }, []);

  const handleCloseExam = useCallback(() => {
    setActiveExam(null);
    setExamAnswers({});
    setExamResult(null);
    setExamError(null);
  }, []);

  const filteredDictionaries = dictionaries
    .filter(d => d.name.toLowerCase().includes(dictSearchQuery.toLowerCase()));

  const paginatedDictionaries = filteredDictionaries
    .slice((dictPage - 1) * DICT_PAGE_SIZE, dictPage * DICT_PAGE_SIZE);

  const totalDictPages = Math.ceil(filteredDictionaries.length / DICT_PAGE_SIZE);

  const totalWordPages = Math.ceil(totalWords / WORD_PAGE_SIZE);
  const workspaceLabel = isSearching
    ? '全库搜索'
    : selectedDictionary?.name || '词汇总览';
  const workspaceMeta = isSearching
    ? searchKeyword
      ? `关键词「${searchKeyword}」`
      : '输入关键词开始搜索'
    : selectedDictionary?.category || '选择一本辞书开始浏览';
  const activeWordCount = isSearching
    ? totalWords
    : selectedDictionary?.wordCount || totalWords;

  return (
    <div className="app">
      <header className="app__header">
        <div className="app__header-content">
          <div className="app__hero">
            <div className="app__hero-copy">
              <p className="app__eyebrow">Vocabulary Studio</p>
              <h1 className="app__title">把词汇库做成一块真正能学习、检索和练习的工作台</h1>
              <p className="app__subtitle">
                在辞书、搜索、详情和考试之间自然切换，让每次查词都更专注，也更有沉浸感。
              </p>
              <div className="app__hero-stats">
                <div className="app__hero-stat">
                  <span className="app__hero-stat-label">辞书总数</span>
                  <strong className="app__hero-stat-value">{dictionaries.length}</strong>
                </div>
                <div className="app__hero-stat">
                  <span className="app__hero-stat-label">当前工作区</span>
                  <strong className="app__hero-stat-value">{workspaceLabel}</strong>
                </div>
                <div className="app__hero-stat">
                  <span className="app__hero-stat-label">词条规模</span>
                  <strong className="app__hero-stat-value">{activeWordCount}</strong>
                </div>
              </div>
            </div>

            <div className="app__hero-search">
              <div className="app__search-card">
                <div className="app__search-card-header">
                  <p className="app__search-label">快速搜索</p>
                  <span className="app__search-mode">{workspaceMeta}</span>
                </div>
                <div className="app__search">
                  <SearchBox
                    onLoading={setLoading}
                    onClear={handleSearchClear}
                    onSearchQueryChange={handleSearchQueryChange}
                    value={searchKeyword}
                  />
                </div>
                <p className="app__search-help">
                  支持按关键词横跨全部单词检索，也可以切回左侧辞书做定向学习。
                </p>
              </div>
            </div>
          </div>
        </div>
      </header>

      <main className={`app__main ${sidebarCollapsed ? 'app__main--sidebar-collapsed' : ''}`}>
        <aside className={`app__sidebar ${sidebarCollapsed ? 'app__sidebar--collapsed' : ''}`}>
          <button 
            className="sidebar__toggle"
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            title={sidebarCollapsed ? '展开词典列表' : '收起词典列表'}
          >
            {sidebarCollapsed ? '▶' : '◀'}
          </button>
          {!sidebarCollapsed && (
            <div className="sidebar__section">
              <div className="sidebar__header">
                <div className="sidebar__header-copy">
                  <p className="sidebar__eyebrow">Library</p>
                  <h2 className="sidebar__title">辞书书架</h2>
                  <p className="sidebar__summary">
                    {filteredDictionaries.length} / {dictionaries.length} 本可用辞书
                  </p>
                </div>

                <div className="sidebar__actions">
                  <button
                    className="add-dictionary-btn"
                    onClick={() => setShowCreateModal(true)}
                    title="创建新辞书"
                  >
                    <span>新建辞书</span>
                  </button>
                </div>
              </div>

              <div className="sidebar__toolbar">
                <div className="sidebar__search">
                  <input
                    type="text"
                    className="sidebar__search-input"
                    placeholder="筛选辞书名称"
                    value={dictSearchQuery}
                    onChange={(e) => setDictSearchQuery(e.target.value)}
                  />
                  {dictSearchQuery && (
                    <button
                      className="sidebar__search-clear"
                      onClick={() => setDictSearchQuery('')}
                    >
                      ×
                    </button>
                  )}
                </div>
                <p className="sidebar__hint">导入、创建或选择一本辞书，右侧会立即同步单词工作区。</p>
              </div>

              {loading && dictionaries.length === 0 ? (
                <div className="sidebar__loading">
                  <span className="sidebar__spinner"></span>
                </div>
              ) : filteredDictionaries.length === 0 ? (
                <div className="sidebar__empty">
                  <p>还没有匹配的辞书</p>
                </div>
              ) : (
                <div className="sidebar__list">
                  {paginatedDictionaries.map((dict, index) => (
                    <div
                      key={dict.id}
                      style={{ animationDelay: `${index * 50}ms` }}
                    >
                      <DictionaryCard
                        dictionary={dict}
                        isSelected={selectedDictionary?.id === dict.id}
                        onClick={() => handleSelectDictionary(dict)}
                        onDelete={() => handleDeleteDictionary(dict.id)}
                        onAddJson={() => handleAddWords(dict)}
                        onImportCsv={() => handleImportCsv(dict)}
                      />
                    </div>
                  ))}
                </div>
              )}
              {totalDictPages > 1 && (
                <div className="sidebar__pagination">
                  <button
                    className="sidebar__page-btn"
                    disabled={dictPage === 1 || loading}
                    onClick={() => setDictPage(p => p - 1)}
                  >
                    ‹
                  </button>
                  <span className="sidebar__page-info">{dictPage} / {totalDictPages}</span>
                  <button
                    className="sidebar__page-btn"
                    disabled={dictPage === totalDictPages || loading}
                    onClick={() => setDictPage(p => p + 1)}
                  >
                    ›
                  </button>
                </div>
              )}
            </div>
          )}
        </aside>

        <section className="app__content">
          <div className="content__panel content__panel--list">
            <div className="panel__header">
              <div className="panel__header-main">
                <div>
                  <p className="panel__eyebrow">{isSearching ? 'Search Results' : 'Word Shelf'}</p>
                  <h2 className="panel__title">
                    {isSearching ? '搜索结果' : selectedDictionary?.name || '单词列表'}
                  </h2>
                </div>
                {activeWordCount > 0 && (
                  <span className="panel__count">{activeWordCount} 个词条</span>
                )}
              </div>
              {selectedDictionary && !isSearching && (
                <div className="panel__header-actions">
                  <button className="exam-history-btn" onClick={handleOpenExamHistory}>
                    考试历史
                  </button>
                  <button className="exam-trigger-btn" onClick={handleOpenExamSetup}>
                    开始考试
                  </button>
                </div>
              )}
            </div>
            <div className="panel__list-wrapper">
              <WordList
                words={metaWords}
                selectedWord={selectedWord}
                onSelectWord={handleSelectWord}
                loading={loading && metaWords.length === 0}
              />
            </div>
            {totalWordPages > 1 && (
              <div className="content__pagination">
                <button
                  className="content__page-btn"
                  disabled={wordPage === 1 || loading}
                  onClick={() => setWordPage(p => p - 1)}
                >
                  ‹
                </button>
                <span className="content__page-info">{wordPage} / {totalWordPages}</span>
                <button
                  className="content__page-btn"
                  disabled={wordPage === totalWordPages || loading}
                  onClick={() => setWordPage(p => p + 1)}
                >
                  ›
                </button>
              </div>
            )}
          </div>

          <div className="content__panel content__panel--detail">
            <div className="panel__header">
              <div>
                <p className="panel__eyebrow">Word Detail</p>
                <h2 className="panel__title">单词详情</h2>
              </div>
              <span className="panel__count">
                {selectedWord ? '已选词条' : '等待选择'}
              </span>
            </div>
            <WordDetail word={selectedWord} />
          </div>
        </section>
      </main>

      <CreateDictionaryModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onDictionaryCreated={handleDictionaryCreated}
      />
      
      {dictionaryForAdd && (
        <AddWordListModal
          isOpen={showAddWordListModal}
          onClose={() => setShowAddWordListModal(false)}
          dictionary={dictionaryForAdd}
          onSuccess={handleWordListAdded}
        />
      )}
      
      {dictionaryForCsvImport && (
        <CsvImportModal
          isOpen={showCsvImportModal}
          onClose={() => setShowCsvImportModal(false)}
          dictionary={dictionaryForCsvImport}
          onSuccess={handleCsvImported}
        />
      )}

      <CreateExamModal
        isOpen={showExamSetupModal}
        dictionary={selectedDictionary}
        loading={examLoading}
        error={examError}
        onClose={() => {
          setShowExamSetupModal(false);
          setExamError(null);
        }}
        onStart={handleStartExam}
      />

      <ExamHistoryModal
        isOpen={showExamHistoryModal}
        dictionary={selectedDictionary}
        historyItems={examHistory}
        loading={examLoading}
        error={examError}
        onClose={() => {
          setShowExamHistoryModal(false);
          setExamError(null);
        }}
        onViewResult={handleViewHistoryResult}
      />

      <ExamSessionModal
        isOpen={activeExam !== null}
        dictionary={selectedDictionary}
        exam={activeExam}
        selectedAnswers={examAnswers}
        result={examResult}
        loading={examLoading}
        error={examError}
        onClose={handleCloseExam}
        onSelectOption={handleSelectExamOption}
        onSubmit={handleSubmitExam}
      />
    </div>
  );
}

export default App;
