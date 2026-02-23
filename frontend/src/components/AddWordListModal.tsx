import { useState } from 'react';
import { dictionaryWordApi } from '../api';
import type { Dictionary } from '../types';
import type { WordListProcessResult } from '../api';

interface AddWordListModalProps {
  isOpen: boolean;
  onClose: () => void;
  dictionary: Dictionary;
  onSuccess?: () => void;
}

export function AddWordListModal({ isOpen, onClose, dictionary, onSuccess }: AddWordListModalProps) {
  const [wordList, setWordList] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<WordListProcessResult | null>(null);

  const handleWordListChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setWordList(e.target.value);
    setResult(null);
  };

  const handleFormatJson = () => {
    try {
      if (!wordList.trim()) {
        setError('请输入JSON内容');
        return;
      }

      const parsed = JSON.parse(wordList);
      const formatted = JSON.stringify(parsed, null, 2);
      setWordList(formatted);
      setError(null);
    } catch (err) {
      if (err instanceof SyntaxError) {
        const message = err.message;
        let friendlyMessage = 'JSON格式错误：';
        
        if (message.includes('Unexpected token')) {
          friendlyMessage += '存在意外的符号，请检查引号、逗号或括号是否匹配。';
        } else if (message.includes('Unexpected end')) {
          friendlyMessage += 'JSON不完整，可能缺少括号、引号或逗号。';
        } else if (message.includes('Expected')) {
          friendlyMessage += '语法错误，请检查JSON结构。';
        } else {
          friendlyMessage += message;
        }
        
        setError(friendlyMessage);
      } else {
        setError('JSON格式错误，无法格式化。请检查语法。');
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      if (!wordList.trim()) {
        throw new Error('请输入JSON格式的单词清单');
      }

      let wordEntries;
      try {
        wordEntries = JSON.parse(wordList);
      } catch (parseError) {
        throw new Error('JSON格式错误，请检查语法');
      }

      if (!Array.isArray(wordEntries)) {
        throw new Error('输入必须是JSON数组格式');
      }

      if (wordEntries.length === 0) {
        throw new Error('请输入至少一个单词条目');
      }

      if (wordEntries.length > 1000) {
        throw new Error('每次最多添加1000个单词条目');
      }

      for (let i = 0; i < wordEntries.length; i++) {
        const entry = wordEntries[i];
        if (!entry || typeof entry !== 'object') {
          throw new Error(`第${i + 1}个条目必须是对象`);
        }
        if (!entry.word || typeof entry.word !== 'string' || entry.word.trim().length === 0) {
          throw new Error(`第${i + 1}个条目缺少有效的word字段`);
        }
        if (entry.difficulty !== undefined && (typeof entry.difficulty !== 'number' || entry.difficulty < 1 || entry.difficulty > 5)) {
          throw new Error(`第${i + 1}个条目的difficulty必须是1-5之间的数字`);
        }
      }

      const data = await dictionaryWordApi.addWordList(dictionary.id, wordEntries);
      setResult(data);

      if (onSuccess) {
        onSuccess();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '添加单词失败');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setWordList('');
    setError(null);
    setResult(null);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal__header">
          <h2 className="modal__title">添加单词到辞书</h2>
          <button className="modal__close" onClick={handleClose}>&times;</button>
        </div>
        
        <form onSubmit={handleSubmit} className="modal__form">
          <div className="form__group">
            <label htmlFor="wordList" className="form__label">
              JSON格式单词清单 *
            </label>
            <div className="form__hint">
              <p><strong>格式要求：</strong>必须是JSON数组，每个对象包含以下字段：</p>
              <ul>
                <li><code>word</code> (必填): 单词，1-100字符，只包含字母、空格、连字符和撇号</li>
                <li><code>phonetic</code> (可选): 音标，最多200字符</li>
                <li><code>definition</code> (可选): 定义，最多2000字符</li>
                <li><code>partOfSpeech</code> (可选): 词性，最多50字符</li>
                <li><code>exampleSentence</code> (可选): 例句，最多1000字符</li>
                <li><code>translation</code> (可选): 翻译，最多500字符</li>
                <li><code>difficulty</code> (可选): 难度，1-5整数，默认为2</li>
              </ul>
              <p><strong>提示词示例：</strong>请生成一个包含单词、音标、定义、词性、例句、翻译和难度的JSON数组，难度范围1-5，1为最简单，5为最难。</p>
            </div>
            <div className="form__actions">
              <button
                type="button"
                className="btn btn--secondary"
                onClick={handleFormatJson}
                disabled={loading || !wordList.trim()}
              >
                格式化JSON
              </button>
            </div>
            <textarea
              id="wordList"
              value={wordList}
              onChange={handleWordListChange}
              className="form__input"
              placeholder='[
  {
    "word": "apple",
    "phonetic": "/ˈæpəl/",
    "definition": "A fruit that grows on trees",
    "partOfSpeech": "noun",
    "exampleSentence": "I ate an apple for breakfast.",
    "translation": "苹果",
    "difficulty": 2
  },
  {
    "word": "banana",
    "phonetic": "/bəˈnɑːnə/",
    "definition": "A long curved fruit with yellow skin",
    "partOfSpeech": "noun",
    "exampleSentence": "She bought a bunch of bananas.",
    "translation": "香蕉",
    "difficulty": 1
  }
]'
              rows={12}
              disabled={loading}
              style={{ fontFamily: 'monospace', resize: 'vertical', fontSize: '0.9em' }}
            />
          </div>

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
                  <span className="form__result-label">总单词数：</span>
                  <span className="form__result-value">{result.total}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">已存在：</span>
                  <span className="form__result-value">{result.existed}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">新创建：</span>
                  <span className="form__result-value">{result.created}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">成功添加：</span>
                  <span className="form__result-value">{result.added}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">失败：</span>
                  <span className="form__result-value">{result.failed}</span>
                </div>
              </div>
            </div>
          )}

          <div className="modal__footer">
            <button
              type="button"
              className="btn btn--secondary"
              onClick={handleClose}
              disabled={loading}
            >
              关闭
            </button>
            <button
              type="submit"
              className="btn btn--primary"
              disabled={loading || !wordList.trim()}
            >
              {loading ? '处理中...' : '添加单词'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}