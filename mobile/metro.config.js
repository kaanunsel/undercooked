const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

/**
 * Metro configuration
 * Ignore native ios/ and android/ folders so Xcode xcuserstate / Gradle writes
 * never trigger spurious Fast Refresh ("Refreshing...") banners during gameplay.
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = {
  resolver: {
    blockList: [/\/ios\/.*/, /\/android\/.*/, /.*\.xcuserstate$/],
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
