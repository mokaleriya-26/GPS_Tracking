import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Badge, Table, Button } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import {
  Car, Bell, Users, Navigation, TrendingUp,
  AlertTriangle, Shield, Activity, RefreshCw,
  AlertCircle, Scale, IndianRupee
} from 'lucide-react';
import { getDriverRanking } from '../api/drivers';
import { getTrips } from '../api/trips';
import { getFleetPenaltySummary } from '../api/penalties';

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

const Dashboard = ({ vehicles = [], alerts = [], alertSummary = null }) => {
  const [ranking, setRanking] = useState([]);
  const [recentTrips, setRecentTrips] = useState([]);
  const [penaltySummary, setPenaltySummary] = useState(null);
  const [loadingExtra, setLoadingExtra] = useState(true);

  useEffect(() => {
    setLoadingExtra(true);
    Promise.all([
      getDriverRanking().catch(() => []),
      getTrips({ page: 0, size: 5 }).catch(() => []),
      getFleetPenaltySummary().catch(() => null)
    ]).then(([rank, trips, penalties]) => {
      setRanking(Array.isArray(rank) ? rank.slice(0, 5) : []);
      const tripList = Array.isArray(trips) ? trips : (trips?.content || []);
      setRecentTrips(tripList.slice(0, 5));
      if (penalties) setPenaltySummary(penalties);
    }).finally(() => setLoadingExtra(false));
  }, []);

  const formatINR = (amt) => {
    if (amt == null) return '₹0.00';
    const n = typeof amt === 'number' ? amt : parseFloat(amt) || 0;
    return '₹' + n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  // Computed stats from real PostgreSQL summary or client-computed fallback
  const activeVehicles = vehicles.filter(v => isActive(v)).length;
  const criticalAlertCount = alertSummary?.criticalAlerts ?? alerts.filter(a => isCritical(a) && !isHistorical(a)).length;
  const openAlerts = alertSummary?.openAlerts ?? alerts.filter(a => a.status === 'OPEN').length;
  const historicalAlerts = alertSummary?.historicalAlerts ?? alerts.filter(a => isHistorical(a)).length;
  const totalAlerts = alertSummary?.totalAlerts ?? alerts.length;

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

      {/* Standard KPI Row */}
      <Row className="g-3 mb-3">
        <StatCard icon={<Car />} label="Total Vehicles" value={vehicles.length}
          sub={`${activeVehicles} currently active`} color="#2563eb" />
        <StatCard icon={<Users />} label="Drivers" value={ranking.length || '—'}
          sub="with safety scores" color="#7c3aed" />
        <StatCard icon={<Bell />} label="Open Alerts" value={openAlerts}
          sub={criticalAlertCount > 0 ? `${criticalAlertCount} critical` : 'All clear'} color="#dc2626" />
        <StatCard icon={<Shield />} label="Historical" value={historicalAlerts}
          sub="silenced (CSV import)" color="#64748b" />
      </Row>

      {/* Progressive Penalty KPI Row */}
      <Row className="g-3 mb-4">
        <StatCard icon={<IndianRupee />} label="Fleet Penalties" value={formatINR(penaltySummary?.totalFleetPenalties)}
          sub={`${penaltySummary?.totalPenaltyBearingAlerts ?? 0} penalty-bearing alerts`} color="#dc2626" />
        <StatCard icon={<AlertCircle />} label="Warning-Only Alerts" value={penaltySummary?.totalWarningOnlyAlerts ?? 0}
          sub="₹0.00 penalty applied" color="#d97706" />
        <StatCard icon={<TrendingUp />} label="Top Penalized Driver"
          value={penaltySummary?.highestPenalizedDriverCode || '—'}
          sub={penaltySummary?.highestPenalizedDriverName ? `${penaltySummary.highestPenalizedDriverName} (${formatINR(penaltySummary.highestDriverPenaltyTotal)})` : 'No penalties recorded'}
          color="#7c3aed" />
        <StatCard icon={<Scale />} label="Penalty Types"
          value={penaltySummary?.penaltyBreakdownByAlertType ? Object.keys(penaltySummary.penaltyBreakdownByAlertType).length : 0}
          sub="Configured violation types" color="#059669" />
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
          <Card className="border-0 shadow-sm mb-3" style={{ borderRadius: 14 }}>
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

          {/* Progressive Penalties Ledger & Breakdown */}
          <Card className="border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <Card.Header className="bg-white border-0 py-3 px-4 d-flex justify-content-between align-items-center">
              <h5 className="mb-0 fw-bold" style={{ fontSize: 15 }}>
                <Scale size={16} className="me-2 text-danger" />
                Progressive Penalties & Recent Ledger
              </h5>
              <Button as={Link} to="/settings" variant="outline-danger" size="sm" style={{ borderRadius: 8 }}>
                Manage Rules
              </Button>
            </Card.Header>
            <Card.Body className="p-4 pt-1">
              {/* Alert Type Breakdown Chips */}
              <div className="mb-3">
                <div style={{ fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 6 }}>
                  Penalties by Alert Type
                </div>
                <div className="d-flex flex-wrap gap-2">
                  {penaltySummary?.penaltyBreakdownByAlertType && Object.entries(penaltySummary.penaltyBreakdownByAlertType).map(([type, total]) => (
                    <Badge key={type} bg="light" text="dark" className="border px-2 py-1 d-flex align-items-center gap-1" style={{ fontSize: 12 }}>
                      <span className="fw-semibold">{type}:</span>
                      <span className="text-danger fw-bold">{formatINR(total)}</span>
                      {penaltySummary.alertCountByAlertType?.[type] != null && (
                        <span className="text-muted ms-1">({penaltySummary.alertCountByAlertType[type]} events)</span>
                      )}
                    </Badge>
                  ))}
                  {(!penaltySummary?.penaltyBreakdownByAlertType || Object.keys(penaltySummary.penaltyBreakdownByAlertType).length === 0) && (
                    <span className="text-muted" style={{ fontSize: 12 }}>No penalty-bearing alerts recorded yet.</span>
                  )}
                </div>
              </div>

              {/* Recent Penalties Table */}
              <div className="table-responsive">
                <Table hover className="mb-0 align-middle" style={{ fontSize: 13 }}>
                  <thead style={{ background: '#f8fafc', fontSize: 12 }}>
                    <tr>
                      <th className="py-2 border-0">Driver</th>
                      <th className="py-2 border-0">Alert Type</th>
                      <th className="py-2 border-0">Occur #</th>
                      <th className="py-2 border-0">Classification</th>
                      <th className="py-2 border-0">Fine</th>
                      <th className="py-2 border-0">Applied Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(penaltySummary?.recentPenalties || []).slice(0, 5).map((p, i) => (
                      <tr key={p.id || i}>
                        <td className="fw-semibold">{p.driverName || '—'} <span className="text-muted" style={{ fontSize: 11 }}>({p.driverCode})</span></td>
                        <td>{p.alertType || '—'}</td>
                        <td>
                          <Badge bg="secondary" style={{ fontSize: 11 }}>#{p.occurrenceNumber || 1}</Badge>
                        </td>
                        <td>
                          <Badge bg={p.classification === 'PENALTY' ? 'danger' : 'warning'}
                            text={p.classification === 'PENALTY' ? 'white' : 'dark'} style={{ fontSize: 11 }}>
                            {p.classification || 'WARNING'}
                          </Badge>
                        </td>
                        <td className="fw-bold" style={{ color: p.classification === 'PENALTY' ? '#dc2626' : '#64748b' }}>
                          {formatINR(p.monetaryAmount)}
                        </td>
                        <td style={{ fontSize: 12 }}>{formatTime(p.dateApplied)}</td>
                      </tr>
                    ))}
                    {(!penaltySummary?.recentPenalties || penaltySummary.recentPenalties.length === 0) && (
                      <tr>
                        <td colSpan={6} className="text-center text-muted py-3">No recent penalties recorded</td>
                      </tr>
                    )}
                  </tbody>
                </Table>
              </div>
            </Card.Body>
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
                        {a.occurrenceNumber != null && (
                          <div className="mt-1 d-flex align-items-center gap-1" style={{ fontSize: 10 }}>
                            <Badge bg={a.penaltyClassification === 'PENALTY' ? 'danger' : 'warning'}
                              text={a.penaltyClassification === 'PENALTY' ? 'white' : 'dark'} style={{ fontSize: 9 }}>
                              #{a.occurrenceNumber} {a.penaltyClassification || 'Warning'}
                            </Badge>
                            <span className="fw-semibold" style={{ color: a.penaltyClassification === 'PENALTY' ? '#dc2626' : '#64748b' }}>
                              {formatINR(a.penaltyAmount)}
                            </span>
                          </div>
                        )}
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
