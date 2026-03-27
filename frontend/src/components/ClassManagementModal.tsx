import { useCallback, useEffect, useMemo, useState } from 'react';
import { classroomApi, userApi } from '../api';
import type { Classroom, User } from '../types';

interface ClassManagementModalProps {
  isOpen: boolean;
  currentUser: User;
  onClose: () => void;
  onClassroomsChanged?: (classrooms: Classroom[]) => void;
  onMembershipChanged?: () => Promise<void> | void;
}

const EMPTY_FORM = {
  name: '',
  description: '',
  teacherId: '',
};

export function ClassManagementModal({
  isOpen,
  currentUser,
  onClose,
  onClassroomsChanged,
  onMembershipChanged,
}: ClassManagementModalProps) {
  const [classrooms, setClassrooms] = useState<Classroom[]>([]);
  const [teachers, setTeachers] = useState<User[]>([]);
  const [students, setStudents] = useState<User[]>([]);
  const [selectedClassroom, setSelectedClassroom] = useState<Classroom | null>(null);
  const [classroomStudents, setClassroomStudents] = useState<User[]>([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editForm, setEditForm] = useState(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const isAdmin = currentUser.role === 'ADMIN';

  const loadClassrooms = useCallback(async () => {
    const nextClassrooms = await classroomApi.getAll();
    const sortedClassrooms = [...nextClassrooms].sort((left, right) => right.id - left.id);
    setClassrooms(sortedClassrooms);
    onClassroomsChanged?.(sortedClassrooms);
    return sortedClassrooms;
  }, [onClassroomsChanged]);

  const loadTeachers = useCallback(async () => {
    if (!isAdmin) {
      setTeachers([]);
      return [];
    }

    const allUsers = await userApi.getAll();
    const nextTeachers = allUsers.filter((user) => user.role === 'TEACHER');
    setTeachers(nextTeachers);
    return nextTeachers;
  }, [isAdmin]);

  const loadStudents = useCallback(async () => {
    const nextStudents = await userApi.getStudents();
    setStudents(nextStudents);
    return nextStudents;
  }, []);

  const loadClassroomStudents = useCallback(async (classroomId: number) => {
    const nextStudents = await classroomApi.getStudents(classroomId);
    setClassroomStudents(nextStudents);
    return nextStudents;
  }, []);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    let mounted = true;

    const bootstrap = async () => {
      setLoading(true);
      setError(null);
      setSuccessMessage(null);

      try {
        const [nextClassrooms, nextTeachers] = await Promise.all([
          loadClassrooms(),
          loadTeachers(),
          loadStudents(),
        ]);

        if (!mounted) {
          return;
        }

        if (isAdmin && nextTeachers.length > 0) {
          setForm((previous) => ({
            ...previous,
            teacherId: previous.teacherId || String(nextTeachers[0].id),
          }));
        }

        if (!selectedClassroom && nextClassrooms.length > 0) {
          setSelectedClassroom(nextClassrooms[0]);
        }
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : '加载班级数据失败');
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    bootstrap();

    return () => {
      mounted = false;
    };
  }, [isAdmin, isOpen, loadClassrooms, loadStudents, loadTeachers, selectedClassroom]);

  useEffect(() => {
    if (!isOpen || !selectedClassroom) {
      setClassroomStudents([]);
      return;
    }

    let mounted = true;

    const bootstrap = async () => {
      try {
        const nextStudents = await loadClassroomStudents(selectedClassroom.id);
        if (mounted) {
          setClassroomStudents(nextStudents);
        }
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : '加载班级学生失败');
        }
      }
    };

    bootstrap();

    return () => {
      mounted = false;
    };
  }, [isOpen, loadClassroomStudents, selectedClassroom]);

  useEffect(() => {
    if (!selectedClassroom) {
      setEditForm(EMPTY_FORM);
      return;
    }

    setEditForm({
      name: selectedClassroom.name,
      description: selectedClassroom.description ?? '',
      teacherId: selectedClassroom.teacherId ? String(selectedClassroom.teacherId) : '',
    });
  }, [selectedClassroom]);

  const classroomStudentIds = useMemo(
    () => new Set(classroomStudents.map((student) => student.id)),
    [classroomStudents],
  );

  const candidateStudents = useMemo(
    () => students.filter((student) => !classroomStudentIds.has(student.id)),
    [students, classroomStudentIds],
  );

  if (!isOpen) {
    return null;
  }

  const handleCreateInputChange = (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) => {
    const { name, value } = event.target;
    setForm((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleEditInputChange = (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) => {
    const { name, value } = event.target;
    setEditForm((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleCreateClassroom = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const created = await classroomApi.create({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        teacherId: isAdmin && form.teacherId ? Number(form.teacherId) : undefined,
      });
      setForm((previous) => ({
        ...EMPTY_FORM,
        teacherId: previous.teacherId,
      }));
      setSelectedClassroom(created);
      setSuccessMessage('班级已创建。');
      await loadClassrooms();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '创建班级失败');
    } finally {
      setSubmitLoading(false);
    }
  };

  const handleDeleteClassroom = async (classroom: Classroom) => {
    if (!window.confirm(`确定删除班级「${classroom.name}」吗？`)) {
      return;
    }

    setSavingKey(`delete-${classroom.id}`);
    setError(null);
    setSuccessMessage(null);

    try {
      await classroomApi.deleteById(classroom.id);
      if (selectedClassroom?.id === classroom.id) {
        setSelectedClassroom(null);
        setClassroomStudents([]);
      }
      setSuccessMessage('班级已删除。');
      await loadClassrooms();
      await onMembershipChanged?.();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '删除班级失败');
    } finally {
      setSavingKey(null);
    }
  };

  const handleUpdateClassroom = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedClassroom) {
      return;
    }

    setSavingKey(`update-${selectedClassroom.id}`);
    setError(null);
    setSuccessMessage(null);

    try {
      const updated = await classroomApi.update(selectedClassroom.id, {
        name: editForm.name.trim(),
        description: editForm.description.trim() || undefined,
        teacherId: isAdmin && editForm.teacherId ? Number(editForm.teacherId) : undefined,
      });
      setSelectedClassroom(updated);
      setSuccessMessage('班级信息已更新。');
      const refreshedClassrooms = await loadClassrooms();
      const syncedClassroom = refreshedClassrooms.find((classroom) => classroom.id === updated.id) ?? updated;
      setSelectedClassroom(syncedClassroom);
      await onMembershipChanged?.();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '更新班级失败');
    } finally {
      setSavingKey(null);
    }
  };

  const handleAddStudent = async (studentId: number) => {
    if (!selectedClassroom) {
      return;
    }

    setSavingKey(`add-${studentId}`);
    setError(null);
    setSuccessMessage(null);

    try {
      await classroomApi.addStudent(selectedClassroom.id, studentId);
      setSuccessMessage('学生已加入班级。');
      await Promise.all([
        loadClassrooms(),
        loadClassroomStudents(selectedClassroom.id),
        Promise.resolve(onMembershipChanged?.()),
      ]);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '加入班级失败');
    } finally {
      setSavingKey(null);
    }
  };

  const handleRemoveStudent = async (studentId: number) => {
    if (!selectedClassroom) {
      return;
    }

    setSavingKey(`remove-${studentId}`);
    setError(null);
    setSuccessMessage(null);

    try {
      await classroomApi.removeStudent(selectedClassroom.id, studentId);
      setSuccessMessage('学生已移出班级。');
      await Promise.all([
        loadClassrooms(),
        loadClassroomStudents(selectedClassroom.id),
        Promise.resolve(onMembershipChanged?.()),
      ]);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '移出班级失败');
    } finally {
      setSavingKey(null);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal modal--wide">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">班级管理</h2>
            <p className="user-management__subtitle">
              创建班级、维护班级成员，然后就可以按班级分配辞书。
            </p>
          </div>
          <button className="modal__close" onClick={onClose} disabled={loading || submitLoading}>
            &times;
          </button>
        </div>

        <div className="class-management__body">
          <section className="class-management__section">
            <div className="user-management__section-header">
              <div>
                <p className="panel__eyebrow">Create Classroom</p>
                <h3 className="user-management__section-title">新建班级</h3>
              </div>
              <span className="panel__count">{classrooms.length} 个班级</span>
            </div>

            <form onSubmit={handleCreateClassroom} className="modal__form user-management__form">
              <div className="form__group">
                <label htmlFor="classroom-name" className="form__label">班级名称</label>
                <input
                  id="classroom-name"
                  name="name"
                  className="form__input"
                  value={form.name}
                  onChange={handleCreateInputChange}
                  placeholder="例如 高一(3)班"
                  disabled={submitLoading}
                />
              </div>

              {isAdmin && (
                <div className="form__group">
                  <label htmlFor="classroom-teacher" className="form__label">负责教师</label>
                  <select
                    id="classroom-teacher"
                    name="teacherId"
                    className="form__select"
                    value={form.teacherId}
                    onChange={handleCreateInputChange}
                    disabled={submitLoading}
                  >
                    {teachers.map((teacher) => (
                      <option key={teacher.id} value={teacher.id}>
                        {teacher.displayName} ({teacher.username})
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div className="form__group">
                <label htmlFor="classroom-description" className="form__label">班级说明</label>
                <textarea
                  id="classroom-description"
                  name="description"
                  className="form__input class-management__textarea"
                  value={form.description}
                  onChange={handleCreateInputChange}
                  placeholder="可选：记录学段、用途或教学备注"
                  disabled={submitLoading}
                />
              </div>

              {error && <div className="form__error">{error}</div>}
              {successMessage && <div className="form__success">{successMessage}</div>}

              <div className="modal__footer user-management__footer">
                <button type="submit" className="btn btn--primary" disabled={submitLoading}>
                  {submitLoading ? '创建中...' : '创建班级'}
                </button>
              </div>
            </form>

            <div className="class-management__editor">
              <div className="user-management__section-header">
                <div>
                  <p className="panel__eyebrow">Edit Classroom</p>
                  <h3 className="user-management__section-title">编辑班级信息</h3>
                </div>
                <span className="panel__count">
                  {selectedClassroom ? `ID ${selectedClassroom.id}` : '请选择班级'}
                </span>
              </div>

              {!selectedClassroom ? (
                <div className="exam-history__empty class-management__empty">
                  <p>从右侧班级列表中选择一个班级后，可在这里修改班级信息。</p>
                </div>
              ) : (
                <form onSubmit={handleUpdateClassroom} className="modal__form user-management__form">
                  <div className="form__group">
                    <label htmlFor="edit-classroom-name" className="form__label">班级名称</label>
                    <input
                      id="edit-classroom-name"
                      name="name"
                      className="form__input"
                      value={editForm.name}
                      onChange={handleEditInputChange}
                      placeholder="例如 高一(3)班"
                      disabled={savingKey === `update-${selectedClassroom.id}`}
                    />
                  </div>

                  {isAdmin && (
                    <div className="form__group">
                      <label htmlFor="edit-classroom-teacher" className="form__label">负责教师</label>
                      <select
                        id="edit-classroom-teacher"
                        name="teacherId"
                        className="form__select"
                        value={editForm.teacherId}
                        onChange={handleEditInputChange}
                        disabled={savingKey === `update-${selectedClassroom.id}`}
                      >
                        {teachers.map((teacher) => (
                          <option key={teacher.id} value={teacher.id}>
                            {teacher.displayName} ({teacher.username})
                          </option>
                        ))}
                      </select>
                    </div>
                  )}

                  <div className="form__group">
                    <label htmlFor="edit-classroom-description" className="form__label">班级说明</label>
                    <textarea
                      id="edit-classroom-description"
                      name="description"
                      className="form__input class-management__textarea"
                      value={editForm.description}
                      onChange={handleEditInputChange}
                      placeholder="可选：记录学段、用途或教学备注"
                      disabled={savingKey === `update-${selectedClassroom.id}`}
                    />
                  </div>

                  <div className="modal__footer user-management__footer">
                    <button
                      type="submit"
                      className="btn btn--primary"
                      disabled={savingKey === `update-${selectedClassroom.id}`}
                    >
                      {savingKey === `update-${selectedClassroom.id}` ? '保存中...' : '保存修改'}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </section>

          <section className="class-management__section">
            <div className="user-management__section-header">
              <div>
                <p className="panel__eyebrow">Classrooms</p>
                <h3 className="user-management__section-title">班级列表</h3>
              </div>
            </div>

            {loading && classrooms.length === 0 ? (
              <div className="sidebar__loading">
                <span className="sidebar__spinner"></span>
              </div>
            ) : classrooms.length === 0 ? (
              <div className="exam-history__empty">
                <p>还没有班级，请先创建一个。</p>
              </div>
            ) : (
              <div className="class-management__list">
                {classrooms.map((classroom) => (
                  <article key={classroom.id} className={`class-management__card ${selectedClassroom?.id === classroom.id ? 'class-management__card--selected' : ''}`}>
                    <button
                      type="button"
                      className="class-management__card-main"
                      onClick={() => setSelectedClassroom(classroom)}
                    >
                      <div>
                        <h4 className="user-management__name">{classroom.name}</h4>
                        <p className="user-management__username">
                          {classroom.teacherName || '未命名教师'} · {classroom.studentCount} 名学生
                        </p>
                      </div>
                      {classroom.description && (
                        <p className="class-management__description">{classroom.description}</p>
                      )}
                    </button>
                    <button
                      type="button"
                      className="btn btn--secondary"
                      onClick={() => handleDeleteClassroom(classroom)}
                      disabled={savingKey === `delete-${classroom.id}`}
                    >
                      {savingKey === `delete-${classroom.id}` ? '删除中...' : '删除'}
                    </button>
                  </article>
                ))}
              </div>
            )}
          </section>

          <section className="class-management__section class-management__section--members">
            <div className="user-management__section-header">
              <div>
                <p className="panel__eyebrow">Members</p>
                <h3 className="user-management__section-title">
                  {selectedClassroom ? `班级成员 · ${selectedClassroom.name}` : '班级成员'}
                </h3>
              </div>
            </div>

            {!selectedClassroom ? (
              <div className="exam-history__empty">
                <p>请选择一个班级开始管理成员。</p>
              </div>
            ) : (
              <div className="teacher-student__body">
                <section className="teacher-student__section">
                  <div className="teacher-student__section-header">
                    <div>
                      <p className="panel__eyebrow">In Class</p>
                      <h3 className="user-management__section-title">已在班级</h3>
                    </div>
                    <span className="panel__count">{classroomStudents.length} 人</span>
                  </div>

                  {classroomStudents.length === 0 ? (
                    <div className="exam-history__empty">
                      <p>这个班级还没有学生</p>
                    </div>
                  ) : (
                    <div className="teacher-student__list">
                      {classroomStudents.map((student) => (
                        <article key={student.id} className="teacher-student__item">
                          <div>
                            <h4 className="user-management__name">{student.displayName}</h4>
                            <p className="user-management__username">{student.username}</p>
                          </div>
                          <button
                            type="button"
                            className="btn btn--secondary"
                            onClick={() => handleRemoveStudent(student.id)}
                            disabled={savingKey === `remove-${student.id}`}
                          >
                            {savingKey === `remove-${student.id}` ? '处理中...' : '移出班级'}
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
                      <h3 className="user-management__section-title">可加入学生</h3>
                    </div>
                    <span className="panel__count">{candidateStudents.length} 人</span>
                  </div>

                  {candidateStudents.length === 0 ? (
                    <div className="exam-history__empty">
                      <p>暂无可加入的学生</p>
                    </div>
                  ) : (
                    <div className="teacher-student__list">
                      {candidateStudents.map((student) => (
                        <article key={student.id} className="teacher-student__item">
                          <div>
                            <h4 className="user-management__name">{student.displayName}</h4>
                            <p className="user-management__username">{student.username}</p>
                          </div>
                          <button
                            type="button"
                            className="btn btn--primary"
                            onClick={() => handleAddStudent(student.id)}
                            disabled={savingKey === `add-${student.id}`}
                          >
                            {savingKey === `add-${student.id}` ? '处理中...' : '加入班级'}
                          </button>
                        </article>
                      ))}
                    </div>
                  )}
                </section>
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
