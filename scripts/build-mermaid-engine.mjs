import { build } from 'esbuild';
import { readFileSync, writeFileSync } from 'fs';

const banner = `
var atob = globalThis.atob || function(input) {
  var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
  var str = String(input).replace(/=+$/, '');
  var output = '';
  for (var bc = 0, bs, buffer, idx = 0; (buffer = str.charAt(idx++)); ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer, bc++ % 4) ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6)) : 0) {
    buffer = chars.indexOf(buffer);
  }
  return output;
};
var btoa = globalThis.btoa || function(input) {
  var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
  var str = String(input);
  var output = '';
  for (var block = 0, charCode, idx = 0, map = chars; str.charAt(idx | 0) || (map = '=', idx % 1); output += map.charAt(63 & block >> 8 - idx % 1 * 8)) {
    charCode = str.charCodeAt(idx += 3 / 4);
    block = block << 8 | charCode;
  }
  return output;
};
globalThis.atob = atob;
globalThis.btoa = btoa;
globalThis.TextEncoder = globalThis.TextEncoder || function TextEncoder() {};
globalThis.TextEncoder.prototype.encode = function encode(input) {
  var str = unescape(encodeURIComponent(String(input)));
  var out = new Uint8Array(str.length);
  for (var i = 0; i < str.length; i++) out[i] = str.charCodeAt(i);
  return out;
};
globalThis.crypto = globalThis.crypto || {};
globalThis.crypto.getRandomValues = globalThis.crypto.getRandomValues || function getRandomValues(array) {
  for (var i = 0; i < array.length; i++) array[i] = Math.floor(Math.random() * 256);
  return array;
};
globalThis.structuredClone = globalThis.structuredClone || function structuredClone(value) {
  if (value === undefined || value === null) return value;
  return JSON.parse(JSON.stringify(value));
};
globalThis.screen = globalThis.screen || { width: 1280, height: 720, availWidth: 1280, availHeight: 720 };
globalThis.__dmtoolsTimerQueue = [];
globalThis.__dmtoolsDrainTimers = function __dmtoolsDrainTimers() {
  var count = 0;
  while (globalThis.__dmtoolsTimerQueue.length && count++ < 256) {
    globalThis.__dmtoolsTimerQueue.shift()();
  }
};
globalThis.setTimeout = globalThis.setTimeout || function setTimeout(callback) {
  if (typeof callback === 'function') globalThis.__dmtoolsTimerQueue.push(callback);
  return 0;
};
globalThis.clearTimeout = globalThis.clearTimeout || function clearTimeout() {};
globalThis.requestAnimationFrame = globalThis.requestAnimationFrame || function requestAnimationFrame(callback) {
  if (typeof callback === 'function') globalThis.__dmtoolsTimerQueue.push(function() { callback(globalThis.performance.now()); });
  return 0;
};
globalThis.cancelAnimationFrame = globalThis.cancelAnimationFrame || function cancelAnimationFrame() {};
globalThis.localStorage = globalThis.localStorage || {
  getItem: function() { return null; },
  setItem: function() {},
  removeItem: function() {},
  clear: function() {}
};
globalThis.performance = globalThis.performance || { now: function() { return Date.now(); } };
`;

await build({
  entryPoints: ['src/js/mermaid-engine-entry.js'],
  bundle: true,
  platform: 'browser',
  format: 'iife',
  globalName: 'MermaidEngine',
  banner: { js: banner },
  outfile: 'src/main/resources/mermaid/mermaid-renderer.js',
  logLevel: 'warning',
});

// Post-build patch: fix dagre layout's ranksep/nodesep priority so that
// per-diagram data4Layout.rankSpacing takes precedence over the global
// flowchart.rankSpacing default, preventing config leakage across diagram types.
// Without this, state/class/requirement diagrams inherit flowchart's rankSpacing=86.
const bundlePath = 'src/main/resources/mermaid/mermaid-renderer.js';
let bundle = readFileSync(bundlePath, 'utf8');
const before = 'nodesep: data4Layout.config?.nodeSpacing || data4Layout.config?.flowchart?.nodeSpacing || data4Layout.nodeSpacing,\n          ranksep: data4Layout.config?.rankSpacing || data4Layout.config?.flowchart?.rankSpacing || data4Layout.rankSpacing,';
const after  = 'nodesep: data4Layout.config?.nodeSpacing || data4Layout.nodeSpacing || data4Layout.config?.flowchart?.nodeSpacing,\n          ranksep: data4Layout.config?.rankSpacing || data4Layout.rankSpacing || data4Layout.config?.flowchart?.rankSpacing,';
if (bundle.includes(before)) {
  bundle = bundle.replace(before, after);
  writeFileSync(bundlePath, bundle, 'utf8');
  console.log('Applied dagre ranksep priority patch.');
} else if (!bundle.includes(after)) {
  console.warn('WARNING: dagre ranksep priority patch target not found – manual patch may be needed.');
}
