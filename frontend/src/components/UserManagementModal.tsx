import { useCallback, useEffect, useMemo, useState } from 'react';
import { userApi } from '../api';
import { TeacherStudentManagementModal } from './TeacherStudentManagementModal';
import type { User, UserRole, UserStatus } from '../types';

interface UserManagementModalProps {
  isOpen: boolean;
  currentUser: User;
  onClose: () => void;
  onUsersChanged?: (users: User[]) => void;
}

const ROLE_OPTIONS: UserRole[] = ['ADMIN', 'TEACHER', 'STUDENT'];
const STATUS_OPTIONS: UserStatus[] = ['ACTIVE', 'DISABLED', 'LOCKED'];

const EMPTY_FORM = {
  username: '',
  password: '',
  displayName: '',
  email: '',
  phone: '',
  role: 'STUDENT' as UserRole,
};

function formatDateTime(value?: string | null) {
  if (!value) {
    return '暂无';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function UserManagementModal({
  isOpen,
  currentUser,
  onClose,
  onUsersChanged,
}: UserManagementModalProps) {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [createForm, setCreateForm] = useState(EMPTY_FORM);
  const [teacherForStudentManagement, setTeacherForStudentManagement] = useState<User | null>(null);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const nextUsers = await userApi.getAll();
      const sortedUsers = [...nextUsers].sort((left, right) => right.id - left.id);
      setUsers(sortedUsers);
      onUsersChanged?.(sortedUsers);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载用户列表失败');
    } finally {
      setLoading(false);
    }
  }, [onUsersChanged]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    setSuccessMessage(null);
    loadUsers();
  }, [isOpen, loadUsers]);

  const roleCounts = useMemo(
    () => ROLE_OPTIONS.reduce<Record<UserRole, number>>((counts, role) => {
      counts[role] = users.filter((user) => user.role === role).length;
      return counts;
    }, { ADMIN: 0, TEACHER: 0, STUDENT: 0 }),
    [users],
  );

  const allStudents = useMemo(
    () => users.filter((user) => user.role === 'STUDENT'),
    [users],
  );

  if (!isOpen) {
    return null;
  }

  const handleCreateInputChange = (
    event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>,
  ) => {
    const { name, value } = event.target;
    setCreateForm((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleCreateUser = async (event: React.FormEvent) => {
    event.preventDefault();

    setSubmitLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      if (!createForm.username.trim()) {
        throw new Error('用户名不能为空');
      }

      if (!createForm.password.trim()) {
        throw new Error('密码不能为空');
      }

      if (!createForm.displayName.trim()) {
        throw new Error('显示名称不能为空');
      }

      await userApi.create({
        username: createForm.username.trim(),
        password: createForm.password,
        displayName: createForm.displayName.trim(),
        email: createForm.email.trim() || undefined,
        phone: createForm.phone.trim() || undefined,
        role: createForm.role,
      });

      setCreateForm(EMPTY_FORM);
      setSuccessMessage('用户已创建，列表已刷新。');
      await loadUsers();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '创建用户失败');
    } finally {
      setSubmitLoading(false);
    }
  };

  const handleRoleChange = async (userId: number, role: UserRole) => {
    setSavingKey(`role-${userId}`);
    setError(null);
    setSuccessMessage(null);

    try {
      await userApi.updateRole(userId, role);
      setSuccessMessage('用户角色已更新。');
      await loadUsers();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '更新角色失败');
    } finally {
      setSavingKey(null);
    }
  };

  const handleStatusChange = async (userId: number, status: UserStatus) => {
    setSavingKey(`status-${userId}`);
    setError(null);
    setSuccessMessage(null);

    try {
      await userApi.updateStatus(userId, status);
      setSuccessMessage('用户状态已更新。');
      await loadUsers();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '更新状态失败');
    } finally {
      setSavingKey(null);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal modal--wide user-management">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">用户管理</h2>
            <p className="user-management__subtitle">
              管理员可以在这里创建账号、调整角色、启停用户。
            </p>
          </div>
          <button className="modal__close" onClick={onClose} disabled={loading || submitLoading}>
            &times;
          </button>
        </div>

        <div className="user-management__body">
          <section className="user-management__section">
            <div className="user-management__section-header">
              <div>
                <p className="panel__eyebrow">Create User</p>
                <h3 className="user-management__section-title">新建账号</h3>
              </div>
              <div className="user-management__chips">
                <span className="app__masthead-chip">管理员 {roleCounts.ADMIN}</span>
                <span className="app__masthead-chip">教师 {roleCounts.TEACHER}</span>
                <span className="app__masthead-chip">学生 {roleCounts.STUDENT}</span>
              </div>
            </div>

            <form onSubmit={handleCreateUser} className="modal__form user-management__form">
              <div className="form__row">
                <div className="form__group form__group--half">
                  <label htmlFor="create-username" className="form__label">
                    用户名
                  </label>
                  <input
                    id="create-username"
                    name="username"
                    className="form__input"
                    value={createForm.username}
                    onChange={handleCreateInputChange}
                    placeholder="例如 teacher_zhang"
                    disabled={submitLoading}
                  />
                </div>
                <div className="form__group form__group--half">
                  <label htmlFor="create-password" className="form__label">
                    初始密码
                  </label>
                  <input
                    id="create-password"
                    name="password"
                    type="password"
                    className="form__input"
                    value={createForm.password}
                    onChange={handleCreateInputChange}
                    placeholder="请输入初始密码"
                    disabled={submitLoading}
                  />
                </div>
              </div>

              <div className="form__row">
                <div className="form__group form__group--half">
                  <label htmlFor="create-displayName" className="form__label">
                    显示名称
                  </label>
                  <input
                    id="create-displayName"
                    name="displayName"
                    className="form__input"
                    value={createForm.displayName}
                    onChange={handleCreateInputChange}
                    placeholder="例如 张老师"
                    disabled={submitLoading}
                  />
                </div>
                <div className="form__group form__group--half">
                  <label htmlFor="create-role" className="form__label">
                    角色
                  </label>
                  <select
                    id="create-role"
                    name="role"
                    className="form__select"
                    value={createForm.role}
                    onChange={handleCreateInputChange}
                    disabled={submitLoading}
                  >
                    {ROLE_OPTIONS.map((role) => (
                      <option key={role} value={role}>
                        {role}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="form__row">
                <div className="form__group form__group--half">
                  <label htmlFor="create-email" className="form__label">
                    邮箱
                  </label>
                  <input
                    id="create-email"
                    name="email"
                    className="form__input"
                    value={createForm.email}
                    onChange={handleCreateInputChange}
                    placeholder="可选"
                    disabled={submitLoading}
                  />
                </div>
                <div className="form__group form__group--half">
                  <label htmlFor="create-phone" className="form__label">
                    手机号
                  </label>
                  <input
                    id="create-phone"
                    name="phone"
                    className="form__input"
                    value={createForm.phone}
                    onChange={handleCreateInputChange}
                    placeholder="可选"
                    disabled={submitLoading}
                  />
                </div>
              </div>

              {error && <div className="form__error">{error}</div>}
              {successMessage && <div className="form__success">{successMessage}</div>}

              <div className="modal__footer user-management__footer">
                <button
                  type="button"
                  className="btn btn--secondary"
                  onClick={loadUsers}
                  disabled={loading || submitLoading}
                >
                  {loading ? '刷新中...' : '刷新列表'}
                </button>
                <button type="submit" className="btn btn--primary" disabled={submitLoading}>
                  {submitLoading ? '创建中...' : '创建用户'}
                </button>
              </div>
            </form>
          </section>

          <section className="user-management__section">
            <div className="user-management__section-header">
              <div>
                <p className="panel__eyebrow">All Users</p>
                <h3 className="user-management__section-title">账号列表</h3>
              </div>
              <span className="panel__count">{users.length} 个账号</span>
            </div>

            {loading && users.length === 0 ? (
              <div className="sidebar__loading">
                <span className="sidebar__spinner"></span>
              </div>
            ) : users.length === 0 ? (
              <div className="exam-history__empty">
                <p>当前还没有用户数据</p>
              </div>
            ) : (
              <div className="user-management__list">
                {users.map((user) => {
                  const isCurrentUser = user.id === currentUser.id;
                  const rowBusy =
                    savingKey === `role-${user.id}` || savingKey === `status-${user.id}`;

                  return (
                    <article key={user.id} className="user-management__card">
                      <div className="user-management__card-main">
                        <div className="user-management__identity">
                          <div>
                            <h4 className="user-management__name">{user.displayName}</h4>
                            <p className="user-management__username">
                              {user.username}
                              {isCurrentUser ? ' · 当前账号' : ''}
                            </p>
                          </div>
                          <div className="user-management__meta">
                            <span className="app__masthead-chip">#{user.id}</span>
                            <span className="app__masthead-chip">{user.role}</span>
                            <span className="app__masthead-chip">{user.status}</span>
                          </div>
                        </div>

                        <div className="user-management__fields">
                          <p>邮箱：{user.email || '暂无'}</p>
                          <p>手机：{user.phone || '暂无'}</p>
                          <p>创建时间：{formatDateTime(user.createdAt)}</p>
                          <p>最近登录：{formatDateTime(user.lastLoginAt)}</p>
                        </div>

                        {user.role === 'TEACHER' && (
                          <div className="user-management__teacher-actions">
                            <button
                              className="btn btn--secondary"
                              onClick={() => setTeacherForStudentManagement(user)}
                            >
                              管理学生归属
                            </button>
                          </div>
                        )}
                      </div>

                      <div className="user-management__controls">
                        <div className="user-management__control">
                          <label className="form__label" htmlFor={`role-${user.id}`}>
                            角色
                          </label>
                          <select
                            id={`role-${user.id}`}
                            className="form__select"
                            value={user.role}
                            disabled={rowBusy || isCurrentUser}
                            onChange={(event) => handleRoleChange(user.id, event.target.value as UserRole)}
                          >
                            {ROLE_OPTIONS.map((role) => (
                              <option key={role} value={role}>
                                {role}
                              </option>
                            ))}
                          </select>
                        </div>

                        <div className="user-management__control">
                          <label className="form__label" htmlFor={`status-${user.id}`}>
                            状态
                          </label>
                          <select
                            id={`status-${user.id}`}
                            className="form__select"
                            value={user.status}
                            disabled={rowBusy || isCurrentUser}
                            onChange={(event) => handleStatusChange(user.id, event.target.value as UserStatus)}
                          >
                            {STATUS_OPTIONS.map((status) => (
                              <option key={status} value={status}>
                                {status}
                              </option>
                            ))}
                          </select>
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      </div>

      <TeacherStudentManagementModal
        isOpen={teacherForStudentManagement !== null}
        teacher={teacherForStudentManagement}
        allStudents={allStudents}
        onClose={() => setTeacherForStudentManagement(null)}
      />
    </div>
  );
}
