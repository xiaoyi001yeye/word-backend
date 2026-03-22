import { useState, useEffect, useCallback, useRef } from 'react';
import { dictionaryApi, metaWordApi } from './api';
import type { Dictionary, MetaWord } from './types';
import { DictionaryCard } from './components/DictionaryCard';
import { WordList } from './components/WordList';
import { WordDetail } from './components/WordDetail';
import { SearchBox } from './components/SearchBox';
import { CreateDictionaryModal } from './components/CreateDictionaryModal';
import { AddWordListModal } from './components/AddWordListModal';
import { CsvImportModal } from './components/CsvImportModal';
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
  const [dictionaryForAdd, setDictionaryForAdd] = useState<Dictionary | null>(null);
  const [dictionaryForCsvImport] = useState<Dictionary | null>(null);
  const [isImportingDictionaries, setIsImportingDictionaries] = useState(false);
  const [importFeedback, setImportFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
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

  const handleImportDictionaries = useCallback(async () => {
    if (isImportingDictionaries) {
      return;
    }
    setIsImportingDictionaries(true);
    setImportFeedback(null);
    try {
      const result = await dictionaryApi.importDictionaries();
      await loadDictionaries();
      setImportFeedback({
        type: 'success',
        message: `导入完成，本次新增 ${result.count} 本辞书。`,
      });
    } catch (error) {
      console.error('Failed to import dictionaries:', error);
      const errorMessage = error instanceof Error ? error.message : String(error);
      setImportFeedback({
        type: 'error',
        message: `导入失败：${errorMessage}`,
      });
    } finally {
      setIsImportingDictionaries(false);
    }
  }, [isImportingDictionaries, loadDictionaries]);

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

  const filteredDictionaries = dictionaries
    .filter(d => d.name.toLowerCase().includes(dictSearchQuery.toLowerCase()));

  const paginatedDictionaries = filteredDictionaries
    .slice((dictPage - 1) * DICT_PAGE_SIZE, dictPage * DICT_PAGE_SIZE);

  const totalDictPages = Math.ceil(filteredDictionaries.length / DICT_PAGE_SIZE);

  const totalWordPages = Math.ceil(totalWords / WORD_PAGE_SIZE);

  return (
    <div className="app">
      <header className="app__header">
        <div className="app__header-content">
          <div className="app__title-row">
            <span className="app__title-icon">📖</span>
            <div className="app__search">
              <SearchBox
                onLoading={setLoading}
                onClear={handleSearchClear}
                onSearchQueryChange={handleSearchQueryChange}
                value={searchKeyword}
              />
            </div>
          </div>
        </div>
      </header>

      <main className="app__main">
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
                <div className="sidebar__search">
                  <input
                    type="text"
                    className="sidebar__search-input"
                    placeholder="搜索词典..."
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
                <div className="sidebar__actions">
                  <button
                    className="import-dictionaries-btn"
                    onClick={handleImportDictionaries}
                    disabled={isImportingDictionaries}
                    title="导入 books 目录下所有辞书"
                  >
                    {isImportingDictionaries ? (
                      <>
                        <span className="btn-spinner"></span>
                        <span>导入中...</span>
                      </>
                    ) : (
                      <span>导入books辞书</span>
                    )}
                  </button>
                  <button
                    className="add-dictionary-btn"
                    onClick={() => setShowCreateModal(true)}
                    title="创建新辞书"
                  >
                    <span>+</span>
                    <span>添加辞书</span>
                  </button>
                </div>
              </div>
              {importFeedback && (
                <div className={`sidebar__import-feedback sidebar__import-feedback--${importFeedback.type}`}>
                  {importFeedback.message}
                </div>
              )}
              {loading && dictionaries.length === 0 ? (
                <div className="sidebar__loading">
                  <span className="sidebar__spinner"></span>
                </div>
              ) : filteredDictionaries.length === 0 ? (
                <div className="sidebar__empty">
                  <p>暂无词典</p>
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
                        onAdd={() => handleAddWords(dict)}
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
              <h2 className="panel__title">
                {isSearching ? '搜索结果' : selectedDictionary?.name || '单词列表'}
              </h2>
              {metaWords.length > 0 && (
                <span className="panel__count">{metaWords.length} 个单词</span>
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
              <h2 className="panel__title">单词详情</h2>
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
    </div>
  );
}

export default App;
