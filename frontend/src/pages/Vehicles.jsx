import React, { useState } from 'react';
import { Badge } from 'react-bootstrap';
import { Car, MapPin, Gauge, User, Search, RefreshCw } from 'lucide-react';

// Spring Boot VehicleDTO field accessors
const getReg = (v) => v.registrationNumber || v.registration_number || v.number || v.code || '—';
const getCode = (v) => v.code || v.vehicle_id || '—';
const getDriver = (v) => v.driverName || v.driver?.name || v.driver_name || 'Unassigned';
const getDriverCode = (v) => v.driverCode || v.driver?.code || '';
const getSpeed = (v) => v.lastSpeedKmph ?? v.lastSpeed ?? v.speed ?? null;
const getLocation = (v) => v.lastRoadName || v.lastLocation || v.location || '—';
const getLat = (v) => v.lastLatitude ?? v.latitude ?? null;
const getLng = (v) => v.lastLongitude ?? v.longitude ?? null;
const isActive = (v) => v.active === true || v.ignitionOn === true || v.ignition === true;
const getTotalTrips = (v) => v.totalTrips ?? v.total_trips ?? null;
const getMaxSpeed = (v) => v.maxSpeedRecorded ?? v.maxSpeed ?? null;

const SpeedBar = ({ speed, max = 120 }) => {
  if (speed == null) return <span className="text-muted">—</span>;
  const pct = Math.min(100, (speed / max) * 100);
  const color = speed > 90 ? '#dc2626' : speed > 60 ? '#f59e0b' : '#16a34a';
  return (
    <div className="d-flex align-items-center gap-2">
      <div style={{ flex: 1, height: 6, background: '#f1f5f9', borderRadius: 3 }}>
        <div style={{ width: `${pct}%`, height: '100%', background: color, borderRadius: 3, transition: 'width 0.3s' }} />
      </div>
      <span style={{ fontSize: 12, fontWeight: 600, color, minWidth: 50 }}>{speed} km/h</span>
    </div>
  );
};

const Vehicles = ({ vehicles = [] }) => {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const filtered = vehicles.filter(v => {
    const q = search.toLowerCase();
    if (search && !(
      getReg(v).toLowerCase().includes(q) ||
      getDriver(v).toLowerCase().includes(q) ||
      getCode(v).toLowerCase().includes(q) ||
      getLocation(v).toLowerCase().includes(q)
    )) return false;
    if (statusFilter === 'active' && !isActive(v)) return false;
    if (statusFilter === 'inactive' && isActive(v)) return false;
    return true;
  });

  const activeCount = vehicles.filter(v => isActive(v)).length;

  return (
    <div className="container-fluid py-4">
      {/* Header */}
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <Car size={22} className="me-2 text-primary" />
            Fleet Vehicles
          </h1>
          <p className="text-muted mb-0">
            {vehicles.length} total · {activeCount} active · {vehicles.length - activeCount} inactive
          </p>
        </div>
      </div>

      {/* Stats row */}
      <div className="row g-3 mb-4">
        {[
          { label: 'Total', value: vehicles.length, color: '#2563eb' },
          { label: 'Active', value: activeCount, color: '#16a34a' },
          { label: 'Inactive', value: vehicles.length - activeCount, color: '#64748b' },
        ].map(s => (
          <div key={s.label} className="col-4">
            <div className="card border-0 shadow-sm text-center py-3" style={{ borderRadius: 12 }}>
              <div style={{ fontSize: 28, fontWeight: 800, color: s.color }}>{s.value}</div>
              <div style={{ fontSize: 12, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{s.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="card border-0 shadow-sm mb-3" style={{ borderRadius: 12 }}>
        <div className="card-body py-2">
          <div className="row g-2 align-items-center">
            <div className="col-md-5">
              <div className="input-group input-group-sm">
                <span className="input-group-text bg-white border-end-0">
                  <Search size={14} className="text-muted" />
                </span>
                <input type="text" className="form-control border-start-0"
                  placeholder="Search by registration, driver, location..."
                  value={search} onChange={e => setSearch(e.target.value)} />
              </div>
            </div>
            <div className="col-auto">
              <select className="form-select form-select-sm" style={{ width: 130 }}
                value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
                <option value="">All Status</option>
                <option value="active">Active</option>
                <option value="inactive">Inactive</option>
              </select>
            </div>
            <div className="col-auto ms-auto text-muted" style={{ fontSize: 12 }}>
              {filtered.length} shown
            </div>
          </div>
        </div>
      </div>

      {/* Vehicle Cards Grid */}
      <div className="row g-3">
        {filtered.map((v, i) => {
          const active = isActive(v);
          const speed = getSpeed(v);
          const lat = getLat(v);
          const lng = getLng(v);

          return (
            <div key={v.id || i} className="col-sm-6 col-xl-4">
              <div className="card border-0 shadow-sm h-100" style={{
                borderRadius: 14,
                borderLeft: `4px solid ${active ? '#16a34a' : '#94a3b8'}`,
              }}>
                <div className="card-body p-3">
                  {/* Header */}
                  <div className="d-flex justify-content-between align-items-start mb-3">
                    <div>
                      <div className="fw-bold" style={{ fontSize: 16, color: '#0f172a' }}>
                        {getReg(v)}
                      </div>
                      <div style={{ fontSize: 12, color: '#94a3b8' }}>{getCode(v)}</div>
                    </div>
                    <Badge bg={active ? 'success' : 'secondary'} style={{ fontSize: 11 }}>
                      {active ? '● Active' : '○ Inactive'}
                    </Badge>
                  </div>

                  {/* Driver */}
                  <div className="d-flex align-items-center gap-2 mb-2">
                    <User size={13} color="#2563eb" />
                    <span style={{ fontSize: 13 }}>
                      <span className="text-muted">Driver: </span>
                      <strong>{getDriver(v)}</strong>
                      {getDriverCode(v) && (
                        <span className="text-muted ms-1" style={{ fontSize: 11 }}>({getDriverCode(v)})</span>
                      )}
                    </span>
                  </div>

                  {/* Location */}
                  <div className="d-flex align-items-start gap-2 mb-2">
                    <MapPin size={13} color="#7c3aed" style={{ marginTop: 2, flexShrink: 0 }} />
                    <span className="text-truncate" style={{ fontSize: 12, color: '#475569', maxWidth: 200 }}>
                      {getLocation(v)}
                    </span>
                  </div>

                  {/* GPS Coords */}
                  {lat != null && lng != null && (
                    <div className="mb-2" style={{ fontSize: 11, color: '#94a3b8' }}>
                      <a href={`https://maps.google.com/?q=${lat},${lng}`}
                        target="_blank" rel="noreferrer"
                        style={{ color: '#2563eb', textDecoration: 'none' }}>
                        📍 {parseFloat(lat).toFixed(4)}, {parseFloat(lng).toFixed(4)} ↗
                      </a>
                    </div>
                  )}

                  {/* Speed bar */}
                  <div className="mt-2">
                    <div className="d-flex align-items-center gap-1 mb-1">
                      <Gauge size={12} color="#64748b" />
                      <span style={{ fontSize: 11, color: '#94a3b8' }}>Current Speed</span>
                    </div>
                    <SpeedBar speed={speed} />
                  </div>

                  {/* Footer stats */}
                  <div className="d-flex justify-content-between mt-3 pt-2"
                    style={{ borderTop: '1px solid #f1f5f9', fontSize: 11, color: '#64748b' }}>
                    {getTotalTrips(v) != null && (
                      <span>🚗 {getTotalTrips(v)} trips</span>
                    )}
                    {getMaxSpeed(v) != null && (
                      <span>⚡ Max {getMaxSpeed(v)} km/h</span>
                    )}
                    {v.id && <span className="text-muted">#{v.id}</span>}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {filtered.length === 0 && (
        <div className="text-center py-5">
          <Car size={48} className="mb-3 text-muted opacity-25" />
          <p className="text-muted">
            {search ? `No vehicles matching "${search}"` : 'No vehicles found'}
          </p>
        </div>
      )}
    </div>
  );
};

export default Vehicles;
