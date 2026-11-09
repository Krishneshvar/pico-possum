import { useState, useMemo, useRef, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  FolderTree,
  Plus,
  Trash2,
  Edit2,
  X,
  Tag,
  ChevronRight,
  ChevronDown,
  Folder,
  FolderOpen,
  LayoutGrid,
  Columns,
  ListTree,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import * as XLSX from 'xlsx';
import DataTable from '../../../components/common/DataTable';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';
import ExportButton from '../../../components/common/ExportButton';
import { exportWithUserLocation, escapeCsv } from '../../../lib/exportUtils';
import Select from '../../../components/common/Select';

// Recursive Tree Node Component
function TreeNode({
  node,
  selectedId,
  onSelect,
  onAddChild,
  onEdit,
  onDelete,
  productCounts,
  expandedIds,
  onToggleExpand,
}) {
  const hasChildren = node.children && node.children.length > 0;
  const isExpanded = expandedIds.has(node.id);
  const isSelected = selectedId === node.id;
  const count = productCounts[node.id] || 0;

  return (
    <div className="select-none">
      <div
        className={`group flex items-center justify-between py-1.5 px-2 rounded-lg text-xs cursor-pointer transition-colors ${
          isSelected
            ? 'bg-brand text-white font-semibold shadow-xs'
            : 'text-text-secondary hover:bg-surface-secondary hover:text-text-primary'
        }`}
        onClick={() => onSelect(node.id)}
      >
        <div className="flex items-center gap-1.5 min-w-0">
          {hasChildren ? (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onToggleExpand(node.id);
              }}
              className="p-0.5 text-text-muted hover:text-text-primary transition-colors cursor-pointer"
            >
              {isExpanded ? (
                <ChevronDown className="w-3.5 h-3.5" />
              ) : (
                <ChevronRight className="w-3.5 h-3.5" />
              )}
            </button>
          ) : (
            <span className="w-4" />
          )}

          {isExpanded ? (
            <FolderOpen className="w-3.5 h-3.5 text-brand shrink-0" />
          ) : (
            <Folder className="w-3.5 h-3.5 text-text-muted shrink-0" />
          )}

          <span className="truncate">{node.name}</span>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          <span
            className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono border ${
              isSelected
                ? 'bg-white/20 text-white border-white/30'
                : 'bg-surface-secondary text-text-muted border-border-default'
            }`}
          >
            {count}
          </span>

          <div className="hidden group-hover:flex items-center gap-0.5">
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onAddChild(node);
              }}
              title="Add subcategory"
              className="p-1 rounded text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
            >
              <Plus className="w-3 h-3" />
            </button>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onEdit(node);
              }}
              title="Edit category"
              className="p-1 rounded text-text-muted hover:text-text-primary hover:bg-surface-secondary transition-colors cursor-pointer"
            >
              <Edit2 className="w-3 h-3" />
            </button>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onDelete(node);
              }}
              title="Delete category"
              className="p-1 rounded text-text-muted hover:text-danger hover:bg-danger-bg transition-colors cursor-pointer"
            >
              <Trash2 className="w-3 h-3" />
            </button>
          </div>
        </div>
      </div>

      {hasChildren && isExpanded && (
        <div className="pl-4 border-l border-white/15 ml-2 my-0.5 space-y-0.5">
          {node.children.map((child) => (
            <TreeNode
              key={child.id}
              node={child}
              selectedId={selectedId}
              onSelect={onSelect}
              onAddChild={onAddChild}
              onEdit={onEdit}
              onDelete={onDelete}
              productCounts={productCounts}
              expandedIds={expandedIds}
              onToggleExpand={onToggleExpand}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function CategoriesView() {
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      setModalOpen(false);
      setName('');
      setParentId('');
      toast.success('Category created successfully');
    },
    onError: (err) => toast.error(`Failed to create category: ${err}`, { title: 'Create failed' }),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => tauriApi.updateCategory(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      setModalOpen(false);
      setEditingCategory(null);
      setName('');
      setParentId('');
      toast.success('Category updated successfully');
    },
    onError: (err) => toast.error(`Failed to update category: ${err}`, { title: 'Update failed' }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      toast.success('Category deleted successfully');
    },
    onError: (err) => toast.error(`Failed to delete category: ${err}`, { title: 'Delete failed' }),
  });

  const handleExportCsv = async () => {
    if (!categories.length) {
      toast.warning('No categories to export');
      return;
    }

    const headers = ['Category Name', 'Parent Category', 'Product Count'];
    const rows = categories.map((c) => [
      escapeCsv(c.name || ''),
      escapeCsv(categoryMap[c.parent_id]?.name || 'None'),
      escapeCsv(productCounts[c.id] || 0),
    ]);

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\r\n');
    const dateStr = new Date().toISOString().split('T')[0];
    await exportWithUserLocation({
      filename: `categories_${dateStr}.csv`,
      extension: 'csv',
      data: csvContent,
      count: categories.length,
      entityName: 'categories',
    });
  };

  const handleExportXlsx = async () => {
    if (!categories.length) {
      toast.warning('No categories to export');
      return;
    }

    const headers = ['Category Name', 'Parent Category', 'Product Count'];
    const rows = categories.map((c) => [
      c.name || '',
      categoryMap[c.parent_id]?.name || 'None',
      productCounts[c.id] || 0,
    ]);

    const ws = XLSX.utils.aoa_to_sheet([headers, ...rows]);
    ws['!cols'] = [{ wch: 28 }, { wch: 24 }, { wch: 16 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Categories');
    const xlsxArrayBuffer = XLSX.write(wb, { type: 'array', bookType: 'xlsx' });
    const dateStr = new Date().toISOString().split('T')[0];
    await exportWithUserLocation({
      filename: `categories_${dateStr}.xlsx`,
      extension: 'xlsx',
      data: xlsxArrayBuffer,
      count: categories.length,
      entityName: 'categories',
    });
  };

  const handleOpenCreate = (parent = null) => {
    setEditingCategory(null);
    setName('');
    setParentId(parent ? parent.id : '');
    setModalOpen(true);
  };

  const handleOpenEdit = (c) => {
    setEditingCategory(c);
    setName(c.name);
    setParentId(c.parent_id || '');
    setModalOpen(true);
  };

  const handleDelete = async (c) => {
    const count = productCounts[c.id] || 0;
    if (count > 0) {
      toast.warning(
        `Cannot delete "${c.name}" because it still contains ${count} product${count === 1 ? '' : 's'}. Reassign those products first.`
      );
      return;
    }

    const { confirmed } = await confirmDialog({
      title: `Delete Category "${c.name}"?`,
      message: `Are you sure you want to delete category "${c.name}"? This action cannot be undone.`,
      confirmLabel: 'Delete Category',
      cancelLabel: 'Cancel',
      tone: 'danger',
    });
    if (confirmed) {
      deleteMutation.mutate(c.id);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!name.trim()) {
      toast.warning('Category name is required');
      return;
    }

    const payload = {
      name: name.trim(),
      parent_id: parentId ? parentId : null,
    };

    if (editingCategory) {
      updateMutation.mutate({ id: editingCategory.id, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  // Filtered categories when a tree node is selected
  const displayedCategories = useMemo(() => {
    if (!selectedTreeId) return categories;

    const ids = new Set([selectedTreeId]);
    const collectDescendants = (id) => {
      const node = categoryMap[id];
      if (!node) return;
      node.children?.forEach((child) => {
        ids.add(child.id);
        collectDescendants(child.id);
      });
    };
    collectDescendants(selectedTreeId);

    return categories.filter((c) => ids.has(c.id));
  }, [categories, selectedTreeId, categoryMap]);

  const columns = [
    {
      key: 'name',
      label: 'Category Name',
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
      label: 'Parent Category',
      className: 'text-text-muted text-xs',
      render: (r) => r.parent_name || '—',
    },
    {
      key: 'product_count',
      label: 'Products',
      className: 'text-text-muted font-mono text-xs',
      sortValue: (r) => productCounts[r.id] || 0,
      render: (r) => {
        const count = productCounts[r.id] || 0;
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-surface-secondary text-text-secondary border border-border-default">
            <Tag className="w-2.5 h-2.5 text-text-muted" />
            {count} {count === 1 ? 'item' : 'items'}
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
            <h2 className="text-sm font-bold text-text-primary">Categories Hierarchy</h2>
            <p className="text-[11px] text-text-muted">Organize catalog hierarchy and class mappings</p>
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
              title="Split View (Tree + Table)"
            >
              <Columns className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Split</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode('tree')}
              className={`p-1.5 rounded flex items-center gap-1 transition-colors cursor-pointer ${
                viewMode === 'tree' ? 'bg-brand text-white font-semibold shadow-2xs' : 'text-text-muted hover:text-text-primary'
              }`}
              title="Tree View"
            >
              <ListTree className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Tree</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode('table')}
              className={`p-1.5 rounded flex items-center gap-1 transition-colors cursor-pointer ${
                viewMode === 'table' ? 'bg-brand text-white font-semibold shadow-2xs' : 'text-text-muted hover:text-text-primary'
              }`}
              title="Table View"
            >
              <LayoutGrid className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Table</span>
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
            <span>Add Category</span>
          </button>
        </div>
      </div>

      {/* ── Main Content Area ── */}
      <div className="flex-1 flex overflow-hidden">
        {/* Category Tree Pane */}
        {(viewMode === 'split' || viewMode === 'tree') && (
          <div
            className={`flex flex-col border-r border-border-default bg-surface text-text-primary overflow-hidden ${
              viewMode === 'tree' ? 'flex-1' : ''
            }`}
            style={viewMode === 'split' ? { width: splitWidth, shrink: 0 } : undefined}
          >
            <div className="p-3 bg-gray-500 border-b border-gray-600 flex items-center justify-between text-white shadow-2xs">
              <span className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-1.5">
                <ListTree className="w-3.5 h-3.5 text-white/90" />
                Category Tree
              </span>
              {selectedTreeId && (
                <button
                  type="button"
                  onClick={() => setSelectedTreeId(null)}
                  className="text-[11px] text-white/90 hover:text-white hover:underline cursor-pointer font-medium"
                >
                  Clear Selection
                </button>
              )}
            </div>

            <div className="flex-1 overflow-y-auto p-3 space-y-1">
              <div
                className={`py-1.5 px-2.5 rounded-lg text-xs cursor-pointer flex items-center justify-between transition-colors ${
                  !selectedTreeId
                    ? 'bg-brand text-white font-bold border border-brand/50 shadow-xs'
                    : 'text-text-secondary hover:bg-surface-secondary hover:text-text-primary'
                }`}
                onClick={() => setSelectedTreeId(null)}
              >
                <div className="flex items-center gap-2">
                  <FolderOpen className="w-3.5 h-3.5 text-brand" />
                  <span>All Categories</span>
                </div>
              </div>

              {treeRoots.map((root) => (
                <TreeNode
                  key={root.id}
                  node={root}
                  selectedId={selectedTreeId}
                  onSelect={setSelectedTreeId}
                  onAddChild={handleOpenCreate}
                  onEdit={handleOpenEdit}
                  onDelete={handleDelete}
                  productCounts={productCounts}
                  expandedIds={expandedIds}
                  onToggleExpand={handleToggleExpand}
                />
              ))}

              {categories.length === 0 && !isLoading && (
                <div className="text-center py-8 text-xs text-text-muted">
                  No categories created yet. Click "Add Category" above.
                </div>
              )}
            </div>
          </div>
        )}

        {/* Drag divider in split mode */}
        {viewMode === 'split' && (
          <div
            onMouseDown={handleDividerMouseDown}
            className="w-1 shrink-0 bg-border-default hover:bg-brand cursor-col-resize transition-colors group relative"
            title="Drag to resize pane"
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
                  Filtering category and subcategories for:{' '}
                  <strong>{categories.find((c) => c.id === selectedTreeId)?.name}</strong>
                </span>
                <button
                  type="button"
                  onClick={() => setSelectedTreeId(null)}
                  className="text-[11px] underline hover:opacity-80 cursor-pointer"
                >
                  Show all
                </button>
              </div>
            )}

            <div className="flex-1 overflow-hidden">
              <DataTable
                columns={columns}
                persistKey="categories"
                data={displayedCategories}
                keyFn={(r) => r.id}
                searchKeys={['name', 'parent_name']}
                pageSize={20}
                emptyMessage="No categories found."
                isLoading={isLoading}
                actions={(c) => (
                  <DropdownActions
                    items={[
                      {
                        label: 'Add Subcategory',
                        icon: <Plus className="w-3.5 h-3.5" />,
                        onClick: () => handleOpenCreate(c),
                      },
                      {
                        label: 'Edit Category',
                        icon: <Edit2 className="w-3.5 h-3.5" />,
                        onClick: () => handleOpenEdit(c),
                      },
                      'divider',
                      {
                        label: 'Delete Category',
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
      {modalOpen && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-xl text-text-primary animate-scale-in">
            <div className="flex justify-between items-center border-b border-border-default pb-3">
              <h3 className="font-bold text-sm text-text-primary">
                {editingCategory ? 'Edit Category' : 'Create Category'}
              </h3>
              <button
                type="button"
                onClick={() => setModalOpen(false)}
                className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-3 text-xs">
              <div>
                <label className="text-text-secondary block font-semibold mb-1">
                  Category Name <span className="text-danger">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Beverages, Dairy, Snacks"
                  className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none"
                />
              </div>

              <div>
                <label className="text-text-secondary block font-semibold mb-1">Parent Category (Optional)</label>
                <Select
                  value={parentId}
                  onChange={(val) => setParentId(val)}
                  placeholder="None (Top-Level Category)"
                  options={[
                    { value: '', label: 'None (Top-Level Category)' },
                    ...categories
                      .filter((c) => !editingCategory || c.id !== editingCategory.id)
                      .map((c) => ({ value: String(c.id), label: c.name })),
                  ]}
                />
                <p className="text-[10px] text-text-muted mt-1">
                  Nest this category under an existing parent class
                </p>
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-border-default">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="px-3 py-1.5 rounded-lg font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="px-4 py-1.5 rounded-lg font-bold bg-brand text-white hover:bg-brand-hover shadow-xs disabled:opacity-50 cursor-pointer"
                >
                  {editingCategory ? 'Save' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
