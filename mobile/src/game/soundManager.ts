import Sound from 'react-native-sound';

Sound.setCategory('Playback', true);

const SOUND_FILES: Record<string, string> = {
  ORDER_COMPLETE: 'ding.wav',
  WRONG_DISH: 'buzz.wav',
  ORDER_EXPIRED: 'buzz.wav',
  CHOP_DONE: 'chop.wav',
  PATTY_COOKED: 'sizzle.wav',
  PATTY_BURNT: 'buzz.wav',
  PLATE_RETURNED: 'plate.wav',
  NEW_ORDER: 'chime.wav',
};

const loadedSounds: Record<string, Sound | null> = {};
let bgmSound: Sound | null = null;
let bgmWanted = false;
let bgmMuted = false;

export function playGameSound(event: string): void {
  if (bgmMuted) {
    return;
  }
  const filename = SOUND_FILES[event];
  if (!filename) {
    return;
  }

  const cached = loadedSounds[filename];
  if (cached) {
    cached.stop(() => cached.play());
    return;
  }
  if (cached === null) {
    return;
  }

  try {
    const s = new Sound(filename, Sound.MAIN_BUNDLE, (err) => {
      if (err) {
        loadedSounds[filename] = null;
        return;
      }
      loadedSounds[filename] = s;
      s.setVolume(0.75);
      s.play();
    });
  } catch {
    loadedSounds[filename] = null;
  }
}

export function startBackgroundMusic(): void {
  bgmWanted = true;
  if (bgmMuted) {
    return;
  }
  if (bgmSound) {
    bgmSound.setVolume(0.32);
    bgmSound.setNumberOfLoops(-1);
    bgmSound.play();
    return;
  }

  try {
    const music = new Sound('kitchen_bgm.wav', Sound.MAIN_BUNDLE, (err) => {
      if (err) {
        return;
      }
      bgmSound = music;
      music.setNumberOfLoops(-1);
      music.setVolume(0.32);
      if (bgmWanted && !bgmMuted) {
        music.play();
      }
    });
  } catch {
    // Ignore if audio session unavailable
  }
}

export function stopBackgroundMusic(): void {
  bgmWanted = false;
  if (bgmSound) {
    bgmSound.stop();
  }
}

export function toggleAudioMute(): boolean {
  bgmMuted = !bgmMuted;
  if (bgmMuted) {
    if (bgmSound) {
      bgmSound.pause();
    }
  } else if (bgmWanted) {
    startBackgroundMusic();
  }
  return bgmMuted;
}

export function isAudioMuted(): boolean {
  return bgmMuted;
}
