import { useState, useMemo, useRef, useEffect } from 'react';
import { readTableState, writeTableState } from './tableState';

export function useTableColumns({ columns, persistKey }) {
  const persistedTableState = useMemo(() => readTableState(persistKey), [persistKey]);

  const [colOrder, setColOrder] = useState(() => {
    const currentKeys = columns.map((c) => c.key);
    const savedOrder = persistedTableState?.order ?? [];
    const kept = savedOrder.filter((k) => currentKeys.includes(k));
    return [...kept, ...currentKeys.filter((k) => !kept.includes(k))];
  });
  const [draggedColKey, setDraggedColKey] = useState(null);
  const [dragOverColKey, setDragOverColKey] = useState(null);

  const [colWidths, setColWidths] = useState(() => {
    const savedWidths = persistedTableState?.widths ?? {};
    const validWidths = {};
    for (const col of columns) {
      const width = savedWidths[col.key];
      if (typeof width === 'number' && Number.isFinite(width) && width > 0) {
        validWidths[col.key] = width;
      }
    }
    return validWidths;
  });
  const isResizing = useRef(false);
  const suppressHeaderClick = useRef(false);

  // Sync colOrder if columns change
  useEffect(() => {
    const currentKeys = columns.map((c) => c.key);
    setColOrder((prev) => {
      const filteredPrev = prev.filter((k) => currentKeys.includes(k));
      const newKeys = currentKeys.filter((k) => !filteredPrev.includes(k));
      const next = [...filteredPrev, ...newKeys];
      const unchanged =
        next.length === prev.length && next.every((key, i) => key === prev[i]);
      return unchanged ? prev : next;
    });
  }, [columns]);

  // Persist column order/widths
  useEffect(() => {
    if (!persistKey) return;
    const timer = setTimeout(() => {
      writeTableState(persistKey, { order: colOrder, widths: colWidths });
    }, 200);
    return () => clearTimeout(timer);
  }, [persistKey, colOrder, colWidths]);

  const orderedColumns = useMemo(() => {
    const colMap = new Map(columns.map((c) => [c.key, c]));
    return colOrder.map((key) => colMap.get(key)).filter(Boolean);
  }, [colOrder, columns]);

  // Drag & Drop Column Reordering
  const handleDragStart = (e, colKey) => {
    if (isResizing.current) {
      e.preventDefault();
      return;
    }
    suppressHeaderClick.current = true;
    setDraggedColKey(colKey);
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', colKey);
  };

  const handleDragOver = (e, targetKey) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (dragOverColKey !== targetKey) {
      setDragOverColKey(targetKey);
    }
  };

  const handleDrop = (e, targetKey) => {
    e.preventDefault();
    if (!draggedColKey || draggedColKey === targetKey) {
      setDraggedColKey(null);
      setDragOverColKey(null);
      return;
    }

    setColOrder((prev) => {
      const next = [...prev];
      const fromIdx = next.indexOf(draggedColKey);
      const toIdx = next.indexOf(targetKey);
      if (fromIdx === -1 || toIdx === -1) return prev;
      next.splice(fromIdx, 1);
      next.splice(toIdx, 0, draggedColKey);
      return next;
    });

    setDraggedColKey(null);
    setDragOverColKey(null);
  };

  const handleDragEnd = () => {
    setDraggedColKey(null);
    setDragOverColKey(null);
    setTimeout(() => {
      suppressHeaderClick.current = false;
    }, 250);
  };

  // Column Resizing Drag Handler
  const startResizing = (e, colKey) => {
    e.preventDefault();
    e.stopPropagation();
    isResizing.current = true;

    const startX = e.clientX;
    const thEl = e.currentTarget.parentElement;
    const startWidth = thEl ? thEl.getBoundingClientRect().width : (colWidths[colKey] || 120);

    const onMouseMove = (moveEvent) => {
      const delta = moveEvent.clientX - startX;
      const newWidth = Math.max(50, Math.round(startWidth + delta));
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
    colOrder,
    setColOrder,
    colWidths,
    setColWidths,
    orderedColumns,
    draggedColKey,
    dragOverColKey,
    handleDragStart,
    handleDragOver,
    handleDrop,
    handleDragEnd,
    startResizing,
    suppressHeaderClick,
  };
}
