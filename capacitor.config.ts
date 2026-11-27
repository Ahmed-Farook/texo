import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.ahmedfarook.notesapp',
  appName: 'texō',
  webDir: 'dist',
  server: {
    androidScheme: 'https'
  },
  plugins: {
    StatusBar: {
      overlaysWebView: true,
      style: 'DEFAULT'
    }
  }
};

export default config;

