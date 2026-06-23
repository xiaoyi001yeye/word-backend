import assert from 'node:assert/strict';
import test from 'node:test';

import {
  WordPronunciationAudioService,
  buildYoudaoAudioUrl,
  parseWordPronunciationDetail,
} from '../src/student/word-pronunciation.ts';

test('parses xxapi pronunciation detail and ignores empty audio URLs', () => {
  const detail = parseWordPronunciationDetail({
    code: 200,
    data: {
      word: 'OK',
      ukphone: '',
      usphone: '',
      ukspeech: 'https://dict.youdao.com/dictvoice?audio=',
      usspeech: ' https://dict.youdao.com/dictvoice?audio=OK\\u0026type=2 ',
    },
  });

  assert.equal(detail?.word, 'OK');
  assert.equal(detail?.ukSpeechUrl, '');
  assert.equal(detail?.usSpeechUrl, 'https://dict.youdao.com/dictvoice?audio=OK&type=2');
});

test('builds Youdao fallback URL for the selected accent', () => {
  assert.equal(
    buildYoudaoAudioUrl('hello world', 'UK'),
    'https://dict.youdao.com/dictvoice?audio=hello%20world&type=1',
  );
  assert.equal(
    buildYoudaoAudioUrl('hello', 'US'),
    'https://dict.youdao.com/dictvoice?audio=hello&type=2',
  );
});

test('plays xxapi audio first and falls back to Youdao when direct audio fails', async () => {
  const played = [];
  const service = new WordPronunciationAudioService({
    fetchJson: async () => ({
      code: 200,
      data: {
        word: 'hello',
        ukphone: '/həˈləʊ/',
        usphone: '/həˈloʊ/',
        ukspeech: 'https://audio.test/hello-uk.mp3',
        usspeech: 'https://audio.test/hello-us.mp3',
      },
    }),
    playAudio: async (url) => {
      played.push(url);
      if (url.includes('audio.test')) {
        throw new Error('broken direct audio');
      }
    },
  });

  await service.playWord('hello', 'UK');

  assert.deepEqual(played, [
    'https://audio.test/hello-uk.mp3',
    'https://dict.youdao.com/dictvoice?audio=hello&type=1',
  ]);
});

test('falls back to Youdao when xxapi has no usable audio URL', async () => {
  const played = [];
  const service = new WordPronunciationAudioService({
    fetchJson: async () => ({
      code: 200,
      data: {
        word: 'OK',
        ukspeech: 'https://dict.youdao.com/dictvoice?audio=',
        usspeech: '',
      },
    }),
    playAudio: async (url) => {
      played.push(url);
    },
  });

  await service.playWord('OK', 'US');

  assert.deepEqual(played, [
    'https://dict.youdao.com/dictvoice?audio=OK&type=2',
  ]);
});
