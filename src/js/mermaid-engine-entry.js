import { parseHTML } from 'linkedom';

function estimateTextWidth(element) {
  const tagName = String(element?.tagName || '').toLowerCase();
  if (tagName === 'style' || tagName === 'script') {
    return 0;
  }
  let text = '';
  if (['text', 'tspan', 'span', 'p', 'div'].includes(tagName)) {
    text = element?.textContent || '';
  } else {
    text = Array.from(element?.querySelectorAll?.('text,tspan,span,p') || [])
      .map((child) => child.textContent || '')
      .join(' ');
  }
  return Math.max(10, text.length * 8);
}

function estimateBox(element) {
  const tagName = String(element?.tagName || '').toLowerCase();
  const className = String(element?.getAttribute?.('class') || '');
  if (tagName === 'svg' || className.split(/\s+/).includes('root')) {
    const extents = estimateSvgExtents(element);
    if (extents) {
      return extents;
    }
    return { width: 1024, height: 768 };
  }
  const width = Number.parseFloat(element?.getAttribute?.('width'));
  const height = Number.parseFloat(element?.getAttribute?.('height'));
  if (Number.isFinite(width) || Number.isFinite(height)) {
    return {
      width: Number.isFinite(width) ? width : estimateTextWidth(element),
      height: Number.isFinite(height) ? height : 16,
    };
  }
  if (tagName === 'style' || tagName === 'script') {
    return { width: 0, height: 0 };
  }
  const textNodes = element?.querySelectorAll?.('text,tspan,span,p') || [];
  const text = ['text', 'tspan', 'span', 'p', 'div'].includes(tagName)
    ? element?.textContent || ''
    : Array.from(textNodes).map((child) => child.textContent || '').join(' ');
  const lines = Math.max(1, text.split(/\n/).length);
  return {
    width: Math.max(10, text.length * 8),
    height: lines * 16,
  };
}

function transformOffset(element) {
  let x = 0;
  let y = 0;
  let current = element;
  while (current) {
    const transform = current.getAttribute?.('transform') || '';
    const match = /translate\(\s*(-?\d+(?:\.\d+)?)(?:[,\s]+(-?\d+(?:\.\d+)?))?/.exec(transform);
    if (match) {
      x += Number.parseFloat(match[1]) || 0;
      y += Number.parseFloat(match[2]) || 0;
    }
    current = current.parentElement;
  }
  return { x, y };
}

function includePoint(bounds, x, y) {
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    return;
  }
  bounds.minX = Math.min(bounds.minX, x);
  bounds.minY = Math.min(bounds.minY, y);
  bounds.maxX = Math.max(bounds.maxX, x);
  bounds.maxY = Math.max(bounds.maxY, y);
}

function estimateSvgExtents(element) {
  const bounds = { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
  for (const child of element.querySelectorAll?.('*') || []) {
    const tagName = String(child.tagName || '').toLowerCase();
    if (tagName === 'style' || tagName === 'script' || tagName === 'defs' || tagName === 'marker') {
      continue;
    }
    const offset = transformOffset(child);
    const x = Number.parseFloat(child.getAttribute?.('x'));
    const y = Number.parseFloat(child.getAttribute?.('y'));
    const width = Number.parseFloat(child.getAttribute?.('width'));
    const height = Number.parseFloat(child.getAttribute?.('height'));
    if (Number.isFinite(x) || Number.isFinite(y) || Number.isFinite(width) || Number.isFinite(height)) {
      const left = Number.isFinite(x) ? x + offset.x : offset.x;
      const top = Number.isFinite(y) ? y + offset.y : offset.y;
      includePoint(bounds, left, top);
      includePoint(bounds, left + (Number.isFinite(width) ? width : estimateTextWidth(child)), top + (Number.isFinite(height) ? height : 16));
    }
    const points = child.getAttribute?.('points');
    if (points) {
      const values = points.match(/-?\d+(?:\.\d+)?/g)?.map(Number) || [];
      for (let i = 0; i + 1 < values.length; i += 2) {
        includePoint(bounds, values[i] + offset.x, values[i + 1] + offset.y);
      }
    }
    const d = child.getAttribute?.('d');
    if (d) {
      const values = d.match(/-?\d+(?:\.\d+)?/g)?.map(Number) || [];
      for (let i = 0; i + 1 < values.length; i += 2) {
        includePoint(bounds, values[i] + offset.x, values[i + 1] + offset.y);
      }
    }
  }
  if (!Number.isFinite(bounds.minX) || !Number.isFinite(bounds.maxX)) {
    return null;
  }
  return {
    width: Math.max(10, bounds.maxX - Math.min(0, bounds.minX)),
    height: Math.max(10, bounds.maxY - Math.min(0, bounds.minY)),
  };
}

function patchSvgMetrics(window) {
  const SVGElement = window.SVGElement || window.Element;
  if (!SVGElement?.prototype) {
    return;
  }

  SVGElement.prototype.getBBox = function getBBox() {
    const { width, height } = estimateBox(this);
    return {
      x: 0,
      y: 0,
      width,
      height,
      top: 0,
      left: 0,
      right: width,
      bottom: height,
      toJSON() {
        return this;
      },
    };
  };

  SVGElement.prototype.getComputedTextLength = function getComputedTextLength() {
    return estimateTextWidth(this);
  };

  SVGElement.prototype.getBoundingClientRect = function getBoundingClientRect() {
    const box = this.getBBox();
    return {
      ...box,
      toJSON() {
        return this;
      },
    };
  };

  const patchElementDimensions = (prototype) => {
    if (!prototype) {
      return;
    }
    Object.defineProperty(prototype, 'clientWidth', {
      configurable: true,
      get() {
        return Number.parseFloat(this.getAttribute?.('width')) || 1024;
      },
    });
    Object.defineProperty(prototype, 'clientHeight', {
      configurable: true,
      get() {
        return Number.parseFloat(this.getAttribute?.('height')) || 768;
      },
    });
  };
  patchElementDimensions(SVGElement.prototype);
  patchElementDimensions(window.Element?.prototype);
  patchElementDimensions(window.HTMLElement?.prototype);
}

function installDom() {
  const { window, document } = parseHTML('<!doctype html><html><body></body></html>');
  patchSvgMetrics(window);
  window.getComputedStyle = window.getComputedStyle || function getComputedStyle(element) {
    const style = element?.style || {};
    return {
      getPropertyValue(name) {
        const value = style.getPropertyValue?.(name) || style[name];
        if (value !== undefined && value !== null && value !== '') {
          return value;
        }
        if (/^(padding|border|margin)/.test(name)) {
          return '0px';
        }
        return '';
      },
      fontSize: style.fontSize || '14px',
      fontFamily: style.fontFamily || 'Arial, sans-serif',
      textAnchor: style.textAnchor || 'start',
    };
  };
  const originalCreateElement = document.createElement.bind(document);
  document.createElement = function createElement(name) {
    const element = originalCreateElement(name);
    if (String(name).toLowerCase() === 'canvas') {
      element.getContext = function getContext(type) {
        if (type !== '2d') {
          return null;
        }
        return {
          measureText(text) {
            const width = Math.max(1, String(text || '').length * 8);
            return {
              width,
              actualBoundingBoxAscent: 11,
              actualBoundingBoxDescent: 4,
              fontBoundingBoxAscent: 12,
              fontBoundingBoxDescent: 4,
            };
          },
          beginPath() {},
          moveTo() {},
          lineTo() {},
          arc() {},
          closePath() {},
          fill() {},
          stroke() {},
          clearRect() {},
          fillRect() {},
          strokeRect() {},
          save() {},
          restore() {},
          translate() {},
          rotate() {},
          scale() {},
        };
      };
    }
    return element;
  };
  const originalGetElementById = document.getElementById.bind(document);
  document.getElementById = function getElementById(id) {
    return originalGetElementById(id) || document.querySelector(`#${String(id).replace(/"/g, '\\"')}`);
  };

  globalThis.window = window;
  globalThis.document = document;
  globalThis.navigator = window.navigator || { userAgent: 'GraalJS' };
  globalThis.Element = window.Element;
  globalThis.HTMLElement = window.HTMLElement || window.Element;
  globalThis.SVGElement = window.SVGElement || window.Element;
  globalThis.Node = window.Node;
  globalThis.location = window.location || {
    protocol: 'http:',
    host: 'localhost',
    pathname: '/',
    search: '',
  };
  window.setTimeout = globalThis.setTimeout;
  window.clearTimeout = globalThis.clearTimeout;
  window.requestAnimationFrame = globalThis.requestAnimationFrame;
  window.cancelAnimationFrame = globalThis.cancelAnimationFrame;
  globalThis.DOMPurify = {
    sanitize(value) {
      return value;
    },
  };

  return window;
}

globalThis.renderMermaidToSvg = async function renderMermaidToSvg(definition) {
  if (!definition || !definition.trim()) {
    throw new Error('Mermaid definition is required');
  }

  const window = installDom();
  const { default: mermaid } = await import('mermaid/dist/mermaid.esm.mjs');
  const { default: zenuml } = await import('@mermaid-js/mermaid-zenuml');
  const id = 'dmtools-mermaid';

  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'loose',
    deterministicIds: true,
    deterministicIDSeed: 'dmtools',
    flowchart: {
      htmlLabels: false,
    },
  });
  await mermaid.registerExternalDiagrams([zenuml]);

  try {
    const result = await mermaid.render(id, definition);
    return result.svg;
  } catch (error) {
    if (error?.stack) {
      throw new Error(error.stack);
    }
    throw error;
  } finally {
    window.close?.();
  }
};
