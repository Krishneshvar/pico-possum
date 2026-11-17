import { exportToCsv, exportToXlsx, escapeCsv } from '../../../lib/exportUtils';
import { toast } from '../../../stores/toast.store';
import { strings } from '../../../lib/strings';

export async function exportSalesReportCsv({ filteredData, visibleColumns, reportType, fromDate, toDate, todayIso }) {
  if (!filteredData.length || !visibleColumns.length) {
    toast.warning(strings.salesReports.export.noData);
    return;
  }

  const headers = visibleColumns.map((c) => escapeCsv(c.label));
  const rows = filteredData.map((row) =>
    visibleColumns.map((col) => escapeCsv(col.exportValue(row)))
  );

  const dateRangeLabel = fromDate && toDate
    ? `${fromDate}_to_${toDate}`
    : fromDate
      ? `from_${fromDate}`
      : toDate
        ? `to_${toDate}`
        : reportType === 'today'
          ? todayIso
          : 'all_time';

  await exportToCsv({
    filename: strings.salesReports.export.filename(reportType, dateRangeLabel, 'csv'),
    headers,
    rows,
    count: filteredData.length,
    entityName: strings.salesReports.export.entityName,
  });
}

export async function exportSalesReportXlsx({ filteredData, visibleColumns, reportType, fromDate, toDate, todayIso }) {
  if (!filteredData.length || !visibleColumns.length) {
    toast.warning(strings.salesReports.export.noData);
    return;
  }

  const headers = visibleColumns.map((c) => c.label);
  const rows = filteredData.map((row) =>
    visibleColumns.map((col) => {
      const val = col.exportValue(row);
      if (col.isNumeric && !isNaN(val)) return Number(val);
      return val;
    })
  );

  const colWidths = visibleColumns.map((col) => ({
    wch: Math.max(col.label.length + 4, 14),
  }));

  const dateRangeLabel = fromDate && toDate
    ? `${fromDate}_to_${toDate}`
    : fromDate
      ? `from_${fromDate}`
      : toDate
        ? `to_${toDate}`
        : reportType === 'today'
          ? todayIso
          : 'all_time';

  await exportToXlsx({
    filename: strings.salesReports.export.filename(reportType, dateRangeLabel, 'xlsx'),
    sheetName: strings.salesReports.export.sheetName,
    headers,
    rows,
    colWidths,
    count: filteredData.length,
    entityName: strings.salesReports.export.entityName,
  });
}
