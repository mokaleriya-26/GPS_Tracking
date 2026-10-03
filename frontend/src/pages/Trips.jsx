import React, { useState, useEffect } from 'react';
import { MapPin, Clock, Gauge, ArrowRight, Filter, Navigation } from 'lucide-react';
import { getTrips } from '../api/trips';

export default function Trips() {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [statusFilter, setStatusFilter] = useState('');

  useEffect(() => {
    setLoading(true);
    getTrips({ page, size: 30 })
      .then(data => {
        const content = data?.content || data || [];
        setTrips(Array.isArray(content) ? content : []);
        setTotalPages(data?.totalPages || 1);
      })
      .catch(e => setError('Failed to load trips. Ensure backend is running on port 8080.'))
      .finally(() => setLoading(false));
  }, [page]);

  const formatDuration = (seconds) => {
    if (!seconds) return 'N/A';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  };

  const formatDateTime = (dt) => {
    if (!dt) return 'N/A';
    return new Date(dt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: true });
  };

  const statusBadge = (status) => {
    const map = { ACTIVE: 'bg-success', COMPLETED: 'bg-primary', CANCELLED: 'bg-secondary' };
    return <span className={`badge ${map[status] || 'bg-secondary'}`}>{status}</span>;
  };

  const filtered = statusFilter ? trips.filter(t => t.status === statusFilter) : trips;

  return (
    <div className="container-fluid py-4">
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <Navigation size={24} className="me-2 text-primary" />
            Trips — Entry / Exit Tracking
          </h1>
          <p className="text-muted mb-0">
            Each trip represents one vehicle entry (start) and exit (end) event.
          </p>
        </div>
        <div className="d-flex align-items-center gap-2">
          <Filter size={16} className="text-muted" />
          <select className="form-select form-select-sm" style={{ width: 140 }}
            value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="">All Status</option>
            <option value="ACTIVE">Active</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>
      </div>

      {error && <div className="alert alert-warning mb-3">{error}</div>}

      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" />
        </div>
      ) : (
        <>
          <div className="card border-0 shadow-sm">
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead style={{ background: '#f8fafc', fontSize: 13 }}>
                  <tr>
                    <th>Vehicle</th>
                    <th>Driver</th>
                    <th>
                      <MapPin size={13} className="me-1" />Entry (Start)
                    </th>
                    <th>
                      <MapPin size={13} className="me-1" />Exit (End)
                    </th>
                    <th>Duration</th>
                    <th>Distance</th>
                    <th>Max Speed</th>
                    <th>Alerts</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody style={{ fontSize: 13 }}>
                  {filtered.map(trip => (
                    <tr key={trip.id}>
                      <td>
                        <div className="fw-semibold">{trip.vehicleCode}</div>
                        <div className="text-muted" style={{ fontSize: 11 }}>{trip.vehicleRegistrationNumber}</div>
                      </td>
                      <td>
                        <div>{trip.driverName || 'N/A'}</div>
                        <div className="text-muted" style={{ fontSize: 11 }}>{trip.driverCode}</div>
                      </td>
                      <td>
                        <div style={{ fontSize: 12 }}>{formatDateTime(trip.startTime)}</div>
                        {trip.startLatitude && (
                          <div className="text-muted" style={{ fontSize: 10 }}>
                            {trip.startLatitude?.toFixed?.(4)}, {trip.startLongitude?.toFixed?.(4)}
                          </div>
                        )}
                      </td>
                      <td>
                        {trip.endTime ? (
                          <>
                            <div style={{ fontSize: 12 }}>{formatDateTime(trip.endTime)}</div>
                            {trip.endLatitude && (
                              <div className="text-muted" style={{ fontSize: 10 }}>
                                {trip.endLatitude?.toFixed?.(4)}, {trip.endLongitude?.toFixed?.(4)}
                              </div>
                            )}
                          </>
                        ) : (
                          <span className="badge bg-success-subtle text-success">In Progress</span>
                        )}
                      </td>
                      <td>
                        <div className="d-flex align-items-center gap-1">
                          <Clock size={12} className="text-muted" />
                          {formatDuration(trip.durationSeconds)}
                        </div>
                        {trip.movingSeconds != null && (
                          <div className="text-muted" style={{ fontSize: 10 }}>
                            Moving: {formatDuration(trip.movingSeconds)}
                          </div>
                        )}
                      </td>
                      <td>
                        {trip.distanceKm != null
                          ? `${parseFloat(trip.distanceKm).toFixed(1)} km`
                          : 'N/A'}
                      </td>
                      <td>
                        <div className="d-flex align-items-center gap-1">
                          <Gauge size={12} className="text-muted" />
                          {trip.maxSpeedKmph != null
                            ? `${parseFloat(trip.maxSpeedKmph).toFixed(0)} km/h`
                            : 'N/A'}
                        </div>
                      </td>
                      <td>
                        {(trip.alertCount || 0) > 0
                          ? <span className="badge bg-danger">{trip.alertCount}</span>
                          : <span className="badge bg-success">0</span>}
                      </td>
                      <td>{statusBadge(trip.status)}</td>
                    </tr>
                  ))}
                  {filtered.length === 0 && (
                    <tr>
                      <td colSpan={9} className="text-center py-5 text-muted">
                        <Navigation size={40} className="mb-2 d-block mx-auto opacity-25" />
                        No trips found.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="d-flex justify-content-center gap-2 mt-3">
              <button className="btn btn-outline-secondary btn-sm"
                disabled={page === 0} onClick={() => setPage(p => p - 1)}>Prev</button>
              <span className="btn btn-sm disabled">Page {page + 1} of {totalPages}</span>
              <button className="btn btn-outline-secondary btn-sm"
                disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Next</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
