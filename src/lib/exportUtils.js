import * as XLSX from 'xlsx';
import { tauriApi } from './tauri';
import { toast } from '../stores/toast.store';

/**
 * Escapes a cell value for standard CSV (RFC 4180).
 */
export const escapeCsv = (val) => {
  if (val === null || val === undefined) return '""';
  const str = String(val).replace(/"/g, '""');
  return `"${str}"`;
};

/**
 * Prompts the user for a save location and writes the exported file.
 * Works seamlessly with native OS dialogs (Zenity/KDialog on Linux, Finder on Mac, Explorer on Windows)
 * with graceful browser fallbacks.
 *
 * @param {Object} options
 * @param {string} options.filename - Suggested filename (e.g. products_2026-11-09.csv)
 * @param {'csv'|'xlsx'} options.extension - File extension
 * @param {string|Uint8Array|ArrayBuffer} options.data - File data
 * @param {number} [options.count] - Number of records exported
 * @param {string} [options.entityName] - Label for success toast (e.g. "products")
 */
export async function exportWithUserLocation({
  filename,
  extension,
  data,
  count,
  entityName = 'records',
}) {
  try {
    const isXlsx = extension.toLowerCase() === 'xlsx';
    let bytes;
    if (typeof data === 'string') {
      bytes = new TextEncoder().encode(data);
    } else if (data instanceof Uint8Array) {
      bytes = data;
    } else if (data instanceof ArrayBuffer) {
      bytes = new Uint8Array(data);
    } else {
      bytes = new Uint8Array(data);
    }

    // 1. Try native desktop save file dialog via Tauri backend command
    let chosenPath = null;
    let tauriAvailable = false;
    try {
      if (tauriApi && typeof tauriApi.promptSaveFileLocation === 'function') {
        chosenPath = await tauriApi.promptSaveFileLocation(filename, extension);
        tauriAvailable = true;
      }
    } catch (e) {
      console.warn('Native promptSaveFileLocation failed or unsupported:', e);
    }

    if (tauriAvailable) {
      if (chosenPath) {
        await tauriApi.saveFileToDisk(chosenPath, Array.from(bytes));
        const displayName = chosenPath.split(/[/\\]/).pop() || filename;
        toast.success(
          `Exported ${count != null ? `${count} ${entityName} ` : ''}to ${displayName}`
        );
        return true;
      } else {
        // User explicitly cancelled the dialog
        return false;
      }
    }

    // 2. Web File System Access API fallback (if available)
    if (typeof window !== 'undefined' && typeof window.showSaveFilePicker === 'function') {
      try {
        const handle = await window.showSaveFilePicker({
          suggestedName: filename,
          types: [
            {
              description: isXlsx ? 'Excel Workbook (.xlsx)' : 'CSV Spreadsheet (.csv)',
              accept: isXlsx
                ? { 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'] }
                : { 'text/csv': ['.csv'] },
            },
          ],
        });
        const writable = await handle.createWritable();
        await writable.write(bytes);
        await writable.close();
        toast.success(
          `Exported ${count != null ? `${count} ${entityName} ` : ''}to ${handle.name || filename}`
        );
        return true;
      } catch (pickerErr) {
        if (pickerErr.name === 'AbortError') {
          return false;
        }
      }
    }

    // 3. Browser anchor download fallback
    const mimeType = isXlsx
      ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      : 'text/csv;charset=utf-8;';
    const blob = new Blob([bytes], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    toast.success(`Exported ${count != null ? `${count} ${entityName}` : filename}`);
    return true;
  } catch (err) {
    const msg = typeof err === 'string' ? err : err?.message || 'Failed to export file';
    toast.error(msg, { title: 'Export Failed' });
    return false;
  }
}

