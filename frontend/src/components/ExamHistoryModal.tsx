import type { Dictionary, ExamHistoryItem } from '../types';

interface ExamHistoryModalProps {
  isOpen: boolean;
  dictionary: Dictionary | null;
  historyItems: ExamHistoryItem[];
  loading: boolean;
  error: string | null;
  onClose: () => void;
  onViewResult: (examId: number) => Promise<void>;
}

export function ExamHistoryModal({
  isOpen,
  dictionary,
  historyItems,
  loading,
  error,
  onClose,
  onViewResult,
}: ExamHistoryModalProps) {
  if (!isOpen || !dictionary) {
    return null;
  }

  return (
    <div className="modal-overlay">
      <div className="modal exam-history">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">考试历史</h2>
            <p className="exam-session__subtitle">{dictionary.name}</p>
          </div>
          <button className="modal__close" onClick={onClose}>
            &times;
          </button>
        </div>

        <div className="exam-session__body">
          {loading ? (
            <div className="exam-history__empty">正在加载历史记录...</div>
          ) : error ? (
            <div className="form__error">{error}</div>
          ) : historyItems.length === 0 ? (
            <div className="exam-history__empty">还没有考试记录，先做一套题试试。</div>
          ) : (
            <div className="exam-history__list">
              {historyItems.map((item) => (
                <div key={item.examId} className="exam-history__item">
                  <div className="exam-history__item-main">
                    <div className="exam-history__score">{item.score} 分</div>
                    <div className="exam-history__meta">
                      <p>{item.correctCount} / {item.questionCount} 题答对</p>
                      <p>{item.submittedAt ? new Date(item.submittedAt).toLocaleString() : '未交卷'}</p>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="btn btn--secondary"
                    onClick={() => onViewResult(item.examId)}
                  >
                    查看详情
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
