import React, { useState, useEffect } from 'react';
import { Card, Table, Badge, Button, Spinner, Alert, Row, Col } from 'react-bootstrap';
import { 
  Bell, AlertTriangle, ShieldAlert, CheckCircle, Clock, 
  ChevronLeft, ChevronRight, RefreshCw, Truck, IndianRupee, MapPin
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

  const renderSeverityBadge = (severity) => {
    const s = (severity || 'LOW').toUpperCase();
    let bg = '#eff6ff';
    let text = '#2563eb';
    let border = '#dbeafe';

    if (s.includes('CRITICAL')) {
      bg = '#fef2f2';
      text = '#dc2626';
      border = '#fee2e2';
    } else if (s.includes('HIGH')) {
      bg = '#fff7ed';
      text = '#ea580c';
      border = '#ffedd5';
    } else if (s.includes('MEDIUM') || s.includes('WARN')) {
      bg = '#fffbeb';
      text = '#d97706';
      border = '#fef3c7';
    }

    return (
      <span
        style={{
          backgroundColor: bg,
          color: text,
          border: `1px solid ${border}`,
          borderRadius: 6,
          padding: '2px 8px',
          fontSize: 11,
          fontWeight: 700,
          display: 'inline-block',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}
      >
        {s}
      </span>
    );
  };

  return (
    <div style={{ fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif" }}>
      {/* Page Header */}
      <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
        <div>
          <h1 className="h3 mb-1 fw-bold" style={{ color: '#0f172a' }}>My Alerts</h1>
          <p className="text-muted mb-0" style={{ fontSize: 13 }}>
            Review real-time safety infractions, trigger details, and associated penalty amounts.
          </p>
        </div>

        <div className="d-flex align-items-center gap-3">
          <span className="text-muted" style={{ fontSize: 13 }}>
            Total Logged: <strong className="text-dark font-monospace">{totalElements}</strong>
          </span>
          <Button
            variant="outline-secondary"
            size="sm"
            onClick={() => fetchAlerts(page)}
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
          <Button variant="outline-danger" size="sm" onClick={() => fetchAlerts(page)}>
            Retry
          </Button>
        </Alert>
      )}

      {/* Main Alerts Table Card */}
      <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
        <div className="card-body p-0">
          {loading ? (
            <div className="py-5 text-center text-muted">
              <Spinner animation="border" variant="success" size="sm" className="mb-2" />
              <div style={{ fontSize: 13 }}>Fetching your safety alerts…</div>
            </div>
          ) : alerts.length === 0 ? (
            <div className="py-5 text-center">
              <CheckCircle size={36} className="text-success mb-2 opacity-75" />
              <h5 className="fw-bold text-dark mt-1">No Alerts Recorded</h5>
              <p className="text-muted small mb-0">Excellent driving record! No safety infractions logged for your account.</p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0" style={{ fontSize: 13 }}>
                <thead className="table-light">
                  <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                    <th className="ps-4">Severity</th>
                    <th>Alert Event</th>
                    <th>Vehicle</th>
                    <th>Timestamp</th>
                    <th>Penalty</th>
                    <th className="pe-4">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {alerts.map((alert) => (
                    <tr key={alert.id || alert.reference}>
                      {/* Severity */}
                      <td className="ps-4">
                        {renderSeverityBadge(alert.severity)}
                      </td>

                      {/* Alert Type & Message */}
                      <td>
                        <strong className="text-dark">
                          {alert.alertType?.replace(/_/g, ' ') || 'Safety Alert'}
                        </strong>
                        <div className="text-muted" style={{ fontSize: 11, maxWidth: 360 }}>
                          {alert.message || (alert.speedKmph ? `Speed: ${alert.speedKmph} km/h (Limit: ${alert.speedLimitKmph || '80'} km/h)` : 'Safety telemetry trigger')}
                        </div>
                      </td>

                      {/* Vehicle */}
                      <td>
                        <div className="d-flex align-items-center gap-2">
                          <Truck size={16} className="text-muted" />
                          <div>
                            <strong className="text-dark font-monospace" style={{ fontSize: 12 }}>
                              {alert.vehicleCode || '—'}
                            </strong>
                            {alert.vehicleRegistrationNumber && (
                              <div className="text-muted" style={{ fontSize: 11 }}>
                                {alert.vehicleRegistrationNumber}
                              </div>
                            )}
                          </div>
                        </div>
                      </td>

                      {/* Timestamp */}
                      <td className="text-muted" style={{ fontSize: 12 }}>
                        <div className="d-flex align-items-center gap-1.5">
                          <Clock size={13} className="text-muted opacity-75" />
                          <span>{formatDateTime(alert.occurredAt)}</span>
                        </div>
                      </td>

                      {/* Penalty */}
                      <td>
                        {alert.penaltyAmount && Number(alert.penaltyAmount) > 0 ? (
                          <span
                            className="badge"
                            style={{
                              backgroundColor: '#fef2f2',
                              color: '#dc2626',
                              border: '1px solid #fee2e2',
                              fontFamily: 'monospace',
                              fontSize: 12,
                              fontWeight: 700,
                              padding: '4px 8px',
                            }}
                          >
                            ₹{Number(alert.penaltyAmount).toFixed(0)}
                            {alert.occurrenceNumber && (
                              <span style={{ fontSize: 10, fontWeight: 400, marginLeft: 4, opacity: 0.8 }}>
                                (#{alert.occurrenceNumber})
                              </span>
                            )}
                          </span>
                        ) : (
                          <span className="text-muted small">None</span>
                        )}
                      </td>

                      {/* Status */}
                      <td className="pe-4">
                        <span
                          className="badge"
                          style={{
                            backgroundColor: alert.status === 'RESOLVED' ? '#ecfdf5' : '#f1f5f9',
                            color: alert.status === 'RESOLVED' ? '#059669' : '#475569',
                            border: `1px solid ${alert.status === 'RESOLVED' ? '#a7f3d0' : '#e2e8f0'}`,
                            fontSize: 10,
                            fontWeight: 700,
                            textTransform: 'uppercase',
                          }}
                        >
                          {alert.status || 'LOGGED'}
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
                onClick={() => fetchAlerts(Math.max(0, page - 1))}
                disabled={page === 0 || loading}
                className="d-flex align-items-center border"
              >
                <ChevronLeft size={16} />
              </Button>
              <Button
                variant="light"
                size="sm"
                onClick={() => fetchAlerts(Math.min(totalPages - 1, page + 1))}
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
