import type { Dictionary, Exam, ExamSubmissionResult } from '../types';

interface ExamSessionModalProps {
  isOpen: boolean;
  dictionary: Dictionary | null;
  exam: Exam | null;
  selectedAnswers: Record<number, string>;
  result: ExamSubmissionResult | null;
  loading: boolean;
  error: string | null;
  onClose: () => void;
  onSelectOption: (questionId: number, optionKey: string) => void;
  onSubmit: () => Promise<void>;
}

export function ExamSessionModal({
  isOpen,
  dictionary,
  exam,
  selectedAnswers,
  result,
  loading,
  error,
  onClose,
  onSelectOption,
  onSubmit,
}: ExamSessionModalProps) {
  if (!isOpen || !exam) {
    return null;
  }

  const answeredCount = Object.keys(selectedAnswers).length;

  return (
    <div className="modal-overlay">
      <div className="modal exam-session">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">{result ? '考试结果' : '考试进行中'}</h2>
            <p className="exam-session__subtitle">{dictionary?.name || exam.dictionaryName}</p>
          </div>
          <button className="modal__close" onClick={onClose}>
            &times;
          </button>
        </div>

        <div className="exam-session__body">
          {result ? (
            <>
              <div className="exam-result__summary">
                <div className="exam-result__score">{result.score} 分</div>
                <div className="exam-result__meta">
                  答对 {result.correctCount} / {result.totalQuestions}
                </div>
              </div>

              <div className="exam-result__list">
                {result.results.map((item, index) => (
                  <div
                    key={item.questionId}
                    className={`exam-question exam-question--result ${
                      item.correct ? 'exam-question--correct' : 'exam-question--wrong'
                    }`}
                  >
                    <div className="exam-question__title">
                      <span className="exam-question__index">{index + 1}</span>
                      <span className="exam-question__word">{item.word}</span>
                    </div>
                    <p className="exam-question__result-line">
                      你的答案：{item.selectedOption ? `${item.selectedOption}. ${item.selectedTranslation || ''}` : '未作答'}
                    </p>
                    <p className="exam-question__result-line">
                      正确答案：{item.correctOption}. {item.correctTranslation}
                    </p>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <>
              <div className="exam-session__meta">
                <span>共 {exam.questionCount} 题</span>
                <span>已作答 {answeredCount} 题</span>
              </div>

              <div className="exam-session__questions">
                {exam.questions.map((question, index) => (
                  <div key={question.questionId} className="exam-question">
                    <div className="exam-question__title">
                      <span className="exam-question__index">{index + 1}</span>
                      <span className="exam-question__word">{question.word}</span>
                    </div>

                    <div className="exam-question__options">
                      {question.options.map((option) => {
                        const selected = selectedAnswers[question.questionId] === option.key;
                        return (
                          <button
                            key={option.key}
                            type="button"
                            className={`exam-option ${selected ? 'exam-option--selected' : ''}`}
                            onClick={() => onSelectOption(question.questionId, option.key)}
                          >
                            <span className="exam-option__key">{option.key}</span>
                            <span className="exam-option__text">{option.translation}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>

              {error && <div className="form__error">{error}</div>}
            </>
          )}
        </div>

        <div className="modal__footer">
          <button type="button" className="btn btn--secondary" onClick={onClose}>
            {result ? '关闭' : '退出考试'}
          </button>
          {!result && (
            <button type="button" className="btn btn--primary" onClick={onSubmit} disabled={loading}>
              {loading ? '正在评分...' : '交卷评分'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
