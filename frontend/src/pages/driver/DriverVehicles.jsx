import React, { useState, useEffect } from 'react';
import { Truck, CheckCircle2, History, Gauge, MapPin, RefreshCw, AlertCircle } from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

export default function DriverVehicles() {
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filter, setFilter] = useState('ALL'); // ALL, CURRENT, HISTORICAL

  const fetchVehicles = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await driverPortalApi.getVehicles();
      setVehicles(data);
    } catch (err) {
      console.error('Failed to load driver vehicles:', err);
      setError('Unable to fetch vehicle history. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchVehicles();
  }, []);

  const filteredVehicles = vehicles.filter(v => {
    if (filter === 'CURRENT') return v.assignmentStatus === 'CURRENT';
    if (filter === 'HISTORICAL') return v.assignmentStatus === 'HISTORICAL';
    return true;
  });

  const currentCount = vehicles.filter(v => v.assignmentStatus === 'CURRENT').length;
  const historicalCount = vehicles.filter(v => v.assignmentStatus === 'HISTORICAL').length;

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh]">
        <div className="w-12 h-12 border-4 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
        <p className="mt-4 text-slate-400 font-medium">Loading vehicle history...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">My Vehicles</h1>
          <p className="text-sm text-slate-400 mt-1">
            Current primary vehicle assignments and historical fleet units operated across your trips
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Filter Pills */}
          <div className="flex items-center bg-slate-800/80 p-1 rounded-xl border border-slate-700/60 text-xs">
            <button
              onClick={() => setFilter('ALL')}
              className={`px-3 py-1.5 rounded-lg font-medium transition-all ${
                filter === 'ALL'
                  ? 'bg-emerald-500 text-slate-950 font-semibold shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              All ({vehicles.length})
            </button>
            <button
              onClick={() => setFilter('CURRENT')}
              className={`px-3 py-1.5 rounded-lg font-medium transition-all ${
                filter === 'CURRENT'
                  ? 'bg-emerald-500 text-slate-950 font-semibold shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              Active ({currentCount})
            </button>
            <button
              onClick={() => setFilter('HISTORICAL')}
              className={`px-3 py-1.5 rounded-lg font-medium transition-all ${
                filter === 'HISTORICAL'
                  ? 'bg-emerald-500 text-slate-950 font-semibold shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              Historical ({historicalCount})
            </button>
          </div>

          <button
            onClick={fetchVehicles}
            className="p-2 bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 text-slate-300 rounded-xl transition-colors"
            title="Refresh list"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-rose-500/10 border border-rose-500/20 rounded-xl flex items-center justify-between text-rose-300 text-sm">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <button onClick={fetchVehicles} className="underline text-xs hover:text-rose-200">Retry</button>
        </div>
      )}

      {/* Vehicles Grid */}
      {filteredVehicles.length === 0 ? (
        <div className="bg-slate-800/40 border border-slate-700/60 rounded-2xl p-12 text-center">
          <Truck className="w-12 h-12 text-slate-600 mx-auto mb-3" />
          <h3 className="text-base font-semibold text-white">No Vehicles Found</h3>
          <p className="text-slate-400 text-sm mt-1 max-w-md mx-auto">
            {filter === 'ALL'
              ? 'No vehicle records or past trip logs are associated with this driver identity.'
              : `No ${filter.toLowerCase()} vehicles found matching your current filter.`}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredVehicles.map(veh => {
            const isCurrent = veh.assignmentStatus === 'CURRENT';
            return (
              <div
                key={veh.id}
                className={`bg-slate-800/60 backdrop-blur border rounded-2xl p-6 relative overflow-hidden transition-all hover:border-slate-600 ${
                  isCurrent 
                    ? 'border-emerald-500/40 shadow-lg shadow-emerald-500/5' 
                    : 'border-slate-700/60 opacity-90'
                }`}
              >
                {/* Glow for current */}
                {isCurrent && (
                  <div className="absolute top-0 right-0 w-24 h-24 bg-emerald-500/10 rounded-full blur-2xl pointer-events-none"></div>
                )}

                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <div className={`p-3 rounded-xl ${
                      isCurrent 
                        ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' 
                        : 'bg-slate-700/40 text-slate-400 border border-slate-700'
                    }`}>
                      <Truck className="w-6 h-6" />
                    </div>
                    <div>
                      <h3 className="text-lg font-bold text-white tracking-tight">{veh.code}</h3>
                      <p className="text-xs text-slate-400">{veh.model || 'Commercial Fleet Unit'}</p>
                    </div>
                  </div>

                  <span className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2.5 py-1 rounded-full border ${
                    isCurrent 
                      ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30' 
                      : 'bg-slate-700/40 text-slate-400 border-slate-600'
                  }`}>
                    {isCurrent ? (
                      <>
                        <CheckCircle2 className="w-3 h-3 text-emerald-400" />
                        ASSIGNED
                      </>
                    ) : (
                      <>
                        <History className="w-3 h-3 text-slate-400" />
                        HISTORICAL
                      </>
                    )}
                  </span>
                </div>

                <div className="mt-6 space-y-2.5 text-xs">
                  <div className="flex items-center justify-between p-2 rounded-lg bg-slate-900/40 border border-slate-700/40">
                    <span className="text-slate-400">Registration Plate</span>
                    <span className="font-mono font-bold text-white tracking-wider">{veh.registrationNumber}</span>
                  </div>

                  <div className="flex items-center justify-between p-2 rounded-lg bg-slate-900/40 border border-slate-700/40">
                    <span className="text-slate-400">Status</span>
                    <span className={`font-semibold ${
                      veh.status === 'ACTIVE' ? 'text-emerald-400' : 'text-slate-300'
                    }`}>
                      {veh.status || 'ACTIVE'}
                    </span>
                  </div>

                  <div className="flex items-center justify-between p-2 rounded-lg bg-slate-900/40 border border-slate-700/40">
                    <span className="text-slate-400">Trips Driven</span>
                    <span className="font-bold text-white">
                      {veh.tripCountWithDriver || 0} Trips
                    </span>
                  </div>

                  {veh.lastKnownLocation && (
                    <div className="flex items-center justify-between p-2 rounded-lg bg-slate-900/40 border border-slate-700/40">
                      <span className="text-slate-400 flex items-center gap-1">
                        <MapPin className="w-3 h-3 text-rose-400" /> Location
                      </span>
                      <span className="text-slate-300 truncate max-w-[140px]">{veh.lastKnownLocation}</span>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
