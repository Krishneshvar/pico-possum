import { useState, useMemo, useRef, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  FolderTree,
  Plus,
  Trash2,
  Edit2,
  Tag,
  Folder,
  LayoutGrid,
  Columns,
  ListTree,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import DataTable from '../../../components/common/DataTable';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import ExportButton from '../../../components/common/ExportButton';
import { exportToCsv, exportToXlsx, escapeCsv } from '../../../lib/exportUtils';
import CategoryModal from './CategoryModal';
import CategoryTree from './CategoryTree';
import { exportCategoriesCsv, exportCategoriesXlsx } from './categoryExport';
import { strings } from '../../../lib/strings';

export default function CategoriesView() {
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [initialParentId, setInitialParentId] = useState('');
  const [viewMode, setViewMode] = useState('split');
  const [selectedTreeId, setSelectedTreeId] = useState(null);
  const [expandedIds, setExpandedIds] = useState(new Set());
  const [splitWidth, setSplitWidth] = useState(280);
  const isDragging = useRef(false);
  const dragStartX = useRef(0);
  const dragStartWidth = useRef(0);

  const handleDividerMouseDown = useCallback((e) => {
    e.preventDefault();
    isDragging.current = true;
    dragStartX.current = e.clientX;
    dragStartWidth.current = splitWidth;

    const onMouseMove = (ev) => {
      if (!isDragging.current) return;
      const delta = ev.clientX - dragStartX.current;
      const next = Math.min(520, Math.max(200, dragStartWidth.current + delta));
      setSplitWidth(next);
    };

    const onMouseUp = () => {
      isDragging.current = false;
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  }, [splitWidth]);

  const { data: categories = [], isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: () => tauriApi.getCategories(),
  });

  const { data: products = [] } = useQuery({
    queryKey: ['products'],
    queryFn: () => tauriApi.getProducts(null, null),
  });

  const productCounts = useMemo(() => {
    const counts = {};
    for (const p of products) {
      if (p.category_id) {
        counts[p.category_id] = (counts[p.category_id] || 0) + 1;
      }
    }
    return counts;
  }, [products]);

  const { treeRoots, categoryMap } = useMemo(() => {
    const map = {};
    categories.forEach((cat) => {
      map[cat.id] = { ...cat, children: [] };
    });

    const roots = [];
    categories.forEach((cat) => {
      if (cat.parent_id && map[cat.parent_id]) {
        map[cat.parent_id].children.push(map[cat.id]);
      } else {
        roots.push(map[cat.id]);
      }
    });

    return { treeRoots: roots, categoryMap: map };
  }, [categories]);

  const handleToggleExpand = (id) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const createMutation = useMutation({
    mutationFn: (payload) => tauriApi.createCategory(payload),
    onSuccess: (_data, payload) => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      if (payload?.parent_id) {
        setExpandedIds((prev) => new Set([...prev, payload.parent_id]));
      }
      setModalOpen(false);
      toast.success(strings.categories.toasts.created);
    },
    onError: (err) =>
      toast.error(strings.categories.toasts.createFailed(err), {
        title: strings.categories.toasts.createFailedTitle,
      }),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => tauriApi.updateCategory(id, payload),
    onSuccess: (_data, { payload }) => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      if (payload?.parent_id) {
        setExpandedIds((prev) => new Set([...prev, payload.parent_id]));
      }
      setModalOpen(false);
      setEditingCategory(null);
      toast.success(strings.categories.toasts.updated);
    },
    onError: (err) =>
      toast.error(strings.categories.toasts.updateFailed(err), {
        title: strings.categories.toasts.updateFailedTitle,
      }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteCategory(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      if (selectedTreeId === id) {
        setSelectedTreeId(null);
      }
      toast.success(strings.categories.toasts.deleted);
    },
    onError: (err) =>
      toast.error(strings.categories.toasts.deleteFailed(err), {
        title: strings.categories.toasts.deleteFailedTitle,
      }),
  });

  const handleExportCsv = () =>
    exportCategoriesCsv({ categories, categoryMap, productCounts });

  const handleExportXlsx = () =>
    exportCategoriesXlsx({ categories, categoryMap, productCounts });

  const handleOpenCreate = (parentCat = null) => {
    setEditingCategory(null);
    setInitialParentId(parentCat ? parentCat.id : '');
    setModalOpen(true);
  };

  const handleOpenEdit = (category) => {
    setEditingCategory(category);
    setInitialParentId(category.parent_id || '');
    setModalOpen(true);
  };

  const handleDelete = async (category) => {
    const hasChildren = (categoryMap[category.id]?.children?.length || 0) > 0;
    const hasProducts = (productCounts[category.id] || 0) > 0;

    let warningMessage = typeof strings.categories.dialogs.deleteMessage === 'function'
      ? strings.categories.dialogs.deleteMessage(category.name)
      : `Are you sure you want to delete category "${category.name}"?`;

    if (hasChildren && hasProducts) {
      warningMessage = strings.categories.dialogs.deleteWithChildrenAndProducts(
        category.name,
        categoryMap[category.id].children.length,
        productCounts[category.id]
      );
    } else if (hasChildren) {
      warningMessage = strings.categories.dialogs.deleteWithChildren(
        category.name,
        categoryMap[category.id].children.length
      );
    } else if (hasProducts) {
      warningMessage = strings.categories.dialogs.deleteWithProducts(
        category.name,
        productCounts[category.id]
      );
    }

    const titleText = typeof strings.categories.dialogs.deleteTitle === 'function'
      ? strings.categories.dialogs.deleteTitle(category.name)
      : strings.categories.dialogs.deleteTitle || `Delete Category "${category.name}"?`;

    const { confirmed } = await confirmDialog({
      title: titleText,
      message: warningMessage,
      confirmLabel:
        strings.categories.dialogs.deleteConfirmBtn ||
        strings.categories.dialogs.deleteConfirm ||
        'Delete Category',
      cancelLabel: strings.categories.dialogs.deleteCancelBtn || strings.common.actions.cancel || 'Cancel',
      tone: 'danger',
      icon: Trash2,
    });

    if (confirmed) {
      deleteMutation.mutate(category.id);
    }
  };

  const handleModalSubmit = (values) => {
    const parentId = values.parent_id !== undefined ? values.parent_id : values.parentId;
    const payload = {
      name: values.name.trim(),
      parent_id: parentId ? String(parentId) : null,
      description: values.description ? values.description.trim() : null,
    };

    if (editingCategory) {
      updateMutation.mutate({ id: editingCategory.id, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  // Find all descendant IDs for a category to allow recursive filtering
  const getDescendantIds = useCallback(
    (catId) => {
      const ids = [catId];
      const getChildren = (id) => {
        const node = categoryMap[id];
        if (node && node.children) {
          node.children.forEach((child) => {
            ids.push(child.id);
            getChildren(child.id);
          });
        }
      };
      getChildren(catId);
      return ids;
    },
    [categoryMap]
  );

  const displayedCategories = useMemo(() => {
    let list = categories.map((c) => ({
      ...c,
      parent_name: categoryMap[c.parent_id]?.name || '',
      product_count: productCounts[c.id] || 0,
    }));

    if (selectedTreeId) {
      const validIds = new Set(getDescendantIds(selectedTreeId));
      list = list.filter((c) => validIds.has(c.id));
    }

    return list;
  }, [categories, selectedTreeId, categoryMap, productCounts, getDescendantIds]);

  const columns = [
    {
      key: 'name',
      label: strings.categories.columns.name,
      className: 'font-semibold text-text-primary text-xs',
      render: (r) => (
        <div className="flex items-center gap-2">
          <Folder className="w-3.5 h-3.5 text-text-muted" />
          <span>{r.name}</span>
        </div>
      ),
    },
    {
      key: 'parent_name',
      label: strings.categories.columns.parent,
      className: 'text-text-muted text-xs',
      render: (r) => r.parent_name || strings.common.states.emptyValue,
    },
    {
      key: 'product_count',
      label: strings.categories.columns.products,
      className: 'text-text-muted font-mono text-xs',
      sortValue: (r) => productCounts[r.id] || 0,
      render: (r) => {
        const count = productCounts[r.id] || 0;
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-surface-secondary text-text-secondary border border-border-default">
            <Tag className="w-2.5 h-2.5 text-text-muted" />
            {count} {count === 1 ? strings.common.labels.item : strings.common.labels.items}
          </span>
        );
      },
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas overflow-hidden">
      {/* ── Page Header ── */}
      <div className="h-14 px-5 border-b border-border-default bg-surface flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-brand-subtle text-brand flex items-center justify-center">
            <FolderTree className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-text-primary">{strings.categories.view.title}</h2>
            <p className="text-[11px] text-text-muted">{strings.categories.view.subtitle}</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* View Mode Switcher */}
          <div className="flex bg-surface-secondary border border-border-default rounded-lg p-0.5 text-xs">
            <button
              type="button"
              onClick={() => setViewMode('split')}
              className={`p-1.5 rounded flex items-center gap-1 transition-colors cursor-pointer ${
                viewMode === 'split' ? 'bg-brand text-white font-semibold shadow-2xs' : 'text-text-muted hover:text-text-primary'
              }`}
              title={strings.categories.view.viewModes.splitTitle}
            >
              <Columns className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">{strings.categories.view.viewModes.split}</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode('tree')}
              className={`p-1.5 rounded flex items-center gap-1 transition-colors cursor-pointer ${
                viewMode === 'tree' ? 'bg-brand text-white font-semibold shadow-2xs' : 'text-text-muted hover:text-text-primary'
              }`}
              title={strings.categories.view.viewModes.treeTitle}
            >
              <ListTree className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">{strings.categories.view.viewModes.tree}</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode('table')}
              className={`p-1.5 rounded flex items-center gap-1 transition-colors cursor-pointer ${
                viewMode === 'table' ? 'bg-brand text-white font-semibold shadow-2xs' : 'text-text-muted hover:text-text-primary'
              }`}
              title={strings.categories.view.viewModes.tableTitle}
            >
              <LayoutGrid className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">{strings.categories.view.viewModes.table}</span>
            </button>
          </div>

          <ImportCsvButton
            entity="categories"
            onComplete={() => {
              queryClient.invalidateQueries({ queryKey: ['categories'] });
            }}
          />

          <ExportButton
            onExportCsv={handleExportCsv}
            onExportXlsx={handleExportXlsx}
          />

          <button
            type="button"
            onClick={() => handleOpenCreate()}
            className="px-3.5 py-1.5 rounded-lg bg-brand hover:bg-brand-hover text-white font-bold text-xs flex items-center gap-1.5 shadow-xs transition-colors cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>{strings.categories.view.addCategory}</span>
          </button>
        </div>
      </div>

      {/* ── Main Content Area ── */}
      <div className="flex-1 flex overflow-hidden">
        {/* Category Tree Pane */}
        {(viewMode === 'split' || viewMode === 'tree') && (
          <CategoryTree
            viewMode={viewMode}
            splitWidth={splitWidth}
            selectedTreeId={selectedTreeId}
            setSelectedTreeId={setSelectedTreeId}
            treeRoots={treeRoots}
            productCounts={productCounts}
            expandedIds={expandedIds}
            onToggleExpand={handleToggleExpand}
            onAddChild={handleOpenCreate}
            onEdit={handleOpenEdit}
            onDelete={handleDelete}
          />
        )}

        {/* Drag divider in split mode */}
        {viewMode === 'split' && (
          <div
            onMouseDown={handleDividerMouseDown}
            className="w-1 shrink-0 bg-border-default hover:bg-brand cursor-col-resize transition-colors group relative"
            title={strings.categories.view.resizePane}
          >
            <div className="absolute inset-y-0 -left-1 -right-1" />
          </div>
        )}

        {/* Data Table Pane */}
        {(viewMode === 'split' || viewMode === 'table') && (
          <div className="flex-1 flex flex-col overflow-hidden bg-surface">
            {selectedTreeId && (
              <div className="px-4 py-2 bg-brand-subtle border-b border-brand/20 text-xs text-brand flex items-center justify-between">
                <span>
                  {strings.categories.view.filteringFor}{' '}
                  <strong>{categories.find((c) => c.id === selectedTreeId)?.name}</strong>
                </span>
                <button
                  type="button"
                  onClick={() => setSelectedTreeId(null)}
                  className="text-[11px] underline hover:opacity-80 cursor-pointer"
                >
                  {strings.categories.view.showAll}
                </button>
              </div>
            )}

            <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
              <DataTable
                columns={columns}
                persistKey="categories"
                data={displayedCategories}
                keyFn={(r) => r.id}
                searchKeys={['name', 'parent_name']}
                pageSize={20}
                emptyMessage={strings.categories.view.emptyTable}
                isLoading={isLoading}
                actions={(c) => (
                  <DropdownActions
                    items={[
                      {
                        label: strings.categories.actions.addSubcategory,
                        icon: <Plus className="w-3.5 h-3.5" />,
                        onClick: () => handleOpenCreate(c),
                      },
                      {
                        label: strings.categories.actions.editCategory,
                        icon: <Edit2 className="w-3.5 h-3.5" />,
                        onClick: () => handleOpenEdit(c),
                      },
                      'divider',
                      {
                        label: strings.categories.actions.deleteCategory,
                        icon: <Trash2 className="w-3.5 h-3.5" />,
                        danger: true,
                        onClick: () => handleDelete(c),
                      },
                    ]}
                  />
                )}
              />
            </div>
          </div>
        )}
      </div>

      {/* Modal Dialog for Category Creation/Editing */}
      <CategoryModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        editingCategory={editingCategory}
        initialParentId={initialParentId}
        categories={categories}
        onSubmit={handleModalSubmit}
        isPending={createMutation.isPending || updateMutation.isPending}
      />
    </div>
  );
}
