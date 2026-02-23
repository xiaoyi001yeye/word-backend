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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      if (!wordList.trim()) {
        throw new Error('请输入单词清单');
      }

      const words = wordList
        .split('\n')
        .map(word => word.trim())
        .filter(word => word.length > 0);

      if (words.length === 0) {
        throw new Error('请输入至少一个单词');
      }

      if (words.length > 1000) {
        throw new Error('每次最多添加1000个单词');
      }

      const data = await dictionaryWordApi.addWordList(dictionary.id, words);
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
              单词清单 *
            </label>
            <p className="form__hint">
              每行一个单词，程序会自动匹配元单词表，不存在的单词会自动创建
            </p>
            <textarea
              id="wordList"
              value={wordList}
              onChange={handleWordListChange}
              className="form__input"
              placeholder="例如：&#10;apple&#10;banana&#10;cherry"
              rows={8}
              disabled={loading}
              style={{ fontFamily: 'monospace', resize: 'vertical' }}
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