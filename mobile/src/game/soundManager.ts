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

/**
 * Lightweight native sound playback via react-native-sound.
 * Gracefully no-ops if a WAV asset is not yet bundled in android/res/raw or iOS bundle.
 */
export function playGameSound(event: string): void {
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
    return; // Previously failed to load; skip retrying every tick
  }

  try {
    const s = new Sound(filename, Sound.MAIN_BUNDLE, (err) => {
      if (err) {
        loadedSounds[filename] = null;
        return;
      }
      loadedSounds[filename] = s;
      s.play();
    });
  } catch {
    loadedSounds[filename] = null;
  }
}
