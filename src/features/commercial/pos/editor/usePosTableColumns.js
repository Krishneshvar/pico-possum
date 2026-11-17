import { useState, useRef, useEffect } from 'react';
import { strings } from '../../../../lib/strings';

export const POS_TABLE_WIDTHS_KEY = 'pico_possum_pos_table_widths';

export const POS_COLUMNS = [
  { key: 'index', label: strings.pos.editor.columns.index, defaultWidth: 48, minWidth: 36, align: 'center' },
  { key: 'sku', label: strings.common.labels.sku, defaultWidth: 120, minWidth: 60, align: 'left' },
  { key: 'product', label: strings.common.labels.product, defaultWidth: 260, minWidth: 140, align: 'left' },
  { key: 'qty', label: strings.pos.editor.columns.qty, defaultWidth: 110, minWidth: 70, align: 'center' },
  { key: 'unitPrice', label: strings.pos.editor.columns.unitPrice, defaultWidth: 120, minWidth: 75, align: 'right' },
  { key: 'mrp', label: strings.pos.editor.columns.mrp, defaultWidth: 110, minWidth: 70, align: 'right' },
  { key: 'discount', label: strings.common.labels.discount, defaultWidth: 110, minWidth: 70, align: 'right' },
  { key: 'lineTotal', label: strings.pos.editor.columns.lineTotal, defaultWidth: 130, minWidth: 80, align: 'right' },
];

export function usePosTableColumns() {
  const [colWidths, setColWidths] = useState(() => {
    try {
      const saved = localStorage.getItem(POS_TABLE_WIDTHS_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (parsed && typeof parsed === 'object') return parsed;
      }
    } catch {
      // ignore
    }
    return {};
  });

  useEffect(() => {
    try {
      localStorage.setItem(POS_TABLE_WIDTHS_KEY, JSON.stringify(colWidths));
    } catch {
      // ignore
    }
  }, [colWidths]);

  const getWidthStyle = (key) => {
    const colDef = POS_COLUMNS.find((c) => c.key === key);
    const width = colWidths[key] ?? colDef?.defaultWidth;
    const minWidth = colDef?.minWidth ?? 40;
    if (width) {
      return { width: `${width}px`, minWidth: `${minWidth}px` };
    }
    return { minWidth: `${minWidth}px` };
  };

  const isResizing = useRef(false);
  const startResizing = (e, colKey) => {
    e.preventDefault();
    e.stopPropagation();
    isResizing.current = true;

    const startX = e.clientX;
    const thEl = e.currentTarget.parentElement;
    const startWidth = thEl ? thEl.getBoundingClientRect().width : (colWidths[colKey] || 100);
    const colDef = POS_COLUMNS.find((c) => c.key === colKey);
    const minW = colDef?.minWidth || 40;

    const onMouseMove = (moveEvent) => {
      const delta = moveEvent.clientX - startX;
      const newWidth = Math.max(minW, Math.round(startWidth + delta));
      setColWidths((prev) => ({
        ...prev,
        [colKey]: newWidth,
      }));
    };

    const onMouseUp = () => {
      isResizing.current = false;
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  };

  return {
    colWidths,
    setColWidths,
    getWidthStyle,
    startResizing,
  };
}
