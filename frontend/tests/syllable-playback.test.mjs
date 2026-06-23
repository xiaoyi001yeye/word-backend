import assert from 'node:assert/strict';
import test from 'node:test';

import { SyllablePlaybackController } from '../src/student/syllable-playback.ts';

const segment = {
  text: 're',
  ukPhonetic: '/rɪ/',
  usPhonetic: '/rɪ/',
  ukAudioUrl: 'https://audio.test/re-uk.mp3',
  usAudioUrl: 'https://audio.test/re-us.mp3',
};

test('prefers the selected accent audio URL', async () => {
  const calls = [];
  const controller = new SyllablePlaybackController({
    cancel: () => calls.push('cancel'),
    playAudio: async (url) => calls.push(`audio:${url}`),
    playPronunciation: async (text, accent) => calls.push(`pronunciation:${text}:${accent}`),
  });

  await controller.playSegment(segment, 'US', () => undefined);

  assert.deepEqual(calls, ['cancel', 'audio:https://audio.test/re-us.mp3']);
});

test('falls back to dictionary pronunciation when segment audio playback fails', async () => {
  const calls = [];
  const controller = new SyllablePlaybackController({
    cancel: () => calls.push('cancel'),
    playAudio: async () => {
      calls.push('audio');
      throw new Error('unavailable');
    },
    playPronunciation: async (text, accent) => calls.push(`pronunciation:${text}:${accent}`),
  });

  await controller.playSegment(segment, 'UK', () => undefined);

  assert.deepEqual(calls, ['cancel', 'audio', 'pronunciation:re:UK']);
});

test('slow spelling reads each syllable before the whole word', async () => {
  const calls = [];
  const controller = new SyllablePlaybackController({
    cancel: () => calls.push('cancel'),
    playAudio: async () => undefined,
    playPronunciation: async (text, accent) => calls.push(`${text}:${accent}`),
  });

  await controller.playSequence(
    'remember',
    [{ text: 're' }, { text: 'mem' }, { text: 'ber' }],
    'US',
    () => undefined,
  );

  assert.deepEqual(calls, [
    'cancel',
    're:US',
    'mem:US',
    'ber:US',
    'remember:US',
  ]);
});

test('whole word reading uses pronunciation audio instead of speech synthesis', async () => {
  const calls = [];
  const controller = new SyllablePlaybackController({
    cancel: () => calls.push('cancel'),
    playAudio: async () => calls.push('audio'),
    playPronunciation: async (text, accent) => calls.push(`pronunciation:${text}:${accent}`),
  });

  await controller.playWord('remember', 'US', () => undefined);

  assert.deepEqual(calls, ['cancel', 'pronunciation:remember:US']);
});
