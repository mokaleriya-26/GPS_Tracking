import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Badge, Spinner, Alert } from 'react-bootstrap';
import {
  ShieldCheck, Route, Bell, Scale, Truck, User, Calendar,
  Activity, CheckCircle, AlertTriangle, ArrowRight, DollarSign, Clock
} from 'lucide-react';
import driverPortalApi from '../../api/driverPortal';
import { Link } from 'react-router-dom';

const formatDateTime = (dt) => {
  if (!dt) return '—';
  try {
    return new Date(dt).toLocaleString('en-IN', {
      day: 'numeric', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit', hour12: true
    });
  } catch {
    return dt;
  }
};

export default function DriverDashboard() {
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
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
  }, []);

  if (loading) {
    return (
      <div className="d-flex flex-column align-items-center justify-content-center py-5">
        <Spinner animation="border" variant="success" />
        <span className="mt-2 text-muted" style={{ fontSize: 13 }}>Loading Driver Dashboard…</span>
      </div>
    );
  }

  if (error || !dashboard) {
    return (
      <Alert variant="danger">
        {error || 'Unable to retrieve dashboard information.'}
      </Alert>
    );
  }

  const { driver, assignedVehicles = [], safetyScore, totalTrips, totalAlerts, latestTripStatus, totalPenalties, totalPenaltyAmount, recentTrips = [], recentAlerts = [], recentPenalties = [] } = dashboard;

  const scoreColor = safetyScore >= 85 ? '#10b981' : safetyScore >= 70 ? '#f59e0b' : '#ef4444';
  const scoreCategory = safetyScore >= 85 ? 'Excellent' : safetyScore >= 70 ? 'Satisfactory' : 'Needs Improvement';

  return (
    <div style={{ fontFamily: 'Inter, sans-serif' }}>
      {/* Welcome Banner */}
      <div style={{
        background: 'linear-gradient(135deg, #064e3b, #047857)',
        color: '#fff',
        borderRadius: 16,
        padding: '24px 28px',
        marginBottom: 24,
        boxShadow: '0 10px 25px -5px rgba(5, 150, 105, 0.3)',
      }}>
        <div className="d-flex align-items-center justify-content-between flex-wrap gap-3">
          <div>
            <div className="d-flex align-items-center gap-2 mb-1">
              <span className="badge" style={{ background: 'rgba(255,255,255,0.2)', fontSize: 11, fontWeight: 700 }}>
                DRIVER PORTAL
              </span>
              <span className="badge" style={{ background: 'rgba(16, 185, 129, 0.3)', color: '#a7f3d0', fontSize: 11 }}>
                ACTIVE ACCOUNT
              </span>
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, margin: 0, letterSpacing: '-0.02em' }}>
              Welcome back, {driver?.name || 'Driver'}!
            </h1>
            <p style={{ margin: '4px 0 0', color: '#a7f3d0', fontSize: 13 }}>
              Driver Code: <strong>{driver?.code || '—'}</strong> · Phone: {driver?.phone || '—'} · License: {driver?.licenseNumber || '—'}
            </p>
          </div>

          <div className="d-flex gap-2">
            <Link to="/driver/reports" className="btn btn-light btn-sm fw-semibold d-flex align-items-center gap-1" style={{ borderRadius: 8 }}>
              <span>My Reports</span>
              <ArrowRight size={14} />
            </Link>
          </div>
        </div>
      </div>

      {/* KPI Cards */}
      <Row className="g-3 mb-4">
        {/* Safety Score */}
        <Col xs={12} sm={6} lg={3}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span className="text-muted fw-semibold" style={{ fontSize: 12 }}>Safety Score</span>
                <div style={{ width: 34, height: 34, borderRadius: 9, background: '#ecfdf5', color: '#059669', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <ShieldCheck size={18} />
                </div>
              </div>
              <div className="d-flex align-items-baseline gap-2">
                <span style={{ fontSize: 28, fontWeight: 800, color: scoreColor }}>
                  {safetyScore != null ? Math.round(safetyScore) : '—'}
                </span>
                <span className="text-muted" style={{ fontSize: 12 }}>/ 100</span>
              </div>
              <div className="mt-2">
                <span className="badge" style={{ background: `${scoreColor}18`, color: scoreColor, fontSize: 10, fontWeight: 700 }}>
                  {scoreCategory} Performance
                </span>
              </div>
            </div>
          </div>
        </Col>

        {/* Total Trips */}
        <Col xs={12} sm={6} lg={3}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span className="text-muted fw-semibold" style={{ fontSize: 12 }}>Total Trips</span>
                <div style={{ width: 34, height: 34, borderRadius: 9, background: '#eff6ff', color: '#2563eb', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Route size={18} />
                </div>
              </div>
              <div className="d-flex align-items-baseline gap-2">
                <span style={{ fontSize: 28, fontWeight: 800, color: '#0f172a' }}>
                  {totalTrips.toLocaleString()}
                </span>
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Latest Status: <strong className="text-dark">{latestTripStatus}</strong>
              </div>
            </div>
          </div>
        </Col>

        {/* Total Alerts */}
        <Col xs={12} sm={6} lg={3}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span className="text-muted fw-semibold" style={{ fontSize: 12 }}>Total Alerts</span>
                <div style={{ width: 34, height: 34, borderRadius: 9, background: '#fffbeb', color: '#d97706', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Bell size={18} />
                </div>
              </div>
              <div className="d-flex align-items-baseline gap-2">
                <span style={{ fontSize: 28, fontWeight: 800, color: '#d97706' }}>
                  {totalAlerts.toLocaleString()}
                </span>
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Monitored in real-time
              </div>
            </div>
          </div>
        </Col>

        {/* Total Penalties */}
        <Col xs={12} sm={6} lg={3}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span className="text-muted fw-semibold" style={{ fontSize: 12 }}>Total Penalties</span>
                <div style={{ width: 34, height: 34, borderRadius: 9, background: '#fee2e2', color: '#dc2626', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Scale size={18} />
                </div>
              </div>
              <div className="d-flex align-items-baseline gap-2">
                <span style={{ fontSize: 28, fontWeight: 800, color: '#dc2626' }}>
                  ₹{totalPenaltyAmount != null ? parseFloat(totalPenaltyAmount).toFixed(2) : '0.00'}
                </span>
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                <strong>{totalPenalties}</strong> logged occurrences
              </div>
            </div>
          </div>
        </Col>
      </Row>

      {/* Assigned Vehicles Card */}
      <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 14 }}>
        <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center gap-2">
            <Truck size={18} className="text-primary" />
            <span className="fw-bold text-dark" style={{ fontSize: 15 }}>My Assigned Vehicles</span>
          </div>
          <Link to="/driver/vehicles" className="text-decoration-none fw-semibold" style={{ fontSize: 12 }}>
            View All ({assignedVehicles.length}) →
          </Link>
        </div>
        <div className="card-body p-4 pt-0">
          {assignedVehicles.length === 0 ? (
            <div className="text-muted text-center py-3" style={{ fontSize: 13 }}>
              No vehicle currently assigned.
            </div>
          ) : (
            <Row className="g-3">
              {assignedVehicles.map((v) => (
                <Col key={v.id} xs={12} md={6} lg={4}>
                  <div style={{
                    background: '#f8fafc',
                    border: '1px solid #e2e8f0',
                    borderRadius: 12,
                    padding: '14px 16px',
                  }}>
                    <div className="d-flex align-items-center justify-content-between mb-2">
                      <span className="fw-bold text-dark" style={{ fontSize: 14 }}>{v.code}</span>
                      <Badge bg="success" pill style={{ fontSize: 10 }}>Assigned</Badge>
                    </div>
                    <div className="text-muted" style={{ fontSize: 12, marginBottom: 4 }}>
                      Reg: <strong>{v.registrationNumber}</strong>
                    </div>
                    <div className="text-muted" style={{ fontSize: 11 }}>
                      {v.makeModel || 'Fleet Vehicle'} · {v.fuelType || 'Diesel'}
                    </div>
                  </div>
                </Col>
              ))}
            </Row>
          )}
        </div>
      </div>

      {/* Recent Activity Sections: Trips & Alerts */}
      <Row className="g-4">
        {/* Recent Trips */}
        <Col xs={12} lg={6}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center justify-content-between">
              <div className="d-flex align-items-center gap-2">
                <Route size={18} className="text-primary" />
                <span className="fw-bold text-dark" style={{ fontSize: 15 }}>Recent Trips</span>
              </div>
              <Link to="/driver/trips" className="text-decoration-none fw-semibold" style={{ fontSize: 12 }}>
                All Trips →
              </Link>
            </div>
            <div className="card-body p-0">
              {recentTrips.length === 0 ? (
                <div className="p-4 text-center text-muted" style={{ fontSize: 13 }}>No recent trips found.</div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0" style={{ fontSize: 12 }}>
                    <thead className="table-light">
                      <tr>
                        <th>Trip</th>
                        <th>Started</th>
                        <th>Distance</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentTrips.map((t) => (
                        <tr key={t.id}>
                          <td>
                            <strong>#{t.id}</strong>
                            <div className="text-muted" style={{ fontSize: 11 }}>{t.vehicleRegistrationNumber || t.vehicleCode}</div>
                          </td>
                          <td>{formatDateTime(t.startTime)}</td>
                          <td>{t.distanceKm ? `${t.distanceKm.toFixed(1)} km` : '—'}</td>
                          <td>
                            <Badge bg={t.status === 'COMPLETED' ? 'success' : 'primary'} style={{ fontSize: 10 }}>
                              {t.status}
                            </Badge>
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

        {/* Recent Alerts */}
        <Col xs={12} lg={6}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center justify-content-between">
              <div className="d-flex align-items-center gap-2">
                <Bell size={18} className="text-warning" />
                <span className="fw-bold text-dark" style={{ fontSize: 15 }}>Recent Alerts</span>
              </div>
              <Link to="/driver/alerts" className="text-decoration-none fw-semibold" style={{ fontSize: 12 }}>
                All Alerts →
              </Link>
            </div>
            <div className="card-body p-0">
              {recentAlerts.length === 0 ? (
                <div className="p-4 text-center text-muted" style={{ fontSize: 13 }}>No recent alerts for your account!</div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0" style={{ fontSize: 12 }}>
                    <thead className="table-light">
                      <tr>
                        <th>Type</th>
                        <th>Severity</th>
                        <th>Time</th>
                        <th>Penalty</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentAlerts.map((a) => (
                        <tr key={a.id}>
                          <td>
                            <strong>{a.alertType}</strong>
                            <div className="text-muted" style={{ fontSize: 11 }}>{a.roadName || 'Highway'}</div>
                          </td>
                          <td>
                            <Badge bg={a.severity === 'CRITICAL' ? 'danger' : 'warning text-dark'} style={{ fontSize: 10 }}>
                              {a.severity}
                            </Badge>
                          </td>
                          <td>{formatDateTime(a.occurredAt)}</td>
                          <td>
                            {a.penaltyAmount ? (
                              <span className="text-danger fw-bold">₹{parseFloat(a.penaltyAmount).toFixed(0)}</span>
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
      </Row>
    </div>
  );
}
