import {
  ChevronRight,
  ChevronDown,
  Folder,
  FolderOpen,
  Plus,
  Edit2,
  Trash2,
  ListTree,
} from 'lucide-react';
import { strings } from '../../../lib/strings';

// Recursive Tree Node Component
export function TreeNode({
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
              title={strings.categories.tree.addSubcategory}
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
              title={strings.categories.tree.editCategory}
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
              title={strings.categories.tree.deleteCategory}
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

export default function CategoryTree({
  viewMode,
  splitWidth,
  selectedTreeId,
  setSelectedTreeId,
  treeRoots,
  productCounts,
  expandedIds,
  onToggleExpand,
  onAddChild,
  onEdit,
  onDelete,
}) {
  return (
    <div
      className={`flex flex-col border-r border-border-default bg-surface text-text-primary overflow-hidden ${
        viewMode === 'tree' ? 'flex-1' : ''
      }`}
      style={viewMode === 'split' ? { width: splitWidth, shrink: 0 } : undefined}
    >
      <div className="p-3 bg-gray-500 border-b border-gray-600 flex items-center justify-between text-white shadow-2xs">
        <span className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-1.5">
          <ListTree className="w-3.5 h-3.5 text-white/90" />
          {strings.categories.view.treePaneTitle}
        </span>
        {selectedTreeId && (
          <button
            type="button"
            onClick={() => setSelectedTreeId(null)}
            className="text-[11px] text-white/90 hover:text-white hover:underline cursor-pointer font-medium"
          >
            {strings.categories.view.clearSelection}
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
            <span>{strings.categories.view.allCategories}</span>
          </div>
        </div>

        {treeRoots.map((root) => (
          <TreeNode
            key={root.id}
            node={root}
            selectedId={selectedTreeId}
            onSelect={setSelectedTreeId}
            onAddChild={onAddChild}
            onEdit={onEdit}
            onDelete={onDelete}
            productCounts={productCounts}
            expandedIds={expandedIds}
            onToggleExpand={onToggleExpand}
          />
        ))}
      </div>
    </div>
  );
}
