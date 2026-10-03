import React, { useState, useEffect } from 'react';
import { Badge, Button } from 'react-bootstrap';
import {
  Bell, Mail, Smartphone, AlertCircle, AlertTriangle,
  CheckCircle, Clock, Filter, RefreshCw, Search
} from 'lucide-react';
import { getAlerts, resolveAlert } from '../api/alerts';

// Spring Boot AlertDTO field accessors
const getAlertType = (a) => a.alertType || a.alert_type || '—';
const getSeverity = (a) => a.severity || 'WARNING';
const getVehicle = (a) => a.vehicleRegistrationNumber || a.vehicleCode || '—';
const getDriver = (a) => a.driverName || '—';
const getDriverPhone = (a) => a.driverPhone || '—';
const getSpeed = (a) => a.speedKmph ?? a.speed ?? null;
const getLocation = (a) => a.roadName || a.location || '—';
const getDistance = (a) => a.tripDistanceKm ?? a.trip_distance ?? null;
const getDuration = (a) => a.tripDurationMin ?? a.trip_duration ?? null;
const getOccurredAt = (a) => a.occurredAt || a.timestamp || null;
const getStatus = (a) => a.status || 'OPEN';
const getDelivery = (a) => a.deliveryStatus || 'PENDING';
const isHistorical = (a) => getStatus(a) === 'HISTORICAL' || getDelivery(a) === 'SILENCED';
const isCritical = (a) => getSeverity(a) === 'CRITICAL' || getAlertType(a).toLowerCase().includes('overspeed') || getAlertType(a).toLowerCase().includes('disconnect');

const formatDateTime = (dt) => {
  if (!dt) return '—';
  return new Date(dt).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: true
  });
};

const RecipientBadge = ({ label, sent, error, silenced, requestId }) => {
  if (silenced) return (
    <span className="badge border text-muted" style={{ background: '#f8fafc', fontSize: 11 }}>
      — {label}
    </span>
  );
  if (sent) return (
    <span className="badge border border-success text-success"
      style={{ background: '#f0fdf4', fontSize: 11 }}
      title={requestId ? `MSG91 ID: ${requestId}` : 'Sent'}>
      ✓ {label}
    </span>
  );
  if (error) return (
    <span className="badge border border-danger text-danger"
      style={{ background: '#fef2f2', fontSize: 11 }} title={`Error: ${error}`}>
      ✗ {label}
    </span>
  );
  return (
    <span className="badge border text-secondary" style={{ background: '#f8fafc', fontSize: 11 }}>
      ○ {label}
    </span>
  );
};

export default function Alerts() {
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [resolving, setResolving] = useState(null);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [severityFilter, setSeverityFilter] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [error, setError] = useState(null);

  const loadAlerts = (p = 0) => {
    setLoading(true);
    const params = { page: p, size: 30 };
    if (statusFilter) params.status = statusFilter;
    getAlerts(params)
      .then(data => {
        const list = Array.isArray(data) ? data : (data?.content || []);
        setAlerts(list);
        setTotalPages(data?.totalPages || 1);
        setPage(p);
      })
      .catch(() => setError('Failed to load alerts. Is the backend running on port 8080?'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadAlerts(0); }, [statusFilter]);

  const handleResolve = async (alertId) => {
    setResolving(alertId);
    try {
      await resolveAlert(alertId);
      setAlerts(prev => prev.map(a => a.id === alertId ? { ...a, status: 'RESOLVED' } : a));
    } catch (e) {
      alert('Could not resolve alert: ' + (e?.response?.data?.message || e.message));
    } finally {
      setResolving(null);
    }
  };

  const filtered = alerts.filter(a => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      getAlertType(a).toLowerCase().includes(q) ||
      getDriver(a).toLowerCase().includes(q) ||
      getVehicle(a).toLowerCase().includes(q) ||
      getLocation(a).toLowerCase().includes(q)
    );
  }).filter(a => {
    if (!severityFilter) return true;
    return getSeverity(a) === severityFilter;
  });

  const statusBadge = (a) => {
    const s = getStatus(a);
    const d = getDelivery(a);
    if (d === 'SILENCED' || s === 'HISTORICAL')
      return <Badge bg="secondary" style={{ fontSize: 10 }}>Historical</Badge>;
    if (s === 'RESOLVED')
      return <Badge bg="success" style={{ fontSize: 10 }}>Resolved</Badge>;
    if (s === 'OPEN')
      return <Badge bg="danger" style={{ fontSize: 10 }}>Open</Badge>;
    return <Badge bg="secondary" style={{ fontSize: 10 }}>{s}</Badge>;
  };

  return (
    <div className="container-fluid py-4">
      {/* Header */}
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <Bell size={22} className="me-2 text-danger" />
            Fleet Alerts
          </h1>
          <p className="text-muted mb-0">
            {alerts.length} alerts loaded · Email & SMS delivery status per recipient
          </p>
        </div>
        <button className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1"
          onClick={() => loadAlerts(page)}>
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {error && <div className="alert alert-warning mb-3">{error}</div>}

      {/* Filters */}
      <div className="card border-0 shadow-sm mb-3">
        <div className="card-body py-2">
          <div className="row g-2 align-items-center">
            <div className="col-md-5">
              <div className="input-group input-group-sm">
                <span className="input-group-text bg-white border-end-0">
                  <Search size={14} className="text-muted" />
                </span>
                <input type="text" className="form-control border-start-0"
                  placeholder="Search alert type, driver, vehicle, location..."
                  value={search} onChange={e => setSearch(e.target.value)} />
              </div>
            </div>
            <div className="col-auto">
              <select className="form-select form-select-sm" style={{ width: 130 }}
                value={statusFilter} onChange={e => { setStatusFilter(e.target.value); }}>
                <option value="">All Status</option>
                <option value="OPEN">Open</option>
                <option value="HISTORICAL">Historical</option>
                <option value="RESOLVED">Resolved</option>
              </select>
            </div>
            <div className="col-auto">
              <select className="form-select form-select-sm" style={{ width: 130 }}
                value={severityFilter} onChange={e => setSeverityFilter(e.target.value)}>
                <option value="">All Severity</option>
                <option value="CRITICAL">Critical</option>
                <option value="WARNING">Warning</option>
                <option value="INFO">Info</option>
              </select>
            </div>
            <div className="col-auto ms-auto text-muted" style={{ fontSize: 12 }}>
              {filtered.length} shown
            </div>
          </div>
        </div>
      </div>

      {/* Alert Cards */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-danger" />
        </div>
      ) : (
        <>
          {filtered.map((alert, idx) => {
            const crit = isCritical(alert);
            const hist = isHistorical(alert);
            const borderColor = hist ? '#e2e8f0' : crit ? '#fca5a5' : '#fcd34d';
            const bgColor = hist ? '#f8fafc' : crit ? '#fff5f5' : '#fffbeb';

            return (
              <div key={alert.id || idx} className="mb-3 border rounded-3 p-0 overflow-hidden"
                style={{ borderColor, background: bgColor, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                <div className="row g-0">
                  {/* Severity stripe */}
                  <div style={{
                    width: 4, background: hist ? '#94a3b8' : crit ? '#dc2626' : '#f59e0b',
                    flexShrink: 0, borderRadius: '12px 0 0 12px'
                  }} />

                  {/* Main content */}
                  <div className="col p-3">
                    <div className="row g-3">
                      {/* Left: Alert details */}
                      <div className="col-md-5">
                        <div className="d-flex align-items-start gap-2 mb-2">
                          <div className="mt-1">
                            {crit && !hist
                              ? <AlertCircle size={18} color="#dc2626" />
                              : hist
                                ? <Clock size={18} color="#94a3b8" />
                                : <AlertTriangle size={18} color="#d97706" />}
                          </div>
                          <div style={{ flex: 1 }}>
                            <div className="fw-bold" style={{
                              fontSize: 14,
                              color: hist ? '#64748b' : crit ? '#dc2626' : '#d97706'
                            }}>
                              {getAlertType(alert)}
                            </div>
                            <div className="d-flex flex-wrap gap-2 mt-1">
                              {statusBadge(alert)}
                              <Badge bg={crit ? 'danger' : 'warning'} text={crit ? 'white' : 'dark'}
                                style={{ fontSize: 10 }}>
                                {getSeverity(alert)}
                              </Badge>
                            </div>
                          </div>
                        </div>

                        <div style={{ fontSize: 12, color: '#475569' }} className="row g-1">
                          <div className="col-6">
                            <span className="text-muted">Vehicle:</span>{' '}
                            <strong>{getVehicle(alert)}</strong>
                          </div>
                          <div className="col-6">
                            <span className="text-muted">Driver:</span>{' '}
                            <strong>{getDriver(alert)}</strong>
                          </div>
                          {getDriverPhone(alert) !== '—' && (
                            <div className="col-6">
                              <span className="text-muted">Phone:</span> {getDriverPhone(alert)}
                            </div>
                          )}
                          {getSpeed(alert) != null && (
                            <div className="col-6">
                              <span className="text-muted">Speed:</span>{' '}
                              <strong>{getSpeed(alert)} km/h</strong>
                            </div>
                          )}
                          <div className="col-12">
                            <span className="text-muted">Location:</span> {getLocation(alert)}
                          </div>
                          {getDistance(alert) != null && (
                            <div className="col-6">
                              <span className="text-muted">Trip:</span>{' '}
                              {parseFloat(getDistance(alert)).toFixed(1)} km
                              {getDuration(alert) != null && ` · ${getDuration(alert)} min`}
                            </div>
                          )}
                          <div className="col-12 text-muted" style={{ fontSize: 11 }}>
                            {formatDateTime(getOccurredAt(alert))}
                          </div>
                        </div>
                      </div>

                      {/* Right: Notification delivery */}
                      <div className="col-md-5 border-start ps-3">
                        <div className="text-uppercase fw-bold mb-2 d-flex justify-content-between"
                          style={{ fontSize: 10, color: '#94a3b8', letterSpacing: '0.08em' }}>
                          <span>Delivery Status</span>
                          {hist && <span className="badge bg-secondary" style={{ fontSize: 9 }}>Silenced</span>}
                        </div>

                        {/* Email */}
                        <div className="mb-2 p-2 rounded border" style={{ background: 'rgba(255,255,255,0.7)', fontSize: 12 }}>
                          <div className="d-flex align-items-center justify-content-between mb-1">
                            <div className="d-flex align-items-center gap-1 fw-semibold">
                              <Mail size={13} color="#2563eb" /> Email
                            </div>
                            {hist
                              ? <span className="badge bg-light border text-muted" style={{ fontSize: 10 }}>Silenced</span>
                              : alert.emailSent
                                ? <Badge bg="success" style={{ fontSize: 10 }}>✓ Sent</Badge>
                                : <Badge bg="secondary" style={{ fontSize: 10 }}>Pending</Badge>
                            }
                          </div>
                          <div className="d-flex flex-wrap gap-1">
                            <RecipientBadge label="Fleet" sent={alert.fleetEmailSent} error={alert.fleetEmailError} silenced={hist} />
                            <RecipientBadge label="Manager" sent={alert.managerEmailSent} error={alert.managerEmailError} silenced={hist} />
                            <RecipientBadge label="Driver" sent={alert.driverEmailSent} error={alert.driverEmailError} silenced={hist} />
                          </div>
                        </div>

                        {/* SMS */}
                        <div className="p-2 rounded border" style={{ background: 'rgba(255,255,255,0.7)', fontSize: 12 }}>
                          <div className="d-flex align-items-center justify-content-between mb-1">
                            <div className="d-flex align-items-center gap-1 fw-semibold">
                              <Smartphone size={13} color="#16a34a" /> SMS (MSG91)
                            </div>
                            {hist
                              ? <span className="badge bg-light border text-muted" style={{ fontSize: 10 }}>Silenced</span>
                              : alert.smsSent
                                ? <Badge bg="success" style={{ fontSize: 10 }}>✓ Sent</Badge>
                                : <Badge bg="secondary" style={{ fontSize: 10 }}>Pending</Badge>
                            }
                          </div>
                          <div className="d-flex flex-wrap gap-1">
                            <RecipientBadge label="Fleet" sent={alert.fleetSmsSent} error={alert.fleetSmsError} silenced={hist} requestId={alert.fleetSmsRequestId} />
                            <RecipientBadge label="Manager" sent={alert.managerSmsSent} error={alert.managerSmsError} silenced={hist} requestId={alert.managerSmsRequestId} />
                            <RecipientBadge label="Driver" sent={alert.driverSmsSent} error={alert.driverSmsError} silenced={hist} requestId={alert.driverSmsRequestId} />
                          </div>
                          {alert.fleetSmsError?.includes('418') && !hist && (
                            <div className="mt-1 p-1 rounded text-danger border border-danger"
                              style={{ background: '#fef2f2', fontSize: 10 }}>
                              MSG91 Error 418: Backend IP not whitelisted in MSG91 API Security.
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Actions */}
                      <div className="col-md-2 d-flex flex-column align-items-end justify-content-start gap-2">
                        {getStatus(alert) === 'OPEN' && (
                          <button className="btn btn-sm btn-outline-success d-flex align-items-center gap-1"
                            onClick={() => handleResolve(alert.id)}
                            disabled={resolving === alert.id}
                            style={{ fontSize: 11, borderRadius: 8 }}>
                            <CheckCircle size={12} />
                            {resolving === alert.id ? 'Resolving...' : 'Resolve'}
                          </button>
                        )}
                        {alert.resolvedBy && (
                          <div style={{ fontSize: 10, color: '#94a3b8', textAlign: 'right' }}>
                            Resolved by<br />{alert.resolvedBy}
                          </div>
                        )}
                        <div style={{ fontSize: 10, color: '#94a3b8', textAlign: 'right' }}>
                          ID #{alert.id}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}

          {filtered.length === 0 && (
            <div className="text-center py-5 text-muted">
              <Bell size={40} className="mb-2 opacity-25" />
              <p>No alerts found{search ? ` matching "${search}"` : ''}.</p>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="d-flex justify-content-center gap-2 mt-3">
              <button className="btn btn-outline-secondary btn-sm"
                disabled={page === 0} onClick={() => loadAlerts(page - 1)}>← Prev</button>
              <span className="btn btn-sm disabled">Page {page + 1} of {totalPages}</span>
              <button className="btn btn-outline-secondary btn-sm"
                disabled={page >= totalPages - 1} onClick={() => loadAlerts(page + 1)}>Next →</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
