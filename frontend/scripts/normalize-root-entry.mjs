import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const indexPath = resolve('dist/index.html');
const html = readFileSync(indexPath, 'utf8');
const entryMatch = html.match(/\s*<script type="module" crossorigin src="([^"]+)"><\/script>/);

if (!entryMatch) {
  throw new Error('Root entry script tag was not normalized');
}

const entryScript = `<script src="${entryMatch[1]}" onload="window.__wordAtelierEntryLoaded && window.__wordAtelierEntryLoaded()" onerror="window.__wordAtelierEntryFailed && window.__wordAtelierEntryFailed()"></script>`;
const normalized = html
  .replace(entryMatch[0], '')
  .replace(/\s+crossorigin(?=[\s>])/g, '')
  .replace('</body>', `    ${entryScript}\n  </body>`);

writeFileSync(indexPath, normalized);
