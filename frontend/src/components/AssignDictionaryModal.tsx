import { useEffect, useMemo, useState } from 'react';
import { dictionaryApi } from '../api';
import type { Classroom, Dictionary, User, UserRole } from '../types';

interface AssignDictionaryModalProps {
  isOpen: boolean;
  dictionary: Dictionary | null;
  availableStudents: User[];
  availableClassrooms: Classroom[];
  actorRole: UserRole;
  onClose: () => void;
}

export function AssignDictionaryModal({
  isOpen,
  dictionary,
  availableStudents,
  availableClassrooms,
  actorRole,
  onClose,
}: AssignDictionaryModalProps) {
  const [selectedStudentIds, setSelectedStudentIds] = useState<number[]>([]);
  const [selectedClassroomIds, setSelectedClassroomIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    setSelectedStudentIds([]);
    setSelectedClassroomIds([]);
    setError(null);
    setSuccessMessage(null);
  }, [isOpen, dictionary?.id]);

  const teacherHint = useMemo(() => {
    if (actorRole !== 'TEACHER') {
      return '你可以按班级批量分配，也可以直接勾选单个学生。';
    }

    if (availableStudents.length === 0 && availableClassrooms.length === 0) {
      return '你还没有可管理的学生或班级，请先在“班级管理”里创建班级并加入学生。';
    }

    return '优先按班级分配会更省事，也可以补充勾选个别学生。';
  }, [actorRole, availableClassrooms.length, availableStudents.length]);

  if (!isOpen || !dictionary) {
    return null;
  }

  const toggleStudent = (studentId: number) => {
    setSelectedStudentIds((previous) => (
      previous.includes(studentId)
        ? previous.filter((id) => id !== studentId)
        : [...previous, studentId]
    ));
  };

  const toggleClassroom = (classroomId: number) => {
    setSelectedClassroomIds((previous) => (
      previous.includes(classroomId)
        ? previous.filter((id) => id !== classroomId)
        : [...previous, classroomId]
    ));
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();

    if (selectedStudentIds.length === 0 && selectedClassroomIds.length === 0) {
      setError('请至少选择一个班级或学生');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const [studentResult, classroomResult] = await Promise.all([
        selectedStudentIds.length > 0
          ? dictionaryApi.assignStudents(dictionary.id, selectedStudentIds)
          : Promise.resolve({ message: '', dictionaryId: dictionary.id, assignedCount: 0 }),
        selectedClassroomIds.length > 0
          ? dictionaryApi.assignClassrooms(dictionary.id, selectedClassroomIds)
          : Promise.resolve({ message: '', dictionaryId: dictionary.id, assignedCount: 0 }),
      ]);

      setSuccessMessage(`分配完成，本次新增 ${studentResult.assignedCount + classroomResult.assignedCount} 条学生分配记录。`);
      setSelectedStudentIds([]);
      setSelectedClassroomIds([]);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '分配辞书失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">分配辞书</h2>
            <p className="user-management__subtitle">当前辞书：{dictionary.name}</p>
          </div>
          <button className="modal__close" onClick={onClose} disabled={loading}>
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit} className="modal__form">
          <div className="exam-setup__summary">
            <p className="exam-setup__dictionary">{dictionary.name}</p>
            <p className="exam-setup__meta">{teacherHint}</p>
          </div>

          {availableClassrooms.length > 0 && (
            <div className="assign-dictionary__group">
              <p className="form__label">按班级分配</p>
              <div className="assign-dictionary__list">
                {availableClassrooms.map((classroom) => {
                  const checked = selectedClassroomIds.includes(classroom.id);
                  return (
                    <label key={classroom.id} className={`assign-dictionary__item ${checked ? 'assign-dictionary__item--selected' : ''}`}>
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleClassroom(classroom.id)}
                        disabled={loading}
                      />
                      <div>
                        <strong>{classroom.name}</strong>
                        <p>{classroom.studentCount} 名学生</p>
                      </div>
                    </label>
                  );
                })}
              </div>
            </div>
          )}

          <div className="assign-dictionary__group">
            <p className="form__label">按学生分配</p>
            {availableStudents.length === 0 ? (
              <div className="exam-history__empty assign-dictionary__empty">
                <p>暂无可直接分配学生</p>
              </div>
            ) : (
              <div className="assign-dictionary__list">
                {availableStudents.map((student) => {
                  const checked = selectedStudentIds.includes(student.id);
                  return (
                    <label key={student.id} className={`assign-dictionary__item ${checked ? 'assign-dictionary__item--selected' : ''}`}>
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleStudent(student.id)}
                        disabled={loading}
                      />
                      <div>
                        <strong>{student.displayName}</strong>
                        <p>{student.username}</p>
                      </div>
                    </label>
                  );
                })}
              </div>
            )}
          </div>

          {error && <div className="form__error">{error}</div>}
          {successMessage && <div className="form__success">{successMessage}</div>}

          <div className="modal__footer">
            <button type="button" className="btn btn--secondary" onClick={onClose} disabled={loading}>
              关闭
            </button>
            <button
              type="submit"
              className="btn btn--primary"
              disabled={loading || (availableStudents.length === 0 && availableClassrooms.length === 0)}
            >
              {loading ? '分配中...' : '确认分配'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
