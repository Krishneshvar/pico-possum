import { Info, Shield, FileText, CheckCircle2 } from 'lucide-react';

export default function AboutTab() {
  return (
    <div className="max-w-2xl space-y-6 animate-fade-in text-xs">
      <div>
        <h2 className="text-base font-semibold text-content">About PicoPOSSUM</h2>
        <p className="text-xs text-content-secondary">
          Application version, licensing, and open-source acknowledgements
        </p>
      </div>

      {/* App Summary Card */}
      <div className="p-6 rounded-xl bg-surface border border-border space-y-4 shadow-xs">
        <div className="flex items-center gap-3 pb-3 border-b border-border">
          <div className="w-10 h-10 rounded-xl bg-brand-subtle text-brand flex items-center justify-center border border-brand/20">
            <Info className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-content flex items-center gap-2">
              PicoPOSSUM
              <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-full bg-brand-subtle text-brand border border-brand/20">
                v1.0.0 Stable
              </span>
            </h3>
            <p className="text-[11px] text-content-secondary">
              Offline-first Point of Sale &amp; Inventory Management System
            </p>
          </div>
        </div>

        <p className="text-content-secondary leading-relaxed">
          PicoPOSSUM is an independent, local-first desktop application designed for small shops and retail
          businesses. All sales records, inventory movements, customer databases, and configurations remain strictly
          on your local device with zero external telemetry or cloud dependencies.
        </p>

        <div className="grid grid-cols-2 gap-3 pt-2">
          <div className="p-3 rounded-lg bg-surface-secondary border border-border">
            <span className="text-[10px] text-content-muted uppercase tracking-wider block font-semibold mb-1">
              Desktop Shell &amp; Engine
            </span>
            <span className="font-semibold text-content">Tauri 2 (Rust) + React 19</span>
          </div>
          <div className="p-3 rounded-lg bg-surface-secondary border border-border">
            <span className="text-[10px] text-content-muted uppercase tracking-wider block font-semibold mb-1">
              Database Engine
            </span>
            <span className="font-semibold text-content">SQLite (WAL Mode)</span>
          </div>
        </div>
      </div>

      {/* License & Compliance Card */}
      <div className="p-6 rounded-xl bg-surface border border-border space-y-4 shadow-xs">
        <div className="flex items-center gap-2.5 pb-3 border-b border-border">
          <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-500 flex items-center justify-center border border-emerald-500/20">
            <Shield className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-content">Open-Source License</h3>
            <p className="text-[11px] text-content-secondary">
              Licensed under GNU General Public License v3.0 (GPL-3.0)
            </p>
          </div>
        </div>

        <div className="space-y-2 text-content-secondary">
          <p className="leading-relaxed">
            PicoPOSSUM is free software: you can redistribute it and/or modify it under the terms of the{' '}
            <strong className="text-content">GNU General Public License</strong> as published by the Free Software Foundation.
          </p>
          <div className="flex items-center gap-2 pt-1 text-[11px]">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
            <span>Freedom to use, inspect, modify, and distribute this software</span>
          </div>
          <div className="flex items-center gap-2 text-[11px]">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
            <span>Third-party libraries used strictly under compatible permissive licenses (MIT, Apache 2.0, ISC)</span>
          </div>
        </div>

        <div className="pt-3 border-t border-border flex items-center gap-4 text-[11px]">
          <div className="flex items-center gap-1.5 text-content-muted font-mono">
            <FileText className="w-3.5 h-3.5" />
            <span>THIRD_PARTY_NOTICES.md included in bundle</span>
          </div>
        </div>
      </div>
    </div>
  );
}
