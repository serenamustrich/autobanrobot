globalThis.extensionAPI = globalThis.browser ?? globalThis.chrome;

if (!globalThis.extensionAPI) {
  throw new Error('Safari Web Extension API is unavailable.');
}
