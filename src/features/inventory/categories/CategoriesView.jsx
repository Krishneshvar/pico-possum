import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { FolderTree, Plus, Trash2, Edit2, X, Tag } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import DataTable from '../../../components/common/DataTable';

export default function CategoriesView() {
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [name, setName] = useState('');

  const { data: categories = [], isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: () => tauriApi.getCategories(),
  });

  const { data: products = [] } = useQuery({
    queryKey: ['products'],
    queryFn: () => tauriApi.getProducts({ limit: 1000 }),
  });

  // Calculate product counts per category
  const productCounts = useMemo(() => {
    const counts = {};
    for (const p of products) {
      if (p.category_id) {
        counts[p.category_id] = (counts[p.category_id] || 0) + 1;
      }
    }
    return counts;
  }, [products]);

  const createMutation = useMutation({
    mutationFn: (payload) => tauriApi.createCategory(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      setModalOpen(false);
      setName('');
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
    },
    onError: (err) => alert(`Error: ${err}`),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => tauriApi.deleteCategory(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['categories'] }),
    onError: (err) => alert(`Error: ${err}`),
  });

  const handleOpenCreate = () => {
    setEditingCategory(null);
    setName('');
    setModalOpen(true);
  };

  const handleOpenEdit = (c) => {
    setEditingCategory(c);
    setName(c.name);
    setModalOpen(true);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!name.trim()) return;

    if (editingCategory) {
      updateMutation.mutate({
        id: editingCategory.id,
        payload: { name: name.trim(), parent_id: null },
      });
    } else {
      createMutation.mutate({
        name: name.trim(),
        parent_id: null,
      });
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
      key: 'product_count',
      label: 'Products',
      className: 'text-slate-400 font-mono text-xs',
      render: (r) => {
        const count = productCounts[r.id] || 0;
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-slate-800 text-slate-300 border border-slate-700/60">
            <Tag className="w-2.5 h-2.5 text-slate-500" />
            {count} {count === 1 ? 'product' : 'products'}
          </span>
        );
      },
    },
    {
      key: 'status',
      label: 'Status',
      className: 'text-xs',
      render: () => (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          Active
        </span>
      ),
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
            <p className="text-[11px] text-slate-500">Organize your store catalog into sections</p>
          </div>
        </div>

        <button
          onClick={handleOpenCreate}
          className="px-3.5 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-1.5 shadow-lg shadow-emerald-500/20 transition-all"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>Add Category</span>
        </button>
      </div>

      {/* Categories DataTable */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          data={categories}
          keyFn={(r) => r.id}
          searchKeys={['name']}
          pageSize={20}
          emptyMessage="No categories created yet."
          isLoading={isLoading}
          actions={(c) => (
            <div className="flex items-center justify-end gap-1">
              <button
                onClick={() => handleOpenEdit(c)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-emerald-400 hover:bg-slate-800 transition-colors"
                title="Edit Category"
              >
                <Edit2 className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => {
                  if (confirm(`Delete category '${c.name}'?`)) {
                    deleteMutation.mutate(c.id);
                  }
                }}
                className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition-colors"
                title="Delete Category"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          )}
        />
      </div>

      {/* Modal */}
      {modalOpen && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h3 className="font-bold text-sm text-slate-100">
                {editingCategory ? 'Edit Category' : 'Create Category'}
              </h3>
              <button onClick={() => setModalOpen(false)} className="text-slate-400 hover:text-white">
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
