import { useState, useMemo } from 'react';

const isFilterValSet = (v) => v !== '' && v !== null && v !== undefined && String(v).trim() !== '';

export function getRowIsoDate(val) {
  if (!val) return '';
  if (typeof val === 'string') {
    const trimmed = val.trim();
    const match = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (match) return `${match[1]}-${match[2]}-${match[3]}`;
    const d = new Date(trimmed.replace(' ', 'T'));
    if (!isNaN(d.getTime())) {
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${y}-${m}-${day}`;
    }
  }
  if (val instanceof Date && !isNaN(val.getTime())) {
    const y = val.getFullYear();
    const m = String(val.getMonth() + 1).padStart(2, '0');
    const day = String(val.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
  if (typeof val === 'number') {
    const d = new Date(val);
    if (!isNaN(d.getTime())) {
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${y}-${m}-${day}`;
    }
  }
  return '';
}

export function useTableFiltering({ data, filters = [], searchKeys = [], setPage }) {
  const [search, setSearch] = useState('');
  const [selectValues, setSelectValues] = useState({});
  const [multiValues, setMultiValues] = useState({});
  const [rangeValues, setRangeValues] = useState({});
  const [dateValues, setDateValues] = useState({});

  const handleSearch = (val) => {
    setSearch(val);
    setPage(1);
  };

  const handleSelect = (key, val) => {
    setSelectValues((p) => ({ ...p, [key]: val }));
    setPage(1);
  };

  const handleMulti = (key, vals) => {
    setMultiValues((p) => ({ ...p, [key]: vals }));
    setPage(1);
  };

  const handleRangeMin = (key, val) => {
    setRangeValues((p) => {
      const prev = p[key] ?? {};
      const nextMin = val;
      const nextMax = prev.max;
      if (!isFilterValSet(nextMin) && !isFilterValSet(nextMax)) {
        const next = { ...p };
        delete next[key];
        return next;
      }
      return { ...p, [key]: { ...prev, min: val } };
    });
    setPage(1);
  };

  const handleRangeMax = (key, val) => {
    setRangeValues((p) => {
      const prev = p[key] ?? {};
      const nextMin = prev.min;
      const nextMax = val;
      if (!isFilterValSet(nextMin) && !isFilterValSet(nextMax)) {
        const next = { ...p };
        delete next[key];
        return next;
      }
      return { ...p, [key]: { ...prev, max: val } };
    });
    setPage(1);
  };

  const dateRangeKey = (f) => f.key ?? `${f.fromKey}__${f.toKey}`;

  const handleDateFrom = (dk, val) => {
    setDateValues((p) => ({ ...p, [dk]: { ...p[dk], from: val } }));
    setPage(1);
  };

  const handleDateTo = (dk, val) => {
    setDateValues((p) => ({ ...p, [dk]: { ...p[dk], to: val } }));
    setPage(1);
  };

  // Enrich filter options with record counts from `data`
  const enrichedFilters = useMemo(() => {
    if (!filters || filters.length === 0) return [];
    return filters.map((f) => {
      if ((f.type === 'select' || f.type === 'multiselect') && Array.isArray(f.options)) {
        const filterFn = f.filterFn || f.predicate;
        let countsMap = null;
        if (typeof filterFn !== 'function') {
          countsMap = new Map();
          for (let i = 0; i < data.length; i++) {
            const k = String(data[i][f.key] ?? '');
            countsMap.set(k, (countsMap.get(k) || 0) + 1);
          }
        }

        const newOptions = f.options.map((opt) => {
          let count = 0;
          if (typeof filterFn === 'function') {
            count = data.filter((row) => filterFn(row, [opt.value])).length;
          } else {
            count = countsMap.get(String(opt.value)) || 0;
          }
          const baseLabel = String(opt.label).replace(/\s*\(\d+\)$/, '');
          return {
            ...opt,
            label: `${baseLabel} (${count})`,
          };
        });
        return { ...f, options: newOptions };
      }
      return f;
    });
  }, [filters, data]);

  // Filter pipeline
  const filtered = useMemo(() => {
    let result = data;

    // Global text search across searchKeys
    if (search.trim()) {
      const q = search.trim().toLowerCase();
      result = result.filter((row) => {
        if (!searchKeys || searchKeys.length === 0) {
          return Object.values(row).some((val) =>
            String(val ?? '').toLowerCase().includes(q)
          );
        }
        return searchKeys.some((k) => {
          const val = row[k];
          return String(val ?? '').toLowerCase().includes(q);
        });
      });
    }

    // Single-select dropdown filters
    for (const [key, val] of Object.entries(selectValues)) {
      if (val !== '' && val != null) {
        const filterDef = filters.find((f) => f.key === key);
        const filterFn = filterDef?.filterFn || filterDef?.predicate;
        if (typeof filterFn === 'function') {
          result = result.filter((row) => filterFn(row, val));
        } else {
          result = result.filter((row) => String(row[key]) === String(val));
        }
      }
    }

    // Multi-select checkbox filters
    for (const [key, vals] of Object.entries(multiValues)) {
      if (Array.isArray(vals) && vals.length > 0) {
        const filterDef = filters.find((f) => f.key === key);
        const filterFn = filterDef?.filterFn || filterDef?.predicate;
        if (typeof filterFn === 'function') {
          result = result.filter((row) => filterFn(row, vals));
        } else {
          result = result.filter((row) => vals.includes(String(row[key])));
        }
      }
    }

    // Numeric min/max range filters
    for (const [key, range] of Object.entries(rangeValues)) {
      if (!range) continue;
      const { min, max } = range;
      const hasMin = isFilterValSet(min) && !Number.isNaN(Number(min));
      const hasMax = isFilterValSet(max) && !Number.isNaN(Number(max));
      if (!hasMin && !hasMax) continue;

      const filterDef = filters.find((f) => f.key === key);
      if (typeof filterDef?.filterFn === 'function') {
        const cleanRange = {
          min: hasMin ? min : '',
          max: hasMax ? max : '',
        };
        result = result.filter((row) => filterDef.filterFn(row, cleanRange));
      } else {
        const valFn =
          filterDef?.valueGetter ||
          (filterDef?.transform
            ? (row) => filterDef.transform(row[key])
            : (row) => row[key]);

        if (hasMin) {
          result = result.filter((row) => {
            const v = Number(valFn(row));
            return !Number.isNaN(v) && v >= Number(min);
          });
        }
        if (hasMax) {
          result = result.filter((row) => {
            const v = Number(valFn(row));
            return !Number.isNaN(v) && v <= Number(max);
          });
        }
      }
    }

    // Date range filters
    for (const f of filters) {
      if (f.type !== 'daterange') continue;
      const dk = dateRangeKey(f);
      const dv = dateValues[dk] ?? {};
      const from = f.fromVal !== undefined ? f.fromVal : dv.from;
      const to = f.toVal !== undefined ? f.toVal : dv.to;
      const dateGetter = f.dateGetter || ((row) => {
        const k = f.key || f.fromKey || f.toKey;
        if (k && row[k] !== undefined && row[k] !== null) return row[k];
        return row.createdAt ?? row.created_at ?? row.saleDate ?? row.sale_date ?? row.date;
      });

      if (from) {
        result = result.filter((row) => {
          const rowIso = getRowIsoDate(dateGetter(row));
          return Boolean(rowIso && rowIso >= from);
        });
      }
      if (to) {
        result = result.filter((row) => {
          const rowIso = getRowIsoDate(dateGetter(row));
          return Boolean(rowIso && rowIso <= to);
        });
      }
    }

    return result;
  }, [data, search, searchKeys, selectValues, multiValues, rangeValues, dateValues, filters]);

  const hasActiveFilters =
    Boolean(search?.trim()) ||
    Object.values(selectValues).some((v) => isFilterValSet(v)) ||
    Object.values(multiValues).some((arr) => Array.isArray(arr) && arr.length > 0) ||
    Object.values(rangeValues).some((r) => r && (isFilterValSet(r.min) || isFilterValSet(r.max))) ||
    Object.values(dateValues).some((d) => d && (isFilterValSet(d.from) || isFilterValSet(d.to))) ||
    filters.some((f) => f.type === 'daterange' && (isFilterValSet(f.fromVal) || isFilterValSet(f.toVal)));

  const clearAllFilters = () => {
    setSearch('');
    setSelectValues({});
    setMultiValues({});
    setRangeValues({});
    setDateValues({});
    for (const f of filters) {
      if (f.type === 'daterange') {
        f.onFromChange?.('');
        f.onToChange?.('');
      }
    }
    setPage(1);
  };

  return {
    search,
    selectValues,
    multiValues,
    rangeValues,
    dateValues,
    dateRangeKey,
    handleSearch,
    handleSelect,
    handleMulti,
    handleRangeMin,
    handleRangeMax,
    handleDateFrom,
    handleDateTo,
    enrichedFilters,
    filtered,
    hasActiveFilters,
    clearAllFilters,
  };
}
