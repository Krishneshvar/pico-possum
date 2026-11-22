import { useState, useRef, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { HardDrive, RefreshCw, FolderOpen, Upload } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatDateTime } from '../../../lib/formatters';
import SortableTh from '../../../components/common/SortableTh';
import ErrorDetailModal from '../../../components/common/ErrorDetailModal';
import DataProgressModal from '../../../components/common/DataProgressModal';
import { formatHumanError } from '../../../lib/formatHumanError';
import { nextSortState, sortRows } from '../../../lib/sort';
import { strings } from '../../../lib/strings';

export default function BackupTab() {
  const queryClient = useQueryClient();
  const restoreFileInputRef = useRef(null);
  const [backupSort, setBackupSort] = useState({ key: null, dir: null });
  const [restoreError, setRestoreError] = useState(null);
  const [restoreProgress, setRestoreProgress] = useState({
    isOpen: false,
    fileName: '',
    fileSize: null,
    progressPercent: 0,
    statusMessage: '',
    steps: [],
    isProcessing: false,
    result: null,
    error: null,
  });

  const createRestoreSteps = (activeStep = 'validate') => [
    {
      id: 'validate',
      label: 'Validate backup archive & SQLite integrity',
      detail: 'Verifying file format and database header signatures',
      status: activeStep === 'validate' ? 'active' : 'completed',
    },
    {
      id: 'snapshot',
      label: 'Create safety pre-restore backup',
      detail: 'Generating point-in-time recovery safety snapshot',
      status: activeStep === 'snapshot' ? 'active' : ['restore', 'finalize', 'complete'].includes(activeStep) ? 'completed' : 'pending',
    },
    {
      id: 'restore',
      label: 'Restore database records & migrate schemas',
      detail: 'Migrating tables, restoring products, sales, and rebuilding indices',
      status: activeStep === 'restore' ? 'active' : ['finalize', 'complete'].includes(activeStep) ? 'completed' : 'pending',
    },
    {
      id: 'finalize',
      label: 'Finalize & refresh application workspace',
      detail: 'Syncing system cache and reloading data stores',
      status: activeStep === 'finalize' ? 'active' : activeStep === 'complete' ? 'completed' : 'pending',
    },
  ];

  const { data: backupStatus } = useQuery({
    queryKey: ['backupStatus'],
    queryFn: () => tauriApi.getBackupStatus(),
  });

  const { data: backupsList = [] } = useQuery({
    queryKey: ['backupsList'],
    queryFn: () => tauriApi.listBackups(),
  });

  const sortedBackups = useMemo(
    () => sortRows(backupsList, backupSort, (row, key) => row[key] ?? row[key.replace(/([A-Z])/g, '_$1').toLowerCase()]),
    [backupsList, backupSort]
  );

  const createBackupMutation = useMutation({
    mutationFn: () => tauriApi.createManualBackup(),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['backupStatus'] });
      queryClient.invalidateQueries({ queryKey: ['backupsList'] });
      toast.success(strings.settings.toasts.backupCreated(res?.filename || ''));
    },
    onError: (err) =>
      toast.error(strings.settings.toasts.backupFailed(err?.message || err), {
        title: strings.settings.toasts.backupFailedTitle,
      }),
  });

  const restoreBackupMutation = useMutation({
    mutationFn: async (arg) => {
      if (typeof arg === 'object' && arg.bytes) {
        return tauriApi.restoreBackupFile(arg.filename, arg.bytes);
      }
      return tauriApi.restoreBackup(arg);
    },
    onSuccess: (res) => {
      queryClient.invalidateQueries();
      setRestoreError(null);
      toast.success(typeof res === 'string' ? res : strings.settings.toasts.backupRestored(res?.filename || ''));
    },
    onError: (err) => {
      const parsed = formatHumanError(err, 'restore');
      setRestoreError(err);
      toast.error(parsed.message, {
        title: parsed.title,
      });
    },
  });

  const startRestoreWithProgress = (arg, fileName, fileSize = null) => {
    setRestoreProgress({
      isOpen: true,
      fileName,
      fileSize,
      progressPercent: 20,
      statusMessage: 'Inspecting backup file and checking SQLite integrity...',
      steps: createRestoreSteps('validate'),
      isProcessing: true,
      result: null,
      error: null,
    });

    const timer1 = setTimeout(() => {
      setRestoreProgress((prev) => ({
        ...prev,
        progressPercent: 45,
        statusMessage: 'Creating automatic safety pre-restore snapshot...',
        steps: createRestoreSteps('snapshot'),
      }));
    }, 350);

    const timer2 = setTimeout(() => {
      setRestoreProgress((prev) => ({
        ...prev,
        progressPercent: 75,
        statusMessage: 'Restoring tables and executing schema migrations...',
        steps: createRestoreSteps('restore'),
      }));
    }, 800);

    restoreBackupMutation.mutate(arg, {
      onSuccess: (res) => {
        clearTimeout(timer1);
        clearTimeout(timer2);
        setRestoreProgress({
          isOpen: true,
          fileName,
          fileSize,
          progressPercent: 100,
          statusMessage: 'Restoration completed successfully!',
          steps: createRestoreSteps('complete'),
          isProcessing: false,
          result: res,
          error: null,
        });
      },
      onError: (err) => {
        clearTimeout(timer1);
        clearTimeout(timer2);
        const parsed = formatHumanError(err, 'restore');
        setRestoreProgress({
          isOpen: true,
          fileName,
          fileSize,
          progressPercent: 100,
          statusMessage: 'Restore operation failed',
          steps: [
            { id: 'validate', label: 'Validate backup archive & SQLite integrity', status: 'completed' },
            { id: 'restore', label: 'Restore database records & migrate schemas', status: 'error', detail: parsed.message },
          ],
          isProcessing: false,
          result: null,
          error: parsed,
        });
      },
    });
  };

  return (
    <div className="max-w-3xl space-y-6">
      <input
        ref={restoreFileInputRef}
        type="file"
        accept=".db"
        className="hidden"
        onChange={async (e) => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (!file) return;

          const { confirmed } = await confirmDialog({
            title: strings.settings.backup.confirmFileRestore.title,
            message: strings.settings.backup.confirmFileRestore.message(file.name),
            details: strings.settings.backup.confirmFileRestore.details,
            confirmLabel: strings.settings.backup.confirmFileRestore.confirmLabel,
            tone: 'warning',
          });
          if (!confirmed) return;

          if (file.path) {
            startRestoreWithProgress(file.path, file.name, `${(file.size / 1024).toFixed(1)} KB`);
          } else {
            try {
              const buffer = await file.arrayBuffer();
              const bytes = Array.from(new Uint8Array(buffer));
              startRestoreWithProgress({ filename: file.name, bytes }, file.name, `${(file.size / 1024).toFixed(1)} KB`);
            } catch (err) {
              const parsed = formatHumanError(err, 'restore');
              setRestoreError(err);
              toast.error(parsed.message, { title: parsed.title });
            }
          }
        }}
      />

      <div>
        <h2 className="text-base font-semibold text-content">{strings.settings.backup.title}</h2>
        <p className="text-xs text-content-secondary">
          {strings.settings.backup.subtitle}
        </p>
      </div>

      {/* Status & Manual Action */}
      <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-border">
          <div>
            <h3 className="text-sm font-semibold text-content">{strings.settings.backup.maintenance}</h3>
            <p className="text-xs text-content-secondary">
              {strings.settings.backup.maintenanceHint}
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => createBackupMutation.mutate()}
              disabled={createBackupMutation.isPending}
              className="px-4 py-2 rounded-lg bg-brand hover:bg-brand-hover text-white font-medium text-xs flex items-center gap-1.5 transition-colors cursor-pointer"
            >
              {createBackupMutation.isPending ? (
                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <HardDrive className="w-3.5 h-3.5" />
              )}
              <span>{strings.settings.backup.createBackup}</span>
            </button>
            <button
              type="button"
              onClick={async () => {
                try {
                  await tauriApi.openBackupFolder();
                  toast.success('Opened backup folder');
                } catch (err) {
                  toast.error(`Could not open backup folder: ${err}`);
                }
              }}
              className="px-3 py-2 rounded-lg bg-surface-secondary border border-border text-content-secondary hover:text-content font-medium text-xs flex items-center gap-1.5 transition-colors"
              title={strings.settings.backup.openFolderTitle}
            >
              <FolderOpen className="w-3.5 h-3.5 text-content-muted" />
              <span>{strings.settings.backup.openFolder}</span>
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
          <div className="p-3.5 rounded-lg bg-surface-secondary border border-border">
            <span className="text-[11px] text-content-muted font-medium block mb-1">
              {strings.settings.backup.storagePath}
            </span>
            <div className="font-mono text-content text-xs break-all select-all">
              {backupStatus?.backupLocation ?? backupStatus?.backup_location ?? strings.settings.backup.loadingLocation}
            </div>
          </div>

          <div className="p-3.5 rounded-lg bg-surface-secondary border border-border">
            <span className="text-[11px] text-content-muted font-medium block mb-1">
              {strings.settings.backup.latestSnapshot}
            </span>
            <div className="text-content font-medium text-xs">
              {(backupStatus?.latestBackup ?? backupStatus?.latest_backup) ? (
                <span className="text-success font-semibold">
                  {(backupStatus.latestBackup ?? backupStatus.latest_backup).filename} (
                  {Math.round(((backupStatus.latestBackup ?? backupStatus.latest_backup).sizeBytes ?? (backupStatus.latestBackup ?? backupStatus.latest_backup).size_bytes) / 1024)} KB)
                </span>
              ) : (
                <span className="text-content-muted">{strings.settings.backup.noSnapshots}</span>
              )}
            </div>
            {(backupStatus?.latestBackup ?? backupStatus?.latest_backup)?.createdAt && (
              <span className="text-[11px] text-content-muted block mt-0.5">
                {formatDateTime((backupStatus.latestBackup ?? backupStatus.latest_backup).createdAt ?? (backupStatus.latestBackup ?? backupStatus.latest_backup).created_at)}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Available Backups Table */}
      <div className="p-6 rounded-xl bg-surface border border-border space-y-4">
        <div className="flex items-center justify-between pb-2 border-b border-border">
          <h3 className="text-xs font-semibold text-content uppercase tracking-wider">
            {strings.settings.backup.availableBackups(backupsList.length)}
          </h3>
          <button
            type="button"
            onClick={() => {
              if (restoreFileInputRef.current) {
                restoreFileInputRef.current.value = '';
                restoreFileInputRef.current.click();
              }
            }}
            className="text-xs text-brand hover:underline font-medium flex items-center gap-1"
          >
            <Upload className="w-3.5 h-3.5" />
            <span>{strings.settings.backup.uploadExternal}</span>
          </button>
        </div>

        {backupsList.length === 0 ? (
          <div className="text-center py-8 text-xs text-content-muted">
            {strings.settings.backup.emptyState}
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-surface shadow-2xs">
            <table className="w-full text-left text-xs border-collapse">
              <thead className="bg-table-header-bg text-table-header-text font-semibold border-b border-table-header-border">
                <tr className="h-10 text-[11px] uppercase tracking-wider">
                  <SortableTh sortKey="filename" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="py-2.5 px-3.5 pl-4">{strings.settings.backup.table.filename}</SortableTh>
                  <SortableTh sortKey="backupType" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="py-2.5 px-3.5">{strings.settings.backup.table.type}</SortableTh>
                  <SortableTh sortKey="createdAt" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} className="py-2.5 px-3.5">{strings.settings.backup.table.date}</SortableTh>
                  <SortableTh sortKey="sizeBytes" sort={backupSort} onSort={(key) => setBackupSort((p) => nextSortState(p, key))} align="right" className="py-2.5 px-3.5">{strings.settings.backup.table.size}</SortableTh>
                  <th className="py-2.5 pl-3.5 pr-4 font-semibold text-right">{strings.settings.backup.table.action}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {sortedBackups.map((bk) => (
                  <tr key={bk.filename} className="hover:bg-surface-secondary transition-colors">
                    <td className="py-2.5 px-3.5 pl-4 font-medium text-content truncate max-w-xs">{bk.filename}</td>
                    <td className="py-2.5 px-3.5">
                      <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-surface-secondary text-content-secondary border border-border">
                        {bk.backupType ?? bk.backup_type}
                      </span>
                    </td>
                    <td className="py-2.5 px-3.5 text-content-secondary text-xs">{formatDateTime(bk.createdAt ?? bk.created_at)}</td>
                    <td className="py-2.5 px-3.5 text-right text-content financial-value">{Math.round((bk.sizeBytes ?? bk.size_bytes) / 1024)} KB</td>
                    <td className="py-2.5 pl-3.5 pr-4 text-right">
                      <button
                        type="button"
                        onClick={async () => {
                          const { confirmed } = await confirmDialog({
                            title: strings.settings.backup.confirmBackupRestore.title,
                            message: strings.settings.backup.confirmBackupRestore.message(bk.filename),
                            details: strings.settings.backup.confirmBackupRestore.details,
                            confirmLabel: strings.settings.backup.confirmBackupRestore.confirmLabel,
                            tone: 'warning',
                          });
                          if (confirmed) {
                            startRestoreWithProgress(bk.filename, bk.filename, `${Math.round((bk.sizeBytes ?? bk.size_bytes) / 1024)} KB`);
                          }
                        }}
                        disabled={restoreBackupMutation.isPending}
                        className="px-3 py-1 rounded-lg bg-surface border border-border hover:bg-surface-secondary text-content text-xs font-medium transition-colors cursor-pointer"
                      >
                        {strings.settings.backup.restore}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Restore Progress & Status Modal */}
      <DataProgressModal
        isOpen={restoreProgress.isOpen}
        onClose={() => setRestoreProgress((prev) => ({ ...prev, isOpen: false }))}
        title="Restoring Database Backup"
        operationType="restore"
        entityLabel="Full Database Backup"
        fileName={restoreProgress.fileName}
        fileSize={restoreProgress.fileSize}
        progressPercent={restoreProgress.progressPercent}
        statusMessage={restoreProgress.statusMessage}
        steps={restoreProgress.steps}
        isProcessing={restoreProgress.isProcessing}
        result={restoreProgress.result}
        error={restoreProgress.error}
      />

      {/* Restore Error Detail Modal Overlay */}
      <ErrorDetailModal
        isOpen={Boolean(restoreError)}
        onClose={() => setRestoreError(null)}
        error={restoreError}
        context="restore"
        onChooseAnother={() => {
          if (restoreFileInputRef.current) {
            restoreFileInputRef.current.value = '';
            restoreFileInputRef.current.click();
          }
        }}
        chooseAnotherLabel={strings.settings.backup.uploadExternal}
      />
    </div>
  );
}
