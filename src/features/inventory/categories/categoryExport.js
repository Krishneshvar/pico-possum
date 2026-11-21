import { exportToCsv, exportToXlsx, escapeCsv } from '../../../lib/exportUtils';
import { toast } from '../../../stores/toast.store';
import { strings } from '../../../lib/strings';

export async function exportCategoriesCsv({ categories, categoryMap, productCounts }) {
  if (!categories.length) {
    toast.warning(strings.categories.toasts.noCategoriesToExport);
    return;
  }

  const headers = [
    strings.categories.export.headers.name,
    strings.categories.export.headers.parent,
    strings.categories.export.headers.productCount,
  ];
  const rows = categories.map((c) => {
    const pId = c.parentId ?? c.parent_id;
    return [
      escapeCsv(c.name || ''),
      escapeCsv(categoryMap[pId]?.name || strings.common.states.none),
      escapeCsv(productCounts[c.id] || 0),
    ];
  });

  const dateStr = new Date().toISOString().split('T')[0];
  await exportToCsv({
    filename: `categories_${dateStr}.csv`,
    headers,
    rows,
    count: categories.length,
    entityName: strings.categories.export.entityName,
  });
}

export async function exportCategoriesXlsx({ categories, categoryMap, productCounts }) {
  if (!categories.length) {
    toast.warning(strings.categories.toasts.noCategoriesToExport);
    return;
  }

  const headers = [
    strings.categories.export.headers.name,
    strings.categories.export.headers.parent,
    strings.categories.export.headers.productCount,
  ];
  const rows = categories.map((c) => {
    const pId = c.parentId ?? c.parent_id;
    return [
      c.name || '',
      categoryMap[pId]?.name || strings.common.states.none,
      productCounts[c.id] || 0,
    ];
  });

  const dateStr = new Date().toISOString().split('T')[0];
  await exportToXlsx({
    filename: `categories_${dateStr}.xlsx`,
    headers,
    rows,
    sheetName: strings.categories.export.sheetName,
    entityName: strings.categories.export.entityName,
  });
}
