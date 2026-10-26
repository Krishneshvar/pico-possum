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
import DataTable from '../../../components/common/DataTable';
import DropdownActions from '../../../components/common/DropdownActions';
import ImportCsvButton from '../../../components/common/ImportCsvButton';


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
        className={`group flex items-center justify-between py-1.5 px-2 rounded-lg text-xs cursor-pointer transition-all ${
          isSelected
            ? 'bg-emerald-500/15 text-emerald-300 font-semibold border border-emerald-500/30'
            : 'text-slate-300 hover:bg-slate-800/80 hover:text-white'
        }`}
        onClick={() => onSelect(node.id)}
      >
        <div className="flex items-center gap-1.5 min-w-0">
          {hasChildren ? (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onToggleExpand(node.id);
              }}
              className="p-0.5 text-slate-500 hover:text-slate-200 transition-colors"
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
            <FolderOpen className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          ) : (
            <Folder className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          )}

          <span className="truncate">{node.name}</span>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          <span className="px-1.5 py-0.2 rounded-full text-[10px] font-mono bg-slate-800/80 text-slate-400 border border-slate-700/60">
            {count}
          </span>

          <div className="hidden group-hover:flex items-center gap-0.5">
            <button
              onClick={(e) => {
                e.stopPropagation();
                onAddChild(node);
              }}
              title="Add subcategory"
              className="p-1 rounded text-slate-400 hover:text-emerald-400 hover:bg-slate-700/60"
            >
              <Plus className="w-3 h-3" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                onEdit(node);
              }}
              title="Edit category"
              className="p-1 rounded text-slate-400 hover:text-emerald-400 hover:bg-slate-700/60"
            >
              <Edit2 className="w-3 h-3" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                onDelete(node);
              }}
              title="Delete category"
              className="p-1 rounded text-slate-400 hover:text-rose-400 hover:bg-slate-700/60"
            >
              <Trash2 className="w-3 h-3" />
            </button>
          </div>
        </div>
      </div>

      {hasChildren && isExpanded && (
        <div className="pl-4 border-l border-slate-800/80 ml-2 my-0.5 space-y-0.5">
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
  const [viewMode, setViewMode] = useState('split'); // 'split' | 'table' | 'tree'
  const [selectedTreeId, setSelectedTreeId] = useState(null);
  const [expandedIds, setExpandedIds] = useState(new Set());
  const [splitWidth, setSplitWidth] = useState(288); // px — default ~w-72
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
      const next = Math.min(560, Math.max(180, dragStartWidth.current + delta));
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
    queryFn: () => tauriApi.getProducts({ limit: 1000 }),
  });

  // Calculate direct product counts per category
  const productCounts = useMemo(() => {
    const counts = {};
    for (const p of products) {
      if (p.category_id) {
        counts[p.category_id] = (counts[p.category_id] || 0) + 1;
      }
    }
    return counts;
  }, [products]);

  // Build hierarchical tree structure
  const treeRoots = useMemo(() => {
    const map = {};
    const roots = [];
    categories.forEach((c) => {
      map[c.id] = { ...c, children: [] };
    });
    categories.forEach((c) => {
      if (c.parent_id && map[c.parent_id]) {
        map[c.parent_id].children.push(map[c.id]);
      } else {
        roots.push(map[c.id]);
      }
    });
    return roots;
  }, [categories]);

  // Filtered table rows if a tree node is selected
  const displayedCategories = useMemo(() => {
    if (!selectedTreeId) return categories;

    // Helper to get selected node + all descendant ids
    const descendantIds = new Set([selectedTreeId]);
    const addChildren = (catId) => {
      categories.forEach((c) => {
        if (c.parent_id === catId && !descendantIds.has(c.id)) {
          descendantIds.add(c.id);
          addChildren(c.id);
        }
      });
    };
    addChildren(selectedTreeId);

    return categories.filter((c) => descendantIds.has(c.id));
  }, [categories, selectedTreeId]);

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
    },
    onError: (err) => alert(`Error: ${err}`),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => tauriApi.updateCategory(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      setModalOpen(false);
      setEditingCategory(null);
      setName('');
      setParentId('');
    },
    onError: (err) => alert(`Error: ${err}`),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      if (selectedTreeId) setSelectedTreeId(null);
    },
    onError: (err) => alert(`Error: ${err}`),
  });

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

  const handleDelete = (c) => {
    if (confirm(`Delete category '${c.name}'? Subcategories and products will be detached.`)) {
      deleteMutation.mutate(c.id);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!name.trim()) return;

    const payload = {
      name: name.trim(),
      parent_id: parentId || null,
    };

    if (editingCategory) {
      updateMutation.mutate({ id: editingCategory.id, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const columns = [
    {
      key: 'name',
      label: 'Category Name',
      className: 'font-semibold text-slate-200',
      render: (r) => (
        <div className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded-lg bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20 shrink-0">
            <FolderTree className="w-3.5 h-3.5" />
          </div>
          <span>{r.name}</span>
        </div>
      ),
    },
    {
      key: 'parent_name',
      label: 'Parent Category',
      className: 'text-slate-400 text-xs',
      render: (r) =>
        r.parent_name ? (
          <span className="inline-flex items-center gap-1 text-slate-300">
            <Folder className="w-3 h-3 text-slate-500" />
            {r.parent_name}
          </span>
        ) : (
          <span className="text-slate-600">—</span>
        ),
    },
    {
      key: 'product_count',
      label: 'Products',
      className: 'text-slate-400 font-mono text-xs',
      render: (r) => {
        const count = productCounts[r.id] || 0;
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-slate-800 text-slate-300 border border-slate-700/60">
            <Tag className="w-2.5 h-2.5 text-slate-500" />
            {count} {count === 1 ? 'item' : 'items'}
          </span>
        );
      },
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <FolderTree className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Product Categories</h2>
            <p className="text-[11px] text-slate-500">Organize store catalog into classes and hierarchy</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* View Mode Toggle */}
          <div className="flex bg-slate-900 border border-slate-800 rounded-lg p-0.5 text-xs">
            <button
              onClick={() => setViewMode('split')}
              className={`p-1.5 rounded flex items-center gap-1 transition-all ${
                viewMode === 'split' ? 'bg-emerald-500 text-slate-950 font-bold shadow' : 'text-slate-400 hover:text-white'
              }`}
              title="Split View (Tree + Table)"
            >
              <Columns className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Split</span>
            </button>
            <button
              onClick={() => setViewMode('tree')}
              className={`p-1.5 rounded flex items-center gap-1 transition-all ${
                viewMode === 'tree' ? 'bg-emerald-500 text-slate-950 font-bold shadow' : 'text-slate-400 hover:text-white'
              }`}
              title="Tree View"
            >
              <ListTree className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Tree</span>
            </button>
            <button
              onClick={() => setViewMode('table')}
              className={`p-1.5 rounded flex items-center gap-1 transition-all ${
                viewMode === 'table' ? 'bg-emerald-500 text-slate-950 font-bold shadow' : 'text-slate-400 hover:text-white'
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

          <button
            onClick={() => handleOpenCreate()}
            className="px-3.5 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-1.5 shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Category</span>
          </button>
        </div>
      </div>


      {/* Main Content Area */}
      <div className="flex-1 flex overflow-hidden">
        {/* Category Tree Pane (Visible in 'split' or 'tree' mode) */}
        {(viewMode === 'split' || viewMode === 'tree') && (
          <div
          className={`flex flex-col border-r border-slate-800/80 bg-slate-900/30 overflow-hidden ${
              viewMode === 'tree' ? 'flex-1' : ''
            }`}
            style={viewMode === 'split' ? { width: splitWidth, flexShrink: 0 } : undefined}
          >
            <div className="p-3 border-b border-slate-800/80 flex items-center justify-between">
              <span className="text-xs font-bold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
                <ListTree className="w-3.5 h-3.5 text-emerald-400" />
                Category Hierarchy
              </span>
              {selectedTreeId && (
                <button
                  onClick={() => setSelectedTreeId(null)}
                  className="text-[11px] text-slate-400 hover:text-emerald-400"
                >
                  Clear Selection
                </button>
              )}
            </div>

            <div className="flex-1 overflow-y-auto p-3 space-y-1">
              {/* All Categories Root selector */}
              <div
                className={`py-1.5 px-2.5 rounded-lg text-xs cursor-pointer flex items-center justify-between transition-colors ${
                  !selectedTreeId
                    ? 'bg-emerald-500/15 text-emerald-300 font-bold border border-emerald-500/30'
                    : 'text-slate-300 hover:bg-slate-800/80'
                }`}
                onClick={() => setSelectedTreeId(null)}
              >
                <div className="flex items-center gap-2">
                  <FolderOpen className="w-3.5 h-3.5 text-emerald-400" />
                  <span>All Categories</span>
                </div>
                <span className="text-[10px] font-mono text-slate-500">
                  {categories.length} total
                </span>
              </div>

              {/* Hierarchy Tree Nodes */}
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
                <div className="text-center py-8 text-xs text-slate-500">
                  No categories created yet. Click "Add Category" above.
                </div>
              )}
            </div>
          </div>
        )}

        {/* Drag divider — only in split mode */}
        {viewMode === 'split' && (
          <div
            onMouseDown={handleDividerMouseDown}
            className="w-1 flex-shrink-0 bg-slate-800/80 hover:bg-emerald-500/60 active:bg-emerald-500 cursor-col-resize transition-colors group relative"
            title="Drag to resize"
          >
            <div className="absolute inset-y-0 -left-1 -right-1" />
          </div>
        )}

        {/* Data Table Pane (Visible in 'split' or 'table' mode) */}
        {(viewMode === 'split' || viewMode === 'table') && (
          <div className="flex-1 flex flex-col overflow-hidden">
            {selectedTreeId && (
              <div className="px-4 py-2 bg-emerald-500/10 border-b border-emerald-500/20 text-xs text-emerald-300 flex items-center justify-between">
                <span>
                  Showing category and descendants for:{' '}
                  <strong>{categories.find((c) => c.id === selectedTreeId)?.name}</strong>
                </span>
                <button
                  onClick={() => setSelectedTreeId(null)}
                  className="text-[11px] underline hover:text-white"
                >
                  Show all categories
                </button>
              </div>
            )}

            <div className="flex-1 overflow-hidden">
              <DataTable
                columns={columns}
                data={displayedCategories}
                keyFn={(r) => r.id}
                searchKeys={['name', 'parent_name']}
                pageSize={20}
                emptyMessage="No categories found."
                isLoading={isLoading}
                actions={(c) => (
                  <DropdownActions items={[
                    { label: 'Add Subcategory', icon: <Plus className="w-3.5 h-3.5" />, onClick: () => handleOpenCreate(c) },
                    { label: 'Edit Category', icon: <Edit2 className="w-3.5 h-3.5" />, onClick: () => handleOpenEdit(c) },
                    'divider',
                    { label: 'Delete Category', icon: <Trash2 className="w-3.5 h-3.5" />, danger: true, onClick: () => handleDelete(c) },
                  ]} />
                )}
              />
            </div>
          </div>
        )}
      </div>

      {/* Create / Edit Modal with Parent Category field */}
      {modalOpen && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h3 className="font-bold text-sm text-slate-100">
                {editingCategory ? 'Edit Category' : 'Create Category'}
              </h3>
              <button
                onClick={() => setModalOpen(false)}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Category Name *</label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Beverages, Snacks, Personal Care"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Parent Category (Optional)</label>
                <select
                  value={parentId}
                  onChange={(e) => setParentId(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 focus:border-emerald-500 focus:outline-none"
                >
                  <option value="">None (Top-Level Category)</option>
                  {categories
                    .filter((c) => !editingCategory || c.id !== editingCategory.id)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                </select>
                <p className="text-[10px] text-slate-500 mt-1">
                  Nest this category under an existing parent class
                </p>
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="px-3 py-1.5 rounded-lg font-semibold text-slate-400 hover:bg-slate-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="px-4 py-1.5 rounded-lg font-bold bg-emerald-500 text-slate-950 hover:bg-emerald-400"
                >
                  {editingCategory ? 'Save Changes' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
