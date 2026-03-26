import { useMemo, useState } from 'react';

interface LoginScreenProps {
  loading: boolean;
  error: string | null;
  onSubmit: (username: string, password: string) => Promise<void>;
}

export function LoginScreen({ loading, error, onSubmit }: LoginScreenProps) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin123456');

  const insightCopy = useMemo(
    () => [
      '管理员可创建账号并分配角色',
      '教师可管理自己的词书与学生',
      '学生只看到被分配的学习资源',
    ],
    [],
  );

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    await onSubmit(username.trim(), password);
  };

  return (
    <div className="auth-shell">
      <section className="auth-shell__hero">
        <div className="auth-shell__hero-copy">
          <p className="app__eyebrow">Word Atelier Access</p>
          <h1 className="auth-shell__title">让词书、角色与考试终于在同一套入口里协同起来。</h1>
          <p className="auth-shell__subtitle">
            登录后按角色进入同一个学习工作台。管理员治理资源，教师组织学习，学生只处理自己的任务。
          </p>
        </div>

        <div className="auth-shell__insights">
          {insightCopy.map((item) => (
            <div key={item} className="auth-shell__insight">
              <span className="auth-shell__insight-index">0{insightCopy.indexOf(item) + 1}</span>
              <span>{item}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="auth-shell__panel">
        <div className="auth-shell__panel-header">
          <div>
            <p className="app__eyebrow">Sign In</p>
            <h2 className="auth-shell__panel-title">进入你的学习台面</h2>
          </div>
          <span className="auth-shell__badge">JWT</span>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-form__field">
            <span className="form__label">用户名</span>
            <input
              className="form__input"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="输入用户名"
              autoComplete="username"
              disabled={loading}
            />
          </label>

          <label className="auth-form__field">
            <span className="form__label">密码</span>
            <input
              className="form__input"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="输入密码"
              autoComplete="current-password"
              disabled={loading}
            />
          </label>

          {error && <div className="form__error">{error}</div>}

          <button className="btn btn--primary auth-form__submit" type="submit" disabled={loading}>
            {loading ? '正在登录...' : '登录进入'}
          </button>
        </form>

        <div className="auth-shell__demo">
          <p className="auth-shell__demo-label">默认测试账号</p>
          <p className="auth-shell__demo-value">admin / admin123456</p>
        </div>
      </section>
    </div>
  );
}
