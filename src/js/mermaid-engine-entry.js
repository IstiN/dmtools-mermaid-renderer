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
  if (!text) return 0;
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
    // Container elements (<g>, <svg>) position their children via `transform`,
    // NOT via `x`/`y` attributes. Using `x`/`y` on <g> in addition to the
    // accumulated transform offset would double-count the position (e.g. sankey
    // sets both `x=590` AND `transform="translate(590,...)"` on node groups).
    const isContainer = tagName === 'g' || tagName === 'svg' || tagName === 'a'
      || tagName === 'defs' || tagName === 'marker' || tagName === 'clippath'
      || tagName === 'symbol' || tagName === 'pattern';
    const x = isContainer ? NaN : Number.parseFloat(child.getAttribute?.('x'));
    const y = isContainer ? NaN : Number.parseFloat(child.getAttribute?.('y'));
    const width = isContainer ? NaN : Number.parseFloat(child.getAttribute?.('width'));
    const height = isContainer ? NaN : Number.parseFloat(child.getAttribute?.('height'));
    if (Number.isFinite(x) || Number.isFinite(y) || Number.isFinite(width) || Number.isFinite(height)) {
      const estW = Number.isFinite(width) ? width : (tagName === 'text' || tagName === 'tspan' ? estimateTextWidth(child) : 10);
      const estH = Number.isFinite(height) ? height : 16;
      // Respect text-anchor for text/tspan: "end" means text extends LEFT of x;
      // "middle" means text is centered on x; "start" (default) extends RIGHT.
      let left = Number.isFinite(x) ? x + offset.x : offset.x;
      let top = Number.isFinite(y) ? y + offset.y : offset.y;
      if (tagName === 'text' || tagName === 'tspan') {
        const anchor = getEffectiveTextAnchor(child);
        if (anchor === 'end') {
          left -= estW;
        } else if (anchor === 'middle') {
          left -= estW / 2;
        }
      }
      includePoint(bounds, left, top);
      includePoint(bounds, left + estW, top + estH);
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
    if (d) {
      // Parse path points. For very long paths (>500 chars), parse only the
      // Move-to (M/m) and first few coordinates to get approximate bounds.
      const pathPoints = d.length < 2000
        ? parseSvgPathPoints(d)
        : parseSvgPathPoints(d.slice(0, 500));
      for (const [px, py] of pathPoints) {
        includePoint(bounds, px + offset.x, py + offset.y);
      }
    }
    // Handle circle/ellipse elements (cx/cy/r/rx/ry attributes)
    const cx = Number.parseFloat(child.getAttribute?.('cx'));
    const cy = Number.parseFloat(child.getAttribute?.('cy'));
    if (Number.isFinite(cx) || Number.isFinite(cy)) {
      const r = Number.parseFloat(child.getAttribute?.('r')) || 0;
      const rx = Number.parseFloat(child.getAttribute?.('rx')) || r;
      const ry = Number.parseFloat(child.getAttribute?.('ry')) || r;
      const cxVal = (Number.isFinite(cx) ? cx : 0) + offset.x;
      const cyVal = (Number.isFinite(cy) ? cy : 0) + offset.y;
      includePoint(bounds, cxVal - rx, cyVal - ry);
      includePoint(bounds, cxVal + rx, cyVal + ry);
    }
  }
  if (!Number.isFinite(bounds.minX) || !Number.isFinite(bounds.maxX)) {
    return null;
  }
  return {
    x: bounds.minX,
    y: bounds.minY,
    minX: bounds.minX,
    minY: bounds.minY,
    maxX: bounds.maxX,
    maxY: bounds.maxY,
    width: Math.max(10, bounds.maxX - bounds.minX),
    height: Math.max(10, bounds.maxY - bounds.minY),
  };
}

function normalizeSvgOutput(svgText) {
  // Parse the SVG to compute actual content bounds as a safety net.
  // This ensures content that falls outside Mermaid's computed viewBox (e.g.
  // kanban sections at y=-300, git branch labels at x<0) is not clipped.
  const { document } = parseHTML(svgText);
  const svg = document.querySelector('svg');
  const contentBounds = svg ? estimateSvgExtents(svg) : null;

  const viewBoxMatch = /viewBox="([^"]+)"/.exec(svgText);
  if (viewBoxMatch) {
    const vals = viewBoxMatch[1].trim().split(/\s+/).map(Number);
    if (vals.length === 4 && vals.every(Number.isFinite) && vals[2] > 0 && vals[3] > 0) {
      // Mermaid provided a valid viewBox. Expand it if content extends outside.
      const [vbX, vbY, vbW, vbH] = vals;
      let minX = vbX, minY = vbY, maxX = vbX + vbW, maxY = vbY + vbH;
      if (contentBounds) {
        const sidePadding = 10;
        const bottomPadding = 20;
        // Compute top padding adaptively: text with dominant-baseline="middle" or
        // "central" has its CENTER at y, so the top of the text is at y - fontSize/2
        // and can extend ABOVE contentBounds.minY. "hanging" baseline text hangs BELOW
        // its y coordinate so no extra space is needed above it (8px visual margin
        // is sufficient). Use 25px for "middle"/"central" to handle large CSS fonts
        // (e.g. venn .venn-title at 32px) that override the attribute font-size.
        let topPadding = 8; // default: small margin for "hanging" / normal baseline
        if (svg) {
          const allTexts = svg.querySelectorAll('text');
          for (const t of allTexts) {
            const baseline = t.getAttribute('dominant-baseline') || 'auto';
            if (baseline === 'middle' || baseline === 'central') {
              // Compute global y of this text element
              const localY = parseFloat(t.getAttribute('y') || '0') || 0;
              const off = transformOffset(t);
              const globalY = localY + off.y;
              // If this text is near the top of the content, we need more padding
              if (globalY <= contentBounds.minY + 5) {
                topPadding = 25;
                break;
              }
            }
          }
        }
        // Only expand the viewBox — never shrink it (estimateSvgExtents may underestimate).
        minX = Math.min(minX, contentBounds.minX - sidePadding);
        minY = Math.min(minY, contentBounds.minY - topPadding);
        maxX = Math.max(maxX, contentBounds.maxX + sidePadding);
        maxY = Math.max(maxY, contentBounds.maxY + bottomPadding);
      }
      const w = maxX - minX;
      const h = maxY - minY;
      const nextViewBox = `viewBox="${minX} ${minY} ${w} ${h}"`;
      const nextStyle = `style="max-width: ${w}px;"`;
      let normalized = svgText.replace(/viewBox="[^"]+"/, nextViewBox);
      normalized = /<svg\b[^>]*\sstyle="[^"]*"/.test(normalized)
        ? normalized.replace(/(<svg\b[^>]*?)\sstyle="[^"]*"/, `$1 ${nextStyle}`)
        : normalized.replace('<svg ', `<svg ${nextStyle} `);
      return normalized;
    }
  }
  // Fallback: compute viewBox entirely from element positions.
  if (!svg || !contentBounds) {
    return svgText;
  }
  const padding = 10;
  const minX = contentBounds.minX - padding;
  const minY = contentBounds.minY - padding;
  const width = Math.max(10, contentBounds.width + padding * 2);
  const height = Math.max(10, contentBounds.height + padding * 2);
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

// Cache: maps SVG root element → Array<{classSet, props}>
const cssPropsCache = new WeakMap();

// Parse CSS class rules in the SVG's <style> element for text-layout properties.
// Each rule is stored as {classSet: Set<string>, props: {textAnchor?, dominantBaseline?}}.
// Compound selectors like ".foo.bar" require the element to have ALL classes.
function buildCssPropMap(svgRoot) {
  if (cssPropsCache.has(svgRoot)) return cssPropsCache.get(svgRoot);
  const rules = [];
  for (const styleEl of svgRoot.querySelectorAll?.('style') || []) {
    const css = styleEl.textContent || '';
    if (!css) continue;
    for (const [, selector, block] of css.matchAll(/([^{]+)\{([^}]*)\}/g)) {
      const anchorMatch = /\btext-anchor\s*:\s*([\w-]+)/.exec(block);
      const baselineMatch = /\bdominant-baseline\s*:\s*([\w-]+)/.exec(block);
      if (!anchorMatch && !baselineMatch) continue;
      const props = {};
      if (anchorMatch) props.textAnchor = anchorMatch[1];
      if (baselineMatch) props.dominantBaseline = baselineMatch[1];
      // Each comma-separated sub-selector is treated independently.
      // Only extract classes from the LAST simple selector (the target element).
      // e.g. ".ishikawa .foo.bar" → last part is ".foo.bar" → requires {foo, bar}
      // e.g. ".ishikawa .foo" → last part is ".foo" → requires {foo}
      for (const subSel of selector.split(',')) {
        // Split on CSS combinators (whitespace, >, +, ~) to isolate the last simple selector
        const parts = subSel.trim().split(/[\s>+~]+/);
        const lastPart = parts[parts.length - 1];
        const classes = (lastPart.match(/\.[-\w]+/g) || []).map(c => c.slice(1));
        if (classes.length > 0) {
          rules.push({ classSet: new Set(classes), props });
        }
      }
    }
  }
  if (rules.length > 0) cssPropsCache.set(svgRoot, rules);
  return rules;
}

// Get CSS text-layout properties for an element by matching its classes
// against CSS rules. Returns the props from the MOST SPECIFIC matching rule
// (most classes matched), or {} if none match.
function getEffectiveCssProps(element) {
  const svgRoot = element.closest?.('svg');
  if (!svgRoot) return {};
  const rules = buildCssPropMap(svgRoot);
  const elementClasses = new Set((element.getAttribute?.('class') || '').split(/\s+/).filter(Boolean));
  let best = null;
  let bestSize = 0;
  for (const rule of rules) {
    // All classes in rule.classSet must be present in the element
    let allMatch = true;
    for (const c of rule.classSet) {
      if (!elementClasses.has(c)) { allMatch = false; break; }
    }
    if (allMatch && rule.classSet.size > bestSize) {
      best = rule.props;
      bestSize = rule.classSet.size;
    }
  }
  return best || {};
}

// Get the effective text-anchor for an element, considering CSS class rules
// which override presentation attributes (SVG spec: CSS wins over attributes).
function getEffectiveTextAnchor(element) {
  const inline = element.style?.textAnchor;
  if (inline) return inline;
  const cssProps = getEffectiveCssProps(element);
  if (cssProps.textAnchor) return cssProps.textAnchor;
  return element.getAttribute?.('text-anchor')
    || element.closest?.('text')?.getAttribute?.('text-anchor')
    || 'start';
}

// Get the effective dominant-baseline for an element, considering CSS class rules.
function getEffectiveDominantBaseline(element) {
  const inline = element.style?.dominantBaseline;
  if (inline) return inline;
  const cssProps = getEffectiveCssProps(element);
  if (cssProps.dominantBaseline) return cssProps.dominantBaseline;
  return element.getAttribute?.('dominant-baseline') || 'auto';
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
    // Empty text → zero width (don't fall back to min-width estimate)
    if (!text) return { width: 0, height: Number(javaMetrics.measureHeight(fontSize, fontFamily)) || 16 };
    try {
      const w = Number(javaMetrics.measureWidth(text, fontSize, fontFamily)) || estimateTextWidth(element);
      const h = Number(javaMetrics.measureHeight(fontSize, fontFamily)) * Math.max(1, estimateLineCount(element, text));
      return { width: w, height: h };
    } catch (_) {
      return estimateBox(element);
    }
  }

  // Parse a translate(tx, ty) transform from an element's transform attribute.
  function parseTranslate(element) {
    const tr = element?.getAttribute?.('transform') || '';
    const m = /translate\(\s*([^,)\s]+)[\s,]*([^)]*)\)/.exec(tr);
    return {
      tx: m ? Number.parseFloat(m[1]) || 0 : 0,
      ty: m ? Number.parseFloat(m[2]) || 0 : 0,
    };
  }

  // Compute getBBox for a <g>/<a> container by unioning children's bboxes.
  // Includes text, nested groups, and most geometric shapes. Excludes <rect>
  // because Mermaid creates background/border rects AFTER calling getBBox
  // to measure content — including them creates a feedback loop.
  function groupBBox(element) {
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    const skip = new Set(['style', 'script', 'defs', 'marker', 'clippath', 'symbol', 'pattern', 'rect']);
    for (const child of element.children || []) {
      const ct = String(child.tagName || '').toLowerCase();
      if (skip.has(ct)) continue;
      if (typeof child.getBBox !== 'function') continue;
      const box = child.getBBox();
      if (box.width === 0 && box.height === 0) continue;
      const { tx, ty } = parseTranslate(child);
      const l = box.x + tx;
      const t = box.y + ty;
      minX = Math.min(minX, l);
      minY = Math.min(minY, t);
      maxX = Math.max(maxX, l + box.width);
      maxY = Math.max(maxY, t + box.height);
    }
    const valid = Number.isFinite(minX);
    const x = valid ? minX : 0;
    const y = valid ? minY : 0;
    const w = valid ? maxX - minX : 0;
    const h = valid ? maxY - minY : 0;
    return { x, y, width: w, height: h, top: y, left: x, right: x + w, bottom: y + h, toJSON() { return this; } };
  }

  // Read a numeric attribute, returning 0 if missing/NaN.
  function numAttr(el, name) {
    return Number.parseFloat(el?.getAttribute?.(name)) || 0;
  }

  // Compute bbox for geometric SVG elements from their attributes.
  function geometricBBox(element, tagName) {
    switch (tagName) {
      case 'rect':
      case 'image':
      case 'foreignobject':
      case 'use': {
        const x = numAttr(element, 'x');
        const y = numAttr(element, 'y');
        const w = numAttr(element, 'width');
        const h = numAttr(element, 'height');
        if (w > 0 || h > 0) return { x, y, width: w, height: h };
        return null;
      }
      case 'circle': {
        const cx = numAttr(element, 'cx');
        const cy = numAttr(element, 'cy');
        const r = numAttr(element, 'r');
        if (r > 0) return { x: cx - r, y: cy - r, width: 2 * r, height: 2 * r };
        return null;
      }
      case 'ellipse': {
        const cx = numAttr(element, 'cx');
        const cy = numAttr(element, 'cy');
        const rx = numAttr(element, 'rx');
        const ry = numAttr(element, 'ry');
        if (rx > 0 || ry > 0) return { x: cx - rx, y: cy - ry, width: 2 * rx, height: 2 * ry };
        return null;
      }
      case 'line': {
        const x1 = numAttr(element, 'x1'), y1 = numAttr(element, 'y1');
        const x2 = numAttr(element, 'x2'), y2 = numAttr(element, 'y2');
        const lx = Math.min(x1, x2), ly = Math.min(y1, y2);
        return { x: lx, y: ly, width: Math.abs(x2 - x1), height: Math.abs(y2 - y1) };
      }
      case 'path':
      case 'polygon':
      case 'polyline': {
        // Parse coordinates from d (path) or points (polygon/polyline) attributes.
        // Handles M, L, H, V, C, S, Q, T, A commands (absolute only — Mermaid
        // uses absolute coords). Computes tight bounding box from all coordinate pairs.
        const raw = tagName === 'path'
          ? (element?.getAttribute?.('d') || '')
          : (element?.getAttribute?.('points') || '');
        if (!raw) return null;
        let xs = [], ys = [];
        if (tagName === 'path') {
          // Extract all numeric coordinate pairs from path data.
          // Strategy: split by commands, then collect numbers.
          const nums = raw.replace(/[MLHVCSQTAZmlhvcsqtaz,]/g, ' ').trim().split(/\s+/).map(Number);
          // Also parse commands to understand structure
          const cmds = raw.match(/[MLHVCSQTAZmlhvcsqtaz][^MLHVCSQTAZmlhvcsqtaz]*/g) || [];
          let cx = 0, cy = 0;
          for (const cmd of cmds) {
            const type = cmd[0];
            const args = cmd.slice(1).trim().replace(/,/g, ' ').split(/\s+/).filter(s => s).map(Number);
            switch (type) {
              case 'M': case 'L': case 'T':
                for (let i = 0; i + 1 < args.length; i += 2) {
                  cx = args[i]; cy = args[i + 1];
                  xs.push(cx); ys.push(cy);
                }
                break;
              case 'H':
                for (const v of args) { cx = v; xs.push(cx); ys.push(cy); }
                break;
              case 'V':
                for (const v of args) { cy = v; ys.push(cy); xs.push(cx); }
                break;
              case 'C':
                for (let i = 0; i + 5 < args.length; i += 6) {
                  xs.push(args[i], args[i + 2], args[i + 4]);
                  ys.push(args[i + 1], args[i + 3], args[i + 5]);
                  cx = args[i + 4]; cy = args[i + 5];
                }
                break;
              case 'S': case 'Q':
                { const step = type === 'Q' ? 4 : 4;
                  for (let i = 0; i + step - 1 < args.length; i += step) {
                    for (let j = 0; j < step; j += 2) { xs.push(args[i + j]); ys.push(args[i + j + 1]); }
                    cx = args[i + step - 2]; cy = args[i + step - 1];
                  }
                }
                break;
              case 'A':
                for (let i = 0; i + 6 < args.length; i += 7) {
                  cx = args[i + 5]; cy = args[i + 6];
                  xs.push(cx); ys.push(cy);
                }
                break;
              case 'Z': case 'z':
                break;
              // Relative commands — approximate by using current position
              default:
                break;
            }
          }
        } else {
          // polygon/polyline: "x1,y1 x2,y2 ..."
          const pairs = raw.trim().split(/\s+/);
          for (const p of pairs) {
            const [x, y] = p.split(',').map(Number);
            if (Number.isFinite(x)) xs.push(x);
            if (Number.isFinite(y)) ys.push(y);
          }
        }
        if (xs.length === 0) return null;
        const minX = Math.min(...xs), minY = Math.min(...ys);
        const maxX = Math.max(...xs), maxY = Math.max(...ys);
        return { x: minX, y: minY, width: maxX - minX, height: maxY - minY };
      }
      default:
        return null;
    }
  }

  SVGElement.prototype.getBBox = function getBBox() {
    const tagName = String(this?.tagName || '').toLowerCase();
    // Container elements: recursively union children's bboxes (browser behavior).
    if (tagName === 'g' || tagName === 'a') {
      return groupBBox(this);
    }

    // Geometric elements: read dimensions from attributes.
    const geo = geometricBBox(this, tagName);
    if (geo) {
      return { ...geo, top: geo.y, left: geo.x, right: geo.x + geo.width, bottom: geo.y + geo.height, toJSON() { return this; } };
    }

    // Text elements: use AWT text measurement with proper positioning.
    const box = realBox(this);
    const { width, height } = box;
    // For svg/root elements, realBox returns estimateSvgExtents which includes x/y.
    // Use those as defaults so setupGraphViewbox gets the correct bounding origin.
    let x = box.x ?? 0;
    let y = box.y ?? 0;

    if (tagName === 'text' || tagName === 'tspan') {
      // getBBox() must return coordinates in the SVG coordinate system, including
      // the element's own x/y position. text-anchor shifts the left edge relative
      // to the x anchor point:
      //   start: left edge = x
      //   middle: left edge = x - width/2
      //   end: left edge = x - width
      const elementX = Number.parseFloat(this.getAttribute?.('x')) || 0;
      const anchor = getEffectiveTextAnchor(this);
      if (anchor === 'middle') x = elementX - width / 2;
      else if (anchor === 'end') x = elementX - width;
      else x = elementX; // 'start'

      // Compute y from element attributes to match browser baseline positioning.
      // Browser getBBox.y = rendered_baseline - ascent.
      // Mermaid edge labels use: <text y="-10.1"><tspan y="-0.1em" dy="1.1em">label</tspan></text>
      // The tspan y overrides text y, then dy shifts from there.
      const fontSize = parseFontSize(this);
      const fontFamily = parseFontFamily(this);
      // Use real AWT ascent for precise alignment with Batik rendering.
      const ascent = javaMetrics
        ? Number(javaMetrics.measureAscent(fontSize, fontFamily))
        : height * 0.75;
      let baseline = 0;
      const firstTspan = this.querySelector?.('tspan');
      // Parse em-based or px values
      const parseVal = (v) => {
        if (!v) return 0;
        const emMatch = /^(-?[\d.]+)em$/.exec(v);
        if (emMatch) return Number.parseFloat(emMatch[1]) * fontSize;
        return Number.parseFloat(v) || 0;
      };
      if (firstTspan) {
        const tspanY = firstTspan.getAttribute?.('y') || '';
        const tspanDy = firstTspan.getAttribute?.('dy') || '';
        if (tspanY) {
          // Explicit tspan y overrides parent (edge labels: tspan y="-0.1em" dy="1.1em")
          baseline = parseVal(tspanY) + parseVal(tspanDy);
        } else {
          // No explicit tspan y: start from parent text y and add dy offset
          const textY = this.getAttribute?.('y') || '';
          baseline = parseVal(textY) + parseVal(tspanDy);
        }
      } else {
        // No tspan — use text's own y attribute
        baseline = Number.parseFloat(this.getAttribute?.('y')) || 0;
      }
      // dominant-baseline: middle means the text CENTER (not baseline) is at y.
      // getBBox().y = center - height/2 instead of baseline - ascent.
      const dominantBaseline = getEffectiveDominantBaseline(this);
      if (dominantBaseline === 'middle') {
        y = baseline - height / 2;
      } else {
        y = baseline - ascent;
      }
    }
    return {
      x,
      y,
      width,
      height,
      top: y,
      left: x,
      right: x + width,
      bottom: y + height,
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

/**
 * Detects the Mermaid diagram type from the definition text.
 * Returns a lowercase string key matching the diagram family.
 */
function detectDiagramType(text) {
  const lines = text.trim().split('\n');
  for (const line of lines) {
    const t = line.trim().toLowerCase().replace(/\s+/g, ' ');
    if (!t || t.startsWith('%%')) continue;
    if (t.startsWith('flowchart') || t.startsWith('graph ') || t === 'graph') return 'flowchart';
    if (t.startsWith('statediagram')) return 'state';
    if (t.startsWith('classdiagram')) return 'class';
    if (t.startsWith('sequencediagram')) return 'sequence';
    if (t.startsWith('erdiagram')) return 'er';
    if (t.startsWith('sankey')) return 'sankey';
    if (t.startsWith('gantt')) return 'gantt';
    if (t.startsWith('gitgraph')) return 'git';
    if (t.startsWith('mindmap')) return 'mindmap';
    if (t.startsWith('timeline')) return 'timeline';
    if (t.startsWith('block-beta')) return 'block';
    if (t.startsWith('architecture')) return 'architecture';
    if (t.startsWith('c4context') || t.startsWith('c4container') || t.startsWith('c4component') || t.startsWith('c4dynamic') || t.startsWith('c4deployment')) return 'c4';
    if (t.startsWith('kanban')) return 'kanban';
    if (t.startsWith('packet-beta')) return 'packet';
    if (t.startsWith('pie')) return 'pie';
    if (t.startsWith('quadrant')) return 'quadrant';
    if (t.startsWith('radar')) return 'radar';
    if (t.startsWith('requirementdiagram')) return 'requirement';
    if (t.startsWith('sankey-beta')) return 'sankey';
    if (t.startsWith('treemap')) return 'treemap';
    if (t.startsWith('journey')) return 'journey';
    if (t.startsWith('xychart')) return 'xychart';
    if (t.startsWith('zenuml')) return 'zenuml';
    break;
  }
  return 'other';
}

/**
 * Builds a per-diagram-type Mermaid config.
 *
 * Key insight: Mermaid's dagre layout reads ranksep as:
 *   data4Layout.config?.rankSpacing
 *   || data4Layout.config?.flowchart?.rankSpacing
 *   || data4Layout.rankSpacing
 *
 * If flowchart.rankSpacing is set globally, it leaks into state/class/requirement
 * diagrams because those only set data4Layout.rankSpacing (checked last).
 * Fix: set flowchart.rankSpacing ONLY for flowchart renders; leave it undefined
 * for other diagram types so data4Layout.rankSpacing (per-diagram default) wins.
 */
function buildMermaidConfig(diagramType) {
  const base = {
    startOnLoad: false,
    securityLevel: 'loose',
    deterministicIds: true,
    deterministicIDSeed: 'dmtools',
    theme: 'default',
    htmlLabels: false,
    // State diagram ranksep for state/class/requirement diagrams.
    // These all read getConfig().state.rankSpacing.
    // Browser uses ranksep=50 and achieves c2c≈85 (ranksep/2 each side + node).
    // Our headless env produces c2c≈100 with ranksep=50 (no makeSpaceForEdgeLabels).
    // Setting to 35 compensates for the deficit: c2c ≈ 35+50 = 85.
    state: {
      rankSpacing: 35,
    },
    class: {
      htmlLabels: false,
    },
    er: {
      htmlLabels: false,
    },
    // Sankey: disable useMaxWidth so it uses the configured 600×400 dimensions
    // instead of the headless container width (~1190px).
    sankey: {
      useMaxWidth: false,
    },
  };

  if (diagramType === 'flowchart') {
    // With recursive groupBBox that includes path shapes, our node dimensions
    // reported to dagre now match browser dimensions. The default rankSpacing=50
    // (same as browser) should produce correct layout.
    base.flowchart = {
      htmlLabels: false,
      rankSpacing: 50,
      nodeSpacing: 50,
    };
  } else {
    // Leave flowchart.rankSpacing undefined for non-flowchart diagrams so it
    // does NOT leak into dagre's ranksep fallback chain for those diagram types.
    base.flowchart = {
      htmlLabels: false,
      nodeSpacing: 50,
    };
  }

  return base;
}

globalThis.renderMermaidToSvg = async function renderMermaidToSvg(definition, javaMetrics) {
  if (!definition || !definition.trim()) {
    throw new Error('Mermaid definition is required');
  }

  const window = installDom(javaMetrics);
  const { default: mermaid } = await import('mermaid/dist/mermaid.esm.mjs');
  const { default: zenuml } = await import('@mermaid-js/mermaid-zenuml');
  const id = 'dmtools-mermaid';

  const diagramType = detectDiagramType(definition);
  mermaid.initialize(buildMermaidConfig(diagramType));
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
