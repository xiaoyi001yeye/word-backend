import type { MetaWord } from '../types';
import './WordList.css';

interface WordListProps {
  words: MetaWord[];
  selectedWord: MetaWord | null;
  onSelectWord: (word: MetaWord) => void;
  loading?: boolean;
}

export function WordList({ words, selectedWord, onSelectWord, loading }: WordListProps) {
  if (loading) {
    return (
      <div className="word-list">
        <div className="word-list__loading">
          <div className="word-list__spinner"></div>
          <span>加载中...</span>
        </div>
      </div>
    );
  }

  if (words.length === 0) {
    return (
      <div className="word-list">
        <div className="word-list__empty">
          <span className="word-list__empty-icon">📚</span>
          <span>暂无单词</span>
        </div>
      </div>
    );
  }

  return (
    <div className="word-list">
      <div className="word-list__items">
        {words.map((word, index) => {
          const previewText = word.translation || word.definition;

          return (
            <button
              key={word.id}
              type="button"
              className={`word-item ${selectedWord?.id === word.id ? 'selected' : ''}`}
              onClick={() => onSelectWord(word)}
              style={{ animationDelay: `${index * 20}ms` }}
            >
              <div className="word-item__top">
                <div className="word-item__heading">
                  <span className="word-item__text">{word.word}</span>
                  {word.phonetic && <span className="word-item__phonetic">{word.phonetic}</span>}
                </div>
                {word.difficulty && (
                  <span className={`word-item__difficulty difficulty-${word.difficulty}`}>
                    Lv.{word.difficulty}
                  </span>
                )}
              </div>
              {(previewText || word.partOfSpeech) && (
                <div className="word-item__meta">
                  {word.partOfSpeech && (
                    <span className="word-item__part-of-speech">{word.partOfSpeech}</span>
                  )}
                  {previewText && <p className="word-item__preview">{previewText}</p>}
                </div>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
