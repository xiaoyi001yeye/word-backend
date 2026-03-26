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
          <span className="word-detail__empty-icon">Word</span>
          <span className="word-detail__empty-text">选择左侧任意单词后，这里会展示释义、翻译、例句和难度。</span>
        </div>
      </div>
    );
  }

  const difficultyText = {
    1: '简单',
    2: '较简单',
    3: '中等',
    4: '较难',
    5: '困难',
  }[word.difficulty ?? 0];

  return (
    <div className="word-detail">
      <div className="word-detail__hero">
        <div className="word-detail__header">
          <p className="word-detail__eyebrow">Word Entry</p>
          <h2 className="word-detail__word">{word.word}</h2>
          {word.phonetic && <span className="word-detail__phonetic">{word.phonetic}</span>}
        </div>

        <div className="word-detail__hero-meta">
          {word.partOfSpeech && (
            <span className="word-detail__pill">{word.partOfSpeech}</span>
          )}
          {word.difficulty && (
            <span className="word-detail__pill word-detail__pill--accent">
              难度 {word.difficulty} · {difficultyText}
            </span>
          )}
        </div>
      </div>

      <div className="word-detail__content">
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
                {difficultyText}
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
