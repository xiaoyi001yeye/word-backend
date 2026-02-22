import type { MetaWord } from '../types';
import './WordDetail.css';

interface WordDetailProps {
  word: MetaWord | null;
}

export function WordDetail({ word }: WordDetailProps) {
  if (!word) {
    return (
      <div className="word-detail">
        <div className="word-detail__empty">
          <span className="word-detail__empty-icon">📖</span>
          <span className="word-detail__empty-text">选择一个单词查看详情</span>
        </div>
      </div>
    );
  }

  return (
    <div className="word-detail">
      <div className="word-detail__header">
        <h2 className="word-detail__word">{word.word}</h2>
        {word.phonetic && <span className="word-detail__phonetic">{word.phonetic}</span>}
      </div>

      <div className="word-detail__content">
        {word.partOfSpeech && (
          <div className="word-detail__section">
            <span className="word-detail__label">词性</span>
            <span className="word-detail__part-of-speech">{word.partOfSpeech}</span>
          </div>
        )}

        {word.definition && (
          <div className="word-detail__section">
            <span className="word-detail__label">释义</span>
            <p className="word-detail__definition">{word.definition}</p>
          </div>
        )}

        {word.translation && (
          <div className="word-detail__section">
            <span className="word-detail__label">翻译</span>
            <p className="word-detail__translation">{word.translation}</p>
          </div>
        )}

        {word.exampleSentence && (
          <div className="word-detail__section">
            <span className="word-detail__label">例句</span>
            <p className="word-detail__example">{word.exampleSentence}</p>
          </div>
        )}

        {word.difficulty && (
          <div className="word-detail__section">
            <span className="word-detail__label">难度</span>
            <div className="word-detail__difficulty">
              {[1, 2, 3, 4, 5].map((level) => (
                <span
                  key={level}
                  className={`word-detail__difficulty-dot ${level <= word.difficulty! ? 'active' : ''}`}
                />
              ))}
              <span className="word-detail__difficulty-text">
                {word.difficulty === 1 && '简单'}
                {word.difficulty === 2 && '较简单'}
                {word.difficulty === 3 && '中等'}
                {word.difficulty === 4 && '较难'}
                {word.difficulty === 5 && '困难'}
              </span>
            </div>
          </div>
        )}
      </div>

      <div className="word-detail__footer">
        <span className="word-detail__id">ID: {word.id}</span>
      </div>
    </div>
  );
}
