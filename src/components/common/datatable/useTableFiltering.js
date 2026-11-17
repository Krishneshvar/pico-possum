import { useState, useMemo } from 'react';

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
    setRangeValues((p) => ({ ...p, [key]: { ...p[key], min: val } }));
    setPage(1);
  };

  const handleRangeMax = (key, val) => {
    setRangeValues((p) => ({ ...p, [key]: { ...p[key], max: val } }));
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
        const newOptions = f.options.map((opt) => {
          let count = 0;
          if (typeof filterFn === 'function') {
            count = data.filter((row) => filterFn(row, [opt.value])).length;
          } else {
            count = data.filter((row) => String(row[f.key]) === String(opt.value)).length;
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
      const filterDef = filters.find((f) => f.key === key);
      const valFn = filterDef?.valueGetter || ((row) => row[key]);

      if (min !== '' && min != null && !Number.isNaN(Number(min))) {
        result = result.filter((row) => {
          const v = Number(valFn(row));
          return !Number.isNaN(v) && v >= Number(min);
        });
      }
      if (max !== '' && max != null && !Number.isNaN(Number(max))) {
        result = result.filter((row) => {
          const v = Number(valFn(row));
          return !Number.isNaN(v) && v <= Number(max);
        });
      }
    }

    // Date range filters
    for (const f of filters) {
      if (f.type !== 'daterange') continue;
      const dk = dateRangeKey(f);
      const dv = dateValues[dk] ?? {};
      const from = f.fromVal !== undefined ? f.fromVal : dv.from;
      const to = f.toVal !== undefined ? f.toVal : dv.to;
      const dateGetter = f.dateGetter || ((row) => row[f.key]);

      if (from) {
        const fromMidnight = new Date(`${from}T00:00:00`);
        result = result.filter((row) => {
          const d = new Date(dateGetter(row));
          return !Number.isNaN(d.getTime()) && d >= fromMidnight;
        });
      }
      if (to) {
        const toEndOfDay = new Date(`${to}T23:59:59.999`);
        result = result.filter((row) => {
          const d = new Date(dateGetter(row));
          return !Number.isNaN(d.getTime()) && d <= toEndOfDay;
        });
      }
    }

    return result;
  }, [data, search, searchKeys, selectValues, multiValues, rangeValues, dateValues, filters]);

  const hasActiveFilters =
    Boolean(search) ||
    Object.values(selectValues).some((v) => v !== '' && v != null) ||
    Object.values(multiValues).some((arr) => Array.isArray(arr) && arr.length > 0) ||
    Object.values(rangeValues).some((r) => r && (r.min !== '' || r.max !== '')) ||
    Object.values(dateValues).some((d) => d && (d.from || d.to)) ||
    filters.some((f) => f.type === 'daterange' && (f.fromVal || f.toVal));

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
