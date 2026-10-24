import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Badge, Spinner, Alert, Button } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import {
  ShieldCheck, Route, Bell, Scale, Truck, User, Calendar,
  Activity, CheckCircle, AlertTriangle, ArrowRight, IndianRupee,
  Clock, MapPin, RefreshCw, ChevronRight, FileText
} from 'lucide-react';
import driverPortalApi from '../../api/driverPortal';
import { useAuth } from '../../context/AuthContext';

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

export default function DriverDashboard() {
  const { user } = useAuth();
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const loadData = () => {
    setLoading(true);
    driverPortalApi.getDashboard()
      .then((data) => {
        setDashboard(data);
        setError(null);
      })
      .catch((err) => {
        console.error('Failed to load driver dashboard:', err);
        setError('Failed to load driver dashboard data. Please verify your connection.');
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, []);

  if (loading) {
    return (
      <div className="d-flex flex-column align-items-center justify-content-center py-5" style={{ minHeight: '60vh' }}>
        <Spinner animation="border" variant="success" />
        <span className="mt-2 text-muted" style={{ fontSize: 13 }}>Loading Driver Dashboard…</span>
      </div>
    );
  }

  if (error || !dashboard) {
    return (
      <Alert variant="danger" className="d-flex align-items-center justify-content-between p-3 rounded-3">
        <div className="d-flex align-items-center gap-2">
          <AlertTriangle size={18} />
          <span>{error || 'Unable to retrieve dashboard information.'}</span>
        </div>
        <Button variant="outline-danger" size="sm" onClick={loadData}>
          <RefreshCw size={14} className="me-1" /> Retry
        </Button>
      </Alert>
    );
  }

  const {
    driver,
    assignedVehicles = [],
    safetyScore,
    totalTrips = 0,
    totalAlerts = 0,
    latestTripStatus = 'COMPLETED',
    totalPenalties = 0,
    totalPenaltyAmount = 0,
    recentTrips = [],
    recentAlerts = [],
    recentPenalties = []
  } = dashboard;

  const scoreValue = safetyScore != null ? Math.round(safetyScore) : 100;
  const scoreColor = scoreValue >= 85 ? '#059669' : scoreValue >= 70 ? '#d97706' : '#dc2626';
  const scoreCategory = scoreValue >= 85 ? 'Excellent' : scoreValue >= 70 ? 'Satisfactory' : 'Needs Attention';

  const driverDisplayName = driver?.name || user?.driverName || 'Driver';
  const driverDisplayCode = driver?.code || user?.driverCode || (user?.driverId ? `DRV#${user.driverId}` : '—');

  // Severity badge helper matching requirements:
  // Critical: light red, High: light orange, Medium: light amber, Low: light blue/neutral
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
      {/* Dashboard Top Header & Welcome Banner */}
      <div
        className="card border-0 shadow-sm mb-4"
        style={{
          borderRadius: 16,
          background: 'linear-gradient(135deg, #ffffff 0%, #f0fdf4 100%)',
          border: '1px solid #dcfce7 !important',
        }}
      >
        <div className="card-body p-4">
          <div className="d-flex align-items-center justify-content-between flex-wrap gap-3">
            <div className="d-flex align-items-center gap-3">
              <div
                style={{
                  width: 52,
                  height: 52,
                  borderRadius: 14,
                  background: 'linear-gradient(135deg, #059669, #047857)',
                  color: '#ffffff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 8px 16px -4px rgba(5, 150, 105, 0.3)',
                  flexShrink: 0,
                }}
              >
                <User size={26} />
              </div>

              <div>
                <div className="d-flex align-items-center gap-2 mb-1 flex-wrap">
                  <span
                    className="badge"
                    style={{
                      backgroundColor: '#ecfdf5',
                      color: '#059669',
                      border: '1px solid #a7f3d0',
                      fontSize: 10,
                      fontWeight: 700,
                    }}
                  >
                    DRIVER ACCOUNT
                  </span>
                  {assignedVehicles.length > 0 && (
                    <span
                      className="badge bg-light text-secondary border"
                      style={{ fontSize: 10, borderColor: '#e2e8f0' }}
                    >
                      <Truck size={11} className="me-1 text-success" />
                      Assigned: <strong>{assignedVehicles[0].code}</strong>
                    </span>
                  )}
                </div>

                <h1 style={{ fontSize: 22, fontWeight: 800, color: '#0f172a', margin: 0, letterSpacing: '-0.02em' }}>
                  Welcome back, {driverDisplayName}!
                </h1>
                <p style={{ margin: '4px 0 0', color: '#64748b', fontSize: 13 }}>
                  Driver ID: <strong className="text-dark">{driverDisplayCode}</strong>
                  {driver?.phone && ` · Phone: ${driver.phone}`}
                  {driver?.licenseNumber && ` · License: ${driver.licenseNumber}`}
                </p>
              </div>
            </div>

            <div className="d-flex align-items-center gap-2">
              <Button
                variant="outline-secondary"
                size="sm"
                onClick={loadData}
                className="d-flex align-items-center gap-1.5"
                style={{ borderRadius: 8, fontSize: 12, padding: '7px 12px' }}
                title="Refresh dashboard data"
              >
                <RefreshCw size={13} />
                <span className="d-none d-sm-inline">Refresh</span>
              </Button>

              <Link
                to="/driver/reports"
                className="btn btn-success btn-sm d-flex align-items-center gap-1.5 fw-semibold"
                style={{
                  borderRadius: 8,
                  fontSize: 12,
                  padding: '7px 14px',
                  background: 'linear-gradient(135deg, #059669, #047857)',
                  border: 'none',
                  boxShadow: '0 2px 6px rgba(5, 150, 105, 0.25)',
                }}
              >
                <FileText size={14} />
                <span>My Reports</span>
              </Link>
            </div>
          </div>
        </div>
      </div>

      {/* Summary Cards (5 Uniform Cards matching requirements) */}
      <Row className="g-3 mb-4">
        {/* 1. Total Trips */}
        <Col xs={12} sm={6} lg>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Total Trips
                </span>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#eff6ff', color: '#2563eb', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Route size={18} />
                </div>
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#0f172a', lineHeight: 1.2 }}>
                {Number(totalTrips).toLocaleString()}
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Status: <strong className="text-dark">{latestTripStatus || 'COMPLETED'}</strong>
              </div>
            </div>
          </div>
        </Col>

        {/* 2. Total Alerts */}
        <Col xs={12} sm={6} lg>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Total Alerts
                </span>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#fffbeb', color: '#d97706', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Bell size={18} />
                </div>
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#d97706', lineHeight: 1.2 }}>
                {Number(totalAlerts).toLocaleString()}
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Monitored in real-time
              </div>
            </div>
          </div>
        </Col>

        {/* 3. Current Safety Score */}
        <Col xs={12} sm={6} lg>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Safety Score
                </span>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#ecfdf5', color: '#059669', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <ShieldCheck size={18} />
                </div>
              </div>
              <div className="d-flex align-items-baseline gap-1.5">
                <span style={{ fontSize: 26, fontWeight: 800, color: scoreColor, lineHeight: 1.2 }}>
                  {scoreValue}
                </span>
                <span className="text-muted" style={{ fontSize: 12 }}>/ 100</span>
              </div>
              <div className="mt-2">
                <span
                  style={{
                    backgroundColor: `${scoreColor}14`,
                    color: scoreColor,
                    fontSize: 10,
                    fontWeight: 700,
                    padding: '2px 6px',
                    borderRadius: 4,
                  }}
                >
                  {scoreCategory}
                </span>
              </div>
            </div>
          </div>
        </Col>

        {/* 4. Total Penalties (Count) */}
        <Col xs={12} sm={6} lg>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Total Penalties
                </span>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#fee2e2', color: '#dc2626', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Scale size={18} />
                </div>
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#0f172a', lineHeight: 1.2 }}>
                {Number(totalPenalties).toLocaleString()}
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Logged occurrences
              </div>
            </div>
          </div>
        </Col>

        {/* 5. Total Penalty Amount */}
        <Col xs={12} sm={6} lg>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Penalty Amount
                </span>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#fef2f2', color: '#dc2626', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <IndianRupee size={18} />
                </div>
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#dc2626', lineHeight: 1.2 }}>
                ₹{Number(totalPenaltyAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 0 })}
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Progressive liability
              </div>
            </div>
          </div>
        </Col>
      </Row>

      {/* Assigned Vehicles Section */}
      <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 14 }}>
        <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center gap-2">
            <Truck size={18} color="#059669" />
            <span className="fw-bold text-dark" style={{ fontSize: 15 }}>My Assigned Vehicles</span>
          </div>
          <Link
            to="/driver/vehicles"
            className="text-decoration-none fw-semibold d-flex align-items-center gap-1 text-success"
            style={{ fontSize: 12 }}
          >
            <span>View All ({assignedVehicles.length})</span>
            <ChevronRight size={14} />
          </Link>
        </div>
        <div className="card-body p-4 pt-0">
          {assignedVehicles.length === 0 ? (
            <div className="text-muted text-center py-4 bg-light rounded-3" style={{ fontSize: 13 }}>
              <Truck size={24} className="text-muted mb-2 opacity-50" />
              <div>No vehicles currently assigned to your driver identity.</div>
            </div>
          ) : (
            <Row className="g-3">
              {assignedVehicles.map((v) => (
                <Col key={v.id} xs={12} md={6} lg={4}>
                  <div
                    style={{
                      background: '#ffffff',
                      border: '1px solid #e2e8f0',
                      borderRadius: 12,
                      padding: '16px',
                      boxShadow: '0 1px 3px rgba(0, 0, 0, 0.04)',
                      transition: 'border-color 0.15s ease',
                    }}
                  >
                    <div className="d-flex align-items-center justify-content-between mb-2">
                      <div className="d-flex align-items-center gap-2">
                        <div
                          style={{
                            width: 32,
                            height: 32,
                            borderRadius: 8,
                            background: '#ecfdf5',
                            color: '#059669',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          <Truck size={16} />
                        </div>
                        <span className="fw-bold text-dark" style={{ fontSize: 15 }}>{v.code}</span>
                      </div>
                      <span
                        className="badge"
                        style={{
                          backgroundColor: '#ecfdf5',
                          color: '#059669',
                          border: '1px solid #d1fae5',
                          fontSize: 10,
                          fontWeight: 700,
                        }}
                      >
                        ASSIGNED
                      </span>
                    </div>

                    <div className="d-flex justify-content-between align-items-center mb-1 text-muted" style={{ fontSize: 12 }}>
                      <span>Registration:</span>
                      <strong className="text-dark font-monospace">{v.registrationNumber}</strong>
                    </div>
                    <div className="d-flex justify-content-between align-items-center text-muted" style={{ fontSize: 11 }}>
                      <span>Model & Fuel:</span>
                      <span className="text-dark">{v.makeModel || v.vehicleType || 'Commercial'} · {v.fuelType || 'Diesel'}</span>
                    </div>
                  </div>
                </Col>
              ))}
            </Row>
          )}
        </div>
      </div>

      {/* Two Column Layout: Recent Alerts & Recent Penalties */}
      <Row className="g-4 mb-4">
        {/* Recent Alerts Table */}
        <Col xs={12} lg={6}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center justify-content-between">
              <div className="d-flex align-items-center gap-2">
                <Bell size={18} color="#d97706" />
                <span className="fw-bold text-dark" style={{ fontSize: 15 }}>My Recent Alerts</span>
              </div>
              <Link
                to="/driver/alerts"
                className="text-decoration-none fw-semibold d-flex align-items-center gap-1 text-primary"
                style={{ fontSize: 12 }}
              >
                <span>View All Alerts</span>
                <ChevronRight size={14} />
              </Link>
            </div>
            <div className="card-body p-0">
              {recentAlerts.length === 0 ? (
                <div className="p-4 text-center text-muted" style={{ fontSize: 13 }}>
                  <CheckCircle size={28} className="text-success mb-2 opacity-75" />
                  <div className="fw-semibold text-dark">Clean Driving Record!</div>
                  <div className="text-muted small">No recent safety infractions recorded for your account.</div>
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0" style={{ fontSize: 12 }}>
                    <thead className="table-light">
                      <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                        <th className="ps-4">Alert Event</th>
                        <th>Severity</th>
                        <th>Time</th>
                        <th className="pe-4 text-end">Penalty</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentAlerts.map((a) => (
                        <tr key={a.id || a.reference}>
                          <td className="ps-4">
                            <strong className="text-dark">{a.alertType?.replace(/_/g, ' ')}</strong>
                            <div className="text-muted" style={{ fontSize: 11 }}>
                              {a.vehicleCode || a.vehicleRegistrationNumber || 'Vehicle'} · {a.roadName || 'Monitored Route'}
                            </div>
                          </td>
                          <td>{renderSeverityBadge(a.severity)}</td>
                          <td className="text-muted">{formatDateTime(a.occurredAt)}</td>
                          <td className="pe-4 text-end">
                            {a.penaltyAmount && Number(a.penaltyAmount) > 0 ? (
                              <span className="text-danger fw-bold font-monospace">
                                ₹{Number(a.penaltyAmount).toFixed(0)}
                              </span>
                            ) : (
                              <span className="text-success">₹0</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </Col>

        {/* Dedicated Penalties Summary Section */}
        <Col xs={12} lg={6}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center justify-content-between">
              <div className="d-flex align-items-center gap-2">
                <Scale size={18} color="#dc2626" />
                <span className="fw-bold text-dark" style={{ fontSize: 15 }}>My Penalties Summary</span>
              </div>
              <Link
                to="/driver/penalties"
                className="text-decoration-none fw-semibold d-flex align-items-center gap-1 text-danger"
                style={{ fontSize: 12 }}
              >
                <span>View All Penalties</span>
                <ChevronRight size={14} />
              </Link>
            </div>
            <div className="card-body p-0">
              {recentPenalties.length === 0 ? (
                <div className="p-4 text-center text-muted" style={{ fontSize: 13 }}>
                  <CheckCircle size={28} className="text-success mb-2 opacity-75" />
                  <div className="fw-semibold text-dark">No Penalties Assessed</div>
                  <div className="text-muted small">You currently have no progressive penalty deductions on file.</div>
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0" style={{ fontSize: 12 }}>
                    <thead className="table-light">
                      <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                        <th className="ps-4">Violation</th>
                        <th>Occurrence</th>
                        <th>Date</th>
                        <th className="pe-4 text-end">Amount</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentPenalties.map((p, idx) => (
                        <tr key={p.id || idx}>
                          <td className="ps-4">
                            <strong className="text-dark">{p.alertType?.replace(/_/g, ' ') || 'Infraction'}</strong>
                            <div className="text-muted" style={{ fontSize: 11 }}>
                              {p.vehicleRegistration || 'Assigned Vehicle'}
                            </div>
                          </td>
                          <td>
                            <span
                              className="badge"
                              style={{
                                backgroundColor: '#f1f5f9',
                                color: '#334155',
                                fontSize: 10,
                                fontWeight: 700,
                              }}
                            >
                              {p.occurrenceNumber ? `Occurrence #${p.occurrenceNumber}` : '1st Offense'}
                            </span>
                          </td>
                          <td className="text-muted">{formatDateTime(p.dateApplied || p.createdAt)}</td>
                          <td className="pe-4 text-end">
                            <span className="text-danger fw-bold font-monospace">
                              ₹{Number(p.monetaryAmount || 0).toLocaleString('en-IN')}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </Col>
      </Row>

      {/* Recent Trips Section */}
      <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
        <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center gap-2">
            <Route size={18} color="#2563eb" />
            <span className="fw-bold text-dark" style={{ fontSize: 15 }}>My Recent Trips</span>
          </div>
          <Link
            to="/driver/trips"
            className="text-decoration-none fw-semibold d-flex align-items-center gap-1 text-primary"
            style={{ fontSize: 12 }}
          >
            <span>View All Trips</span>
            <ChevronRight size={14} />
          </Link>
        </div>
        <div className="card-body p-0">
          {recentTrips.length === 0 ? (
            <div className="p-4 text-center text-muted" style={{ fontSize: 13 }}>
              <Route size={28} className="text-muted mb-2 opacity-50" />
              <div>No recent trips recorded for this driver account.</div>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0" style={{ fontSize: 12 }}>
                <thead className="table-light">
                  <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                    <th className="ps-4">Vehicle ID</th>
                    <th>Start Time</th>
                    <th>End Time</th>
                    <th>Distance</th>
                    <th>Duration</th>
                    <th className="pe-4">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {recentTrips.map((t) => {
                    const durMins = t.durationSeconds ? Math.round(t.durationSeconds / 60) : 0;
                    const durHours = Math.floor(durMins / 60);
                    const durMinsRemainder = durMins % 60;
                    const durationStr = durHours > 0 ? `${durHours}h ${durMinsRemainder}m` : `${durMins}m`;

                    return (
                      <tr key={t.id}>
                        <td className="ps-4">
                          <strong className="text-dark font-monospace">{t.vehicleCode || `VH#${t.vehicleId}`}</strong>
                          {t.vehicleRegistrationNumber && (
                            <span className="text-muted ms-1" style={{ fontSize: 11 }}>
                              ({t.vehicleRegistrationNumber})
                            </span>
                          )}
                        </td>
                        <td className="text-muted">{formatDateTime(t.startTime)}</td>
                        <td className="text-muted">{t.endTime ? formatDateTime(t.endTime) : 'In Progress'}</td>
                        <td className="fw-semibold text-dark">
                          {t.distanceKm ? `${t.distanceKm.toFixed(1)} km` : '—'}
                        </td>
                        <td className="text-muted">{durationStr}</td>
                        <td className="pe-4">
                          <span
                            className="badge"
                            style={{
                              backgroundColor: t.status === 'COMPLETED' ? '#ecfdf5' : '#eff6ff',
                              color: t.status === 'COMPLETED' ? '#059669' : '#2563eb',
                              border: `1px solid ${t.status === 'COMPLETED' ? '#a7f3d0' : '#bfdbfe'}`,
                              fontSize: 10,
                              fontWeight: 700,
                            }}
                          >
                            {t.status || 'COMPLETED'}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
