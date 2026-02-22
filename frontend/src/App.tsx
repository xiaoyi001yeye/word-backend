import { useState, useEffect, useCallback } from 'react';
import { dictionaryApi, dictionaryWordApi, metaWordApi } from './api';
import type { Dictionary, DictionaryWord, MetaWord } from './types';
import { DictionaryCard } from './components/DictionaryCard';
import { WordList } from './components/WordList';
import { WordDetail } from './components/WordDetail';
import { SearchBox } from './components/SearchBox';
import './App.css';

function App() {
  const [dictionaries, setDictionaries] = useState<Dictionary[]>([]);
  const [selectedDictionary, setSelectedDictionary] = useState<Dictionary | null>(null);
  const [, setDictionaryWords] = useState<DictionaryWord[]>([]);
  const [metaWords, setMetaWords] = useState<MetaWord[]>([]);
  const [selectedWord, setSelectedWord] = useState<MetaWord | null>(null);
  const [allWords, setAllWords] = useState<MetaWord[]>([]);
  const [loading, setLoading] = useState(false);
  const [isSearching, setIsSearching] = useState(false);

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

  const loadDictionaryWords = useCallback(async (dictId: number) => {
    setLoading(true);
    try {
      const dw = await dictionaryWordApi.getByDictionary(dictId);
      setDictionaryWords(dw);
      
      const wordPromises = dw.map((dw) => 
        metaWordApi.getById(dw.metaWordId)
      );
      const words = await Promise.all(wordPromises);
      setMetaWords(words);
      setAllWords(words);
    } catch (error) {
      console.error('Failed to load dictionary words:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDictionaries();
  }, [loadDictionaries]);

  const handleSelectDictionary = (dict: Dictionary) => {
    setSelectedDictionary(dict);
    setSelectedWord(null);
    loadDictionaryWords(dict.id);
  };

  const handleSearchResults = (words: MetaWord[]) => {
    setMetaWords(words);
    setIsSearching(true);
  };

  const handleSearchClear = () => {
    setMetaWords(allWords);
    setIsSearching(false);
  };

  const handleSelectWord = (word: MetaWord) => {
    setSelectedWord(word);
  };

  return (
    <div className="app">
      <header className="app__header">
        <div className="app__header-content">
          <h1 className="app__title">
            <span className="app__title-icon">📖</span>
            词典查询
          </h1>
          <p className="app__subtitle">探索词汇的海洋</p>
        </div>
      </header>

      <main className="app__main">
        <aside className="app__sidebar">
          <div className="sidebar__search">
            <SearchBox
              onSearchResults={handleSearchResults}
              onLoading={setLoading}
              onClear={handleSearchClear}
            />
          </div>
          
          <div className="sidebar__section">
            <h2 className="sidebar__title">词典列表</h2>
            {loading && dictionaries.length === 0 ? (
              <div className="sidebar__loading">
                <span className="sidebar__spinner"></span>
              </div>
            ) : dictionaries.length === 0 ? (
              <div className="sidebar__empty">
                <p>暂无词典</p>
              </div>
            ) : (
              <div className="sidebar__list">
                {dictionaries.map((dict, index) => (
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
          </div>
        </aside>

        <section className="app__content">
          <div className="content__panel content__panel--list">
            <div className="panel__header">
              <h2 className="panel__title">
                {isSearching ? '搜索结果' : selectedDictionary?.name || '单词列表'}
              </h2>
            </div>
            <WordList
              words={metaWords}
              selectedWord={selectedWord}
              onSelectWord={handleSelectWord}
              loading={loading && metaWords.length === 0}
            />
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
