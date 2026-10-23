import React, { useState, useEffect } from 'react';
import { 
  FileText, Printer, Share2, Download, Calendar, 
  ShieldCheck, AlertTriangle, DollarSign, Clock, RefreshCw, CheckCircle2, Copy
} from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

export default function DriverReports() {
  const [periodType, setPeriodType] = useState('MONTHLY'); // MONTHLY, WEEKLY, DAILY, CUSTOM
  const [month, setMonth] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [generating, setGenerating] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const [shareToast, setShareToast] = useState(null);

  const handleGenerate = async (e) => {
    if (e) e.preventDefault();
    try {
      setGenerating(true);
      setError(null);

      let payload = {
        reportType: periodType,
        period: periodType.toLowerCase()
      };

      if (periodType === 'MONTHLY') {
        const [yearStr, monthStr] = month.split('-');
        const y = parseInt(yearStr, 10);
        const m = parseInt(monthStr, 10);
        const start = new Date(Date.UTC(y, m - 1, 1));
        const end = new Date(Date.UTC(y, m, 0, 23, 59, 59));
        payload.startDate = start.toISOString();
        payload.endDate = end.toISOString();
      } else if (periodType === 'CUSTOM') {
        if (!dateFrom || !dateTo) {
          setError('Please select both Start Date and End Date for a custom report.');
          setGenerating(false);
          return;
        }
        payload.startDate = new Date(dateFrom).toISOString();
        payload.endDate = new Date(dateTo).toISOString();
      } else {
        // Default to last 30 days
        const end = new Date();
        const start = new Date();
        start.setDate(end.getDate() - 30);
        payload.startDate = start.toISOString();
        payload.endDate = end.toISOString();
      }

      const res = await driverPortalApi.generateReport(payload);
      setReport(res);
    } catch (err) {
      console.error('Failed to generate driver report:', err);
      setError(err?.response?.data?.message || 'Failed to generate report. Please try again.');
    } finally {
      setGenerating(false);
    }
  };

  // Auto-generate initial report
  useEffect(() => {
    handleGenerate();
  }, []);

  const handlePrint = () => {
    window.print();
  };

  const handleShare = async () => {
    if (!report) return;

    const reportTitle = `Driver Safety & Activity Report - ${report.driverName || 'Driver'}`;
    const reportText = `TrackFleet Personal Report:
Driver: ${report.driverName || 'Driver'} (${report.driverCode || 'N/A'})
Safety Score: ${report.driverSafetyScore ?? '100'}/100
Total Trips: ${report.totalTrips ?? 0}
Distance: ${report.totalDistanceKm ?? 0} km
Total Penalties: ₹${report.totalPenaltyAmount ?? 0}
Alerts Logged: ${report.totalAlerts ?? 0}
Period: ${report.periodStart ? new Date(report.periodStart).toLocaleDateString() : ''} - ${report.periodEnd ? new Date(report.periodEnd).toLocaleDateString() : ''}`;

    if (navigator.share) {
      try {
        await navigator.share({
          title: reportTitle,
          text: reportText,
          url: window.location.href,
        });
        setShareToast({ type: 'success', message: 'Report shared successfully!' });
      } catch (err) {
        if (err.name !== 'AbortError') {
          copyToClipboard(reportText);
        }
      }
    } else {
      copyToClipboard(reportText);
    }
  };

  const copyToClipboard = (text) => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        setShareToast({ type: 'success', message: 'Report summary copied to clipboard!' });
        setTimeout(() => setShareToast(null), 4000);
      }).catch(() => {
        setShareToast({ type: 'error', message: 'Failed to copy report to clipboard.' });
      });
    }
  };

  return (
    <div className="space-y-6">
      {/* Toast Notification for Share */}
      {shareToast && (
        <div className={`p-4 rounded-xl border flex items-center justify-between text-sm transition-all ${
          shareToast.type === 'success' 
            ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300' 
            : 'bg-rose-500/10 border-rose-500/30 text-rose-300'
        }`}>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4" />
            <span>{shareToast.message}</span>
          </div>
          <button onClick={() => setShareToast(null)} className="text-xs underline">Dismiss</button>
        </div>
      )}

      {/* Header (Hidden in Print) */}
      <div className="no-print flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">My Reports</h1>
          <p className="text-sm text-slate-400 mt-1">
            Generate and export personal driver performance, safety compliance, and penalty reports
          </p>
        </div>

        {report && (
          <div className="flex items-center gap-3">
            <button
              onClick={handleShare}
              className="inline-flex items-center gap-2 px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-xl text-xs font-semibold shadow-sm transition-colors"
              title="Share report summary"
            >
              <Share2 className="w-4 h-4 text-emerald-400" /> Share
            </button>
            <button
              onClick={handlePrint}
              className="inline-flex items-center gap-2 px-3.5 py-2 bg-emerald-600 hover:bg-emerald-500 text-slate-950 font-bold rounded-xl text-xs shadow-sm transition-colors"
              title="Print report on A4 paper"
            >
              <Printer className="w-4 h-4" /> Print Report
            </button>
          </div>
        )}
      </div>

      {/* Configuration Controls (Hidden in Print) */}
      <div className="no-print bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl p-6">
        <form onSubmit={handleGenerate} className="flex flex-wrap items-end gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Period Type</label>
            <select
              value={periodType}
              onChange={(e) => setPeriodType(e.target.value)}
              className="bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              <option value="MONTHLY">Monthly</option>
              <option value="CUSTOM">Custom Range</option>
            </select>
          </div>

          {periodType === 'MONTHLY' ? (
            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1.5">Select Month</label>
              <input
                type="month"
                value={month}
                onChange={(e) => setMonth(e.target.value)}
                className="bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
          ) : (
            <>
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">Start Date</label>
                <input
                  type="date"
                  value={dateFrom}
                  onChange={(e) => setDateFrom(e.target.value)}
                  className="bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">End Date</label>
                <input
                  type="date"
                  value={dateTo}
                  onChange={(e) => setDateTo(e.target.value)}
                  className="bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </>
          )}

          <button
            type="submit"
            disabled={generating}
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold rounded-xl text-sm transition-all disabled:opacity-50"
          >
            {generating ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin" /> Generating...
              </>
            ) : (
              <>
                <FileText className="w-4 h-4" /> Generate Report
              </>
            )}
          </button>
        </form>

        {error && (
          <p className="mt-4 text-xs text-rose-400 font-medium">{error}</p>
        )}
      </div>

      {/* Generated Report View (A4 Print Ready) */}
      {report && (
        <div className="report-print-container bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl p-8 space-y-8">
          {/* Print Header */}
          <div className="border-b border-slate-700/60 pb-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <span className="px-2.5 py-0.5 rounded text-[11px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                  CONFIDENTIAL DRIVER STATEMENT
                </span>
                <span className="text-xs text-slate-400 font-mono">
                  Ref: TRK-{report.id || Math.floor(Math.random() * 90000 + 10000)}
                </span>
              </div>
              <h2 className="text-2xl font-bold text-white mt-2">
                Driver Performance & Compliance Report
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                Period: {report.periodStart ? new Date(report.periodStart).toLocaleDateString() : 'Start'} to{' '}
                {report.periodEnd ? new Date(report.periodEnd).toLocaleDateString() : 'End'} | Generated on{' '}
                {new Date().toLocaleString()}
              </p>
            </div>

            <div className="text-right">
              <div className="text-sm font-bold text-white">{report.driverName || 'Driver'}</div>
              <div className="text-xs text-slate-400 font-mono">ID: {report.driverCode || 'N/A'}</div>
              <div className="text-xs text-slate-400">Assigned: {report.vehicleCode || 'Fleet'}</div>
            </div>
          </div>

          {/* Metric Summary Cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-700/50">
              <div className="text-xs text-slate-400">Safety Index</div>
              <div className="text-2xl font-bold text-white font-mono mt-1">
                {report.driverSafetyScore !== undefined && report.driverSafetyScore !== null
                  ? `${Number(report.driverSafetyScore).toFixed(1)}`
                  : '100'} / 100
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-700/50">
              <div className="text-xs text-slate-400">Total Trips</div>
              <div className="text-2xl font-bold text-white font-mono mt-1">
                {report.totalTrips || 0}
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-700/50">
              <div className="text-xs text-slate-400">Distance Driven</div>
              <div className="text-2xl font-bold text-white font-mono mt-1">
                {report.totalDistanceKm ? `${Number(report.totalDistanceKm).toFixed(1)} km` : '0.0 km'}
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-700/50">
              <div className="text-xs text-slate-400">Penalties Assessed</div>
              <div className="text-2xl font-bold text-rose-400 font-mono mt-1">
                ₹{Number(report.totalPenaltyAmount || 0).toLocaleString('en-IN')}
              </div>
            </div>
          </div>

          {/* Alert & Violation Breakdown Table */}
          <div>
            <h3 className="text-sm font-semibold text-white mb-3">Infraction & Safety Event Breakdown</h3>
            <div className="overflow-x-auto rounded-xl border border-slate-700/50">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-900/60 text-slate-400 border-b border-slate-700/50">
                    <th className="py-2.5 px-3">Event Type</th>
                    <th className="py-2.5 px-3">Occurrences</th>
                    <th className="py-2.5 px-3">Severity Level</th>
                    <th className="py-2.5 px-3">Penalty Incurred</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-700/30">
                  {report.alertBreakdown && report.alertBreakdown.length > 0 ? (
                    report.alertBreakdown.map((item, i) => (
                      <tr key={i} className="hover:bg-slate-700/20">
                        <td className="py-2.5 px-3 font-medium text-white">{item.alertType || item.type}</td>
                        <td className="py-2.5 px-3 text-slate-300 font-mono">{item.count}</td>
                        <td className="py-2.5 px-3 text-slate-300">{item.severity || 'STANDARD'}</td>
                        <td className="py-2.5 px-3 text-rose-400 font-mono">
                          ₹{Number(item.penaltyAmount || 0).toFixed(0)}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={4} className="py-6 text-center text-slate-400">
                        No infractions or safety violations reported during this time frame.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Footer for Print */}
          <div className="pt-6 border-t border-slate-700/60 flex items-center justify-between text-[11px] text-slate-400">
            <div>TrackFleet Autonomous Telematics System — Official Record</div>
            <div>Confidential • Personal Driver Access Only</div>
          </div>
        </div>
      )}
    </div>
  );
}
