import { useCallback, useEffect, useMemo, useState } from 'react';
import { teacherApi } from '../api';
import type { User } from '../types';

interface TeacherStudentManagementModalProps {
  isOpen: boolean;
  teacher: User | null;
  allStudents: User[];
  onClose: () => void;
}

export function TeacherStudentManagementModal({
  isOpen,
  teacher,
  allStudents,
  onClose,
}: TeacherStudentManagementModalProps) {
  const [assignedStudents, setAssignedStudents] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [savingStudentId, setSavingStudentId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const loadAssignedStudents = useCallback(async () => {
    if (!teacher) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const students = await teacherApi.getStudents(teacher.id);
      setAssignedStudents(students);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载教师学生关系失败');
    } finally {
      setLoading(false);
    }
  }, [teacher]);

  useEffect(() => {
    if (!isOpen || !teacher) {
      return;
    }

    setSuccessMessage(null);
    loadAssignedStudents();
  }, [isOpen, teacher, loadAssignedStudents]);

  const assignedStudentIds = useMemo(
    () => new Set(assignedStudents.map((student) => student.id)),
    [assignedStudents],
  );

  const unassignedStudents = useMemo(
    () => allStudents.filter((student) => !assignedStudentIds.has(student.id)),
    [allStudents, assignedStudentIds],
  );

  if (!isOpen || !teacher) {
    return null;
  }

  const handleAssignStudent = async (studentId: number) => {
    setSavingStudentId(studentId);
    setError(null);
    setSuccessMessage(null);

    try {
      await teacherApi.assignStudent(teacher.id, studentId);
      setSuccessMessage('学生已归属到当前教师。');
      await loadAssignedStudents();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '分配学生失败');
    } finally {
      setSavingStudentId(null);
    }
  };

  const handleRemoveStudent = async (studentId: number) => {
    setSavingStudentId(studentId);
    setError(null);
    setSuccessMessage(null);

    try {
      await teacherApi.removeStudent(teacher.id, studentId);
      setSuccessMessage('学生已从当前教师名下移除。');
      await loadAssignedStudents();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '移除学生失败');
    } finally {
      setSavingStudentId(null);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal modal--wide">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">教师学生管理</h2>
            <p className="user-management__subtitle">
              为 {teacher.displayName} 绑定学生。老师之后就能给这些学生分配辞书和发起考试。
            </p>
          </div>
          <button className="modal__close" onClick={onClose} disabled={loading}>
            &times;
          </button>
        </div>

        <div className="teacher-student__body">
          {error && <div className="form__error">{error}</div>}
          {successMessage && <div className="form__success">{successMessage}</div>}

          <section className="teacher-student__section">
            <div className="teacher-student__section-header">
              <div>
                <p className="panel__eyebrow">Assigned</p>
                <h3 className="user-management__section-title">已归属学生</h3>
              </div>
              <span className="panel__count">{assignedStudents.length} 人</span>
            </div>

            {loading && assignedStudents.length === 0 ? (
              <div className="sidebar__loading">
                <span className="sidebar__spinner"></span>
              </div>
            ) : assignedStudents.length === 0 ? (
              <div className="exam-history__empty">
                <p>这个老师名下还没有学生</p>
              </div>
            ) : (
              <div className="teacher-student__list">
                {assignedStudents.map((student) => (
                  <article key={student.id} className="teacher-student__item">
                    <div>
                      <h4 className="user-management__name">{student.displayName}</h4>
                      <p className="user-management__username">{student.username}</p>
                    </div>
                    <button
                      className="btn btn--secondary"
                      onClick={() => handleRemoveStudent(student.id)}
                      disabled={savingStudentId === student.id}
                    >
                      {savingStudentId === student.id ? '处理中...' : '移除'}
                    </button>
                  </article>
                ))}
              </div>
            )}
          </section>

          <section className="teacher-student__section">
            <div className="teacher-student__section-header">
              <div>
                <p className="panel__eyebrow">Available Students</p>
                <h3 className="user-management__section-title">可分配学生</h3>
              </div>
              <span className="panel__count">{unassignedStudents.length} 人</span>
            </div>

            {allStudents.length === 0 ? (
              <div className="exam-history__empty">
                <p>系统里还没有学生账号，请先创建学生。</p>
              </div>
            ) : unassignedStudents.length === 0 ? (
              <div className="exam-history__empty">
                <p>所有学生都已经分配给这个老师了</p>
              </div>
            ) : (
              <div className="teacher-student__list">
                {unassignedStudents.map((student) => (
                  <article key={student.id} className="teacher-student__item">
                    <div>
                      <h4 className="user-management__name">{student.displayName}</h4>
                      <p className="user-management__username">{student.username}</p>
                    </div>
                    <button
                      className="btn btn--primary"
                      onClick={() => handleAssignStudent(student.id)}
                      disabled={savingStudentId === student.id}
                    >
                      {savingStudentId === student.id ? '处理中...' : '归属到教师'}
                    </button>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
