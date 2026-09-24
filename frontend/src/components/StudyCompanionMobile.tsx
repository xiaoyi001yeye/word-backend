import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, BookOpenText, Chat, Dog, GraduationCap, IdentificationCard, ImageSquare, PencilSimple, Play, QrCode, Rabbit, SignIn, Smiley, Sparkle, Star, ThumbsUp, Trash, UsersThree } from '@phosphor-icons/react';
import { classroomApi, qrApi, studyCompanionApi } from '../api';
import type { Classroom, ClassroomGroupFeedMessage, QrPageSetting, User, VideoAccessResponse } from '../types';

type MobilePage = 'home' | 'login' | 'community' | 'manage' | 'detail' | 'edit' | 'qr';

interface StudyCompanionMobileProps {
  page: MobilePage;
  user?: User | null;
  classroomId?: number;
  loginLoading?: boolean;
  loginError?: string | null;
  onLogin?: (username: string, password: string) => Promise<void>;
  onBack?: () => void;
}

const TAG_LABELS: Record<string, string> = {
  ENGLISH: '英语',
  MATH: '数学',
  READING: '阅读',
  PROGRAMMING: '编程',
  DAILY_CHECKIN: '每日打卡',
  CLASS_TEACHER: '班主任',
  HIGH_SCHOOL_PREP: '高考备考',
  VOCABULARY: '词汇',
  GROWTH: '成长',
};

const COMPANION_TAG_OPTIONS = [
  { value: 'ENGLISH', label: '英语' },
  { value: 'HIGH_SCHOOL_PREP', label: '高考备考' },
  { value: 'VOCABULARY', label: '词汇' },
  { value: 'READING', label: '阅读' },
  { value: 'GROWTH', label: '成长' },
];

const CLASSROOM_PAGE_SIZE = 10;

const TEACHER_AVATAR_IMAGES: Record<string, string> = {
  'teacher-book': '/avatars/teacher-male-1.png',
  'teacher-glasses': '/avatars/teacher-female-1.png',
  'teacher-lamp': '/avatars/teacher-female-2.png',
  'teacher-tree': '/avatars/teacher-male-2.png',
  'teacher-male-3': '/avatars/teacher-male-3.png',
  'teacher-female-3': '/avatars/teacher-female-3.png',
};

const AVATAR_FALLBACK_ICONS: Record<string, typeof Smiley> = {
  'student-panda': Smiley,
  'student-bear': Dog,
  'student-cat': Smiley,
  'student-rabbit': Rabbit,
  'student-fox': Dog,
  'student-koala': Star,
};

function go(path: string) {
  window.location.assign(path);
}

function tagLabel(tag: string) {
  return TAG_LABELS[tag] ?? tag;
}

function MobileHeader({ title, onBack, action }: { title: string; onBack?: () => void; action?: { label: string; path: string } }) {
  return (
    <header className="companion-mobile__header">
      <button type="button" className="companion-mobile__back" aria-label="返回" onClick={onBack ?? (() => window.history.back())}>
        <ArrowLeft size={23} weight="bold" />
      </button>
      <h1>{title}</h1>
      {action && <button type="button" className="companion-mobile__header-action" onClick={() => go(action.path)}>{action.label}<ArrowRight size={18} weight="bold" /></button>}
      <div className="companion-mobile__brand" aria-label="伴读社区，让学习更美好">
        <UsersThree size={25} weight="fill" />
        <span><strong>伴读社区</strong><small>让学习更美好</small></span>
      </div>
    </header>
  );
}

function MobileLayout({ title, children, onBack, action }: { title: string; children: React.ReactNode; onBack?: () => void; action?: { label: string; path: string } }) {
  return (
    <main className="companion-mobile">
      <MobileHeader title={title} onBack={onBack} action={action} />
      <section className="companion-mobile__content">{children}</section>
    </main>
  );
}

function HomePage() {
  const entries = [
    { className: 'companion-entry-card--student', icon: GraduationCap, title: '学生注册', subtitle: '填写基础资料', path: '/register/student' },
    { className: 'companion-entry-card--teacher', icon: IdentificationCard, title: '老师注册', subtitle: '认证老师身份', path: '/register/teacher' },
    { className: 'companion-entry-card--login', icon: SignIn, title: '登录', subtitle: '已有账号\n立即进入', path: '/login' },
    { className: 'companion-entry-card--qr', icon: QrCode, title: '二维码共享', subtitle: '扫码打开伴读社区', path: '/qr' },
  ];
  return (
    <main className="companion-mobile companion-home-page">
      <section className="companion-home-hero" aria-labelledby="companion-home-title">
        <div className="companion-home-hero__copy">
          <h1 id="companion-home-title">伴读社区</h1>
        </div>
        <p className="companion-home-hero__motto">
          <span>让学习</span>
          <span>更简单更美好</span>
        </p>
      </section>
      <section className="companion-mobile__content companion-home-page__content">
        <section className="companion-home__entries" aria-label="注册与登录入口">
          {entries.map(({ className, icon: Icon, title, subtitle, path }) => (
            <button type="button" key={path} className={`companion-entry-card ${className}`} onClick={() => go(path)}>
              <span className="companion-entry-card__icon"><Icon size={30} weight="bold" /></span>
              <span className="companion-entry-card__copy"><strong>{title}</strong><small>{subtitle}</small></span>
              <ArrowRight className="companion-entry-card__arrow" size={22} weight="bold" />
            </button>
          ))}
        </section>
      </section>
    </main>
  );
}

function LoginPage({ loading, error, onLogin }: { loading?: boolean; error?: string | null; onLogin: (username: string, password: string) => Promise<void> }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  return (
    <MobileLayout title="登录" onBack={() => go('/')}>
      <div className="companion-login-welcome">
        <div className="companion-login-welcome__icon"><BookOpenText size={76} weight="fill" /><Sparkle className="companion-login-welcome__sparkle" size={27} weight="fill" /></div>
        <h2>欢迎回来</h2>
        <p>登录后继续你的伴读计划</p>
      </div>
      <form className="companion-form" onSubmit={(event) => { event.preventDefault(); void onLogin(username.trim(), password); }}>
        <label><span>用户名 <b>*</b></span><input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="请输入用户名" autoComplete="username" disabled={loading} /></label>
        <label><span>密码 <b>*</b></span><input value={password} onChange={(event) => setPassword(event.target.value)} type="password" placeholder="请输入密码" autoComplete="current-password" disabled={loading} /></label>
        {error && <p className="companion-form__error">{error}</p>}
        <button className="companion-button companion-button--primary" type="submit" disabled={loading}>{loading ? '登录中...' : '登录'}</button>
        <div className="companion-form__links"><button type="button" onClick={() => go('/register/student')}>学生注册</button><button type="button" onClick={() => go('/register/teacher')}>老师注册</button></div>
      </form>
    </MobileLayout>
  );
}

function ClassroomCard({ classroom, manage, onSelect, onDelete }: { classroom: Classroom; manage?: boolean; onSelect?: (classroom: Classroom) => void; onDelete?: (classroom: Classroom) => Promise<void> }) {
  const coverUrl = classroom.companionImageUrls?.[0];
  const teacherAvatarUrl = classroom.teacherAvatarKey
    ? TEACHER_AVATAR_IMAGES[classroom.teacherAvatarKey] ?? `/avatars/${classroom.teacherAvatarKey}.png`
    : undefined;
  return (
    <article className="companion-class-card" onClick={manage ? () => onSelect?.(classroom) : undefined}>
      <div className="companion-class-card__cover">{coverUrl ? <img src={coverUrl} alt={`${classroom.name}介绍图片`} /> : <UsersThree size={34} weight="fill" />}</div>
      <div className="companion-class-card__body">
        <div className="companion-class-card__heading"><button type="button" className="companion-class-card__title" onClick={() => go(`/community/${classroom.id}`)}><h3>{classroom.name}</h3></button>{manage && <span>{classroom.studentCount} 名学生</span>}</div>
        <div className="companion-class-card__meta"><div className="companion-tags">{(classroom.companionTags ?? []).map((tag) => <span key={tag}>{tagLabel(tag)}</span>)}</div>{manage ? <span className="companion-card-actions"><button type="button" onClick={(event) => { event.stopPropagation(); onSelect?.(classroom); }}><PencilSimple size={16} />编辑</button><button type="button" className="companion-card-actions__danger" onClick={(event) => { event.stopPropagation(); void onDelete?.(classroom); }}><Trash size={16} />删除</button></span> : <span className="companion-class-card__stats"><span><Chat size={18} />{classroom.commentCount ?? 0}</span><span><ThumbsUp size={18} weight="fill" />{classroom.likeCount ?? 0}</span></span>}</div>
        <div className="companion-class-card__teacher">{teacherAvatarUrl ? <img src={teacherAvatarUrl} alt="" /> : <UsersThree size={20} weight="fill" />}<span>{classroom.teacherName || '老师'}</span>{(classroom.teacherExpertiseTags ?? []).length > 0 && <><i aria-hidden="true">|</i><span>{classroom.teacherExpertiseTags?.map(tagLabel).join('、')}</span></>}</div>
        <p>{classroom.description || '老师还没有填写班级介绍。'}</p>
      </div>
    </article>
  );
}

function ClassroomListPage({ user, manage = false }: { user: User; manage?: boolean }) {
  const [classrooms, setClassrooms] = useState<Classroom[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newName, setNewName] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [newTags, setNewTags] = useState<string[]>([]);
  const [newImageUrl, setNewImageUrl] = useState('');
  const [newVideoId, setNewVideoId] = useState('');
  const [selectedClassroomId, setSelectedClassroomId] = useState<number | null>(null);
  const [videoMessages, setVideoMessages] = useState<ClassroomGroupFeedMessage[]>([]);
  const [formLoading, setFormLoading] = useState(false);
  const [imageUploading, setImageUploading] = useState(false);
  const imageInputRef = useRef<HTMLInputElement>(null);

  const load = async () => {
    setLoading(true);
    try {
      const result = manage
        ? await classroomApi.getPage(page, CLASSROOM_PAGE_SIZE)
        : await classroomApi.getCommunityPage(page, CLASSROOM_PAGE_SIZE);
      setClassrooms(result.content);
      setTotalPages(Math.max(result.totalPages, 1));
      setTotalElements(result.totalElements);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '加载伴读班级失败');
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => { void load(); }, [user.id, user.role, page]);

  useEffect(() => {
    if (!manage || selectedClassroomId === null) {
      setVideoMessages([]);
      setNewName(''); setNewDescription(''); setNewTags([]); setNewImageUrl(''); setNewVideoId('');
      return;
    }
    setFormLoading(true);
    void Promise.all([classroomApi.getById(selectedClassroomId), studyCompanionApi.listMessages(selectedClassroomId, 1, 100, 'VIDEO')])
      .then(([classroom, feed]) => {
        setNewName(classroom.name);
        setNewDescription(classroom.description ?? '');
        setNewTags(classroom.companionTags ?? []);
        setNewImageUrl(classroom.companionImageUrls?.[0] ?? '');
        setNewVideoId(classroom.companionVideoId ? String(classroom.companionVideoId) : '');
        setVideoMessages(feed.content.filter((message) => message.messageType === 'VIDEO' && message.resourceId));
        setError(null);
      })
      .catch((cause) => setError(cause instanceof Error ? cause.message : '加载伴读信息失败'))
      .finally(() => setFormLoading(false));
  }, [manage, selectedClassroomId]);

  const saveClassroom = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!newName.trim()) return;
    const payload = {
      name: newName.trim(),
      description: newDescription.trim() || undefined,
      companionTags: newTags,
      companionImageUrls: newImageUrl.trim() ? [newImageUrl.trim()] : [],
      companionVideoId: newVideoId ? Number(newVideoId) : null,
    };
    try {
      const saved = selectedClassroomId === null ? await classroomApi.create(payload) : await classroomApi.update(selectedClassroomId, payload);
      setSelectedClassroomId(saved.id);
      setPage(1);
      await load();
    } catch (cause) { setError(cause instanceof Error ? cause.message : selectedClassroomId === null ? '创建伴读班级失败' : '保存伴读班级失败'); }
  };

  const startCreate = () => setSelectedClassroomId(null);

  const uploadImage = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    const supportedImageTypes = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp']);
    const supportedImageExtensions = /\.(jpe?g|png|gif|webp)$/i;
    const imageTooLarge = file.size > 5 * 1024 * 1024;
    if ((!supportedImageTypes.has(file.type) && !supportedImageExtensions.test(file.name)) || imageTooLarge) {
      setError('系统只支持 JPG、PNG、GIF、WebP，最大 5MB图片');
      return;
    }
    setImageUploading(true);
    try {
      const uploaded = await classroomApi.uploadCompanionImage(file);
      setNewImageUrl(uploaded.url);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '上传班级介绍图片失败');
    } finally {
      setImageUploading(false);
    }
  };

  const deleteClassroom = async (classroom: Classroom) => {
    if (!window.confirm(`确定删除伴读班级“${classroom.name}”吗？`)) return;
    try { await classroomApi.deleteById(classroom.id); setPage(1); await load(); } catch (cause) { setError(cause instanceof Error ? cause.message : '删除伴读班级失败'); }
  };

  if (manage && user.role === 'STUDENT') {
    return <MobileLayout title="伴读管理" onBack={() => go('/community')}><div className="companion-empty"><UsersThree size={42} /><p>你没有伴读管理权限</p><button type="button" className="companion-button companion-button--primary" onClick={() => go('/community')}>返回伴读社区</button></div></MobileLayout>;
  }

  return (
    <MobileLayout
      title={manage ? '伴读管理' : '伴读社区'}
      onBack={() => go('/')}
      action={!manage && user.role === 'TEACHER' ? { label: '伴读管理', path: '/community/manage' } : undefined}
    >
      {!manage && <div className="companion-community-intro"><BookOpenText size={32} weight="fill" /><h2>伴读社区陪伴你每一天</h2></div>}
      {manage && <form className="companion-inline-form companion-create-form" onSubmit={saveClassroom}>
        <label><span>伴读班级名称 <b>*</b></span><input value={newName} onChange={(event) => setNewName(event.target.value)} placeholder="请输入伴读班级名称，如：古诗伴读" required /></label>
        <label><span>班级视频</span><select value={newVideoId} onChange={(event) => setNewVideoId(event.target.value)} disabled={formLoading || selectedClassroomId === null}><option value="">请选择视频</option>{videoMessages.map((message) => <option key={message.id} value={message.resourceId ?? ''}>{message.resourceTitle || `视频 ${message.resourceId}`}</option>)}</select><small>{selectedClassroomId === null ? '新建伴读后，选择该班级聊天中发布的视频。' : '视频选项来自该班级聊天中发布的视频。'}</small></label>
        <div className="companion-image-picker"><span>班级介绍</span><div className="companion-image-picker__controls"><div className="companion-image-picker__preview">{newImageUrl ? <img src={newImageUrl} alt="班级介绍预览" /> : <><ImageSquare size={34} /><small>点击上传图片</small></>}</div><button className="companion-button" type="button" onClick={() => imageInputRef.current?.click()} disabled={imageUploading}><ImageSquare size={20} />{imageUploading ? '上传中...' : '选择图片'}</button><input ref={imageInputRef} className="companion-image-picker__input" type="file" accept="image/png,image/jpeg,image/gif,image/webp" onChange={uploadImage} /></div></div>
        <label><span>伴读班级介绍 <b>*</b></span><textarea value={newDescription} onChange={(event) => setNewDescription(event.target.value)} placeholder="请输入伴读班级介绍" rows={4} maxLength={500} required /><small>{newDescription.length}/500</small></label>
        <div className="companion-tag-picker"><span>班级伴读标签</span><small>可多选</small><div className="companion-tag-picker__options">{COMPANION_TAG_OPTIONS.map((option) => <button key={option.value} type="button" className={newTags.includes(option.value) ? 'is-selected' : ''} onClick={() => setNewTags((current) => current.includes(option.value) ? current.filter((tag) => tag !== option.value) : [...current, option.value])}>{option.label}</button>)}</div></div>
        <div className="companion-inline-form__actions"><button className="companion-button companion-button--primary" type="submit" disabled={formLoading}>{selectedClassroomId === null ? '创建' : '保存'}</button>{selectedClassroomId !== null && <button className="companion-button" type="button" onClick={startCreate} disabled={formLoading}>新建</button>}</div>
      </form>}
      {loading && <p className="companion-empty">正在加载伴读班级...</p>}
      {error && <p className="companion-form__error">{error}</p>}
      {!loading && !error && classrooms.length === 0 && <div className="companion-empty"><UsersThree size={42} /><p>还没有可查看的伴读班级</p><small>{manage ? '点击右上角加号创建一个伴读班级。' : '加入班级后，就可以开始伴读学习。'}</small></div>}
      {manage && !loading && !error && <div className="companion-list-heading"><span><UsersThree size={24} weight="fill" /><h2>伴读社区</h2></span><strong>共 {totalElements} 班</strong></div>}
      <div className="companion-class-list">{classrooms.map((classroom) => <ClassroomCard key={classroom.id} classroom={classroom} manage={manage} onSelect={manage ? (selected) => setSelectedClassroomId(selected.id) : undefined} onDelete={deleteClassroom} />)}</div>
      {totalPages > 1 && <nav className="companion-pagination" aria-label="伴读班级分页">
        <button type="button" disabled={loading || page <= 1} onClick={() => setPage((current) => current - 1)}>上一页</button>
        <span>第 {page} / {totalPages} 页{!manage && totalElements > 0 ? ` · 共 ${totalElements} 个伴读班级` : ''}</span>
        <button type="button" disabled={loading || page >= totalPages} onClick={() => setPage((current) => current + 1)}>下一页</button>
      </nav>}
    </MobileLayout>
  );
}

function avatarUrl(avatarKey?: string | null) {
  return avatarKey ? TEACHER_AVATAR_IMAGES[avatarKey] : undefined;
}

function Avatar({ avatarKey, size = 32 }: { avatarKey?: string | null; size?: number }) {
  const image = avatarUrl(avatarKey);
  if (image) return <img src={image} alt="" />;
  const Icon = (avatarKey && AVATAR_FALLBACK_ICONS[avatarKey]) || UsersThree;
  return <Icon size={size} weight="fill" />;
}

function formatCommentTime(value?: string | null) {
  if (!value) return '';
  const elapsed = Math.max(0, Date.now() - new Date(value).getTime());
  if (elapsed < 60 * 60 * 1000) return `${Math.max(1, Math.floor(elapsed / (60 * 1000)))} 分钟前`;
  if (elapsed < 24 * 60 * 60 * 1000) return `${Math.floor(elapsed / (60 * 60 * 1000))} 小时前`;
  return `${Math.floor(elapsed / (24 * 60 * 60 * 1000))} 天前`;
}

function DetailPage({ classroomId, user }: { classroomId: number; user: User }) {
  const [classroom, setClassroom] = useState<Classroom | null>(null);
  const [messages, setMessages] = useState<ClassroomGroupFeedMessage[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [commentText, setCommentText] = useState('');
  const [commentSending, setCommentSending] = useState(false);
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const [videoAccess, setVideoAccess] = useState<VideoAccessResponse | null>(null);
  const [videoLoading, setVideoLoading] = useState(false);
  const [videoError, setVideoError] = useState<string | null>(null);
  useEffect(() => { void Promise.all([classroomApi.getCommunityById(classroomId), studyCompanionApi.listMessages(classroomId)]).then(([nextClassroom, feed]) => { setClassroom(nextClassroom); setMessages(feed.content); setLiked(Boolean(nextClassroom.likedByCurrentUser)); setLikeCount(nextClassroom.likeCount ?? 0); }).catch((cause) => setError(cause instanceof Error ? cause.message : '加载伴读详情失败')); }, [classroomId]);
  if (error) return <MobileLayout title="伴读详情"><p className="companion-form__error">{error}</p></MobileLayout>;
  if (!classroom) return <MobileLayout title="伴读详情"><p className="companion-empty">正在加载伴读详情...</p></MobileLayout>;
  const videos = messages.filter((message) => message.messageType === 'VIDEO' && message.resourceId);
  const comments = messages.filter((message) => message.messageType === 'TEXT' && message.content?.trim());
  const commentCount = Math.max(classroom.commentCount ?? 0, comments.length);
  const teacherAvatar = avatarUrl(classroom.teacherAvatarKey);
  const likerCandidates = [...(classroom.likeUserAvatarKeys ?? []), ...(liked && !(classroom.likeUserAvatarKeys ?? []).includes(user.avatarKey ?? '') ? [user.avatarKey] : [])].filter(Boolean) as string[];
  const likerAvatars = likerCandidates.slice(0, Math.min(likeCount, 6));
  const submitComment = async (event: React.FormEvent) => {
    event.preventDefault();
    const content = commentText.trim();
    if (!content || commentSending) return;
    setCommentSending(true);
    try {
      const created = await studyCompanionApi.createComment(classroomId, content);
      setMessages((current) => [created, ...current]);
      setCommentText('');
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '发表评论失败');
    } finally {
      setCommentSending(false);
    }
  };
  const playCompanionVideo = async () => {
    const videoId = videos[0]?.resourceId;
    if (!videoId || videoLoading || videoAccess) return;
    setVideoLoading(true);
    setVideoError(null);
    try {
      setVideoAccess(await studyCompanionApi.playVideo(classroomId, videoId));
    } catch (cause) {
      setVideoError(cause instanceof Error ? cause.message : '获取视频播放地址失败');
    } finally {
      setVideoLoading(false);
    }
  };
  const likeCompanion = async () => {
    if (liked) return;
    try {
      const updated = await classroomApi.likeCommunity(classroomId);
      setClassroom(updated);
      setLiked(Boolean(updated.likedByCurrentUser));
      setLikeCount(updated.likeCount ?? 0);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '点赞失败');
    }
  };
  const deleteComment = async (messageId: number) => {
    try {
      await studyCompanionApi.deleteComment(classroomId, messageId);
      setMessages((current) => current.filter((message) => message.id !== messageId));
      setClassroom((current) => current ? { ...current, commentCount: Math.max(0, (current.commentCount ?? 0) - 1) } : current);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '删除评论失败');
    }
  };
  const canDeleteComment = classroom.teacherId === user.id;
  return <MobileLayout title={classroom.name} onBack={() => go('/community')}>
    <section className="companion-detail__hero-card">
      <div className="companion-detail__teacher">{teacherAvatar ? <img src={teacherAvatar} alt="" /> : <UsersThree size={36} weight="fill" />}<strong>{classroom.teacherName || '老师'}</strong><i>|</i><span>{(classroom.teacherExpertiseTags ?? []).map(tagLabel).join('、') || '伴读老师'}</span></div>
      {videos.length > 0 ? (videoAccess ? <div className="companion-detail__video-player"><video controls autoPlay playsInline poster={videoAccess.coverUrl ?? undefined} src={videoAccess.url} /><span>{videos[0].resourceTitle || '班级伴读视频'}</span></div> : <button type="button" className="companion-detail__video-placeholder" onClick={() => void playCompanionVideo()} disabled={videoLoading}><span>{videos[0].resourceTitle || '班级伴读视频'}</span>{videoLoading ? <strong>正在加载视频...</strong> : <span className="companion-detail__video-play"><Play size={28} weight="fill" /></span>}{videoError && <small>{videoError}</small>}</button>) : <div className="companion-detail__video-placeholder is-empty">班级负责人还没有分享视频</div>}
      {(classroom.companionImageUrls ?? []).map((url) => <img key={url} className="companion-detail__image" src={url} alt={`${classroom.name}介绍`} />)}
      <p className="companion-detail__description">{classroom.description || '暂无班级介绍'}</p>
    </section>
    <section className="companion-detail__social"><div className="companion-detail__social-top"><span><Chat size={25} weight="fill" /> <strong>{commentCount}</strong> 评论</span><button type="button" className={liked ? 'is-liked' : ''} onClick={() => void likeCompanion()}><ThumbsUp size={28} weight="fill" /> <strong>{likeCount}</strong> 点赞</button></div><div className="companion-detail__likers">{likerAvatars.map((key, index) => <span className="companion-detail__liker-avatar" key={`${key}-${index}`}><Avatar avatarKey={key} size={24} /></span>)}<span>等 {likeCount} 人点赞</span></div></section>
    <section className="companion-detail__comments"><div className="companion-detail__comments-heading"><h2>全部评论</h2><span>按时间倒序⌄</span></div>{comments.length === 0 ? <p className="companion-muted">还没有评论，来写下第一条吧。</p> : comments.map((comment) => <article className="companion-detail__comment" key={comment.id}><span className="companion-detail__comment-avatar"><Avatar avatarKey={comment.authorAvatarKey} size={32} /></span><div><div className="companion-detail__comment-author"><strong>{comment.authorName || '伴读同学'}</strong>{canDeleteComment && <button type="button" onClick={() => void deleteComment(comment.id)}>删除</button>}</div><p>{comment.content}</p><small>{formatCommentTime(comment.createdAt)}</small></div></article>)}</section>
    <form className="companion-detail__comment-form" onSubmit={submitComment}><span className="companion-detail__comment-form-avatar"><Avatar avatarKey={user.avatarKey} size={30} /></span><input value={commentText} onChange={(event) => setCommentText(event.target.value)} placeholder="写下你的想法..." maxLength={500} /><button type="submit" disabled={commentSending || !commentText.trim()}>{commentSending ? '发送中' : '发送'}</button></form>
  </MobileLayout>;
}

function EditPage({ classroomId }: { classroomId: number }) {
  const [classroom, setClassroom] = useState<Classroom | null>(null);
  const [name, setName] = useState(''); const [description, setDescription] = useState(''); const [tags, setTags] = useState(''); const [images, setImages] = useState(''); const [videoId, setVideoId] = useState(''); const [feedback, setFeedback] = useState<string | null>(null);
  useEffect(() => { void classroomApi.getById(classroomId).then((value) => { setClassroom(value); setName(value.name); setDescription(value.description ?? ''); setTags((value.companionTags ?? []).join(', ')); setImages((value.companionImageUrls ?? []).join('\n')); setVideoId(value.companionVideoId ? String(value.companionVideoId) : ''); }).catch(() => setFeedback('加载伴读班级失败')); }, [classroomId]);
  if (!classroom) return <MobileLayout title="编辑伴读"><p className="companion-empty">{feedback || '正在加载...'}</p></MobileLayout>;
  const save = async (event: React.FormEvent) => { event.preventDefault(); try { await classroomApi.update(classroomId, { name: name.trim(), description: description.trim(), companionTags: tags.split(/[,，]/).map((item) => item.trim()).filter(Boolean), companionImageUrls: images.split(/\r?\n/).map((item) => item.trim()).filter(Boolean), companionVideoId: videoId ? Number(videoId) : null }); go(`/community/${classroomId}`); } catch (cause) { setFeedback(cause instanceof Error ? cause.message : '保存失败'); } };
  return <MobileLayout title="编辑伴读" onBack={() => go(`/community/${classroomId}`)}><form className="companion-form" onSubmit={save}><label><span>伴读班级名称 <b>*</b></span><input value={name} onChange={(event) => setName(event.target.value)} required /></label><label><span>伴读班级介绍</span><textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={5} placeholder="介绍班级的学习氛围和计划" /></label><label><span>班级介绍图片</span><textarea value={images} onChange={(event) => setImages(event.target.value)} rows={3} placeholder="每行填写一张图片地址" /></label><label><span>班级伴读标签</span><input value={tags} onChange={(event) => setTags(event.target.value)} placeholder="例如：英语，阅读，每日打卡" /></label><label><span>班级主视频 ID</span><input value={videoId} onChange={(event) => setVideoId(event.target.value)} inputMode="numeric" placeholder="可选" /></label>{feedback && <p className="companion-form__error">{feedback}</p>}<button className="companion-button companion-button--primary" type="submit">保存伴读资料</button></form></MobileLayout>;
}

function QrPage({ user }: { user?: User | null }) {
  const [settings, setSettings] = useState<QrPageSetting | null>(null);
  const [title, setTitle] = useState('伴读社区');
  const [backgroundImageUrl, setBackgroundImageUrl] = useState('');
  const [qrImageUrl, setQrImageUrl] = useState('');
  const [feedback, setFeedback] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const backgroundInputRef = useRef<HTMLInputElement>(null);
  const qrImageInputRef = useRef<HTMLInputElement>(null);
  const isAdmin = user?.role === 'ADMIN';

  useEffect(() => {
    void qrApi.get().then((value) => {
      setSettings(value);
      setTitle(value.title);
      setBackgroundImageUrl(value.backgroundImageUrl ?? '');
      setQrImageUrl(value.qrImageUrl ?? '');
    }).catch(() => setFeedback('二维码配置加载失败，请刷新重试'));
  }, []);

  const save = async () => {
    setSaving(true);
    setFeedback(null);
    try {
      const value = await qrApi.update({
        title: title.trim(),
        backgroundImageUrl: backgroundImageUrl.trim() || null,
        qrImageUrl: qrImageUrl.trim() || null,
      });
      setSettings(value);
      setTitle(value.title);
      setBackgroundImageUrl(value.backgroundImageUrl ?? '');
      setQrImageUrl(value.qrImageUrl ?? '');
      setFeedback('保存成功');
    } catch (cause) {
      setFeedback(cause instanceof Error ? cause.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const uploadImage = async (
    event: React.ChangeEvent<HTMLInputElement>,
    setImageUrl: (url: string) => void,
    errorMessage: string,
  ) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    const supported = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp']);
    if (!supported.has(file.type) || file.size > 5 * 1024 * 1024) {
      setFeedback('系统只支持 JPG、PNG、GIF、WebP，最大 5MB图片');
      return;
    }
    setUploading(true);
    setFeedback(null);
    try {
      const uploaded = await classroomApi.uploadCompanionImage(file);
      setImageUrl(uploaded.url);
    } catch (cause) {
      setFeedback(cause instanceof Error ? cause.message : errorMessage);
    } finally {
      setUploading(false);
    }
  };

  if (!settings) return <main className="companion-mobile companion-qr-page"><p className="companion-empty">{feedback || '正在加载...'}</p></main>;
  const background = backgroundImageUrl || '/qr-background.png';
  return <main className="companion-mobile companion-qr-page"><section className="companion-mobile__content"><div className="companion-qr" style={{ backgroundImage: `linear-gradient(rgba(255,255,255,.18), rgba(255,255,255,.18)), url(${background})` }}><button type="button" className="companion-qr__back" aria-label="返回" onClick={() => go('/')}><ArrowLeft size={23} weight="bold" /></button><h1 className="companion-qr__title">{title}</h1><div className="companion-qr__toolbar">{isAdmin && <button type="button" className="companion-button" onClick={() => void save()} disabled={saving || uploading}>{saving ? '保存中...' : '保存'}</button>}</div><img className="companion-qr__image" src={qrImageUrl || settings.qrDataUrl} alt={`扫描进入${title}`} />{isAdmin && <div className="companion-qr__editor"><label><span>伴读社区文本</span><input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={100} /></label><label><span>背景图</span><button type="button" className="companion-button" onClick={() => backgroundInputRef.current?.click()} disabled={uploading}>{uploading ? '上传中...' : '更换背景图'}</button><input ref={backgroundInputRef} className="companion-image-picker__input" type="file" accept="image/png,image/jpeg,image/gif,image/webp" onChange={(event) => void uploadImage(event, setBackgroundImageUrl, '背景图上传失败')} /></label><label><span>二维码图片</span><button type="button" className="companion-button" onClick={() => qrImageInputRef.current?.click()} disabled={uploading}>{uploading ? '上传中...' : '更换二维码图片'}</button><input ref={qrImageInputRef} className="companion-image-picker__input" type="file" accept="image/png,image/jpeg,image/gif,image/webp" onChange={(event) => void uploadImage(event, setQrImageUrl, '二维码图片上传失败')} /></label>{feedback && <p className="companion-form__error">{feedback}</p>}</div>}</div></section></main>;
}

export function StudyCompanionMobile({ page, user, classroomId, loginLoading, loginError, onLogin }: StudyCompanionMobileProps) {
  if (page === 'home') return <HomePage />;
  if (page === 'login' && onLogin) return <LoginPage loading={loginLoading} error={loginError} onLogin={onLogin} />;
  if (page === 'qr') return <QrPage user={user} />;
  if (!user) return null;
  if (page === 'community') return <ClassroomListPage user={user} />;
  if (page === 'manage') return <ClassroomListPage user={user} manage />;
  if (page === 'detail' && classroomId) return <DetailPage classroomId={classroomId} user={user} />;
  if (page === 'edit' && classroomId) return <EditPage classroomId={classroomId} />;
  return <HomePage />;
}
