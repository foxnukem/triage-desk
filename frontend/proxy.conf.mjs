// Dev server proxy: /api → the Spring Boot backend (BACKEND_URL, default http://localhost:8080).
export default {
  '/api': {
    target: process.env.BACKEND_URL ?? 'http://localhost:8080',
    secure: false,
    changeOrigin: true,
    // CPU-only triage can take minutes.
    timeout: 900000,
    proxyTimeout: 900000,
  },
};
