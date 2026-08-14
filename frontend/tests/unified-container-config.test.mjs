import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8');

test('docker compose exposes only one frontend container on localhost 8083', () => {
  const compose = read('docker-compose.yml').replace(/\r\n/g, '\n');

  assert.match(compose, /frontend:\n[\s\S]*?ports:\n\s+- "8083:80"/);
  assert.doesNotMatch(compose, /\n\s+admin-frontend:/);
});

test('frontend build includes the admin sub-application', () => {
  const pkg = JSON.parse(read('frontend/package.json'));
  const adminPkg = JSON.parse(read('frontend/admin/package.json'));

  assert.match(pkg.scripts.build, /npm run build:admin/);
  assert.equal(adminPkg.name, 'frontend-admin');
});

test('admin sub-application is mounted at /admin/', () => {
  const adminVite = read('frontend/admin/vite.config.ts');
  const adminApp = read('frontend/admin/src/App.tsx');
  const nginx = read('frontend/nginx.conf');

  assert.match(adminVite, /base:\s*["']\/admin\/["']/);
  assert.match(nginx, /location\s+\^~\s+\/admin\//);
  assert.match(nginx, /location\s+=\s+\/admin\/login/);
  assert.doesNotMatch(adminApp, /path=["']\/login["']/);
  assert.doesNotMatch(nginx, /proxy_pass\s+http:\/\/admin-frontend/);
});

test('frontend html entry points are not cached across image deployments', () => {
  const nginx = read('frontend/nginx.conf');

  assert.match(nginx, /location\s+=\s+\/index\.html\s*\{[\s\S]*?Cache-Control "[^"]*no-store/);
  assert.match(nginx, /location\s+=\s+\/admin\/index\.html\s*\{[\s\S]*?Cache-Control "[^"]*no-store/);
  assert.match(nginx, /location\s+\/\s*\{[\s\S]*?Cache-Control "[^"]*no-store[\s\S]*?try_files \$uri \$uri\/ \/index\.html/);
  assert.match(nginx, /etag\s+off/);
  assert.match(nginx, /Pragma "no-cache" always/);
  assert.match(nginx, /location\s+~\*\s+\\\.\(js\|css\|png\|jpg\|jpeg\|gif\|ico\|svg\)\$\s*\{[\s\S]*?Cache-Control "public, immutable"/);
});

test('root application reports script failures without timing out slow asset loads', () => {
  const html = read('frontend/index.html');
  const main = read('frontend/src/main.tsx');
  const pkg = JSON.parse(read('frontend/package.json'));
  const normalizeScript = read('frontend/scripts/normalize-root-entry.mjs');
  const styles = read('frontend/src/index.css');
  const viteConfig = read('frontend/vite.config.ts');

  assert.match(html, /登录页加载中/);
  assert.match(html, /addEventListener\('error'/);
  assert.match(html, /unhandledrejection/);
  assert.doesNotMatch(html, /页面启动超时/);
  assert.match(html, /__wordAtelierEntryLoaded/);
  assert.match(html, /__wordAtelierEntryFailed/);
  assert.match(html, /入口脚本已加载/);
  assert.match(pkg.scripts.build, /normalize:root-entry/);
  assert.match(pkg.scripts['normalize:root-entry'], /normalize-root-entry\.mjs/);
  assert.match(normalizeScript, /type="module"/);
  assert.match(normalizeScript, /<script src=/);
  assert.doesNotMatch(normalizeScript, /<script crossorigin src=/);
  assert.match(normalizeScript, /__wordAtelierEntryLoaded/);
  assert.match(normalizeScript, /__wordAtelierEntryFailed/);
  assert.ok(
    normalizeScript.indexOf('.replace(entryMatch[0]') < normalizeScript.indexOf('.replace(/\\s+crossorigin'),
    'normalize-root-entry must remove the matched module script before stripping crossorigin',
  );
  assert.doesNotMatch(styles, /fonts\.googleapis\.com/);
  assert.match(viteConfig, /__FRONTEND_BUILD_STAMP__/);
  assert.match(main, /import\s+App\s+from\s+['"]\.\/App\.tsx['"]/);
  assert.match(main, /frontendBuild\s*=\s*__FRONTEND_BUILD_STAMP__/);
  assert.match(main, /wordAtelierMain\s*=\s*'evaluated'/);
  assert.match(main, /wordAtelierRender\s*=\s*'called'/);
  assert.doesNotMatch(main, /import\(['"]\.\/App\.tsx['"]\)/);
  assert.match(main, /startupRendered\s*=\s*'true'/);
  assert.match(main, /StartupErrorBoundary/);
  assert.match(main, /页面启动失败/);
  assert.match(styles, /\.startup-error/);
  assert.match(read('frontend/src/App.tsx'), /AUTH_BOOTSTRAP_TIMEOUT_MS/);
});
