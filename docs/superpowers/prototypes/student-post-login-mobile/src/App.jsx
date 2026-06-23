import { useEffect, useMemo, useRef, useState } from 'react';
import {
  BookOpen,
  Books,
  CalendarCheck,
  CalendarDots,
  CaretRight,
  ChartBar,
  CheckCircle,
  Clock,
  Exam,
  House,
  LockKey,
  Notebook,
  PencilSimple,
  PlayCircle,
  SealQuestion,
  ShieldCheck,
  Star,
  Student,
  Target,
  UserCircle,
  XCircle,
} from '@phosphor-icons/react';

const quickEntries = [
  { id: 'library', label: '我的词书', helper: '8 本可学', icon: Books },
  { id: 'self-test', label: '自测练习', helper: '按词书生成', icon: PencilSimple },
  { id: 'wrong', label: '错词本', helper: '18 个待巩固', icon: XCircle },
  { id: 'favorite', label: '生词收藏', helper: '9 个重点词', icon: Star },
  { id: 'stats', label: '学习统计', helper: '30 天趋势', icon: ChartBar },
  { id: 'profile', label: '个人中心', helper: '资料与密码', icon: UserCircle },
];

const taskRows = [
  { label: '逾期复习', value: 3, tone: 'danger', icon: Clock },
  { label: '今日复习', value: 5, tone: 'blue', icon: CalendarCheck },
  { label: '新词', value: 6, tone: 'green', icon: BookOpen },
];

const upcomingWords = [
  { word: 'resilient', phonetic: "/rɪ'zɪliənt/", type: '逾期复习', box: 'Box 2' },
  { word: 'persistent', phonetic: "/pər'sɪstənt/", type: '今日复习', box: 'Box 3' },
  { word: 'adaptable', phonetic: "/ə'dæptəbl/", type: '新词', box: 'Box 0' },
];

const dictionaries = [
  { name: 'CET-4 核心词汇', words: 1680, progress: 57, due: 12 },
  { name: '高中英语必修二', words: 742, progress: 81, due: 4 },
  { name: '雅思基础词库', words: 1260, progress: 33, due: 18 },
];

const memoryWords = [
  { word: 'ambiguous', text: '模棱两可的', wrong: 5, favorite: true },
  { word: 'fragile', text: '脆弱的；易碎的', wrong: 4, favorite: false },
  { word: 'allocate', text: '分配；拨出', wrong: 3, favorite: true },
];

const calendarDays = [
  'done', 'done', 'free', 'missed', 'done', 'active', 'idle',
  'done', 'done', 'done', 'free', 'active', 'active', 'idle',
  'done', 'free', 'done', 'done', 'done', 'idle', 'missed',
  'done', 'active', 'done', 'idle', 'free', 'done', 'active',
  'today', 'idle',
];

const tabs = [
  { id: 'home', label: '首页', icon: House },
  { id: 'study', label: '学习', icon: BookOpen },
  { id: 'library', label: '词库', icon: Notebook },
  { id: 'profile', label: '我的', icon: UserCircle },
];

function ProgressRing({ value }) {
  return (
    <div className="progress-ring" style={{ '--progress': `${value * 3.6}deg` }}>
      <div className="progress-ring__inner">
        <strong>{value}%</strong>
        <span>完成进度</span>
      </div>
    </div>
  );
}

function SectionHeader({ eyebrow, title, action }) {
  return (
    <div className="section-header">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      {action}
    </div>
  );
}

function StatusPill({ children, tone = 'blue' }) {
  return <span className={`status-pill status-pill--${tone}`}>{children}</span>;
}

function HomeScreen({ onNavigate }) {
  return (
    <>
      <section className="hero">
        <div className="hero__topline">
          <div>
            <p className="eyebrow">Student Workspace</p>
            <h1>早上好，林同学</h1>
          </div>
          <span className="role-chip">STUDENT</span>
        </div>
        <p className="quote">日拱一卒，功不唐捐。</p>
      </section>

      <section className="mission">
        <div className="mission__summary">
          <div>
            <p className="eyebrow">Today</p>
            <h2>今日任务</h2>
          </div>
          <div className="mission__complete">
            <strong>8<span>/14</span></strong>
            <small>已完成</small>
          </div>
        </div>

        <div className="mission__body">
          <ProgressRing value={57} />
          <div className="mission__rows" aria-label="今日任务分类">
            {taskRows.map((row) => {
              const Icon = row.icon;
              return (
                <button key={row.label} className="mission-row" onClick={() => onNavigate('study')}>
                  <span className={`icon-badge icon-badge--${row.tone}`}>
                    <Icon size={22} weight="regular" />
                  </span>
                  <span>{row.label}</span>
                  <strong>{row.value}</strong>
                </button>
              );
            })}
          </div>
        </div>

        <button className="primary-action" onClick={() => onNavigate('study')}>
          <BookOpen size={26} weight="regular" />
          继续今日学习
        </button>
      </section>

      <section className="reminders grouped-list">
        <button className="list-row" onClick={() => onNavigate('formal-exam')}>
          <span className="icon-badge icon-badge--danger"><Exam size={22} /></span>
          <span>2 个正式考试待完成</span>
          <CaretRight size={20} />
        </button>
        <button className="list-row" onClick={() => onNavigate('study')}>
          <span className="icon-badge icon-badge--blue"><Notebook size={22} /></span>
          <span>卡片盒 12 个到期复习</span>
          <CaretRight size={20} />
        </button>
      </section>

      <section>
        <SectionHeader eyebrow="Shortcuts" title="快速入口" />
        <div className="quick-grid">
          {quickEntries.map((entry) => {
            const Icon = entry.icon;
            return (
              <button key={entry.id} className="quick-card" onClick={() => onNavigate(entry.id)}>
                <Icon size={34} weight="regular" />
                <strong>{entry.label}</strong>
                <span>{entry.helper}</span>
              </button>
            );
          })}
        </div>
      </section>
    </>
  );
}

function StudyScreen({ mode = 'plan', onNavigate }) {
  const [answered, setAnswered] = useState(null);
  const [queue, setQueue] = useState(upcomingWords);
  const [showBoxNote, setShowBoxNote] = useState(false);

  const current = queue[0] ?? upcomingWords[0];
  const currentTaskLabel = mode === 'free' ? '到期复习' : current.type;

  const record = (result) => {
    setAnswered(result);
    window.setTimeout(() => {
      setQueue((items) => (items.length > 1 ? items.slice(1) : upcomingWords));
      setAnswered(null);
    }, 650);
  };

  return (
    <>
      <SectionHeader
        eyebrow={mode === 'free' ? 'Free Study' : 'Study Session'}
        title={mode === 'free' ? '自由学习' : '今日学习'}
        action={<StatusPill tone={mode === 'free' ? 'green' : 'danger'}>{mode === 'free' ? '不计计划进度' : '逾期优先'}</StatusPill>}
      />

      <section className="study-card">
        <div className="study-card__meta">
          <StatusPill tone={mode === 'free' ? 'green' : 'danger'}>{currentTaskLabel}</StatusPill>
          <button onClick={() => setShowBoxNote((value) => !value)} aria-expanded={showBoxNote}>
            {current.box}<CaretRight size={16} />
          </button>
        </div>
        {showBoxNote && <p className="context-note">当前为全局记忆盒位，答对会升盒，答错或跳过会优先安排复习。</p>}
        <h1>{current.word}</h1>
        <p className="phonetic">{current.phonetic}</p>
        <p className="translation">adj. 有弹性的；能恢复的；有韧性的；坚韧的</p>

        <div className="focus-strip">
          <Clock size={20} />
          <span>当前停留时间</span>
          <strong>2分18秒</strong>
        </div>

        {answered && (
          <div className={`result-toast result-toast--${answered}`}>
            {answered === 'known' ? '已记录：我会了，盒位提升' : answered === 'again' ? '已加入自动错词本' : '已跳过，将优先复习'}
          </div>
        )}

        <div className="study-actions">
          <button onClick={() => record('skip')}>先跳过</button>
          <button onClick={() => record('again')}>再学一次</button>
          <button className="primary-action primary-action--compact" onClick={() => record('known')}>我会了</button>
        </div>
      </section>

      <section className="queue-panel">
        <SectionHeader eyebrow="Queue" title="接下来" action={<span className="subtle-count">3 个待学任务</span>} />
        <div className="grouped-list">
          {queue.map((item, index) => (
            <div key={`${item.word}-${index}`} className="list-row list-row--static">
              <span className="queue-index">{index + 1}</span>
              <div>
                <strong>{item.word}</strong>
                <small>{item.phonetic}</small>
              </div>
              <StatusPill tone={item.type === '新词' ? 'green' : 'blue'}>
                {mode === 'free' ? ['到期复习', '高频错词', '未学新词'][index] : item.type}
              </StatusPill>
            </div>
          ))}
        </div>
      </section>

      <section className="self-study-strip">
        {mode === 'free' ? (
          <button onClick={() => onNavigate('library')}>
            <Books size={24} />
            返回已分配词书
            <CaretRight size={20} />
          </button>
        ) : (
          <button onClick={() => onNavigate('free-study')}>
            <Target size={24} />
            自由学习已分配词书
            <CaretRight size={20} />
          </button>
        )}
        <button onClick={() => onNavigate('self-test')}>
          <SealQuestion size={24} />
          开始 20 题自测
          <CaretRight size={20} />
        </button>
      </section>
    </>
  );
}

function LibraryScreen({ mode, onNavigate }) {
  const [selectedMode, setSelectedMode] = useState(mode === 'wrong' || mode === 'favorite' ? mode : 'books');
  const [expandedWord, setExpandedWord] = useState(null);

  return (
    <>
      <SectionHeader eyebrow="Word Library" title="词库与复习" />
      <div className="segmented">
        {[
          ['books', '我的词书'],
          ['wrong', '错词本'],
          ['favorite', '收藏'],
        ].map(([id, label]) => (
          <button key={id} className={selectedMode === id ? 'is-active' : ''} onClick={() => setSelectedMode(id)}>
            {label}
          </button>
        ))}
      </div>

      {selectedMode === 'books' ? (
        <section className="dictionary-list">
          {dictionaries.map((dictionary) => (
            <article key={dictionary.name} className="dictionary-card">
              <div>
                <p className="eyebrow">Assigned Dictionary</p>
                <h2>{dictionary.name}</h2>
                <span>{dictionary.words} 个词 · {dictionary.due} 个到期复习</span>
              </div>
              <div className="dictionary-card__progress">
                <strong>{dictionary.progress}%</strong>
                <button onClick={() => onNavigate('free-study')}>自由学习</button>
              </div>
            </article>
          ))}
        </section>
      ) : (
        <section className="grouped-list memory-list">
          {memoryWords
            .filter((word) => selectedMode === 'wrong' || word.favorite)
            .map((word) => (
              <button
                key={word.word}
                className={`list-row memory-row ${expandedWord === word.word ? 'is-expanded' : ''}`}
                onClick={() => setExpandedWord((value) => value === word.word ? null : word.word)}
                aria-expanded={expandedWord === word.word}
              >
                <div>
                  <strong>{word.word}</strong>
                  <small>{word.text}</small>
                  {expandedWord === word.word && <small className="memory-detail">全局盒位 Box 1 · 明日复习</small>}
                </div>
                <span>{selectedMode === 'wrong' ? `错 ${word.wrong} 次` : '已收藏'}</span>
                <CaretRight size={20} />
              </button>
            ))}
        </section>
      )}
    </>
  );
}

function StatsScreen() {
  const [source, setSource] = useState('全部');
  const [showDetails, setShowDetails] = useState(false);
  const sources = ['全部', '计划', '自由', '正式', '自测'];

  return (
    <>
      <SectionHeader eyebrow="Analytics" title="学习统计" action={<StatusPill tone="green">近 30 天</StatusPill>} />
      <section className="calendar-panel">
        <div className="stats-topline">
          <strong>30 天学习日历</strong>
          <button onClick={() => setShowDetails((value) => !value)} aria-expanded={showDetails}>
            {showDetails ? '收起说明' : '查看日历'}<CaretRight size={18} />
          </button>
        </div>
        <div className="calendar-grid">
          {calendarDays.map((day, index) => (
            <span key={`${day}-${index}`} className={`calendar-cell calendar-cell--${day}`} />
          ))}
        </div>
        <div className="legend">
          <span><i className="calendar-cell--done" />已完成</span>
          <span><i className="calendar-cell--active" />进行中</span>
          <span><i className="calendar-cell--missed" />缺勤</span>
          <span><i className="calendar-cell--free" />自由学习</span>
          <span><i className="calendar-cell--idle" />未开始</span>
        </div>
        {showDetails && <p className="calendar-note">本月完成 21 天，连续学习 6 天；自由学习记录不会改变老师计划完成率。</p>}
      </section>

      <div className="segmented">
        {sources.map((item) => (
          <button key={item} className={source === item ? 'is-active' : ''} onClick={() => setSource(item)}>
            {item}
          </button>
        ))}
      </div>

      <section className="trend-grid">
        <article className="trend-card">
          <span>正确率趋势</span>
          <strong>82%</strong>
          <div className="trend-meter" aria-label="正确率 82%"><span style={{ width: '82%' }} /></div>
          <small>{source}来源 · 较上周提升 6%</small>
        </article>
        <article className="trend-card">
          <span>注意力趋势</span>
          <strong>78%</strong>
          <div className="trend-meter trend-meter--amber" aria-label="注意力 78%"><span style={{ width: '78%' }} /></div>
          <small>{source}来源 · 空闲中断下降</small>
        </article>
      </section>

      <section className="metric-grid" aria-label="学习指标摘要">
        {[
          ['连续学习', '6 天'],
          ['累计完成', '428 词'],
          ['复习积压', '12 个'],
          ['平均有效停留', '2分14秒'],
        ].map(([label, value]) => (
          <div key={label}><span>{label}</span><strong>{value}</strong></div>
        ))}
      </section>
    </>
  );
}

function SelfTestScreen() {
  const [questionCount, setQuestionCount] = useState(20);
  const [dictionary, setDictionary] = useState(dictionaries[0].name);
  const [started, setStarted] = useState(false);
  const [choice, setChoice] = useState(null);

  if (started) {
    return (
      <>
        <SectionHeader eyebrow="Self Practice" title="自测进行中" action={<StatusPill tone="green">1 / {questionCount}</StatusPill>} />
        <section className="assessment-card assessment-card--self">
          <div className="assessment-banner"><SealQuestion size={24} />个人自测 · 不计入老师评价</div>
          <p className="eyebrow">{dictionary}</p>
          <h2>resilient</h2>
          <p className="assessment-prompt">请选择最符合的中文释义</p>
          <div className="choice-list">
            {['有韧性的；能恢复的', '犹豫不决的', '明确而直接的', '容易被忽略的'].map((item) => (
              <button key={item} className={choice === item ? 'is-selected' : ''} onClick={() => setChoice(item)}>{item}</button>
            ))}
          </div>
          <button className="primary-action primary-action--compact" disabled={!choice} onClick={() => setChoice(null)}>
            下一题<PlayCircle size={22} />
          </button>
        </section>
      </>
    );
  }

  return (
    <>
      <SectionHeader eyebrow="Self Practice" title="自测练习" action={<StatusPill tone="green">个人练习</StatusPill>} />
      <section className="assessment-card assessment-card--self">
        <div className="assessment-banner"><ShieldCheck size={24} />自测结果独立记录，不计入正式考试</div>
        <p className="field-label">选择已分配词书</p>
        <div className="selection-list">
          {dictionaries.map((item) => (
            <button key={item.name} className={dictionary === item.name ? 'is-selected' : ''} onClick={() => setDictionary(item.name)}>
              <BookOpen size={20} />
              <span>{item.name}</span>
              {dictionary === item.name && <CheckCircle size={20} weight="fill" />}
            </button>
          ))}
        </div>
        <p className="field-label">题目数量</p>
        <div className="segmented segmented--inline">
          {[10, 20, 30].map((count) => (
            <button key={count} className={questionCount === count ? 'is-active' : ''} onClick={() => setQuestionCount(count)}>{count} 题</button>
          ))}
        </div>
        <button className="primary-action primary-action--compact" onClick={() => setStarted(true)}>
          开始自测<PlayCircle size={22} />
        </button>
      </section>
    </>
  );
}

function FormalExamScreen() {
  const [exam, setExam] = useState(null);
  const [choice, setChoice] = useState(null);

  if (exam) {
    return (
      <>
        <SectionHeader eyebrow="Formal Exam" title="正式考试" action={<StatusPill tone="danger">计入教师评价</StatusPill>} />
        <section className="assessment-card assessment-card--formal">
          <div className="assessment-banner"><Exam size={24} />{exam} · 第 1 / 30 题</div>
          <h2>persistent</h2>
          <p className="assessment-prompt">请选择正确释义，提交后不可修改</p>
          <div className="choice-list">
            {['持久的；坚持不懈的', '灵活适应的', '暂时的', '保守谨慎的'].map((item) => (
              <button key={item} className={choice === item ? 'is-selected' : ''} onClick={() => setChoice(item)}>{item}</button>
            ))}
          </div>
          <button className="primary-action primary-action--compact" disabled={!choice} onClick={() => setChoice(null)}>
            提交并下一题<PlayCircle size={22} />
          </button>
        </section>
      </>
    );
  }

  return (
    <>
      <SectionHeader eyebrow="Formal Exam" title="待完成考试" action={<StatusPill tone="danger">2 场</StatusPill>} />
      <section className="assessment-notice">
        <ShieldCheck size={26} />
        <div><strong>正式考试</strong><span>由老师发布，成绩将计入教师评价。</span></div>
      </section>
      <section className="exam-list">
        {[
          ['周末核心词汇测验', '30 题 · 截止今天 20:00'],
          ['高二必修二单元测验', '20 题 · 截止明天 18:00'],
        ].map(([name, meta]) => (
          <article key={name} className="exam-card">
            <CalendarDots size={28} />
            <div><h2>{name}</h2><span>{meta}</span></div>
            <button onClick={() => setExam(name)}>开始<PlayCircle size={18} /></button>
          </article>
        ))}
      </section>
    </>
  );
}

function ProfileScreen() {
  const [editing, setEditing] = useState(false);
  const [profile, setProfile] = useState({
    displayName: '林同学',
    email: 'lin.student@example.com',
    phone: '138****2026',
  });
  const [draft, setDraft] = useState(profile);
  const [showPassword, setShowPassword] = useState(false);
  const [passwordChanged, setPasswordChanged] = useState(false);
  const [passwordDraft, setPasswordDraft] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
  const [notice, setNotice] = useState(null);

  const toggleEditing = () => {
    if (editing) {
      setProfile(draft);
      setNotice('个人资料已保存');
    } else {
      setDraft(profile);
      setNotice(null);
    }
    setEditing((value) => !value);
  };

  const updateDraft = (field, value) => setDraft((current) => ({ ...current, [field]: value }));
  const passwordValid = passwordDraft.oldPassword.length > 0
    && passwordDraft.newPassword.length >= 8
    && passwordDraft.newPassword === passwordDraft.confirmPassword;

  const submitPassword = () => {
    setPasswordChanged(true);
    setPasswordDraft({ oldPassword: '', newPassword: '', confirmPassword: '' });
  };

  return (
    <>
      <SectionHeader eyebrow="Profile" title="个人中心" action={<button className="small-action" onClick={toggleEditing}>{editing ? '保存' : '编辑'}</button>} />
      <section className="profile-panel">
        <div className="avatar"><Student size={42} /></div>
        <h2>{profile.displayName}</h2>
        <p>高二 3 班 · 王老师</p>
        {notice && <div className="profile-notice"><CheckCircle size={18} weight="fill" />{notice}</div>}
        <div className="profile-fields">
          <label>
            显示名
            <input disabled={!editing} value={draft.displayName} onChange={(event) => updateDraft('displayName', event.target.value)} />
          </label>
          <label>
            邮箱
            <input type="email" disabled={!editing} value={draft.email} onChange={(event) => updateDraft('email', event.target.value)} />
          </label>
          <label>
            手机号
            <input type="tel" disabled={!editing} value={draft.phone} onChange={(event) => updateDraft('phone', event.target.value)} />
          </label>
        </div>
      </section>
      <section className="grouped-list">
        <div className="list-row list-row--static"><span>用户名</span><strong>student_lin</strong></div>
        <div className="list-row list-row--static"><span>角色</span><strong>STUDENT</strong></div>
        <div className="list-row list-row--static"><span>状态</span><strong>正常</strong></div>
        <div className="list-row list-row--static"><span>最近登录</span><strong>今天 08:42</strong></div>
        <button className="list-row password-row" onClick={() => { setShowPassword((value) => !value); setPasswordChanged(false); }}>
          <LockKey size={22} />
          <span>修改密码</span>
          <CaretRight size={20} />
        </button>
      </section>
      {showPassword && (
        <section className="profile-panel password-panel">
          {passwordChanged ? (
            <div className="password-success">
              <CheckCircle size={34} weight="fill" />
              <h2>密码已更新</h2>
              <p>正式产品会清除当前登录态，并要求使用新密码重新登录。</p>
              <button className="small-action" onClick={() => setShowPassword(false)}>返回个人中心</button>
            </div>
          ) : (
            <>
              <h2>修改密码</h2>
              <p>新密码至少 8 位，并需再次确认。</p>
              <div className="profile-fields">
                <label>旧密码<input type="password" value={passwordDraft.oldPassword} onChange={(event) => setPasswordDraft((value) => ({ ...value, oldPassword: event.target.value }))} /></label>
                <label>新密码<input type="password" value={passwordDraft.newPassword} onChange={(event) => setPasswordDraft((value) => ({ ...value, newPassword: event.target.value }))} /></label>
                <label>确认新密码<input type="password" value={passwordDraft.confirmPassword} onChange={(event) => setPasswordDraft((value) => ({ ...value, confirmPassword: event.target.value }))} /></label>
              </div>
              <button className="primary-action primary-action--compact" disabled={!passwordValid} onClick={submitPassword}>更新密码</button>
            </>
          )}
        </section>
      )}
    </>
  );
}

function Content({ activeTab, activeEntry, onNavigate }) {
  if (activeEntry === 'stats') {
    return <StatsScreen />;
  }
  if (activeEntry === 'self-test') {
    return <SelfTestScreen />;
  }
  if (activeEntry === 'formal-exam') {
    return <FormalExamScreen />;
  }
  if (activeEntry === 'free-study') {
    return <StudyScreen mode="free" onNavigate={onNavigate} />;
  }
  if (activeEntry === 'profile' || activeTab === 'profile') {
    return <ProfileScreen />;
  }
  if (activeTab === 'study') {
    return <StudyScreen onNavigate={onNavigate} />;
  }
  if (activeTab === 'library' || ['library', 'wrong', 'favorite'].includes(activeEntry)) {
    return <LibraryScreen mode={activeEntry} onNavigate={onNavigate} />;
  }
  return <HomeScreen onNavigate={onNavigate} />;
}

export function App() {
  const [activeTab, setActiveTab] = useState('home');
  const [activeEntry, setActiveEntry] = useState(null);
  const scrollRef = useRef(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: 0, behavior: 'auto' });
  }, [activeEntry, activeTab]);

  const screenLabel = useMemo(() => {
    if (activeEntry === 'stats') return '学习统计';
    if (activeEntry === 'wrong') return '错词本';
    if (activeEntry === 'favorite') return '生词收藏';
    if (activeEntry === 'self-test') return '自测练习';
    if (activeEntry === 'formal-exam') return '正式考试';
    if (activeEntry === 'free-study') return '自由学习';
    return tabs.find((tab) => tab.id === activeTab)?.label ?? '首页';
  }, [activeEntry, activeTab]);

  const navigate = (target) => {
    if (target === 'stats') {
      setActiveEntry('stats');
      setActiveTab('home');
      return;
    }
    if (target === 'profile') {
      setActiveEntry('profile');
      setActiveTab('profile');
      return;
    }
    if (target === 'wrong' || target === 'favorite' || target === 'library') {
      setActiveEntry(target);
      setActiveTab('library');
      return;
    }
    if (target === 'self-test' || target === 'free-study' || target === 'formal-exam') {
      setActiveEntry(target);
      setActiveTab('study');
      return;
    }
    setActiveEntry(null);
    setActiveTab(target);
  };

  return (
    <main className="prototype-shell">
      <div className="phone-frame" aria-label={`学生端原型，当前页面：${screenLabel}`}>
        <div className="phone-scroll" ref={scrollRef}>
          <Content activeTab={activeTab} activeEntry={activeEntry} onNavigate={navigate} />
        </div>
        <nav className="bottom-nav" aria-label="主导航">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id && (!activeEntry || tab.id !== 'home');
            return (
              <button
                key={tab.id}
                className={isActive ? 'is-active' : ''}
                onClick={() => {
                  setActiveTab(tab.id);
                  setActiveEntry(null);
                }}
              >
                <Icon size={27} weight={isActive ? 'fill' : 'regular'} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </nav>
      </div>
    </main>
  );
}
