import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Users, Search, Shield, TrendingUp, Phone, Award, AlertTriangle, RefreshCw } from 'lucide-react';
import { getDrivers, getDriverRanking } from '../api/drivers';

export default function Drivers() {
  const [searchParams] = useSearchParams();
  const [drivers, setDrivers] = useState([]);
  const [ranking, setRanking] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [error, setError] = useState(null);
  const [tab, setTab] = useState(() => searchParams.get('tab') === 'ranking' ? 'ranking' : 'list');

  useEffect(() => {
    setLoading(true);
    Promise.all([getDrivers(), getDriverRanking()])
      .then(([drvs, rank]) => {
        setDrivers(Array.isArray(drvs) ? drvs : (drvs?.content || []));
        setRanking(Array.isArray(rank) ? rank : []);
      })
      .catch(e => setError('Failed to load driver data. Ensure backend is running on port 8080.'))
      .finally(() => setLoading(false));
  }, []);

  const filtered = drivers.filter(d =>
    !search || d.name?.toLowerCase().includes(search.toLowerCase()) ||
    d.code?.toLowerCase().includes(search.toLowerCase()) ||
    d.phone?.includes(search)
  );

  const scoreColor = (score) => {
    if (score == null) return '#94a3b8';
    if (score >= 80) return '#16a34a';
    if (score >= 60) return '#d97706';
    return '#dc2626';
  };

  const scoreBg = (score) => {
    if (score == null) return '#f1f5f9';
    if (score >= 80) return '#dcfce7';
    if (score >= 60) return '#fef9c3';
    return '#fee2e2';
  };

  if (loading) return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}>
      <div className="spinner-border text-primary" role="status" />
    </div>
  );

  return (
    <div className="container-fluid py-4">
      {/* Header */}
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <Users size={24} className="me-2 text-primary" />
            Drivers
          </h1>
          <p className="text-muted mb-0">{drivers.length} registered drivers</p>
        </div>
        <div className="d-flex gap-2">
          <button className={`btn ${tab === 'list' ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => setTab('list')}>
            <Users size={16} className="me-1" /> All Drivers
          </button>
          <button className={`btn ${tab === 'ranking' ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => setTab('ranking')}>
            <Award size={16} className="me-1" /> Safety Ranking
          </button>
        </div>
      </div>

      {error && <div className="alert alert-warning">{error}</div>}

      {tab === 'list' && (
        <>
          {/* Search */}
          <div className="card border-0 shadow-sm mb-4">
            <div className="card-body py-2">
              <div className="input-group">
                <span className="input-group-text bg-white border-end-0">
                  <Search size={16} className="text-muted" />
                </span>
                <input type="text" className="form-control border-start-0"
                  placeholder="Search by name, code, or phone..."
                  value={search} onChange={e => setSearch(e.target.value)} />
              </div>
            </div>
          </div>

          {/* Driver Cards */}
          <div className="row g-3">
            {filtered.map(driver => (
              <div key={driver.id} className="col-12 col-md-6 col-xl-4">
                <div className="card border-0 shadow-sm h-100"
                  style={{ borderRadius: 12, transition: 'transform 0.2s, box-shadow 0.2s' }}
                  onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.12)'; }}
                  onMouseLeave={e => { e.currentTarget.style.transform = 'none'; e.currentTarget.style.boxShadow = ''; }}>
                  <div className="card-body p-3">
                    <div className="d-flex align-items-center gap-3 mb-3">
                      <div style={{
                        width: 44, height: 44, borderRadius: '50%',
                        background: 'linear-gradient(135deg, #2563eb, #1d4ed8)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        color: '#fff', fontWeight: 700, fontSize: 16, flexShrink: 0
                      }}>
                        {driver.name?.charAt(0) || '?'}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div className="fw-bold text-truncate" style={{ fontSize: 15 }}>{driver.name}</div>
                        <div className="text-muted" style={{ fontSize: 12 }}>{driver.code}</div>
                      </div>
                      {driver.safetyScore != null && (
                        <div style={{
                          background: scoreBg(driver.safetyScore),
                          color: scoreColor(driver.safetyScore),
                          borderRadius: 8, padding: '4px 10px', fontSize: 13, fontWeight: 700
                        }}>
                          {driver.safetyScore?.toFixed ? driver.safetyScore.toFixed(1) : driver.safetyScore}
                        </div>
                      )}
                    </div>
                    <div className="row g-2 text-center" style={{ fontSize: 12 }}>
                      <div className="col-6">
                        <div className="text-muted">Phone</div>
                        <div className="fw-semibold d-flex align-items-center justify-content-center gap-1">
                          <Phone size={11} />
                          {driver.phone || 'N/A'}
                        </div>
                      </div>
                      <div className="col-6">
                        <div className="text-muted">License</div>
                        <div className="fw-semibold">{driver.licenseNumber || 'N/A'}</div>
                      </div>
                      <div className="col-6">
                        <div className="text-muted">Joined</div>
                        <div className="fw-semibold">{driver.joinedOn || 'N/A'}</div>
                      </div>
                      <div className="col-6">
                        <div className="text-muted">Status</div>
                        <span className={`badge ${driver.active ? 'bg-success' : 'bg-secondary'}`}>
                          {driver.active ? 'Active' : 'Inactive'}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ))}
            {filtered.length === 0 && (
              <div className="col-12 text-center py-5 text-muted">
                <Users size={40} className="mb-2 opacity-25" />
                <p>No drivers found{search ? ` matching "${search}"` : ''}.</p>
              </div>
            )}
          </div>
        </>
      )}

      {tab === 'ranking' && (
        <>
          <div className="alert alert-info d-flex align-items-center gap-2 mb-3" style={{ fontSize: 13 }}>
            <Shield size={16} />
            <span>Safety scores are calculated using the formula: <strong>100 - (Overspeed×5) - (Harsh Braking×4) - (Harsh Acceleration×4) - (Night Driving hrs×3) - (Fatigue×8) + (Clean Trips×1)</strong>. Higher is better.</span>
          </div>
          <div className="card border-0 shadow-sm">
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead style={{ background: '#f8fafc' }}>
                  <tr>
                    <th style={{ width: 80, minWidth: 80, whiteSpace: 'nowrap' }}>Rank</th>
                    <th style={{ minWidth: 160 }}>Driver</th>
                    <th style={{ minWidth: 140, whiteSpace: 'nowrap' }}>Safety Score</th>
                    <th style={{ minWidth: 80, whiteSpace: 'nowrap' }}>Trips</th>
                    <th style={{ minWidth: 110, whiteSpace: 'nowrap' }}>Distance</th>
                    <th style={{ minWidth: 100, whiteSpace: 'nowrap' }}>Overspeed</th>
                    <th style={{ minWidth: 120, whiteSpace: 'nowrap' }}>Harsh Braking</th>
                    <th style={{ minWidth: 80, whiteSpace: 'nowrap' }}>Alerts</th>
                  </tr>
                </thead>
                <tbody>
                  {ranking.map((r, i) => (
                    <tr key={r.driverId}>
                      <td style={{ width: 80, minWidth: 80, whiteSpace: 'nowrap' }}>
                        <span style={{
                          fontWeight: 700, fontSize: 14,
                          color: i === 0 ? '#d97706' : i === 1 ? '#94a3b8' : i === 2 ? '#b45309' : '#64748b'
                        }}>
                          #{r.rank || i + 1}
                        </span>
                      </td>
                      <td style={{ minWidth: 160 }}>
                        <div className="fw-semibold">{r.driverName}</div>
                        <div className="text-muted" style={{ fontSize: 11 }}>{r.driverCode}</div>
                      </td>
                      <td style={{ minWidth: 140, whiteSpace: 'nowrap' }}>
                        <div className="d-flex align-items-center gap-2">
                          <div style={{ flex: 1, height: 6, background: '#f1f5f9', borderRadius: 3, maxWidth: 80 }}>
                            <div style={{
                              width: `${r.safetyScore || 0}%`, height: '100%', borderRadius: 3,
                              background: scoreColor(r.safetyScore)
                            }} />
                          </div>
                          <span style={{ color: scoreColor(r.safetyScore), fontWeight: 700, fontSize: 13 }}>
                            {r.safetyScore?.toFixed ? r.safetyScore.toFixed(1) : r.safetyScore || 0}
                          </span>
                        </div>
                      </td>
                      <td style={{ minWidth: 80, whiteSpace: 'nowrap' }}>{r.tripCount || 0}</td>
                      <td style={{ minWidth: 110, whiteSpace: 'nowrap' }}>{r.totalDistanceKm?.toFixed ? r.totalDistanceKm.toFixed(0) : r.totalDistanceKm || 0} km</td>
                      <td style={{ minWidth: 100, whiteSpace: 'nowrap' }}>
                        <span className={`badge ${r.overspeedEvents > 0 ? 'bg-danger' : 'bg-success'}`}>
                          {r.overspeedEvents || 0}
                        </span>
                      </td>
                      <td style={{ minWidth: 120, whiteSpace: 'nowrap' }}>
                        <span className={`badge ${r.harshBrakingEvents > 0 ? 'bg-warning text-dark' : 'bg-success'}`}>
                          {r.harshBrakingEvents || 0}
                        </span>
                      </td>
                      <td style={{ minWidth: 80, whiteSpace: 'nowrap' }}>{r.totalAlerts || 0}</td>
                    </tr>
                  ))}
                  {ranking.length === 0 && (
                    <tr>
                      <td colSpan={8} className="text-center py-4 text-muted">
                        No ranking data available. Ensure driver_daily_stats table has data.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
