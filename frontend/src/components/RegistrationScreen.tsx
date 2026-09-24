import { useMemo, useState } from 'react';
import {
  ArrowLeft,
  BookOpenText,
  Books,
  Check,
  Dog,
  Eye,
  EyeSlash,
  GraduationCap,
  Pencil,
  Rabbit,
  Smiley,
  Sparkle,
  Star,
  UserCircle,
} from '@phosphor-icons/react';
import { userApi } from '../api';
import type { UserRole } from '../types';

type RegistrationRole = Extract<UserRole, 'STUDENT' | 'TEACHER'>;

const STUDENT_AVATARS = ['student-panda', 'student-bear', 'student-cat', 'student-rabbit', 'student-fox', 'student-koala'];
const TEACHER_AVATARS = ['teacher-book', 'teacher-glasses', 'teacher-lamp', 'teacher-tree', 'teacher-male-3', 'teacher-female-3'];
const INTERESTS = ['语文', '数学', '英语', '编程', '阅读'];
const EXPERTISE = ['英语', '数学', '语文', '编程', '竞赛'];
const GRADES = ['PRIMARY_1', 'PRIMARY_2', 'PRIMARY_3', 'PRIMARY_4', 'PRIMARY_5', 'PRIMARY_6', 'JUNIOR_1', 'JUNIOR_2', 'JUNIOR_3', 'SENIOR_1', 'SENIOR_2', 'SENIOR_3'];
const STAGES = ['PRIMARY', 'JUNIOR_HIGH', 'SENIOR_HIGH', 'UNIVERSITY', 'ADULT_EDUCATION'];
const AVATAR_ICONS = {
  'student-panda': Smiley,
  'student-bear': Dog,
  'student-cat': Smiley,
  'student-rabbit': Rabbit,
  'student-fox': Dog,
  'student-koala': Star,
  'teacher-book': BookOpenText,
  'teacher-glasses': Pencil,
  'teacher-lamp': GraduationCap,
  'teacher-tree': Books,
  'teacher-owl': UserCircle,
};
const AVATAR_IMAGES: Record<string, string> = {
  'teacher-book': '/avatars/teacher-male-1.png',
  'teacher-glasses': '/avatars/teacher-female-1.png',
  'teacher-lamp': '/avatars/teacher-female-2.png',
  'teacher-tree': '/avatars/teacher-male-2.png',
  'teacher-male-3': '/avatars/teacher-male-3.png',
  'teacher-female-3': '/avatars/teacher-female-3.png',
};

function displayOption(value: string) {
  const labels: Record<string, string> = {
    PRIMARY: '小学',
    JUNIOR_HIGH: '初中',
    SENIOR_HIGH: '高中',
    UNIVERSITY: '大学',
    ADULT_EDUCATION: '成人教育',
  };
  if (labels[value]) {
    return labels[value];
  }
  return value.replace('PRIMARY_', '小学').replace('JUNIOR_', '初中').replace('SENIOR_', '高中');
}

export function RegistrationScreen({ role }: { role: RegistrationRole }) {
  const isStudent = role === 'STUDENT';
  const avatars = useMemo(() => (isStudent ? STUDENT_AVATARS : TEACHER_AVATARS), [isStudent]);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [avatarKey, setAvatarKey] = useState(avatars[0]);
  const [gender, setGender] = useState('MALE');
  const [schoolName, setSchoolName] = useState('');
  const [grade, setGrade] = useState('');
  const [teachingStage, setTeachingStage] = useState('');
  const [tags, setTags] = useState<string[]>([isStudent ? '语文' : '英语']);
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const toggleTag = (tag: string) => {
    setTags((current) => current.includes(tag) ? current.filter((item) => item !== tag) : [...current, tag]);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!username.trim() || !password || !confirmPassword || !displayName.trim()) {
      setError('请填写所有必填项。');
      return;
    }
    if (password.length < 6) {
      setError('密码至少需要 6 位。');
      return;
    }
    if (password !== confirmPassword) {
      setError('两次输入的密码不一致。');
      return;
    }
    if (isStudent && !grade) {
      setError('请选择年级。');
      return;
    }
    if (!isStudent && !teachingStage) {
      setError('请选择教学学段。');
      return;
    }

    setLoading(true);
    try {
      await userApi.create({
        username: username.trim(),
        password,
        displayName: displayName.trim(),
        role,
        avatarKey,
        gender,
        ...(isStudent
          ? { schoolName: schoolName.trim() || undefined, grade, interestTags: tags }
          : { teachingStage, expertiseTags: tags }),
      });
      setSuccess(true);
      window.setTimeout(() => window.location.assign('/'), 700);
    } catch (registrationError) {
      setError(registrationError instanceof Error ? registrationError.message : '注册失败，请稍后重试。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className={`registration-page registration-page--${isStudent ? 'student' : 'teacher'}`}>
      <header className="registration-page__hero">
        <button type="button" className="registration-page__back" aria-label="返回登录" onClick={() => window.location.assign('/')}>
          <ArrowLeft size={22} weight="bold" />
        </button>
        <div className="registration-page__brand" aria-label="伴读社区，让学习更美好">
          <span className="registration-page__brand-icon" aria-hidden="true">
            <BookOpenText size={34} weight="fill" />
            <Sparkle className="registration-page__brand-sparkle" size={14} weight="fill" />
          </span>
          <span className="registration-page__brand-copy">
            <strong>伴读社区</strong>
            <small>让学习更美好</small>
          </span>
        </div>
        <h1>{isStudent ? '学生注册' : '老师注册'}</h1>
      </header>

      <form className="registration-form" onSubmit={handleSubmit}>
        <input type="hidden" name="role" value={role} />
        <RegistrationInput label="用户名" required value={username} onChange={setUsername} placeholder="请输入用户名" />
        <label className="registration-field">
          <span>密码 <b>*</b></span>
          <span className="registration-password">
            <input type={passwordVisible ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="请设置密码（至少6位）" autoComplete="new-password" />
            <button type="button" aria-label={passwordVisible ? '隐藏密码' : '显示密码'} onClick={() => setPasswordVisible((visible) => !visible)}>
              {passwordVisible ? <EyeSlash size={18} /> : <Eye size={18} />}
            </button>
          </span>
        </label>
        <RegistrationInput label="确认密码" required value={confirmPassword} onChange={setConfirmPassword} placeholder="请再次输入密码" type={passwordVisible ? 'text' : 'password'} />
        <RegistrationInput label="昵称" required value={displayName} onChange={setDisplayName} placeholder="请输入你的昵称" />

        <fieldset className="registration-fieldset">
          <legend>选择头像</legend>
          <div className="registration-avatar-grid">
            {avatars.map((avatar) => {
              const AvatarIcon = AVATAR_ICONS[avatar as keyof typeof AVATAR_ICONS];
              return (
                <button type="button" key={avatar} className={avatarKey === avatar ? 'selected' : ''} onClick={() => setAvatarKey(avatar)} aria-label="选择头像">
                  {AVATAR_IMAGES[avatar] ? <img src={AVATAR_IMAGES[avatar]} alt="" /> : <AvatarIcon size={38} weight={avatarKey === avatar ? 'fill' : 'duotone'} />}
                  {avatarKey === avatar && <span className="registration-avatar-grid__check" aria-hidden="true"><Check size={13} weight="bold" /></span>}
                </button>
              );
            })}
          </div>
        </fieldset>

        <fieldset className="registration-fieldset registration-fieldset--inline">
          <legend>性别</legend>
          <div className="registration-choice-row">
            {['MALE', 'FEMALE'].map((option) => <button type="button" key={option} className={gender === option ? 'selected' : ''} onClick={() => setGender(option)}>{option === 'MALE' ? '男' : '女'}</button>)}
          </div>
        </fieldset>

        {isStudent ? <>
          <RegistrationInput label="学校" value={schoolName} onChange={setSchoolName} placeholder="请输入学校名称" />
          <RegistrationSelect label="年级" required value={grade} onChange={setGrade} options={GRADES} placeholder="请选择年级" />
          <RegistrationTags title="标签" hint="选择你感兴趣的学习内容（可多选）" options={INTERESTS} selected={tags} onToggle={toggleTag} />
        </> : <>
          <RegistrationSelect label="教学学段" required value={teachingStage} onChange={setTeachingStage} options={STAGES} placeholder="请选择教学学段" />
          <RegistrationTags title="擅长领域" options={EXPERTISE} selected={tags} onToggle={toggleTag} />
        </>}

        {error && <p className="registration-form__error" role="alert">{error}</p>}
        {success && <p className="registration-form__success">注册成功，正在返回登录页...</p>}
        <div className="registration-actions">
          <button type="submit" className="registration-actions__submit" disabled={loading || success}>{loading ? '注册中...' : '注册'}</button>
          <button type="button" className="registration-actions__cancel" onClick={() => window.location.assign('/')} disabled={loading}>取消</button>
        </div>
        <p className="registration-form__footer">
          <button type="button" onClick={() => window.location.assign(isStudent ? '/register/teacher' : '/register/student')}>{isStudent ? '我是老师？去老师注册' : '我是学生？去学生注册'}</button>
          <span>·</span>
          <button type="button" onClick={() => window.location.assign('/')}>已有账号？直接登录</button>
        </p>
      </form>
    </main>
  );
}

function RegistrationInput({ label, required, value, onChange, placeholder, type = 'text' }: { label: string; required?: boolean; value: string; onChange: (value: string) => void; placeholder: string; type?: string }) {
  return <label className="registration-field"><span>{label} {required && <b>*</b>}</span><input type={type} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} /></label>;
}

function RegistrationSelect({ label, required, value, onChange, options, placeholder }: { label: string; required?: boolean; value: string; onChange: (value: string) => void; options: string[]; placeholder: string }) {
  return <label className="registration-field"><span>{label} {required && <b>*</b>}</span><select value={value} onChange={(event) => onChange(event.target.value)}><option value="">{placeholder}</option>{options.map((option) => <option key={option} value={option}>{displayOption(option)}</option>)}</select></label>;
}

function RegistrationTags({ title, hint, options, selected, onToggle }: { title: string; hint?: string; options: string[]; selected: string[]; onToggle: (value: string) => void }) {
  return <fieldset className="registration-fieldset"><legend>{title}</legend>{hint && <p>{hint}</p>}<div className="registration-tags">{options.map((option) => <button type="button" key={option} className={selected.includes(option) ? 'selected' : ''} onClick={() => onToggle(option)}>{option}</button>)}</div></fieldset>;
}
