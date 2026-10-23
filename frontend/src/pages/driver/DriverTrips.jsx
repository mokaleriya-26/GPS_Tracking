import React, { useState, useEffect } from 'react';
import { 
  Navigation, Clock, MapPin, Truck, ChevronLeft, 
  ChevronRight, RefreshCw, Gauge, AlertTriangle, CheckCircle 
} from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

export default function DriverTrips() {
  const [trips, setTrips] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchTrips = async (pageNumber = 0) => {
    try {
      setLoading(true);
      setError(null);
      const data = await driverPortalApi.getTrips(pageNumber, 20);
      setTrips(data?.content || []);
      setPage(data?.number || 0);
      setTotalPages(data?.totalPages || 0);
      setTotalElements(data?.totalElements || 0);
    } catch (err) {
      console.error('Failed to load driver trips:', err);
      setError('Unable to fetch trips. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTrips(0);
  }, []);

  const formatDuration = (seconds) => {
    if (!seconds && seconds !== 0) return 'N/A';
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    if (hrs > 0) return `${hrs}h ${mins}m`;
    return `${mins}m`;
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">My Trips</h1>
          <p className="text-sm text-slate-400 mt-1">
            Complete log of completed routes, distance covered, duration, and safety incidents
          </p>
        </div>

        <div className="flex items-center gap-3">
          <span className="text-xs text-slate-400">
            Total Trips: <strong className="text-white font-mono">{totalElements}</strong>
          </span>
          <button
            onClick={() => fetchTrips(page)}
            className="inline-flex items-center gap-2 px-3 py-2 bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 text-slate-300 rounded-lg text-xs font-medium transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Refresh
          </button>
        </div>
      </div>

      {/* Trips Table */}
      <div className="bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-700/60 bg-slate-900/40 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                <th className="py-3.5 px-4">Trip Timeline</th>
                <th className="py-3.5 px-4">Vehicle</th>
                <th className="py-3.5 px-4">Distance & Duration</th>
                <th className="py-3.5 px-4">Speed / Route</th>
                <th className="py-3.5 px-4">Safety Events</th>
                <th className="py-3.5 px-4">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700/40 text-sm">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    <div className="w-8 h-8 border-3 border-emerald-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Fetching trip logs...
                  </td>
                </tr>
              ) : trips.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center">
                    <Navigation className="w-10 h-10 text-slate-600 mx-auto mb-2" />
                    <p className="text-white font-medium">No Trips Found</p>
                    <p className="text-xs text-slate-400 mt-1">No recorded trips exist under this driver profile.</p>
                  </td>
                </tr>
              ) : (
                trips.map(trip => (
                  <tr key={trip.id} className="hover:bg-slate-700/20 transition-colors">
                    {/* Timeline */}
                    <td className="py-3.5 px-4">
                      <div className="font-semibold text-white text-xs">
                        {trip.startTime ? new Date(trip.startTime).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : 'N/A'}
                      </div>
                      <div className="text-[11px] text-slate-400 flex items-center gap-1 mt-0.5">
                        <Clock className="w-3 h-3 text-slate-500" />
                        <span>to: {trip.endTime ? new Date(trip.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'In Progress'}</span>
                      </div>
                    </td>

                    {/* Vehicle */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <div className="flex items-center gap-2">
                        <Truck className="w-4 h-4 text-emerald-400" />
                        <div>
                          <div className="font-mono text-xs font-bold text-white">
                            {trip.vehicleCode || 'N/A'}
                          </div>
                          <div className="text-[11px] text-slate-400">
                            {trip.vehicleRegistrationNumber || ''}
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Distance & Duration */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <div className="font-mono font-bold text-white text-xs">
                        {trip.distanceKm ? `${Number(trip.distanceKm).toFixed(1)} km` : '0.0 km'}
                      </div>
                      <div className="text-[11px] text-slate-400">
                        {formatDuration(trip.durationSeconds)}
                      </div>
                    </td>

                    {/* Speed & Route */}
                    <td className="py-3.5 px-4">
                      <div className="text-xs text-slate-200 flex items-center gap-1">
                        <Gauge className="w-3.5 h-3.5 text-teal-400" />
                        <span>Avg: {trip.averageSpeedKmph ? `${Number(trip.averageSpeedKmph).toFixed(0)} km/h` : 'N/A'}</span>
                        {trip.maxSpeedKmph && (
                          <span className="text-slate-400 text-[11px]"> (Max: {Number(trip.maxSpeedKmph).toFixed(0)})</span>
                        )}
                      </div>
                      {trip.primaryRoad && (
                        <div className="text-[11px] text-slate-400 flex items-center gap-1 mt-0.5 truncate max-w-[200px]">
                          <MapPin className="w-3 h-3 text-slate-500 flex-shrink-0" />
                          <span className="truncate">{trip.primaryRoad}</span>
                        </div>
                      )}
                    </td>

                    {/* Safety Events */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      {(trip.alertCount > 0 || trip.overspeedEvents > 0 || trip.harshBrakingEvents > 0) ? (
                        <div className="flex items-center gap-1.5">
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-rose-500/10 text-rose-400 border border-rose-500/20">
                            <AlertTriangle className="w-3 h-3" />
                            {trip.alertCount || (trip.overspeedEvents || 0) + (trip.harshBrakingEvents || 0)} Alerts
                          </span>
                        </div>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-[11px] text-emerald-400">
                          <CheckCircle className="w-3 h-3" /> Clean
                        </span>
                      )}
                    </td>

                    {/* Status */}
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <span className={`text-[11px] px-2.5 py-0.5 rounded-full font-semibold border ${
                        trip.status === 'COMPLETED'
                          ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                          : trip.status === 'IN_PROGRESS' || trip.status === 'RUNNING'
                          ? 'bg-blue-500/10 text-blue-400 border-blue-500/30 animate-pulse'
                          : 'bg-slate-700 text-slate-300 border-slate-600'
                      }`}>
                        {trip.status || 'COMPLETED'}
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
                onClick={() => fetchTrips(Math.max(0, page - 1))}
                disabled={page === 0 || loading}
                className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 disabled:cursor-not-allowed text-white"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={() => fetchTrips(Math.min(totalPages - 1, page + 1))}
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
