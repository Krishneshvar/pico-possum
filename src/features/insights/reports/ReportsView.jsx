import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BarChart3, TrendingUp, IndianRupee, CreditCard, Banknote, QrCode, ShieldCheck, Flame } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { formatPaise, formatDateTime } from '../../../lib/formatters';

export default function ReportsView() {
  const [activeTab, setActiveTab] = useState('summary'); // 'summary' | 'audit'

  const { data: summary, isLoading: loadingSummary } = useQuery({
    queryKey: ['sales_report_summary'],
    queryFn: () => tauriApi.getSalesReportSummary(),
  });

  const { data: topProducts = [], isLoading: loadingTop } = useQuery({
    queryKey: ['top_products'],
    queryFn: () => tauriApi.getTopSellingProducts(10),
  });

  const { data: auditLogs = [], isLoading: loadingAudit } = useQuery({
    queryKey: ['audit_logs'],
    queryFn: () => tauriApi.getAuditLogs(100),
    enabled: activeTab === 'audit',
  });

  const totalRev = summary?.total_revenue_paise || 0;
  const cashRev = summary?.cash_revenue_paise || 0;
  const cardRev = summary?.card_revenue_paise || 0;
  const upiRev = summary?.upi_revenue_paise || 0;
  const otherRev = summary?.other_revenue_paise || 0;
  const totalCount = summary?.total_sales_count || 0;
  const avgBill = totalCount > 0 ? Math.round(totalRev / totalCount) : 0;

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 overflow-hidden">
      {/* Header */}
      <div className="h-16 px-6 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center border border-emerald-500/20">
            <BarChart3 className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-100">Analytics & Reports</h2>
            <p className="text-xs text-slate-400">Financial summaries, product performance, and system audit trail</p>
          </div>
        </div>

        {/* Tab Toggle */}
        <div className="flex bg-slate-900 border border-slate-800 rounded-xl p-1 text-xs">
          <button
            onClick={() => setActiveTab('summary')}
            className={`px-3 py-1.5 rounded-lg font-semibold transition-all ${
              activeTab === 'summary' ? 'bg-emerald-500 text-slate-950 shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            Sales Overview
          </button>
          <button
            onClick={() => setActiveTab('audit')}
            className={`px-3 py-1.5 rounded-lg font-semibold transition-all ${
              activeTab === 'audit' ? 'bg-emerald-500 text-slate-950 shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            System Audit Trail
          </button>
        </div>
      </div>

      {activeTab === 'summary' ? (
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Top Metric Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="glass-card p-5 rounded-2xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Total Revenue</span>
                <span className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-400">
                  <TrendingUp className="w-4 h-4" />
                </span>
              </div>
              <div className="text-2xl font-black text-slate-100 font-mono">
                {formatPaise(totalRev)}
              </div>
              <p className="text-[11px] text-emerald-400/90 font-medium">All completed transactions</p>
            </div>

            <div className="glass-card p-5 rounded-2xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Total Bills Issued</span>
                <span className="p-1.5 rounded-lg bg-teal-500/10 text-teal-400">
                  <BarChart3 className="w-4 h-4" />
                </span>
              </div>
              <div className="text-2xl font-black text-slate-100 font-mono">
                {totalCount}
              </div>
              <p className="text-[11px] text-slate-400 font-medium">Customer checkouts</p>
            </div>

            <div className="glass-card p-5 rounded-2xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Average Bill Value</span>
                <span className="p-1.5 rounded-lg bg-cyan-500/10 text-cyan-400">
                  <IndianRupee className="w-4 h-4" />
                </span>
              </div>
              <div className="text-2xl font-black text-slate-100 font-mono">
                {formatPaise(avgBill)}
              </div>
              <p className="text-[11px] text-slate-400 font-medium">Per checkout average</p>
            </div>

            <div className="glass-card p-5 rounded-2xl border border-slate-800 space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Total Discounts Given</span>
                <span className="p-1.5 rounded-lg bg-amber-500/10 text-amber-400">
                  <TrendingUp className="w-4 h-4" />
                </span>
              </div>
              <div className="text-2xl font-black text-amber-400 font-mono">
                {formatPaise(summary?.total_discount_paise || 0)}
              </div>
              <p className="text-[11px] text-slate-400 font-medium">Customer savings</p>
            </div>
          </div>

          {/* Payment Method Breakdown Grid */}
          <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4">
            <h3 className="text-sm font-bold text-slate-100">Revenue by Payment Method</h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="bg-slate-950/60 p-4 rounded-xl border border-slate-800/80 space-y-1">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
                  <Banknote className="w-4 h-4 text-emerald-400" /> Cash
                </div>
                <div className="text-xl font-bold font-mono text-slate-100">
                  {formatPaise(cashRev)}
                </div>
                <div className="text-[11px] text-slate-500">
                  {totalRev > 0 ? ((cashRev / totalRev) * 100).toFixed(1) : 0}% of revenue
                </div>
              </div>

              <div className="bg-slate-950/60 p-4 rounded-xl border border-slate-800/80 space-y-1">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
                  <QrCode className="w-4 h-4 text-cyan-400" /> UPI / QR
                </div>
                <div className="text-xl font-bold font-mono text-slate-100">
                  {formatPaise(upiRev)}
                </div>
                <div className="text-[11px] text-slate-500">
                  {totalRev > 0 ? ((upiRev / totalRev) * 100).toFixed(1) : 0}% of revenue
                </div>
              </div>

              <div className="bg-slate-950/60 p-4 rounded-xl border border-slate-800/80 space-y-1">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
                  <CreditCard className="w-4 h-4 text-blue-400" /> Card
                </div>
                <div className="text-xl font-bold font-mono text-slate-100">
                  {formatPaise(cardRev)}
                </div>
                <div className="text-[11px] text-slate-500">
                  {totalRev > 0 ? ((cardRev / totalRev) * 100).toFixed(1) : 0}% of revenue
                </div>
              </div>
            </div>
          </div>

          {/* Top Selling Products */}
          <div className="glass-card p-6 rounded-2xl border border-slate-800 space-y-4">
            <div className="flex items-center gap-2">
              <Flame className="w-4 h-4 text-amber-400" />
              <h3 className="text-sm font-bold text-slate-100">Top Selling Products</h3>
            </div>

            <div className="border border-slate-800 rounded-xl overflow-hidden">
              <table className="w-full text-left border-collapse text-xs">
                <thead className="bg-slate-900 text-slate-400 font-semibold uppercase text-[10px] tracking-wider border-b border-slate-800">
                  <tr>
                    <th className="py-2.5 px-4 w-12 text-center">#</th>
                    <th className="py-2.5 px-4">Product Name</th>
                    <th className="py-2.5 px-4 text-center">Units Sold</th>
                    <th className="py-2.5 px-4 text-right">Total Revenue</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {topProducts.length === 0 ? (
                    <tr>
                      <td colSpan="4" className="py-8 text-center text-slate-500">
                        No sales data available.
                      </td>
                    </tr>
                  ) : (
                    topProducts.map((p, idx) => (
                      <tr key={p.product_id} className="hover:bg-slate-900/40 transition-colors">
                        <td className="py-2.5 px-4 text-center text-slate-500 font-mono font-bold">
                          {idx + 1}
                        </td>
                        <td className="py-2.5 px-4 font-semibold text-slate-200">
                          {p.product_name}
                        </td>
                        <td className="py-2.5 px-4 text-center font-mono font-bold text-emerald-400">
                          {p.total_quantity}
                        </td>
                        <td className="py-2.5 px-4 text-right font-mono font-bold text-slate-100">
                          {formatPaise(p.total_revenue_paise)}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ) : (
        /* Audit Trail View */
        <div className="flex-1 overflow-y-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead className="bg-slate-900 text-slate-400 font-semibold uppercase text-[10px] tracking-wider sticky top-0 border-b border-slate-800 z-10">
              <tr>
                <th className="py-3 px-4 w-44">Timestamp</th>
                <th className="py-3 px-4 w-36">Event</th>
                <th className="py-3 px-4 w-28">Entity</th>
                <th className="py-3 px-4">Description</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono text-[11px]">
              {auditLogs.map((log) => (
                <tr key={log.id} className="hover:bg-slate-900/40 transition-colors">
                  <td className="py-2.5 px-4 text-slate-400">{formatDateTime(log.created_at)}</td>
                  <td className="py-2.5 px-4 font-bold text-emerald-400">{log.event_type}</td>
                  <td className="py-2.5 px-4 text-slate-300">{log.entity_type}</td>
                  <td className="py-2.5 px-4 text-slate-300 font-sans">{log.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
