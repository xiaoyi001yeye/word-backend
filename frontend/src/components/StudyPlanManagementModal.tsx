import { useEffect, useState } from 'react';
import { studyPlanApi } from '../api';
import type {
  Classroom,
  CreateStudyPlanPayload,
  Dictionary,
  StudyPlan,
  StudyPlanOverview,
  StudyPlanStudentAttention,
  StudyPlanStudentSummary,
} from '../types';

interface StudyPlanManagementModalProps {
  isOpen: boolean;
  dictionaries: Dictionary[];
  classrooms: Classroom[];
  onClose: () => void;
}

interface StudyPlanFormState {
  name: string;
  description: string;
  dictionaryId: number | '';
  classroomIds: number[];
  startDate: string;
  endDate: string;
  timezone: string;
  dailyNewCount: number;
  dailyReviewLimit: number;
  reviewMode: CreateStudyPlanPayload['reviewMode'];
  reviewIntervalsText: string;
  completionThreshold: number;
  dailyDeadlineTime: string;
  attentionTrackingEnabled: boolean;
  minFocusSecondsPerWord: number;
  maxFocusSecondsPerWord: number;
  longStayWarningSeconds: number;
  idleTimeoutSeconds: number;
}

const DEFAULT_INTERVALS = '0, 1, 2, 4, 7, 15';

function formatDateInput(date = new Date()) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

function formatPercent(value?: number | null) {
  return `${Number(value ?? 0).toFixed(0)}%`;
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '暂无记录';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function studyPlanStatusLabel(status: StudyPlan['status']) {
  switch (status) {
    case 'DRAFT':
      return '草稿';
    case 'PUBLISHED':
      return '已发布';
    case 'PAUSED':
      return '已暂停';
    case 'COMPLETED':
      return '已完成';
    case 'ARCHIVED':
      return '已归档';
    default:
      return status;
  }
}

function taskStatusLabel(status: StudyPlanStudentSummary['todayStatus']) {
  switch (status) {
    case 'NOT_STARTED':
      return '未开始';
    case 'IN_PROGRESS':
      return '进行中';
    case 'COMPLETED':
      return '已完成';
    case 'MISSED':
      return '已缺勤';
    default:
      return status;
  }
}

function buildInitialFormState(
  dictionaries: Dictionary[],
  classrooms: Classroom[],
): StudyPlanFormState {
  return {
    name: '',
    description: '',
    dictionaryId: dictionaries[0]?.id ?? '',
    classroomIds: classrooms[0] ? [classrooms[0].id] : [],
    startDate: formatDateInput(),
    endDate: '',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai',
    dailyNewCount: 20,
    dailyReviewLimit: 40,
    reviewMode: 'EBBINGHAUS',
    reviewIntervalsText: DEFAULT_INTERVALS,
    completionThreshold: 100,
    dailyDeadlineTime: '21:00',
    attentionTrackingEnabled: true,
    minFocusSecondsPerWord: 5,
    maxFocusSecondsPerWord: 120,
    longStayWarningSeconds: 90,
    idleTimeoutSeconds: 30,
  };
}

export function StudyPlanManagementModal({
  isOpen,
  dictionaries,
  classrooms,
  onClose,
}: StudyPlanManagementModalProps) {
  const [plans, setPlans] = useState<StudyPlan[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null);
  const [overview, setOverview] = useState<StudyPlanOverview | null>(null);
  const [students, setStudents] = useState<StudyPlanStudentSummary[]>([]);
  const [attention, setAttention] = useState<StudyPlanStudentAttention | null>(null);
  const [loadingPlans, setLoadingPlans] = useState(false);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [creating, setCreating] = useState(false);
  const [publishingPlanId, setPublishingPlanId] = useState<number | null>(null);
  const [attentionLoadingStudentId, setAttentionLoadingStudentId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [form, setForm] = useState<StudyPlanFormState>(() => buildInitialFormState(dictionaries, classrooms));

  const selectedPlan = plans.find((plan) => plan.id === selectedPlanId) ?? null;
  const classroomNameMap = new Map(classrooms.map((classroom) => [classroom.id, classroom.name]));

  const loadPlans = async (preferredPlanId?: number) => {
    setLoadingPlans(true);
    setError(null);
    try {
      const nextPlans = await studyPlanApi.list();
      setPlans(nextPlans);

      const nextSelectedId = preferredPlanId
        ?? (selectedPlanId && nextPlans.some((plan) => plan.id === selectedPlanId) ? selectedPlanId : nextPlans[0]?.id ?? null);
      setSelectedPlanId(nextSelectedId);

      if (nextSelectedId) {
        setLoadingDetails(true);
        try {
          const [nextOverview, nextStudents] = await Promise.all([
            studyPlanApi.getOverview(nextSelectedId),
            studyPlanApi.getStudents(nextSelectedId),
          ]);
          setOverview(nextOverview);
          setStudents(nextStudents);
        } finally {
          setLoadingDetails(false);
        }
      } else {
        setOverview(null);
        setStudents([]);
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载学习计划失败');
    } finally {
      setLoadingPlans(false);
    }
  };

  const loadPlanDetails = async (planId: number) => {
    setSelectedPlanId(planId);
    setAttention(null);
    setLoadingDetails(true);
    setError(null);
    try {
      const [nextOverview, nextStudents] = await Promise.all([
        studyPlanApi.getOverview(planId),
        studyPlanApi.getStudents(planId),
      ]);
      setOverview(nextOverview);
      setStudents(nextStudents);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载计划详情失败');
    } finally {
      setLoadingDetails(false);
    }
  };

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    setForm(buildInitialFormState(dictionaries, classrooms));
    setAttention(null);
    setSuccessMessage(null);
    void loadPlans();
  }, [classrooms, dictionaries, isOpen]);

  if (!isOpen) {
    return null;
  }

  const updateForm = <K extends keyof StudyPlanFormState>(key: K, value: StudyPlanFormState[K]) => {
    setForm((current) => ({
      ...current,
      [key]: value,
    }));
  };

  const toggleClassroom = (classroomId: number) => {
    updateForm(
      'classroomIds',
      form.classroomIds.includes(classroomId)
        ? form.classroomIds.filter((id) => id !== classroomId)
        : [...form.classroomIds, classroomId],
    );
  };

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault();
    if (form.dictionaryId === '') {
      setError('请先选择一本文档词书');
      return;
    }
    if (form.classroomIds.length === 0) {
      setError('请至少勾选一个班级');
      return;
    }

    const reviewIntervals = form.reviewIntervalsText
      .split(/[,\s]+/)
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => Number(item))
      .filter((item) => Number.isFinite(item) && item >= 0);

    if (reviewIntervals.length === 0) {
      setError('请填写至少一个复习间隔，例如 0,1,2,4,7,15');
      return;
    }

    setCreating(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const createdPlan = await studyPlanApi.create({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        dictionaryId: form.dictionaryId,
        classroomIds: form.classroomIds,
        startDate: form.startDate,
        endDate: form.endDate || undefined,
        timezone: form.timezone.trim(),
        dailyNewCount: form.dailyNewCount,
        dailyReviewLimit: form.dailyReviewLimit,
        reviewMode: form.reviewMode,
        reviewIntervals,
        completionThreshold: form.completionThreshold,
        dailyDeadlineTime: form.dailyDeadlineTime,
        attentionTrackingEnabled: form.attentionTrackingEnabled,
        minFocusSecondsPerWord: form.minFocusSecondsPerWord,
        maxFocusSecondsPerWord: form.maxFocusSecondsPerWord,
        longStayWarningSeconds: form.longStayWarningSeconds,
        idleTimeoutSeconds: form.idleTimeoutSeconds,
      });

      setForm(buildInitialFormState(dictionaries, classrooms));
      setSuccessMessage(`计划「${createdPlan.name}」已创建，现在可以直接发布给班级。`);
      await loadPlans(createdPlan.id);
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : '创建学习计划失败');
    } finally {
      setCreating(false);
    }
  };

  const handlePublish = async (planId: number) => {
    setPublishingPlanId(planId);
    setError(null);
    setSuccessMessage(null);
    try {
      const publishedPlan = await studyPlanApi.publish(planId);
      setPlans((current) => current.map((plan) => (plan.id === publishedPlan.id ? publishedPlan : plan)));
      setSuccessMessage(`计划「${publishedPlan.name}」已发布，学生登录后就能开始今日学习。`);
      await loadPlanDetails(planId);
    } catch (publishError) {
      setError(publishError instanceof Error ? publishError.message : '发布学习计划失败');
    } finally {
      setPublishingPlanId(null);
    }
  };

  const handleLoadAttention = async (studentId: number) => {
    if (!selectedPlanId) {
      return;
    }

    setAttentionLoadingStudentId(studentId);
    setError(null);
    try {
      const nextAttention = await studyPlanApi.getStudentAttention(selectedPlanId, studentId);
      setAttention(nextAttention);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载学生注意力统计失败');
    } finally {
      setAttentionLoadingStudentId(null);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal modal--wide">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">学习计划</h2>
            <p className="user-management__subtitle">老师在这里创建、发布并追踪班级执行情况。</p>
          </div>
          <button className="modal__close" onClick={onClose} disabled={creating || publishingPlanId !== null}>
            &times;
          </button>
        </div>

        <div className="modal__form study-plan-modal">
          <section className="study-plan-modal__column">
            <div className="study-plan-modal__section-header">
              <div>
                <p className="panel__eyebrow">Create Plan</p>
                <h3 className="study-plan-modal__section-title">新建学习计划</h3>
              </div>
              <span className="panel__count">{classrooms.length} 个可选班级</span>
            </div>

            <form onSubmit={handleCreate} className="study-plan-form">
              <div className="form__group">
                <label htmlFor="studyPlanName" className="form__label">计划名称</label>
                <input
                  id="studyPlanName"
                  className="form__input"
                  value={form.name}
                  onChange={(event) => updateForm('name', event.target.value)}
                  placeholder="例如：三月英语晨读计划"
                  disabled={creating}
                  required
                />
              </div>

              <div className="form__group">
                <label htmlFor="studyPlanDescription" className="form__label">计划说明</label>
                <textarea
                  id="studyPlanDescription"
                  className="form__input study-plan-form__textarea"
                  value={form.description}
                  onChange={(event) => updateForm('description', event.target.value)}
                  placeholder="给学生的提示、复习说明或完成要求"
                  disabled={creating}
                />
              </div>

              <div className="form__group">
                <label htmlFor="studyPlanDictionary" className="form__label">关联辞书</label>
                <select
                  id="studyPlanDictionary"
                  className="form__select"
                  value={form.dictionaryId}
                  onChange={(event) => updateForm('dictionaryId', Number(event.target.value))}
                  disabled={creating || dictionaries.length === 0}
                >
                  {dictionaries.length === 0 ? (
                    <option value="">暂无可用辞书</option>
                  ) : (
                    dictionaries.map((dictionary) => (
                      <option key={dictionary.id} value={dictionary.id}>
                        {dictionary.name}
                      </option>
                    ))
                  )}
                </select>
              </div>

              <div className="form__group">
                <span className="form__label">目标班级</span>
                {classrooms.length === 0 ? (
                  <div className="study-plan-empty">还没有可用班级，请先去“班级管理”创建班级。</div>
                ) : (
                  <div className="study-plan-checklist">
                    {classrooms.map((classroom) => {
                      const checked = form.classroomIds.includes(classroom.id);
                      return (
                        <label
                          key={classroom.id}
                          className={`study-plan-checklist__item ${checked ? 'study-plan-checklist__item--selected' : ''}`}
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleClassroom(classroom.id)}
                            disabled={creating}
                          />
                          <div>
                            <strong>{classroom.name}</strong>
                            <p>{classroom.studentCount} 名学生</p>
                          </div>
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="form__row">
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanStartDate" className="form__label">开始日期</label>
                  <input
                    id="studyPlanStartDate"
                    type="date"
                    className="form__input"
                    value={form.startDate}
                    onChange={(event) => updateForm('startDate', event.target.value)}
                    disabled={creating}
                  />
                </div>
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanEndDate" className="form__label">结束日期</label>
                  <input
                    id="studyPlanEndDate"
                    type="date"
                    className="form__input"
                    value={form.endDate}
                    onChange={(event) => updateForm('endDate', event.target.value)}
                    disabled={creating}
                  />
                </div>
              </div>

              <div className="form__row">
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanDailyNew" className="form__label">每日新词</label>
                  <input
                    id="studyPlanDailyNew"
                    type="number"
                    className="form__input"
                    min={1}
                    value={form.dailyNewCount}
                    onChange={(event) => updateForm('dailyNewCount', Math.max(1, Number(event.target.value) || 1))}
                    disabled={creating}
                  />
                </div>
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanDailyReview" className="form__label">每日复习上限</label>
                  <input
                    id="studyPlanDailyReview"
                    type="number"
                    className="form__input"
                    min={0}
                    value={form.dailyReviewLimit}
                    onChange={(event) => updateForm('dailyReviewLimit', Math.max(0, Number(event.target.value) || 0))}
                    disabled={creating}
                  />
                </div>
              </div>

              <div className="form__row">
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanMode" className="form__label">复习模式</label>
                  <select
                    id="studyPlanMode"
                    className="form__select"
                    value={form.reviewMode}
                    onChange={(event) => updateForm('reviewMode', event.target.value as CreateStudyPlanPayload['reviewMode'])}
                    disabled={creating}
                  >
                    <option value="EBBINGHAUS">艾宾浩斯</option>
                    <option value="FIXED_INTERVAL">固定间隔</option>
                    <option value="CUSTOM">自定义</option>
                  </select>
                </div>
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanDeadline" className="form__label">每日截止时间</label>
                  <input
                    id="studyPlanDeadline"
                    type="time"
                    className="form__input"
                    value={form.dailyDeadlineTime}
                    onChange={(event) => updateForm('dailyDeadlineTime', event.target.value)}
                    disabled={creating}
                  />
                </div>
              </div>

              <div className="form__group">
                <label htmlFor="studyPlanIntervals" className="form__label">复习间隔（天）</label>
                <input
                  id="studyPlanIntervals"
                  className="form__input"
                  value={form.reviewIntervalsText}
                  onChange={(event) => updateForm('reviewIntervalsText', event.target.value)}
                  placeholder="0, 1, 2, 4, 7, 15"
                  disabled={creating}
                />
              </div>

              <div className="form__row">
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanCompletionThreshold" className="form__label">完成率阈值</label>
                  <input
                    id="studyPlanCompletionThreshold"
                    type="number"
                    className="form__input"
                    min={0}
                    max={100}
                    value={form.completionThreshold}
                    onChange={(event) => updateForm('completionThreshold', Math.min(100, Math.max(0, Number(event.target.value) || 0)))}
                    disabled={creating}
                  />
                </div>
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanTimezone" className="form__label">时区</label>
                  <input
                    id="studyPlanTimezone"
                    className="form__input"
                    value={form.timezone}
                    onChange={(event) => updateForm('timezone', event.target.value)}
                    disabled={creating}
                  />
                </div>
              </div>

              <div className="form__group">
                <label className="study-plan-toggle">
                  <input
                    type="checkbox"
                    checked={form.attentionTrackingEnabled}
                    onChange={(event) => updateForm('attentionTrackingEnabled', event.target.checked)}
                    disabled={creating}
                  />
                  <span>启用学生注意力采集与停留时间统计</span>
                </label>
              </div>

              <div className="form__row">
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanMinFocus" className="form__label">单词最短有效停留（秒）</label>
                  <input
                    id="studyPlanMinFocus"
                    type="number"
                    className="form__input"
                    min={0}
                    value={form.minFocusSecondsPerWord}
                    onChange={(event) => updateForm('minFocusSecondsPerWord', Math.max(0, Number(event.target.value) || 0))}
                    disabled={creating}
                  />
                </div>
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanMaxFocus" className="form__label">单词最长停留（秒）</label>
                  <input
                    id="studyPlanMaxFocus"
                    type="number"
                    className="form__input"
                    min={1}
                    value={form.maxFocusSecondsPerWord}
                    onChange={(event) => updateForm('maxFocusSecondsPerWord', Math.max(1, Number(event.target.value) || 1))}
                    disabled={creating}
                  />
                </div>
              </div>

              <div className="form__row">
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanLongStay" className="form__label">长停留提醒（秒）</label>
                  <input
                    id="studyPlanLongStay"
                    type="number"
                    className="form__input"
                    min={1}
                    value={form.longStayWarningSeconds}
                    onChange={(event) => updateForm('longStayWarningSeconds', Math.max(1, Number(event.target.value) || 1))}
                    disabled={creating}
                  />
                </div>
                <div className="form__group form__group--half">
                  <label htmlFor="studyPlanIdle" className="form__label">空闲判定（秒）</label>
                  <input
                    id="studyPlanIdle"
                    type="number"
                    className="form__input"
                    min={1}
                    value={form.idleTimeoutSeconds}
                    onChange={(event) => updateForm('idleTimeoutSeconds', Math.max(1, Number(event.target.value) || 1))}
                    disabled={creating}
                  />
                </div>
              </div>

              {error && <div className="form__error">{error}</div>}
              {successMessage && <div className="form__success">{successMessage}</div>}

              <div className="modal__footer study-plan-modal__footer">
                <button type="button" className="btn btn--secondary" onClick={onClose} disabled={creating}>
                  关闭
                </button>
                <button
                  type="submit"
                  className="btn btn--primary"
                  disabled={creating || dictionaries.length === 0 || classrooms.length === 0}
                >
                  {creating ? '创建中...' : '创建计划'}
                </button>
              </div>
            </form>
          </section>

          <section className="study-plan-modal__column">
            <div className="study-plan-modal__section-header">
              <div>
                <p className="panel__eyebrow">Live Board</p>
                <h3 className="study-plan-modal__section-title">计划执行看板</h3>
              </div>
              <button className="exam-history-btn" onClick={() => void loadPlans(selectedPlanId ?? undefined)} disabled={loadingPlans}>
                {loadingPlans ? '刷新中...' : '刷新'}
              </button>
            </div>

            {plans.length === 0 ? (
              <div className="study-plan-empty">还没有学习计划，左侧创建后就会出现在这里。</div>
            ) : (
              <div className="study-plan-card-list">
                {plans.map((plan) => (
                  <article
                    key={plan.id}
                    className={`study-plan-card ${selectedPlanId === plan.id ? 'study-plan-card--selected' : ''}`}
                  >
                    <button
                      type="button"
                      className="study-plan-card__body"
                      onClick={() => void loadPlanDetails(plan.id)}
                    >
                      <div className="study-plan-card__meta">
                        <span className={`study-plan-status study-plan-status--${plan.status.toLowerCase()}`}>
                          {studyPlanStatusLabel(plan.status)}
                        </span>
                        <span>{plan.dictionaryName}</span>
                      </div>
                      <h4 className="study-plan-card__title">{plan.name}</h4>
                      <p className="study-plan-card__summary">
                        {plan.studentCount} 名学生，{plan.dailyNewCount} 新词 / {plan.dailyReviewLimit} 复习
                      </p>
                      <p className="study-plan-card__summary">
                        班级：{plan.classroomIds.map((id) => classroomNameMap.get(id) ?? `#${id}`).join('、')}
                      </p>
                    </button>

                    <div className="study-plan-card__actions">
                      <span className="study-plan-card__date">{plan.startDate} 开始</span>
                      {plan.status === 'DRAFT' && (
                        <button
                          type="button"
                          className="exam-trigger-btn"
                          onClick={() => void handlePublish(plan.id)}
                          disabled={publishingPlanId === plan.id}
                        >
                          {publishingPlanId === plan.id ? '发布中...' : '发布'}
                        </button>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            )}

            {loadingDetails ? (
              <div className="study-plan-empty">正在加载计划详情...</div>
            ) : selectedPlan && overview ? (
              <>
                <div className="study-plan-overview">
                  <div className="study-plan-overview__item">
                    <span>今日完成</span>
                    <strong>{overview.completedStudents}/{overview.totalStudents}</strong>
                  </div>
                  <div className="study-plan-overview__item">
                    <span>进行中</span>
                    <strong>{overview.inProgressStudents}</strong>
                  </div>
                  <div className="study-plan-overview__item">
                    <span>未开始</span>
                    <strong>{overview.notStartedStudents}</strong>
                  </div>
                  <div className="study-plan-overview__item">
                    <span>已缺勤</span>
                    <strong>{overview.missedStudents}</strong>
                  </div>
                  <div className="study-plan-overview__item">
                    <span>平均完成率</span>
                    <strong>{formatPercent(overview.averageCompletionRate)}</strong>
                  </div>
                  <div className="study-plan-overview__item">
                    <span>平均注意力</span>
                    <strong>{formatPercent(overview.averageAttentionScore)}</strong>
                  </div>
                </div>

                <div className="study-plan-students">
                  <div className="study-plan-modal__section-header">
                    <div>
                      <p className="panel__eyebrow">Students</p>
                      <h4 className="study-plan-modal__section-title">学生执行情况</h4>
                    </div>
                    <span className="panel__count">{selectedPlan.name}</span>
                  </div>

                  {students.length === 0 ? (
                    <div className="study-plan-empty">当前计划还没有学生实例，发布后会自动生成。</div>
                  ) : (
                    <div className="study-plan-student-list">
                      {students.map((student) => (
                        <article key={student.studentStudyPlanId} className="study-plan-student-card">
                          <div>
                            <h5>{student.studentName}</h5>
                            <p>
                              {taskStatusLabel(student.todayStatus)} · {student.completedCount}/{student.totalTaskCount} · 连续学习 {student.currentStreak} 天
                            </p>
                            <p>
                              停留均值 {Number(student.avgFocusSecondsPerWord ?? 0).toFixed(1)} 秒 / 注意力 {formatPercent(student.attentionScore)}
                            </p>
                            <p>最近学习：{formatDateTime(student.lastStudyAt)}</p>
                          </div>
                          <button
                            type="button"
                            className="exam-history-btn"
                            onClick={() => void handleLoadAttention(student.studentId)}
                            disabled={attentionLoadingStudentId === student.studentId}
                          >
                            {attentionLoadingStudentId === student.studentId ? '加载中...' : '停留统计'}
                          </button>
                        </article>
                      ))}
                    </div>
                  )}
                </div>

                {attention && (
                  <div className="study-plan-attention">
                    <div className="study-plan-modal__section-header">
                      <div>
                        <p className="panel__eyebrow">Attention</p>
                        <h4 className="study-plan-modal__section-title">{attention.studentName} 的每日停留统计</h4>
                      </div>
                      <span className="panel__count">{attention.dailyStats.length} 天</span>
                    </div>

                    {attention.dailyStats.length === 0 ? (
                      <div className="study-plan-empty">这个学生还没有产生停留统计数据。</div>
                    ) : (
                      <div className="study-plan-attention-list">
                        {attention.dailyStats.map((dailyStat) => (
                          <div key={dailyStat.taskDate} className="study-plan-attention-list__item">
                            <strong>{dailyStat.taskDate}</strong>
                            <span>访问 {dailyStat.wordsVisited} 个词</span>
                            <span>完成 {dailyStat.wordsCompleted} 个词</span>
                            <span>总停留 {dailyStat.totalFocusSeconds} 秒</span>
                            <span>均值 {Number(dailyStat.avgFocusSecondsPerWord ?? 0).toFixed(1)} 秒</span>
                            <span>长停留 {dailyStat.longStayWordCount} 次</span>
                            <span>空闲中断 {dailyStat.idleInterruptCount} 次</span>
                            <span>注意力 {formatPercent(dailyStat.attentionScore)}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </>
            ) : (
              <div className="study-plan-empty">选择右侧列表中的计划，就能看到执行概览和学生停留统计。</div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
