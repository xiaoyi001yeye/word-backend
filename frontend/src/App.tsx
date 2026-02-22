import { useState, useEffect, useCallback, useRef } from 'react';
import { dictionaryApi, dictionaryWordApi } from './api';
import type { Dictionary, MetaWord } from './types';
import { DictionaryCard } from './components/DictionaryCard';
import { WordList } from './components/WordList';
import { WordDetail } from './components/WordDetail';
import { SearchBox } from './components/SearchBox';
import './App.css';

function App() {
  const [dictionaries, setDictionaries] = useState<Dictionary[]>([]);
  const [selectedDictionary, setSelectedDictionary] = useState<Dictionary | null>(null);
  const [metaWords, setMetaWords] = useState<MetaWord[]>([]);
  const [selectedWord, setSelectedWord] = useState<MetaWord | null>(null);
  const [loading, setLoading] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [dictSearchQuery, setDictSearchQuery] = useState('');
  const [dictPage, setDictPage] = useState(1);
  const DICT_PAGE_SIZE = 5;
  const [wordPage, setWordPage] = useState(1);
  const WORD_PAGE_SIZE = 10;
  const [totalWords, setTotalWords] = useState(0);
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

  const loadDictionaryWords = useCallback(async (dictId: number, page: number = 1) => {
    if (isLoadingRef.current) return;
    if (lastLoadedRef.current?.dictId === dictId && lastLoadedRef.current?.page === page) return;
    
    isLoadingRef.current = true;
    setLoading(true);
    try {
      const words = await dictionaryWordApi.getWordsByDictionary(dictId, page, WORD_PAGE_SIZE);
      setMetaWords(words);
      lastLoadedRef.current = { dictId, page };
    } catch (error) {
      console.error('Failed to load dictionary words:', error);
    } finally {
      setLoading(false);
      isLoadingRef.current = false;
    }
  }, []);

  useEffect(() => {
    if (dictionaries.length === 0) {
      loadDictionaries();
    }
  }, []);

  useEffect(() => {
    if (!selectedDictionary || isSearching) return;
    
    console.log('[useEffect] wordPage:', wordPage, 'prev:', prevWordPageRef.current, 'lastLoaded:', lastLoadedRef.current);
    
    if (prevWordPageRef.current === wordPage && lastLoadedRef.current?.dictId === selectedDictionary.id) {
      console.log('[useEffect] Skip - already loaded');
      return;
    }
    prevWordPageRef.current = wordPage;
    
    loadDictionaryWords(selectedDictionary.id, wordPage);
  }, [selectedDictionary, wordPage, isSearching]);

  const handleSelectDictionary = useCallback((dict: Dictionary) => {
    setSelectedDictionary(dict);
    setSelectedWord(null);
    setWordPage(1);
    setTotalWords(dict.wordCount || 0);
    lastLoadedRef.current = null;
  }, []);

  const handleSearchResults = useCallback((words: MetaWord[]) => {
    setMetaWords(words);
    setIsSearching(true);
    setWordPage(1);
    prevWordPageRef.current = 0;
    lastLoadedRef.current = null;
  }, []);

  const handleSearchClear = useCallback(() => {
    setIsSearching(false);
    setWordPage(1);
    prevWordPageRef.current = 0;
    lastLoadedRef.current = null;
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
                onSearchResults={handleSearchResults}
                onLoading={setLoading}
                onClear={handleSearchClear}
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
    </div>
  );
}

export default App;
