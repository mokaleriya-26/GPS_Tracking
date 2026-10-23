import React, { useState, useEffect } from 'react';
import { 
  AlertTriangle, ShieldAlert, CheckCircle, Clock, 
  ChevronLeft, ChevronRight, RefreshCw, Filter, DollarSign, Truck
} from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

export default function DriverAlerts() {
  const [alerts, setAlerts] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchAlerts = async (pageNumber = 0) => {
    try {
      setLoading(true);
      setError(null);
      const data = await driverPortalApi.getAlerts(pageNumber, 20);
      setAlerts(data?.content || []);
      setPage(data?.number || 0);
      setTotalPages(data?.totalPages || 0);
      setTotalElements(data?.totalElements || 0);
    } catch (err) {
      console.error('Failed to load driver alerts:', err);
      setError('Unable to fetch alerts. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAlerts(0);
  }, []);

  const getSeverityBadge = (severity) => {
    const s = severity?.toUpperCase();
    switch (s) {
      case 'CRITICAL':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
      case 'HIGH':
        return 'bg-orange-500/10 text-orange-400 border-orange-500/30';
      case 'MEDIUM':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/30';
      case 'LOW':
      default:
        return 'bg-blue-500/10 text-blue-400 border-blue-500/30';
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">My Alerts</h1>
          <p className="text-sm text-slate-400 mt-1">
            Real-time and historical safety infractions, trigger details, and associated penalty amounts
          </p>
        </div>

        <div className="flex items-center gap-3">
          <span className="text-xs text-slate-400">
            Total Logged: <strong className="text-white font-mono">{totalElements}</strong>
          </span>
          <button
            onClick={() => fetchAlerts(page)}
            className="inline-flex items-center gap-2 px-3 py-2 bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 text-slate-300 rounded-lg text-xs font-medium transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Refresh
          </button>
        </div>
      </div>

      {/* Alerts Table */}
      <div className="bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-700/60 bg-slate-900/40 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                <th className="py-3.5 px-4">Severity</th>
                <th className="py-3.5 px-4">Alert Event</th>
                <th className="py-3.5 px-4">Vehicle</th>
                <th className="py-3.5 px-4">Timestamp</th>
                <th className="py-3.5 px-4">Penalty</th>
                <th className="py-3.5 px-4">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700/40 text-sm">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    <div className="w-8 h-8 border-3 border-emerald-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Fetching your safety alerts...
                  </td>
                </tr>
              ) : alerts.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center">
                    <CheckCircle className="w-10 h-10 text-emerald-500 mx-auto mb-2" />
                    <p className="text-white font-medium">No Alerts Recorded</p>
                    <p className="text-xs text-slate-400 mt-1">Excellent driving record! No safety infractions logged.</p>
                  </td>
                </tr>
              ) : (
                alerts.map(alert => (
                  <tr key={alert.id || alert.reference} className="hover:bg-slate-700/20 transition-colors">
                    {/* Severity */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${getSeverityBadge(alert.severity)}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${
                          alert.severity === 'CRITICAL' ? 'bg-rose-400 animate-ping' : 'bg-current'
                        }`} />
                        {alert.severity || 'INFO'}
                      </span>
                    </td>

                    {/* Alert Type & Message */}
                    <td className="py-3.5 px-4">
                      <div className="font-semibold text-white">
                        {alert.alertType?.replace(/_/g, ' ') || 'Safety Alert'}
                      </div>
                      <div className="text-xs text-slate-400 mt-0.5 line-clamp-1 max-w-md">
                        {alert.message || (alert.speedKmph ? `Speed: ${alert.speedKmph} km/h (Limit: ${alert.speedLimitKmph || 'N/A'})` : 'Safety system triggered event')}
                      </div>
                    </td>

                    {/* Vehicle */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <div className="flex items-center gap-2">
                        <Truck className="w-4 h-4 text-slate-400" />
                        <div>
                          <div className="font-mono text-xs font-bold text-white">
                            {alert.vehicleCode || 'N/A'}
                          </div>
                          <div className="text-[11px] text-slate-400">
                            {alert.vehicleRegistrationNumber || ''}
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Timestamp */}
                    <td className="py-3.5 px-4 whitespace-nowrap text-xs text-slate-300">
                      <div className="flex items-center gap-1.5">
                        <Clock className="w-3.5 h-3.5 text-slate-400" />
                        {alert.occurredAt ? new Date(alert.occurredAt).toLocaleString() : 'N/A'}
                      </div>
                    </td>

                    {/* Penalty */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      {alert.penaltyAmount && Number(alert.penaltyAmount) > 0 ? (
                        <div className="inline-flex items-center gap-1 px-2.5 py-0.5 bg-rose-500/10 text-rose-400 border border-rose-500/20 rounded-full font-mono font-bold text-xs">
                          <DollarSign className="w-3 h-3" />
                          ₹{Number(alert.penaltyAmount).toFixed(0)}
                          {alert.occurrenceNumber && (
                            <span className="text-[10px] text-rose-300/70 ml-0.5 font-normal">
                              (#{alert.occurrenceNumber})
                            </span>
                          )}
                        </div>
                      ) : (
                        <span className="text-xs text-slate-500">None</span>
                      )}
                    </td>

                    {/* Status */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <span className={`text-[11px] px-2 py-0.5 rounded font-medium ${
                        alert.status === 'RESOLVED'
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                          : alert.status === 'ACTIVE'
                          ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20 animate-pulse'
                          : 'bg-slate-700 text-slate-300'
                      }`}>
                        {alert.status || 'LOGGED'}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="p-4 border-t border-slate-700/60 bg-slate-900/40 flex items-center justify-between text-xs text-slate-400">
            <div>
              Showing Page <span className="font-semibold text-white">{page + 1}</span> of{' '}
              <span className="font-semibold text-white">{totalPages}</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => fetchAlerts(Math.max(0, page - 1))}
                disabled={page === 0 || loading}
                className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed text-white"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={() => fetchAlerts(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1 || loading}
                className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed text-white"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
