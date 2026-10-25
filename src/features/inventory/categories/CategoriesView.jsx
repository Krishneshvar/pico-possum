import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { FolderTree, Plus, Trash2, Edit2, X } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';

export default function CategoriesView() {
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [name, setName] = useState('');

  const { data: categories = [], isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: () => tauriApi.getCategories(),
  });

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

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-16 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <FolderTree className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">Product Categories</h2>
            <p className="text-xs text-slate-400">Organize your store catalog into sections</p>
          </div>
        </div>

        <button
          onClick={handleOpenCreate}
          className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/20 transition-all"
        >
          <Plus className="w-4 h-4" />
          <span>Add Category</span>
        </button>
      </div>

      {/* Categories Grid */}
      <div className="flex-1 overflow-y-auto p-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {categories.map((c) => (
            <div
              key={c.id}
              className="glass-card p-4 rounded-xl border border-slate-800 flex items-center justify-between group hover:border-slate-700 transition-all"
            >
              <div>
                <h4 className="text-sm font-semibold text-slate-200 group-hover:text-emerald-400 transition-colors">
                  {c.name}
                </h4>
                <p className="text-[11px] text-slate-500 mt-0.5">Active</p>
              </div>

              <div className="flex items-center gap-1 opacity-80 group-hover:opacity-100">
                <button
                  onClick={() => handleOpenEdit(c)}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-emerald-400 hover:bg-slate-800"
                >
                  <Edit2 className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={() => {
                    if (confirm(`Delete category '${c.name}'?`)) {
                      deleteMutation.mutate(c.id);
                    }
                  }}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>
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
