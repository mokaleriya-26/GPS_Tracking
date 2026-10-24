import React, { useState, useEffect } from 'react';
import { Card, Table, Badge, Button, Spinner, Alert } from 'react-bootstrap';
import { 
  Navigation, Clock, MapPin, Truck, ChevronLeft, 
  ChevronRight, RefreshCw, Gauge, AlertTriangle, CheckCircle 
} from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

const formatDateTime = (dt) => {
  if (!dt) return '—';
  try {
    return new Date(dt).toLocaleString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    });
  } catch {
    return dt;
  }
};

const formatTimeOnly = (dt) => {
  if (!dt) return '—';
  try {
    return new Date(dt).toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    });
  } catch {
    return dt;
  }
};

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
    if (!seconds && seconds !== 0) return '—';
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    if (hrs > 0) return `${hrs}h ${mins}m`;
    return `${mins}m`;
  };

  return (
    <div style={{ fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif" }}>
      {/* Header */}
      <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
        <div>
          <h1 className="h3 mb-1 fw-bold" style={{ color: '#0f172a' }}>My Trips</h1>
          <p className="text-muted mb-0" style={{ fontSize: 13 }}>
            Complete log of driven routes, distance covered, duration, and safety incidents.
          </p>
        </div>

        <div className="d-flex align-items-center gap-3">
          <span className="text-muted" style={{ fontSize: 13 }}>
            Total Trips: <strong className="text-dark font-monospace">{totalElements}</strong>
          </span>
          <Button
            variant="outline-secondary"
            size="sm"
            onClick={() => fetchTrips(page)}
            disabled={loading}
            className="d-flex align-items-center gap-1.5"
            style={{ borderRadius: 8, fontSize: 12 }}
          >
            <RefreshCw size={13} className={loading ? 'spin' : ''} />
            <span>Refresh</span>
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="danger" className="mb-4 rounded-3 d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center gap-2">
            <AlertTriangle size={18} />
            <span>{error}</span>
          </div>
          <Button variant="outline-danger" size="sm" onClick={() => fetchTrips(page)}>
            Retry
          </Button>
        </Alert>
      )}

      {/* Trips Table Card */}
      <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
        <div className="card-body p-0">
          {loading ? (
            <div className="py-5 text-center text-muted">
              <Spinner animation="border" variant="success" size="sm" className="mb-2" />
              <div style={{ fontSize: 13 }}>Fetching trip logs…</div>
            </div>
          ) : trips.length === 0 ? (
            <div className="py-5 text-center">
              <Navigation size={36} className="text-muted mb-2 opacity-50" />
              <h5 className="fw-bold text-dark mt-1">No Trips Found</h5>
              <p className="text-muted small mb-0">No recorded trips exist under this driver profile.</p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0" style={{ fontSize: 13 }}>
                <thead className="table-light">
                  <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                    <th className="ps-4">Trip Timeline</th>
                    <th>Vehicle</th>
                    <th>Distance & Duration</th>
                    <th>Speed & Route</th>
                    <th>Safety Events</th>
                    <th className="pe-4">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {trips.map((trip) => (
                    <tr key={trip.id}>
                      {/* Timeline */}
                      <td className="ps-4">
                        <strong className="text-dark" style={{ fontSize: 13 }}>
                          {formatDateTime(trip.startTime)}
                        </strong>
                        <div className="text-muted" style={{ fontSize: 11 }}>
                          End: {trip.endTime ? formatDateTime(trip.endTime) : 'In Progress'}
                        </div>
                      </td>

                      {/* Vehicle */}
                      <td>
                        <div className="d-flex align-items-center gap-2">
                          <Truck size={16} className="text-muted" />
                          <div>
                            <strong className="text-dark font-monospace" style={{ fontSize: 12 }}>
                              {trip.vehicleCode || '—'}
                            </strong>
                            {trip.vehicleRegistrationNumber && (
                              <div className="text-muted" style={{ fontSize: 11 }}>
                                {trip.vehicleRegistrationNumber}
                              </div>
                            )}
                          </div>
                        </div>
                      </td>

                      {/* Distance & Duration */}
                      <td>
                        <div className="fw-bold text-dark">
                          {trip.distanceKm ? `${Number(trip.distanceKm).toFixed(1)} km` : '0.0 km'}
                        </div>
                        <div className="text-muted" style={{ fontSize: 11 }}>
                          {formatDuration(trip.durationSeconds)}
                        </div>
                      </td>

                      {/* Speed & Route */}
                      <td>
                        <div className="d-flex align-items-center gap-1.5 text-dark" style={{ fontSize: 12 }}>
                          <Gauge size={13} className="text-muted" />
                          <span>Avg: {trip.averageSpeedKmph ? `${Math.round(trip.averageSpeedKmph)} km/h` : '—'}</span>
                          {trip.maxSpeedKmph && (
                            <span className="text-muted" style={{ fontSize: 11 }}>
                              (Max: {Math.round(trip.maxSpeedKmph)})
                            </span>
                          )}
                        </div>
                        {trip.primaryRoad && (
                          <div className="text-muted text-truncate mt-0.5" style={{ fontSize: 11, maxWidth: 200 }}>
                            <MapPin size={11} className="me-1 text-secondary opacity-75" />
                            {trip.primaryRoad}
                          </div>
                        )}
                      </td>

                      {/* Safety Events */}
                      <td>
                        {(trip.alertCount > 0 || trip.overspeedEvents > 0 || trip.harshBrakingEvents > 0) ? (
                          <span
                            className="badge"
                            style={{
                              backgroundColor: '#fef2f2',
                              color: '#dc2626',
                              border: '1px solid #fee2e2',
                              fontSize: 11,
                              fontWeight: 700,
                            }}
                          >
                            <AlertTriangle size={11} className="me-1" />
                            {trip.alertCount || (trip.overspeedEvents || 0) + (trip.harshBrakingEvents || 0)} Incidents
                          </span>
                        ) : (
                          <span
                            className="badge"
                            style={{
                              backgroundColor: '#ecfdf5',
                              color: '#059669',
                              border: '1px solid #a7f3d0',
                              fontSize: 11,
                              fontWeight: 600,
                            }}
                          >
                            <CheckCircle size={11} className="me-1" /> Clean
                          </span>
                        )}
                      </td>

                      {/* Status */}
                      <td className="pe-4">
                        <span
                          className="badge"
                          style={{
                            backgroundColor: trip.status === 'COMPLETED' ? '#ecfdf5' : '#eff6ff',
                            color: trip.status === 'COMPLETED' ? '#059669' : '#2563eb',
                            border: `1px solid ${trip.status === 'COMPLETED' ? '#a7f3d0' : '#bfdbfe'}`,
                            fontSize: 10,
                            fontWeight: 700,
                            textTransform: 'uppercase',
                          }}
                        >
                          {trip.status || 'COMPLETED'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="card-footer bg-white border-top py-3 px-4 d-flex align-items-center justify-content-between">
            <span className="text-muted" style={{ fontSize: 12 }}>
              Showing Page <strong className="text-dark">{page + 1}</strong> of{' '}
              <strong className="text-dark">{totalPages}</strong>
            </span>
            <div className="d-flex align-items-center gap-2">
              <Button
                variant="light"
                size="sm"
                onClick={() => fetchTrips(Math.max(0, page - 1))}
                disabled={page === 0 || loading}
                className="d-flex align-items-center border"
              >
                <ChevronLeft size={16} />
              </Button>
              <Button
                variant="light"
                size="sm"
                onClick={() => fetchTrips(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1 || loading}
                className="d-flex align-items-center border"
              >
                <ChevronRight size={16} />
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
