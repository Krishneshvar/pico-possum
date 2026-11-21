import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  ShieldCheck,
  RefreshCw,
  Eye,
  X,
  Clock,
  User,
  Layers,
  ChevronDown,
  ChevronRight,
  Copy,
  Check,
} from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatDateTime } from '../../../lib/formatters';
import DataTable from '../../../components/common/DataTable';
import { strings } from '../../../lib/strings';

export default function AuditLogView() {
  const [selectedLog, setSelectedLog] = useState(null);
  const [showRawJson, setShowRawJson] = useState(false);
  const [copiedJson, setCopiedJson] = useState(false);

  const { data: logs = [], isLoading, refetch } = useQuery({
    queryKey: ['auditLogs'],
    queryFn: () => tauriApi.getAuditLogs(300),
  });

  const eventTypeOptions = useMemo(() => {
    const counts = {};
    for (const l of logs) {
      const et = l.eventType ?? l.event_type;
      if (et) {
        counts[et] = (counts[et] || 0) + 1;
      }
    }
    const types = Object.keys(counts).sort();
    return types.map((t) => ({
      value: t,
      label: strings.common.table.filterCount(t, counts[t]),
    }));
  }, [logs]);

  const getActor = (log) => {
    if (log.actor) return log.actor;
    const entityType = log.entityType ?? log.entity_type;
    const eventType = log.eventType ?? log.event_type;
    if (entityType === 'SYSTEM' || eventType?.includes('BACKUP'))
      return strings.productFlow.auditLog.actors.system;
    return strings.productFlow.auditLog.actors.admin;
  };

  const columns = [
    {
      key: 'createdAt',
      label: strings.productFlow.columns.timestamp,
      className: 'text-content-secondary text-xs whitespace-nowrap',
      render: (r) => formatDateTime(r.createdAt ?? r.created_at),
    },
    {
      key: 'actor',
      label: strings.productFlow.columns.actor,
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-surface-secondary text-content-secondary border border-border">
          <User className="w-3 h-3 text-content-muted" />
          <span>{getActor(r)}</span>
        </span>
      ),
    },
    {
      key: 'eventType',
      label: strings.productFlow.columns.action,
      headerClassName: 'text-center',
      className: 'text-center',
      render: (r) => (
        <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium bg-surface-secondary text-content border border-border">
          {r.eventType ?? r.event_type}
        </span>
      ),
    },
    {
      key: 'entityType',
      label: strings.productFlow.columns.module,
      className: 'text-xs text-content-secondary font-medium tracking-wide uppercase',
      render: (r) => (r.entityType ?? r.entity_type) || 'SYSTEM',
    },
    {
      key: 'entityId',
      label: strings.productFlow.columns.entityId,
      className: 'font-mono text-xs text-content-muted truncate max-w-[120px]',
      render: (r) => (r.entityId ?? r.entity_id) || strings.common.states.emptyValue,
    },
    {
      key: 'description',
      label: strings.common.labels.description,
      className: 'text-content text-xs font-normal',
    },
  ];

  const handleCopyJson = () => {
    if (!selectedLog) return;
    navigator.clipboard.writeText(JSON.stringify(selectedLog, null, 2));
    setCopiedJson(true);
    setTimeout(() => setCopiedJson(false), 2000);
  };

  const a = strings.productFlow.auditLog;
  const c = strings.common;

  return (
    <div className="flex-1 flex flex-col h-full bg-canvas text-content overflow-hidden">
      {/* Header */}
      <div className="h-14 px-6 border-b border-border bg-surface flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
            <ShieldCheck className="w-4 h-4" />
          </div>
          <div>
            <h1 className="text-sm font-semibold text-content">{a.title}</h1>
            <p className="text-xs text-content-secondary">
              {a.subtitle}
            </p>
          </div>
        </div>

        <button
          onClick={() => refetch()}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content-secondary hover:text-content text-xs font-medium transition-colors"
          title={a.refreshTitle}
        >
          <RefreshCw className="w-3.5 h-3.5 text-brand" />
          <span>{c.actions.refresh}</span>
        </button>
      </div>

      {/* Main Audit DataTable */}
      <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
        <DataTable
          columns={columns}
          persistKey="audit-log"
          data={logs}
          keyFn={(r) => r.id}
          searchKeys={['description', 'entityId', 'eventType', 'entityType']}
          filters={[
            {
              type: 'multiselect',
              key: 'eventType',
              label: a.allActions,
              options: eventTypeOptions,
              filterFn: (row, vals) => vals.includes(row.eventType ?? row.event_type),
            },
          ]}
          pageSize={25}
          emptyMessage={a.emptyTable}
          isLoading={isLoading}
          actions={(r) => (
            <button
              onClick={() => {
                setSelectedLog(r);
                setShowRawJson(false);
              }}
              className="p-1.5 rounded-lg text-content-secondary hover:text-brand hover:bg-surface-secondary transition-colors"
              title={a.inspectTitle}
            >
              <Eye className="w-3.5 h-3.5" />
            </button>
          )}
        />
      </div>

      {/* Audit Detail Inspector Drawer / Modal */}
      {selectedLog && (
        <div className="fixed inset-0 bg-slate-950/60 z-50 flex items-center justify-center p-4">
          <div className="bg-surface border border-border rounded-xl max-w-lg w-full p-6 space-y-4 shadow-xl">
            <div className="flex justify-between items-center border-b border-border pb-3">
              <div className="flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-brand" />
                <h3 className="font-semibold text-sm text-content">{a.detailsTitle}</h3>
              </div>
              <button
                onClick={() => setSelectedLog(null)}
                className="text-content-muted hover:text-content p-1 rounded-lg hover:bg-surface-secondary transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-3 p-3.5 bg-surface-secondary rounded-lg border border-border">
                <div>
                  <span className="text-[11px] text-content-muted font-medium block">{a.details.timestamp}</span>
                  <span className="text-content font-medium">{formatDateTime(selectedLog.createdAt ?? selectedLog.created_at)}</span>
                </div>
                <div>
                  <span className="text-[11px] text-content-muted font-medium block">{a.details.actor}</span>
                  <span className="text-content font-medium">{getActor(selectedLog)}</span>
                </div>
                <div>
                  <span className="text-[11px] text-content-muted font-medium block">{a.details.actionEvent}</span>
                  <span className="text-brand font-medium">{selectedLog.eventType ?? selectedLog.event_type}</span>
                </div>
                <div>
                  <span className="text-[11px] text-content-muted font-medium block">{a.details.moduleEntity}</span>
                  <span className="text-content font-medium">{(selectedLog.entityType ?? selectedLog.entity_type) || 'SYSTEM'}</span>
                </div>
                {(selectedLog.entityId ?? selectedLog.entity_id) && (
                  <div className="col-span-2">
                    <span className="text-[11px] text-content-muted font-medium block">{a.details.entityIdentifier}</span>
                    <span className="font-mono text-content-secondary text-[11px] break-all">
                      {selectedLog.entityId ?? selectedLog.entity_id}
                    </span>
                  </div>
                )}
              </div>

              <div>
                <label className="text-content-secondary block mb-1 font-medium">{a.details.eventDescription}</label>
                <div className="p-3 bg-surface-secondary border border-border rounded-lg text-content text-xs leading-relaxed">
                  {selectedLog.description}
                </div>
              </div>

              {/* Collapsible Technical Raw JSON View */}
              <div className="border border-border rounded-lg overflow-hidden">
                <button
                  type="button"
                  onClick={() => setShowRawJson(!showRawJson)}
                  className="w-full flex items-center justify-between px-3 py-2 bg-surface-secondary text-content-secondary hover:text-content text-xs font-medium transition-colors"
                >
                  <span className="flex items-center gap-1.5">
                    {showRawJson ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
                    <span>{a.rawData}</span>
                  </span>
                  <span className="text-[10px] text-content-muted uppercase">{a.json}</span>
                </button>

                {showRawJson && (
                  <div className="p-3 bg-canvas border-t border-border space-y-2">
                    <div className="flex justify-end">
                      <button
                        type="button"
                        onClick={handleCopyJson}
                        className="flex items-center gap-1 px-2 py-1 rounded bg-surface border border-border text-[11px] text-content-secondary hover:text-content"
                      >
                        {copiedJson ? <Check className="w-3 h-3 text-success" /> : <Copy className="w-3 h-3" />}
                        <span>{copiedJson ? a.copied : a.copyJson}</span>
                      </button>
                    </div>
                    <pre className="p-2.5 bg-surface-secondary border border-border rounded font-mono text-[11px] text-content overflow-x-auto max-h-48">
                      {JSON.stringify(selectedLog, null, 2)}
                    </pre>
                  </div>
                )}
              </div>
            </div>

            <div className="flex justify-end pt-3 border-t border-border">
              <button
                onClick={() => setSelectedLog(null)}
                className="px-4 py-2 rounded-lg font-medium bg-surface border border-border hover:bg-surface-secondary text-content text-xs transition-colors"
              >
                {c.actions.close}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
