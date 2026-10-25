import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ShieldCheck, RefreshCw, Eye, X, Terminal, Clock, Box } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';

const ACTION_COLORS = {
  SALE_CREATED: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
  SALE_CANCELLED: 'bg-rose-500/15 text-rose-400 border-rose-500/30',
  PRODUCT_CREATED: 'bg-sky-500/15 text-sky-400 border-sky-500/30',
  PRODUCT_UPDATED: 'bg-amber-500/15 text-amber-400 border-amber-500/30',
  PRODUCT_DELETED: 'bg-rose-500/15 text-rose-400 border-rose-500/30',
  STOCK_ADJUSTMENT: 'bg-indigo-500/15 text-indigo-400 border-indigo-500/30',
  CUSTOMER_CREATED: 'bg-teal-500/15 text-teal-400 border-teal-500/30',
  LEGACY_MIGRATION: 'bg-purple-500/15 text-purple-400 border-purple-500/30',
};

export default function AuditLogView() {
  const [selectedLog, setSelectedLog] = useState(null);

  const { data: logs = [], isLoading, refetch } = useQuery({
    queryKey: ['audit_logs'],
    queryFn: () => tauriApi.getAuditLogs(300),
  });

  const eventTypeOptions = useMemo(() => {
    const types = Array.from(new Set(logs.map((l) => l.event_type))).filter(Boolean);
    return types.map((t) => ({ value: t, label: t }));
  }, [logs]);

  const columns = [
    {
      key: 'created_at',
      label: 'Timestamp',
      className: 'font-mono text-slate-400 text-[11px] whitespace-nowrap',
      render: (r) => formatDateTime(r.created_at),
    },
    {
      key: 'event_type',
      label: 'Action',
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => {
        const cls = ACTION_COLORS[r.event_type] || 'bg-slate-800 text-slate-300 border-slate-700/60';
        return (
          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold font-mono border ${cls}`}>
            {r.event_type}
          </span>
        );
      },
    },
    {
      key: 'entity_type',
      label: 'Module',
      className: 'font-mono text-xs text-slate-300 uppercase tracking-wider',
      render: (r) => r.entity_type || 'SYSTEM',
    },
    {
      key: 'entity_id',
      label: 'Entity ID',
      className: 'font-mono text-[11px] text-slate-500 truncate max-w-[120px]',
      render: (r) => r.entity_id || '—',
    },
    {
      key: 'description',
      label: 'Description',
      className: 'text-slate-300 text-xs font-normal',
    },
  ];

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-14 px-5 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <ShieldCheck className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Audit Log</h2>
            <p className="text-[11px] text-slate-500">Security event trail, transaction records, and administrative modifications</p>
          </div>
        </div>

        <button
          onClick={() => refetch()}
          className="p-1.5 rounded-lg border border-slate-800 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          title="Refresh Logs"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {/* Main Audit DataTable */}
      <div className="flex-1 overflow-hidden">
        <DataTable
          columns={columns}
          data={logs}
          keyFn={(r) => r.id}
          searchKeys={['description', 'entity_id', 'event_type', 'entity_type']}
          filters={[
            {
              key: 'event_type',
              label: 'All Actions',
              options: eventTypeOptions,
            },
          ]}
          pageSize={25}
          emptyMessage="No audit logs found."
          isLoading={isLoading}
          actions={(r) => (
            <button
              onClick={() => setSelectedLog(r)}
              className="p-1.5 rounded-lg text-slate-400 hover:text-emerald-400 hover:bg-slate-800 transition-colors"
              title="Inspect Audit Entry"
            >
              <Eye className="w-3.5 h-3.5" />
            </button>
          )}
        />
      </div>

      {/* Audit Detail Inspector Modal */}
      {selectedLog && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-5 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <Terminal className="w-4 h-4 text-emerald-400" />
                <h3 className="font-bold text-sm text-slate-100">Audit Event Details</h3>
              </div>
              <button
                onClick={() => setSelectedLog(null)}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-3 p-3 bg-slate-950 rounded-xl border border-slate-800/80">
                <div>
                  <span className="text-[10px] text-slate-500 uppercase font-medium block">Timestamp</span>
                  <span className="font-mono text-slate-300">{formatDateTime(selectedLog.created_at)}</span>
                </div>
                <div>
                  <span className="text-[10px] text-slate-500 uppercase font-medium block">Action</span>
                  <span className="font-mono font-bold text-emerald-400">{selectedLog.event_type}</span>
                </div>
                <div>
                  <span className="text-[10px] text-slate-500 uppercase font-medium block">Module / Entity</span>
                  <span className="font-mono text-slate-300">{selectedLog.entity_type}</span>
                </div>
                <div>
                  <span className="text-[10px] text-slate-500 uppercase font-medium block">Entity ID</span>
                  <span className="font-mono text-slate-400 text-[11px] truncate block">{selectedLog.entity_id}</span>
                </div>
              </div>

              <div>
                <label className="text-slate-400 block mb-1 font-medium">Event Description</label>
                <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl font-mono text-slate-200 text-xs">
                  {selectedLog.description}
                </div>
              </div>
            </div>

            <div className="flex justify-end pt-2 border-t border-slate-800">
              <button
                onClick={() => setSelectedLog(null)}
                className="px-4 py-1.5 rounded-lg font-bold bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
