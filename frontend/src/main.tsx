import { Component, StrictMode, type ErrorInfo, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

declare const __FRONTEND_BUILD_STAMP__: string;

document.documentElement.dataset.wordAtelierMain = 'evaluated';

type StartupErrorBoundaryProps = {
  children: ReactNode;
};

type StartupErrorBoundaryState = {
  error: unknown;
};

class StartupErrorBoundary extends Component<StartupErrorBoundaryProps, StartupErrorBoundaryState> {
  state: StartupErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: unknown): StartupErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: unknown, errorInfo: ErrorInfo) {
    console.error('Application render failed', error, errorInfo);
  }

  render() {
    if (this.state.error) {
      return <StartupErrorView error={this.state.error} />;
    }

    return this.props.children;
  }
}

function StartupErrorView({ error }: { error: unknown }) {
  const message = error instanceof Error ? error.message : String(error);

  return (
    <main className="startup-error" role="alert">
      <section>
        <p>Application Error</p>
        <h1>页面启动失败</h1>
        <pre>{message || '未知前端错误'}</pre>
        <button type="button" onClick={() => window.location.reload()}>重新加载</button>
      </section>
    </main>
  );
}

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('Root element #root was not found');
}

rootElement.dataset.frontendBuild = __FRONTEND_BUILD_STAMP__;
document.documentElement.dataset.wordAtelierRender = 'called';

createRoot(rootElement).render(
  <StrictMode>
    <StartupErrorBoundary>
      <App />
    </StartupErrorBoundary>
  </StrictMode>,
);

rootElement.dataset.startupRendered = 'true';
