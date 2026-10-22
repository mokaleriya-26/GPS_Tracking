import React, { useState, useEffect } from 'react';
import { Badge, Button, Modal, Form } from 'react-bootstrap';
import {
  Bell, Mail, Smartphone, AlertCircle, AlertTriangle,
  CheckCircle, Clock, Filter, RefreshCw, Search, Send
} from 'lucide-react';
import { getAlerts, resolveAlert, createAlert } from '../api/alerts';
import { getVehicles } from '../api/vehicles';
import { getDrivers } from '../api/drivers';

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

  // Demo Trigger Modal state
  const [showDemoModal, setShowDemoModal] = useState(false);
  const [demoLoading, setDemoLoading] = useState(false);
  const [demoNotification, setDemoNotification] = useState(null);
  const [vehiclesList, setVehiclesList] = useState([]);
  const [driversList, setDriversList] = useState([]);
  const [demoForm, setDemoForm] = useState({
    alertType: 'Over-Speeding Alert',
    severity: 'CRITICAL',
    vehicleId: '1',
    driverId: '20',
    speedKmph: 85.5,
    speedLimitKmph: 60.0,
    roadName: 'Western Express Highway, Mumbai',
    message: 'Live demonstration alert: Vehicle exceeded speed limit by 25.5 km/h'
  });

  useEffect(() => {
    getVehicles().then(data => {
      const v = Array.isArray(data) ? data : (data?.content || []);
      setVehiclesList(v);
    }).catch(() => {});
    getDrivers().then(data => {
      const d = Array.isArray(data) ? data : (data?.content || []);
      setDriversList(d);
    }).catch(() => {});
  }, []);

  const handleTriggerDemo = async (e) => {
    e.preventDefault();
    setDemoLoading(true);
    setDemoNotification(null);
    try {
      const res = await createAlert({
        reference: 'DEMO_ALERT_' + Date.now(),
        alertType: demoForm.alertType,
        severity: demoForm.severity,
        vehicleId: Number(demoForm.vehicleId),
        driverId: Number(demoForm.driverId),
        speedKmph: Number(demoForm.speedKmph),
        speedLimitKmph: Number(demoForm.speedLimitKmph),
        roadName: demoForm.roadName,
        message: demoForm.message
      });

      setShowDemoModal(false);
      setDemoNotification('Live alert created! Real emails are being delivered to Fleet (riyamokale@gmail.com), Manager (rhea260906@gmail.com), and Driver (merinjoys28@gmail.com).');
      loadAlerts(0);
      
      // Re-fetch after 5 seconds to show updated "Sent" status badges
      setTimeout(() => loadAlerts(0), 5000);
      setTimeout(() => setDemoNotification(null), 15000);
    } catch (err) {
      alert('Failed to trigger alert: ' + (err?.response?.data?.message || err.message));
    } finally {
      setDemoLoading(false);
    }
  };

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
            {alerts.length} alerts loaded · Live multi-recipient email delivery tracking
          </p>
        </div>
        <div className="d-flex align-items-center gap-2">
          <Button variant="danger" size="sm" className="d-flex align-items-center gap-1 shadow-sm px-3"
            onClick={() => setShowDemoModal(true)}>
            <Send size={14} /> Trigger Live Alert
          </Button>
          <button className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1"
            onClick={() => loadAlerts(page)}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>

      {demoNotification && (
        <div className="alert alert-success alert-dismissible fade show d-flex align-items-center gap-2 mb-3 shadow-sm border-0"
          style={{ background: '#dcfce7', color: '#166534', borderLeft: '4px solid #16a34a' }}>
          <CheckCircle size={18} className="text-success flex-shrink-0" />
          <div className="fw-medium">{demoNotification}</div>
          <button type="button" className="btn-close" onClick={() => setDemoNotification(null)}></button>
        </div>
      )}

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
                          {alert.occurrenceNumber != null && (
                            <div className="col-12 mt-1 pt-1 border-top">
                              <span className={`badge ${alert.penaltyClassification === 'PENALTY' ? 'bg-danger text-white' : 'bg-warning text-dark'}`} style={{ fontSize: 11 }}>
                                {alert.penaltyClassification === 'PENALTY' ? '🚨 Fine Applied' : '⚠️ Warning'} · Occurrence #{alert.occurrenceNumber}
                              </span>
                              <span className="ms-2 fw-bold" style={{ color: alert.penaltyClassification === 'PENALTY' ? '#dc2626' : '#64748b', fontSize: 12 }}>
                                Alert Penalty: ₹{alert.penaltyAmount != null ? Number(alert.penaltyAmount).toFixed(2) : '0.00'}
                              </span>
                              {alert.driverCumulativePenalties != null && (
                                <span className="ms-2 text-muted" style={{ fontSize: 11 }}>
                                  (Driver Cumulative: ₹{Number(alert.driverCumulativePenalties).toFixed(2)})
                                </span>
                              )}
                            </div>
                          )}
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

      {/* Trigger Live Demo Alert Modal */}
      <Modal show={showDemoModal} onHide={() => !demoLoading && setShowDemoModal(false)} centered size="lg">
        <Modal.Header closeButton={!demoLoading} className="bg-light">
          <Modal.Title className="h5 fw-bold d-flex align-items-center gap-2 mb-0">
            <Send size={18} className="text-danger" />
            Trigger Live Fleet Alert (Demo)
          </Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleTriggerDemo}>
          <Modal.Body className="p-4">
            <p className="text-muted small mb-3">
              Simulate an instant telemetry event. When submitted, the Spring Boot backend creates the alert record and dispatches real-time HTML emails to Fleet Admin, Manager, and the selected Driver.
            </p>

            <div className="row g-3">
              <div className="col-md-6">
                <Form.Group>
                  <Form.Label className="small fw-semibold text-secondary">Alert Type</Form.Label>
                  <Form.Select
                    value={demoForm.alertType}
                    onChange={e => setDemoForm({ ...demoForm, alertType: e.target.value })}>
                    <option value="Over-Speeding Alert">Over-Speeding Alert</option>
                    <option value="Harsh Braking Alert">Harsh Braking Alert</option>
                    <option value="Harsh Acceleration Alert">Harsh Acceleration Alert</option>
                    <option value="Geofence Breach Alert">Geofence Breach Alert</option>
                    <option value="SOS Panic Alert">SOS Panic Alert</option>
                  </Form.Select>
                </Form.Group>
              </div>

              <div className="col-md-6">
                <Form.Group>
                  <Form.Label className="small fw-semibold text-secondary">Severity</Form.Label>
                  <Form.Select
                    value={demoForm.severity}
                    onChange={e => setDemoForm({ ...demoForm, severity: e.target.value })}>
                    <option value="CRITICAL">CRITICAL</option>
                    <option value="WARNING">WARNING</option>
                    <option value="INFO">INFO</option>
                  </Form.Select>
                </Form.Group>
              </div>

              <div className="col-md-6">
                <Form.Group>
                  <Form.Label className="small fw-semibold text-secondary">Vehicle</Form.Label>
                  <Form.Select
                    value={demoForm.vehicleId}
                    onChange={e => setDemoForm({ ...demoForm, vehicleId: e.target.value })}>
                    {vehiclesList.length > 0 ? (
                      vehiclesList.map(v => (
                        <option key={v.id} value={v.id}>
                          {v.registrationNumber || v.code} ({v.model || 'Fleet Vehicle'})
                        </option>
                      ))
                    ) : (
                      <option value="1">MH03AC4582 (VH001 - Heavy Duty Truck)</option>
                    )}
                  </Form.Select>
                </Form.Group>
              </div>

              <div className="col-md-6">
                <Form.Group>
                  <Form.Label className="small fw-semibold text-secondary">Driver (Dynamic Email)</Form.Label>
                  <Form.Select
                    value={demoForm.driverId}
                    onChange={e => setDemoForm({ ...demoForm, driverId: e.target.value })}>
                    {driversList.length > 0 ? (
                      driversList.map(d => (
                        <option key={d.id} value={d.id}>
                          {d.name} {d.email ? `(${d.email})` : '(No Email)'}
                        </option>
                      ))
                    ) : (
                      <option value="20">Merin Joys (merinjoys28@gmail.com)</option>
                    )}
                  </Form.Select>
                </Form.Group>
              </div>

              <div className="col-md-6">
                <Form.Group>
                  <Form.Label className="small fw-semibold text-secondary">Speed / Limit (km/h)</Form.Label>
                  <div className="input-group">
                    <Form.Control
                      type="number"
                      step="0.5"
                      placeholder="Speed"
                      value={demoForm.speedKmph}
                      onChange={e => setDemoForm({ ...demoForm, speedKmph: e.target.value })}
                    />
                    <span className="input-group-text bg-light text-muted">/</span>
                    <Form.Control
                      type="number"
                      step="0.5"
                      placeholder="Limit"
                      value={demoForm.speedLimitKmph}
                      onChange={e => setDemoForm({ ...demoForm, speedLimitKmph: e.target.value })}
                    />
                  </div>
                </Form.Group>
              </div>

              <div className="col-md-6">
                <Form.Group>
                  <Form.Label className="small fw-semibold text-secondary">Road / Location</Form.Label>
                  <Form.Control
                    type="text"
                    value={demoForm.roadName}
                    onChange={e => setDemoForm({ ...demoForm, roadName: e.target.value })}
                  />
                </Form.Group>
              </div>

              <div className="col-12">
                <Form.Group>
                  <Form.Label className="small fw-semibold text-secondary">Alert Message</Form.Label>
                  <Form.Control
                    as="textarea"
                    rows={2}
                    value={demoForm.message}
                    onChange={e => setDemoForm({ ...demoForm, message: e.target.value })}
                  />
                </Form.Group>
              </div>
            </div>

            {/* Recipient preview box */}
            <div className="p-3 mt-3 rounded border" style={{ background: '#f8fafc', fontSize: 13 }}>
              <div className="fw-bold mb-1 text-dark d-flex align-items-center gap-1">
                <Mail size={14} className="text-primary" /> Live Recipients to be Notified:
              </div>
              <ul className="list-unstyled mb-0 ms-2 small text-muted">
                <li>• <strong>Fleet Admin:</strong> <code>riyamokale@gmail.com</code></li>
                <li>• <strong>Manager:</strong> <code>rhea260906@gmail.com</code></li>
                <li>• <strong>Driver:</strong> Dynamic lookup from driver record (e.g. <code>merinjoys28@gmail.com</code>)</li>
              </ul>
            </div>
          </Modal.Body>
          <Modal.Footer className="bg-light">
            <Button variant="outline-secondary" size="sm" onClick={() => setShowDemoModal(false)} disabled={demoLoading}>
              Cancel
            </Button>
            <Button variant="danger" size="sm" type="submit" disabled={demoLoading} className="d-flex align-items-center gap-2">
              {demoLoading ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true" />
                  Dispatching Emails...
                </>
              ) : (
                <>
                  <Send size={14} /> Send Alert & Deliver Emails
                </>
              )}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>
    </div>
  );
}
