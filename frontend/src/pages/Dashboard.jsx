import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Badge, Table, Button } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import {
  Car, Bell, Users, Navigation, TrendingUp,
  AlertTriangle, Shield, Activity, RefreshCw
} from 'lucide-react';
import { getDriverRanking } from '../api/drivers';
import { getTrips } from '../api/trips';

// Spring Boot VehicleDTO field names
const getVehicleReg = (v) => v.registrationNumber || v.registration_number || v.number || v.code || '—';
const getDriverName = (v) => v.driverName || v.driver_name || v.driver?.name || 'Unassigned';
const getSpeed = (v) => v.lastSpeedKmph ?? v.lastSpeed ?? v.speed ?? 0;
const getLocation = (v) => v.lastRoadName || v.lastLocation || v.location || '—';
const isActive = (v) => v.active === true || v.ignitionOn === true || v.ignition === true;

// Spring Boot AlertDTO field names
const getAlertType = (a) => a.alertType || a.alert_type || '—';
const getAlertVehicle = (a) => a.vehicleRegistrationNumber || a.vehicle_number || a.vehicleCode || '—';
const getAlertDriver = (a) => a.driverName || a.driver_name || '—';
const getAlertTime = (a) => a.occurredAt || a.timestamp || a.created_at;
const isCritical = (a) => {
  const t = getAlertType(a).toLowerCase();
  return t.includes('overspeed') || t.includes('disconnect');
};
const isHistorical = (a) => a.status === 'HISTORICAL' || a.deliveryStatus === 'SILENCED';

const StatCard = ({ icon, label, value, sub, color, to }) => (
  <Col xs={6} lg={3}>
    <Card className="border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
      <Card.Body className="d-flex align-items-center gap-3 p-3">
        <div style={{
          width: 48, height: 48, borderRadius: 12, flexShrink: 0,
          background: `${color}18`,
          display: 'flex', alignItems: 'center', justifyContent: 'center'
        }}>
          {React.cloneElement(icon, { size: 22, color })}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            {label}
          </div>
          <div style={{ fontSize: 26, fontWeight: 800, color: '#0f172a', lineHeight: 1.2 }}>
            {value}
          </div>
          {sub && <div style={{ fontSize: 11, color: '#64748b', marginTop: 2 }}>{sub}</div>}
        </div>
      </Card.Body>
    </Card>
  </Col>
);

const Dashboard = ({ vehicles = [], alerts = [] }) => {
  const [ranking, setRanking] = useState([]);
  const [recentTrips, setRecentTrips] = useState([]);
  const [loadingExtra, setLoadingExtra] = useState(true);

  useEffect(() => {
    setLoadingExtra(true);
    Promise.all([
      getDriverRanking().catch(() => []),
      getTrips({ page: 0, size: 5 }).catch(() => [])
    ]).then(([rank, trips]) => {
      setRanking(Array.isArray(rank) ? rank.slice(0, 5) : []);
      const tripList = Array.isArray(trips) ? trips : (trips?.content || []);
      setRecentTrips(tripList.slice(0, 5));
    }).finally(() => setLoadingExtra(false));
  }, []);

  // Computed stats
  const activeVehicles = vehicles.filter(v => isActive(v)).length;
  const criticalAlertCount = alerts.filter(a => isCritical(a) && !isHistorical(a)).length;
  const openAlerts = alerts.filter(a => a.status === 'OPEN').length;
  const historicalAlerts = alerts.filter(a => isHistorical(a)).length;
  const totalAlerts = alerts.length;

  const scoreColor = (s) => {
    if (s == null) return '#94a3b8';
    if (s >= 80) return '#16a34a';
    if (s >= 60) return '#d97706';
    return '#dc2626';
  };

  const formatTime = (dt) => {
    if (!dt) return '—';
    return new Date(dt).toLocaleString('en-IN', {
      day: '2-digit', month: 'short',
      hour: '2-digit', minute: '2-digit', hour12: true
    });
  };

  return (
    <>
      {/* Header */}
      <div className="mb-4 d-flex align-items-center justify-content-between">
        <div>
          <h1 className="h3 mb-1 fw-bold" style={{ color: '#0f172a' }}>Fleet Overview</h1>
          <p className="text-muted mb-0">
            Live monitoring — {vehicles.length} vehicles · {totalAlerts} alerts total
          </p>
        </div>
        <Badge bg="success" className="d-flex align-items-center gap-1" style={{ fontSize: 12 }}>
          <Activity size={12} /> Live
        </Badge>
      </div>

      {/* KPI Row */}
      <Row className="g-3 mb-4">
        <StatCard icon={<Car />} label="Total Vehicles" value={vehicles.length}
          sub={`${activeVehicles} currently active`} color="#2563eb" />
        <StatCard icon={<Users />} label="Drivers" value={ranking.length || '—'}
          sub="with safety scores" color="#7c3aed" />
        <StatCard icon={<Bell />} label="Open Alerts" value={openAlerts}
          sub={criticalAlertCount > 0 ? `${criticalAlertCount} critical` : 'All clear'} color="#dc2626" />
        <StatCard icon={<Shield />} label="Historical" value={historicalAlerts}
          sub="silenced (CSV import)" color="#64748b" />
      </Row>

      <Row className="g-3">
        {/* Vehicle Table */}
        <Col lg={8}>
          <Card className="border-0 shadow-sm mb-3" style={{ borderRadius: 14 }}>
            <Card.Header className="bg-white border-0 py-3 px-4 d-flex justify-content-between align-items-center">
              <h5 className="mb-0 fw-bold" style={{ fontSize: 15 }}>
                <Car size={16} className="me-2 text-primary" />
                Vehicles
              </h5>
              <Button as={Link} to="/vehicles" variant="outline-primary" size="sm" style={{ borderRadius: 8 }}>
                View All
              </Button>
            </Card.Header>
            <div className="table-responsive">
              <Table hover className="mb-0 align-middle" style={{ fontSize: 13 }}>
                <thead style={{ background: '#f8fafc', fontSize: 12 }}>
                  <tr>
                    <th className="px-4 py-3 border-0">Registration</th>
                    <th className="py-3 border-0">Driver</th>
                    <th className="py-3 border-0">Status</th>
                    <th className="py-3 border-0">Speed</th>
                    <th className="py-3 border-0">Location</th>
                  </tr>
                </thead>
                <tbody>
                  {vehicles.slice(0, 6).map((v, i) => (
                    <tr key={v.id || i}>
                      <td className="px-4 fw-semibold">{getVehicleReg(v)}</td>
                      <td>{getDriverName(v)}</td>
                      <td>
                        <Badge bg={isActive(v) ? 'success' : 'secondary'} style={{ fontSize: 11 }}>
                          {isActive(v) ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td>{getSpeed(v) > 0 ? `${getSpeed(v)} km/h` : '—'}</td>
                      <td>
                        <div className="text-truncate" style={{ maxWidth: 160 }}>
                          {getLocation(v)}
                        </div>
                      </td>
                    </tr>
                  ))}
                  {vehicles.length === 0 && (
                    <tr>
                      <td colSpan={5} className="text-center text-muted py-4">
                        No vehicles loaded
                      </td>
                    </tr>
                  )}
                </tbody>
              </Table>
            </div>
          </Card>

          {/* Recent Trips */}
          <Card className="border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <Card.Header className="bg-white border-0 py-3 px-4 d-flex justify-content-between align-items-center">
              <h5 className="mb-0 fw-bold" style={{ fontSize: 15 }}>
                <Navigation size={16} className="me-2 text-primary" />
                Recent Trips
              </h5>
              <Button as={Link} to="/trips" variant="outline-primary" size="sm" style={{ borderRadius: 8 }}>
                View All
              </Button>
            </Card.Header>
            <div className="table-responsive">
              <Table hover className="mb-0 align-middle" style={{ fontSize: 13 }}>
                <thead style={{ background: '#f8fafc', fontSize: 12 }}>
                  <tr>
                    <th className="px-4 py-3 border-0">Vehicle</th>
                    <th className="py-3 border-0">Driver</th>
                    <th className="py-3 border-0">Start</th>
                    <th className="py-3 border-0">Distance</th>
                    <th className="py-3 border-0">Alerts</th>
                  </tr>
                </thead>
                <tbody>
                  {recentTrips.map((t, i) => (
                    <tr key={t.id || i}>
                      <td className="px-4 fw-semibold">{t.vehicleCode || t.vehicleRegistrationNumber || '—'}</td>
                      <td>{t.driverName || '—'}</td>
                      <td style={{ fontSize: 12 }}>{formatTime(t.startTime)}</td>
                      <td>{t.distanceKm != null ? `${parseFloat(t.distanceKm).toFixed(1)} km` : '—'}</td>
                      <td>
                        <Badge bg={(t.alertCount || 0) > 0 ? 'danger' : 'success'} style={{ fontSize: 11 }}>
                          {t.alertCount || 0}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                  {recentTrips.length === 0 && !loadingExtra && (
                    <tr>
                      <td colSpan={5} className="text-center text-muted py-3">No trips found</td>
                    </tr>
                  )}
                </tbody>
              </Table>
            </div>
          </Card>
        </Col>

        {/* Right column */}
        <Col lg={4}>
          {/* Driver Ranking */}
          <Card className="border-0 shadow-sm mb-3" style={{ borderRadius: 14 }}>
            <Card.Header className="bg-white border-0 py-3 px-4 d-flex justify-content-between align-items-center">
              <h5 className="mb-0 fw-bold" style={{ fontSize: 15 }}>
                <TrendingUp size={16} className="me-2 text-primary" />
                Top Drivers
              </h5>
              <Button as={Link} to="/drivers" variant="link" size="sm" className="p-0" style={{ fontSize: 12 }}>
                Rankings →
              </Button>
            </Card.Header>
            <Card.Body className="px-4 py-2">
              {ranking.slice(0, 5).map((r, i) => (
                <div key={r.driverId || i} className="d-flex align-items-center gap-3 py-2"
                  style={{ borderBottom: i < 4 ? '1px solid #f1f5f9' : 'none' }}>
                  <span style={{
                    width: 24, height: 24, borderRadius: '50%', flexShrink: 0,
                    background: i === 0 ? '#fef9c3' : i === 1 ? '#f1f5f9' : '#fdf2f8',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 11, fontWeight: 700,
                    color: i === 0 ? '#d97706' : i === 1 ? '#475569' : '#9f1239'
                  }}>
                    {i + 1}
                  </span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="fw-semibold text-truncate" style={{ fontSize: 13 }}>{r.driverName}</div>
                    <div style={{ fontSize: 11, color: '#94a3b8' }}>{r.driverCode}</div>
                  </div>
                  <span style={{
                    fontSize: 13, fontWeight: 700,
                    color: scoreColor(r.safetyScore)
                  }}>
                    {r.safetyScore?.toFixed ? r.safetyScore.toFixed(1) : r.safetyScore || '—'}
                  </span>
                </div>
              ))}
              {ranking.length === 0 && (
                <p className="text-muted text-center py-3" style={{ fontSize: 13 }}>
                  No ranking data yet
                </p>
              )}
            </Card.Body>
          </Card>

          {/* Recent Alerts */}
          <Card className="border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <Card.Header className="bg-white border-0 py-3 px-4 d-flex justify-content-between align-items-center">
              <h5 className="mb-0 fw-bold" style={{ fontSize: 15 }}>
                <AlertTriangle size={16} className="me-2 text-danger" />
                Recent Alerts
              </h5>
              <Button as={Link} to="/alerts" variant="link" size="sm" className="p-0" style={{ fontSize: 12 }}>
                All →
              </Button>
            </Card.Header>
            <Card.Body className="px-3 py-2" style={{ maxHeight: 300, overflowY: 'auto' }}>
              {alerts.slice(0, 8).map((a, i) => {
                const crit = isCritical(a);
                const hist = isHistorical(a);
                return (
                  <div key={a.id || i} className="py-2"
                    style={{ borderBottom: i < Math.min(alerts.length - 1, 7) ? '1px solid #f1f5f9' : 'none' }}>
                    <div className="d-flex align-items-start gap-2">
                      <span style={{
                        width: 8, height: 8, borderRadius: '50%', marginTop: 5, flexShrink: 0,
                        background: hist ? '#94a3b8' : crit ? '#dc2626' : '#f59e0b'
                      }} />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div className="d-flex justify-content-between">
                          <span style={{ fontSize: 12, fontWeight: 600, color: hist ? '#94a3b8' : crit ? '#dc2626' : '#d97706' }}>
                            {getAlertType(a)}
                          </span>
                          {hist && <Badge bg="secondary" style={{ fontSize: 9 }}>Historical</Badge>}
                        </div>
                        <div style={{ fontSize: 11, color: '#64748b' }}>
                          {getAlertDriver(a)} · {getAlertVehicle(a)}
                        </div>
                        <div style={{ fontSize: 10, color: '#94a3b8' }}>{formatTime(getAlertTime(a))}</div>
                      </div>
                    </div>
                  </div>
                );
              })}
              {alerts.length === 0 && (
                <p className="text-center text-muted py-3" style={{ fontSize: 13 }}>No alerts</p>
              )}
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </>
  );
};

export default Dashboard;
