import { parseHTML } from 'linkedom';

function visibleText(element) {
  const tagName = String(element?.tagName || '').toLowerCase();
  if (tagName === 'style' || tagName === 'script') {
    return '';
  }
  if (['text', 'span', 'p', 'div'].includes(tagName)) {
    return element?.textContent || '';
  }
  if (tagName === 'tspan') {
    const children = Array.from(element?.children || []);
    return children.length ? children.map(visibleText).join(' ') : element?.textContent || '';
  }
  return Array.from(element?.children || [])
    .map(visibleText)
    .filter(Boolean)
    .join(' ');
}

function estimateTextWidth(element) {
  const tagName = String(element?.tagName || '').toLowerCase();
  if (tagName === 'style' || tagName === 'script') {
    return 0;
  }
  const text = visibleText(element);
  const fontSize = parseFontSize(element);
  return Math.max(10, text.length * fontSize * 0.58);
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
  const text = visibleText(element);
  const lines = estimateLineCount(element, text);
  const fontSize = parseFontSize(element);
  return {
    width: Math.max(10, text.length * fontSize * 0.58),
    height: Math.max(fontSize * 1.45, lines * fontSize * 1.45),
  };
}

function parseFontSize(element) {
  const styleSize = element?.style?.fontSize;
  const attrStyle = element?.getAttribute?.('style') || '';
  const attrMatch = /font-size\s*:\s*(\d+(?:\.\d+)?)px/.exec(attrStyle);
  const raw = styleSize || (attrMatch ? `${attrMatch[1]}px` : '');
  const parsed = Number.parseFloat(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 16;
}

function parseFontFamily(element) {
  const attrStyle = element?.getAttribute?.('style') || '';
  const match = /font-family\s*:\s*([^;]+)/.exec(attrStyle);
  if (match) return match[1].trim();
  const fontAttr = element?.getAttribute?.('font-family');
  if (fontAttr) return fontAttr;
  return 'sans-serif';
}

function estimateLineCount(element, text) {
  const tagName = String(element?.tagName || '').toLowerCase();
  if (tagName === 'text') {
    const rowCount = element.querySelectorAll?.('tspan.row').length || 0;
    if (rowCount > 0) {
      return rowCount;
    }
    const directTspans = element.querySelectorAll?.(':scope > tspan').length || 0;
    if (directTspans > 1) {
      return directTspans;
    }
  }
  const paragraphs = element?.querySelectorAll?.('p')?.length || 0;
  if (paragraphs > 0) {
    return paragraphs;
  }
  return Math.max(1, String(text || '').split(/\n/).length);
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

const SKIP_IN_EXTENTS = new Set(['style', 'script', 'defs', 'marker', 'clippath', 'pattern', 'mask', 'symbol', 'lineargradient', 'radialgradient', 'filter', 'fegaussianblur', 'feblend', 'fecomposite']);

function isInsideDefLike(element) {
  let p = element.parentElement;
  while (p) {
    const t = String(p.tagName || '').toLowerCase();
    if (SKIP_IN_EXTENTS.has(t)) return true;
    p = p.parentElement;
  }
  return false;
}

function parseSvgPathPoints(d) {
  // Only extract endpoint coordinates from absolute M, L, C, Q, S, T, A commands.
  // Skip relative commands (lowercase) and arc parameters.
  const points = [];
  const tokens = d.match(/[MLCSQTAHVZmlcsqtahvz]|[-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?/g) || [];
  let cmd = 'M';
  let i = 0;
  while (i < tokens.length) {
    const t = tokens[i];
    if (/^[MLCSQTAHVZmlcsqtahvz]$/.test(t)) {
      cmd = t;
      i++;
      continue;
    }
    const num = (j) => Number(tokens[i + j] || 0);
    switch (cmd) {
      case 'M': case 'L': case 'T':
        if (i + 1 < tokens.length) { points.push([num(0), num(1)]); i += 2; } else { i++; }
        break;
      case 'C':
        if (i + 5 < tokens.length) { points.push([num(4), num(5)]); i += 6; } else { i++; }
        break;
      case 'Q': case 'S':
        if (i + 3 < tokens.length) { points.push([num(2), num(3)]); i += 4; } else { i++; }
        break;
      case 'A':
        if (i + 6 < tokens.length) { points.push([num(5), num(6)]); i += 7; } else { i++; }
        break;
      case 'H':
        // only x — skip vertical component
        i += 1;
        break;
      case 'V':
        // only y — skip
        i += 1;
        break;
      default:
        // lowercase (relative) or unknown — skip
        i++;
    }
  }
  return points;
}

function estimateSvgExtents(element) {
  const bounds = { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
  for (const child of element.querySelectorAll?.('*') || []) {
    const tagName = String(child.tagName || '').toLowerCase();
    if (SKIP_IN_EXTENTS.has(tagName) || isInsideDefLike(child)) {
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
    const x1 = Number.parseFloat(child.getAttribute?.('x1'));
    const y1 = Number.parseFloat(child.getAttribute?.('y1'));
    const x2 = Number.parseFloat(child.getAttribute?.('x2'));
    const y2 = Number.parseFloat(child.getAttribute?.('y2'));
    if (Number.isFinite(x1) && Number.isFinite(y1)) includePoint(bounds, x1 + offset.x, y1 + offset.y);
    if (Number.isFinite(x2) && Number.isFinite(y2)) includePoint(bounds, x2 + offset.x, y2 + offset.y);
    const points = child.getAttribute?.('points');
    if (points) {
      const values = points.match(/-?\d+(?:\.\d+)?/g)?.map(Number) || [];
      for (let i = 0; i + 1 < values.length; i += 2) {
        includePoint(bounds, values[i] + offset.x, values[i + 1] + offset.y);
      }
    }
    const d = child.getAttribute?.('d');
    if (d && d.length < 500) {
      for (const [px, py] of parseSvgPathPoints(d)) {
        includePoint(bounds, px + offset.x, py + offset.y);
      }
    }
  }
  if (!Number.isFinite(bounds.minX) || !Number.isFinite(bounds.maxX)) {
    return null;
  }
  return {
    minX: bounds.minX,
    minY: bounds.minY,
    maxX: bounds.maxX,
    maxY: bounds.maxY,
    width: Math.max(10, bounds.maxX - bounds.minX),
    height: Math.max(10, bounds.maxY - bounds.minY),
  };
}

function normalizeSvgOutput(svgText) {
  // Prefer Mermaid's own viewBox — only recompute if missing or zero-sized.
  const viewBoxMatch = /viewBox="([^"]+)"/.exec(svgText);
  if (viewBoxMatch) {
    const vals = viewBoxMatch[1].trim().split(/\s+/).map(Number);
    if (vals.length === 4 && vals.every(Number.isFinite) && vals[2] > 0 && vals[3] > 0) {
      // Mermaid provided a valid viewBox — keep it, just update max-width style.
      const [, , w] = vals;
      const nextStyle = `style="max-width: ${w}px;"`;
      const svgWithStyle = /<svg\b[^>]*\sstyle="[^"]*"/.test(svgText)
        ? svgText.replace(/(<svg\b[^>]*?)\sstyle="[^"]*"/, `$1 ${nextStyle}`)
        : svgText.replace('<svg ', `<svg ${nextStyle} `);
      return svgWithStyle;
    }
  }
  // Fallback: compute viewBox from element positions.
  const { document } = parseHTML(svgText);
  const svg = document.querySelector('svg');
  if (!svg) {
    return svgText;
  }
  const bounds = estimateSvgExtents(svg);
  if (!bounds) {
    return svgText;
  }
  const padding = 10;
  const minX = bounds.minX - padding;
  const minY = bounds.minY - padding;
  const width = Math.max(10, bounds.width + padding * 2);
  const height = Math.max(10, bounds.height + padding * 2);
  const nextViewBox = `viewBox="${minX} ${minY} ${width} ${height}"`;
  const nextStyle = `style="max-width: ${width}px;"`;
  let normalized = viewBoxMatch
    ? svgText.replace(/viewBox="[^"]+"/, nextViewBox)
    : svgText.replace('<svg ', `<svg ${nextViewBox} `);
  normalized = /<svg\b[^>]*\sstyle="[^"]*"/.test(normalized)
    ? normalized.replace(/(<svg\b[^>]*?)\sstyle="[^"]*"/, `$1 ${nextStyle}`)
    : normalized.replace('<svg ', `<svg ${nextStyle} `);
  return normalized;
}

function patchSvgMetrics(window, javaMetrics) {
  const SVGElement = window.SVGElement || window.Element;
  if (!SVGElement?.prototype) {
    return;
  }

  function realTextWidth(element) {
    if (!javaMetrics) return estimateTextWidth(element);
    const text = visibleText(element);
    if (!text) return 0;
    const fontSize = parseFontSize(element);
    const fontFamily = parseFontFamily(element);
    try {
      return Number(javaMetrics.measureWidth(text, fontSize, fontFamily)) || estimateTextWidth(element);
    } catch (_) {
      return estimateTextWidth(element);
    }
  }

  function realBox(element) {
    const tagName = String(element?.tagName || '').toLowerCase();
    if (tagName === 'svg' || String(element?.getAttribute?.('class') || '').split(/\s+/).includes('root')) {
      const extents = estimateSvgExtents(element);
      return extents || { width: 1024, height: 768 };
    }
    const attrW = Number.parseFloat(element?.getAttribute?.('width'));
    const attrH = Number.parseFloat(element?.getAttribute?.('height'));
    if (Number.isFinite(attrW) || Number.isFinite(attrH)) {
      return {
        width: Number.isFinite(attrW) ? attrW : realTextWidth(element),
        height: Number.isFinite(attrH) ? attrH : 16,
      };
    }
    if (tagName === 'style' || tagName === 'script') return { width: 0, height: 0 };
    if (!javaMetrics) return estimateBox(element);
    const text = visibleText(element);
    const fontSize = parseFontSize(element);
    const fontFamily = parseFontFamily(element);
    try {
      const w = Number(javaMetrics.measureWidth(text, fontSize, fontFamily)) || estimateTextWidth(element);
      const h = Number(javaMetrics.measureHeight(fontSize, fontFamily)) * Math.max(1, estimateLineCount(element, text));
      return { width: w, height: h };
    } catch (_) {
      return estimateBox(element);
    }
  }

  SVGElement.prototype.getBBox = function getBBox() {
    const { width, height } = realBox(this);
    const tagName = String(this?.tagName || '').toLowerCase();
    const y = tagName === 'text' || tagName === 'tspan' ? -height * 0.75 : 0;
    return {
      x: 0,
      y,
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
    return realTextWidth(this);
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

function installDom(javaMetrics) {
  const { window, document } = parseHTML('<!doctype html><html><body></body></html>');
  patchSvgMetrics(window, javaMetrics);
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
      let canvasFont = '14px sans-serif';
      element.getContext = function getContext(type) {
        if (type !== '2d') {
          return null;
        }
        return {
          get font() { return canvasFont; },
          set font(v) { canvasFont = v || canvasFont; },
          measureText(text) {
            let width;
            if (javaMetrics) {
              try {
                const match = /(\d+(?:\.\d+)?)px\s+(.+)/.exec(canvasFont);
                const fontSize = match ? Number(match[1]) : 14;
                const fontFamily = match ? match[2] : 'sans-serif';
                width = Number(javaMetrics.measureWidth(String(text || ''), fontSize, fontFamily));
              } catch (_) {
                width = 0;
              }
            }
            if (!width) {
              width = Math.max(1, String(text || '').length * 8);
            }
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

globalThis.renderMermaidToSvg = async function renderMermaidToSvg(definition, javaMetrics) {
  if (!definition || !definition.trim()) {
    throw new Error('Mermaid definition is required');
  }

  const window = installDom(javaMetrics);
  const { default: mermaid } = await import('mermaid/dist/mermaid.esm.mjs');
  const { default: zenuml } = await import('@mermaid-js/mermaid-zenuml');
  const id = 'dmtools-mermaid';

  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'loose',
    deterministicIds: true,
    deterministicIDSeed: 'dmtools',
    theme: 'default',
    look: 'classic',
    htmlLabels: false,
    flowchart: {
      htmlLabels: false,
    },
    class: {
      htmlLabels: false,
    },
    er: {
      htmlLabels: false,
    },
  });
  await mermaid.registerExternalDiagrams([zenuml]);

  try {
    const result = await mermaid.render(id, definition);
    return normalizeSvgOutput(result.svg);
  } catch (error) {
    if (error?.stack) {
      throw new Error(error.stack);
    }
    throw error;
  } finally {
    window.close?.();
  }
};
