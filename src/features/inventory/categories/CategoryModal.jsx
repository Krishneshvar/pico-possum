import { useState, useEffect } from 'react';
import { X } from 'lucide-react';
import Select from '../../../components/common/Select';
import { strings } from '../../../lib/strings';

export default function CategoryModal({
  open,
  onClose,
  editingCategory = null,
  initialParentId = '',
  categories = [],
  onSubmit,
  isPending = false,
}) {
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');

  useEffect(() => {
    if (editingCategory) {
      setName(editingCategory.name || '');
      setParentId(editingCategory.parent_id ? String(editingCategory.parent_id) : '');
    } else {
      setName('');
      setParentId(initialParentId ? String(initialParentId) : '');
    }
  }, [editingCategory, initialParentId, open]);

  if (!open) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!name.trim()) return;
    onSubmit({
      name: name.trim(),
      parentId: parentId ? parseInt(parentId, 10) : null,
    });
  };

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fade-in">
      <div className="bg-surface border border-border-default rounded-2xl max-w-sm w-full p-5 space-y-4 shadow-xl text-text-primary animate-scale-in">
        <div className="flex justify-between items-center border-b border-border-default pb-3">
          <h3 className="font-bold text-sm text-text-primary">
            {editingCategory
              ? strings.categories.form.editTitle
              : strings.categories.form.createTitle}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-text-muted hover:text-text-primary hover:bg-surface-secondary cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3 text-xs">
          <div>
            <label className="text-text-secondary block font-semibold mb-1">
              {strings.categories.form.nameLabel} <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={strings.categories.form.namePlaceholder}
              className="w-full bg-surface-secondary border border-border-strong rounded-lg p-2 text-text-primary focus:border-brand focus:outline-none"
            />
          </div>

          <div>
            <label className="text-text-secondary block font-semibold mb-1">
              {strings.categories.form.parentLabel}
            </label>
            <Select
              value={parentId}
              onChange={(val) => setParentId(val)}
              placeholder={strings.categories.form.noneTopLevel}
              options={[
                { value: '', label: strings.categories.form.noneTopLevel },
                ...categories
                  .filter((c) => !editingCategory || c.id !== editingCategory.id)
                  .map((c) => ({ value: String(c.id), label: c.name })),
              ]}
            />
            <p className="text-[10px] text-text-muted mt-1">
              {strings.categories.form.parentHint}
            </p>
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-border-default">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 rounded-lg font-semibold text-text-secondary hover:bg-surface-secondary border border-border-default cursor-pointer"
            >
              {strings.common.actions.cancel}
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="px-4 py-1.5 rounded-lg font-bold bg-brand text-white hover:bg-brand-hover shadow-xs disabled:opacity-50 cursor-pointer"
            >
              {editingCategory ? strings.common.actions.save : strings.common.actions.create}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
