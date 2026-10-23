import React, { useState, useEffect } from 'react';
import { 
  DollarSign, AlertTriangle, ShieldAlert, CheckCircle2, 
  Clock, RefreshCw, Calendar, FileText, ArrowUpRight 
} from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

export default function DriverPenalties() {
  const [penalties, setPenalties] = useState([]);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchPenaltiesData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [list, sum] = await Promise.all([
        driverPortalApi.getPenalties(),
        driverPortalApi.getPenaltySummary().catch(() => null)
      ]);
      setPenalties(list || []);
      setSummary(sum);
    } catch (err) {
      console.error('Failed to load penalties data:', err);
      setError('Unable to fetch penalty history. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPenaltiesData();
  }, []);

  const totalAmount = summary?.totalMonetaryAmount !== undefined 
    ? summary.totalMonetaryAmount 
    : penalties.reduce((acc, p) => acc + (Number(p.monetaryAmount) || 0), 0);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">My Penalties</h1>
          <p className="text-sm text-slate-400 mt-1">
            Progressive infraction deductions, fine records, and safety disciplinary status
          </p>
        </div>

        <button
          onClick={fetchPenaltiesData}
          className="inline-flex items-center gap-2 px-3 py-2 bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 text-slate-300 rounded-lg text-xs font-medium transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" /> Refresh
        </button>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Penalties Amount */}
        <div className="bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl p-5 relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Total Fines Assessed</span>
            <div className="p-2 rounded-xl bg-rose-500/10 text-rose-400">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-white font-mono mt-3">
            ₹{Number(totalAmount).toLocaleString('en-IN', { minimumFractionDigits: 0 })}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            Cumulative financial liability
          </p>
        </div>

        {/* Total Infractions Count */}
        <div className="bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl p-5 relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Total Infractions</span>
            <div className="p-2 rounded-xl bg-amber-500/10 text-amber-400">
              <AlertTriangle className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-white font-mono mt-3">
            {penalties.length}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            Recorded violation occurrences
          </p>
        </div>

        {/* Active Penalties */}
        <div className="bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl p-5 relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Active Records</span>
            <div className="p-2 rounded-xl bg-orange-500/10 text-orange-400">
              <ShieldAlert className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-white font-mono mt-3">
            {summary?.activeCount ?? penalties.filter(p => p.status === 'ACTIVE').length}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            Pending or active deductions
          </p>
        </div>

        {/* Waived or Cleared */}
        <div className="bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl p-5 relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-400">Waived / Resolved</span>
            <div className="p-2 rounded-xl bg-emerald-500/10 text-emerald-400">
              <CheckCircle2 className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-white font-mono mt-3">
            {summary?.waivedCount ?? penalties.filter(p => p.status === 'WAIVED' || p.status === 'REVOKED').length}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            Appealed or excused penalties
          </p>
        </div>
      </div>

      {/* Penalties List Table */}
      <div className="bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-700/60 bg-slate-900/40 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                <th className="py-3.5 px-4">Violation Details</th>
                <th className="py-3.5 px-4">Occurrence</th>
                <th className="py-3.5 px-4">Vehicle</th>
                <th className="py-3.5 px-4">Date Applied</th>
                <th className="py-3.5 px-4">Fine Amount</th>
                <th className="py-3.5 px-4">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700/40 text-sm">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    <div className="w-8 h-8 border-3 border-emerald-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Loading penalty history...
                  </td>
                </tr>
              ) : penalties.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center">
                    <CheckCircle2 className="w-10 h-10 text-emerald-500 mx-auto mb-2" />
                    <p className="text-white font-medium">Clean Record — No Penalties</p>
                    <p className="text-xs text-slate-400 mt-1">You currently have no progressive penalties or fines on record.</p>
                  </td>
                </tr>
              ) : (
                penalties.map((penalty, idx) => (
                  <tr key={penalty.id || idx} className="hover:bg-slate-700/20 transition-colors">
                    {/* Violation Type */}
                    <td className="py-3.5 px-4">
                      <div className="font-semibold text-white">
                        {penalty.alertType?.replace(/_/g, ' ') || penalty.penaltyType || 'Violation'}
                      </div>
                      <div className="text-xs text-slate-400 mt-0.5 max-w-sm truncate">
                        {penalty.reason || penalty.notes || `Progressive penalty record #${penalty.occurrenceNumber || 1}`}
                      </div>
                    </td>

                    {/* Occurrence */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-mono font-medium bg-slate-700/60 text-slate-200">
                        {penalty.occurrenceNumber ? `Occurrence #${penalty.occurrenceNumber}` : '1st Offense'}
                      </span>
                    </td>

                    {/* Vehicle */}
                    <td className="py-3.5 px-4 whitespace-nowrap font-mono text-xs text-slate-300">
                      {penalty.vehicleRegistration || 'Assigned Unit'}
                    </td>

                    {/* Date Applied */}
                    <td className="py-3.5 px-4 whitespace-nowrap text-xs text-slate-300">
                      <div className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5 text-slate-400" />
                        {penalty.dateApplied || penalty.calculationTimestamp || penalty.createdAt
                          ? new Date(penalty.dateApplied || penalty.calculationTimestamp || penalty.createdAt).toLocaleString()
                          : 'Recorded'}
                      </div>
                    </td>

                    {/* Fine Amount */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <span className="font-mono font-bold text-rose-400 text-sm">
                        ₹{Number(penalty.monetaryAmount || 0).toLocaleString('en-IN')}
                      </span>
                    </td>

                    {/* Status */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <span className={`text-[11px] px-2.5 py-0.5 rounded-full font-semibold border ${
                        penalty.status === 'WAIVED' || penalty.status === 'REVOKED'
                          ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                          : penalty.status === 'ACTIVE'
                          ? 'bg-rose-500/10 text-rose-400 border-rose-500/30'
                          : 'bg-amber-500/10 text-amber-400 border-amber-500/30'
                      }`}>
                        {penalty.status || 'ACTIVE'}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
