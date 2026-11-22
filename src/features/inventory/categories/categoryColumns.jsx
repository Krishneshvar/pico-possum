import { Folder, Tag } from 'lucide-react';
import { strings } from '../../../lib/strings';

export function getCategoryColumns({ productCounts }) {
  return [
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
      key: 'parentName',
      label: strings.categories.columns.parent,
      className: 'text-text-muted text-xs',
      render: (r) => r.parentName || strings.common.states.emptyValue,
    },
    {
      key: 'productCount',
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
}
