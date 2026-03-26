import { useEffect, useState } from 'react';
import type { Dictionary } from '../types';

interface CreateExamModalProps {
  isOpen: boolean;
  dictionary: Dictionary | null;
  loading: boolean;
  error: string | null;
  onClose: () => void;
  onStart: (questionCount: number) => Promise<void>;
}

export function CreateExamModal({
  isOpen,
  dictionary,
  loading,
  error,
  onClose,
  onStart,
}: CreateExamModalProps) {
  const [questionCount, setQuestionCount] = useState(10);

  useEffect(() => {
    if (!dictionary) {
      return;
    }
    const suggestedCount = Math.max(1, Math.min(dictionary.wordCount || 10, 10));
    setQuestionCount(suggestedCount);
  }, [dictionary]);

  if (!isOpen || !dictionary) {
    return null;
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    await onStart(questionCount);
  };

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal__header">
          <h2 className="modal__title">生成考试</h2>
          <button className="modal__close" onClick={onClose} disabled={loading}>
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit} className="modal__form">
          <div className="exam-setup__summary">
            <p className="exam-setup__dictionary">{dictionary.name}</p>
            <p className="exam-setup__meta">
              当前辞书共 {dictionary.wordCount || 0} 个单词，系统会生成四选一翻译题。
            </p>
          </div>

          <div className="form__group">
            <label htmlFor="questionCount" className="form__label">
              题目数量
            </label>
            <input
              id="questionCount"
              type="number"
              className="form__input"
              min={1}
              max={Math.max(dictionary.wordCount || 1, 1)}
              value={questionCount}
              onChange={(event) => setQuestionCount(Math.max(1, Number(event.target.value) || 1))}
              disabled={loading}
            />
          </div>

          {error && <div className="form__error">{error}</div>}

          <div className="modal__footer">
            <button type="button" className="btn btn--secondary" onClick={onClose} disabled={loading}>
              取消
            </button>
            <button type="submit" className="btn btn--primary" disabled={loading}>
              {loading ? '正在出题...' : '开始考试'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
