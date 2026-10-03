import React, { useState, useEffect } from 'react';
import { DollarSign, Search, TrendingUp, TrendingDown, Calendar, Car, AlertCircle } from 'lucide-react';
import api from '../api/axios';

const formatDate = (d) => d ? new Date(d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
const typeColor = { FUEL: '#2563eb', MAINTENANCE: '#f59e0b', TOLL: '#7c3aed', INSURANCE: '#0891b2', OTHER: '#64748b' };

export default function Costs() {
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    api.get('/api/costs?size=200').then(r => {
      const data = r.data?.data;
      setRecords(Array.isArray(data) ? data : (data?.content || []));
    }).catch(() => setError('Backend not reachable or no cost records yet.')).finally(() => setLoading(false));
  }, []);

  const filtered = records.filter(r => {
    if (search && ![ r.vehicleCode, r.vehicleRegistrationNumber, r.description, r.costType ]
      .some(v => v?.toLowerCase().includes(search.toLowerCase()))) return false;
    if (typeFilter && r.costType !== typeFilter) return false;
    return true;
  });

  const total = filtered.reduce((sum, r) => sum + (parseFloat(r.amount) || 0), 0);

  const byType = records.reduce((acc, r) => {
    acc[r.costType] = (acc[r.costType] || 0) + (parseFloat(r.amount) || 0);
    return acc;
  }, {});

  return (
    <div className="container-fluid py-4">
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <DollarSign size={22} className="me-2 text-success" />Cost Records
          </h1>
          <p className="text-muted mb-0">{records.length} cost entries · Total: ₹{total.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</p>
        </div>
      </div>

      {error && <div className="alert alert-info"><AlertCircle size={16} className="me-2" />{error}</div>}

      {/* Summary cards */}
      <div className="row g-3 mb-4">
        {Object.entries(byType).slice(0, 4).map(([type, amt]) => (
          <div key={type} className="col-6 col-lg-3">
            <div className="card border-0 shadow-sm text-center py-3" style={{ borderRadius: 12 }}>
              <div style={{ fontSize: 20, fontWeight: 800, color: typeColor[type] || '#64748b' }}>
                ₹{amt.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
              </div>
              <div style={{ fontSize: 11, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{type}</div>
            </div>
          </div>
        ))}
      </div>

      <div className="card border-0 shadow-sm mb-3" style={{ borderRadius: 12 }}>
        <div className="card-body py-2">
          <div className="row g-2">
            <div className="col-md-6">
              <div className="input-group input-group-sm">
                <span className="input-group-text bg-white border-end-0"><Search size={14} className="text-muted" /></span>
                <input className="form-control border-start-0" placeholder="Search..."
                  value={search} onChange={e => setSearch(e.target.value)} />
              </div>
            </div>
            <div className="col-auto">
              <select className="form-select form-select-sm" style={{ width: 140 }}
                value={typeFilter} onChange={e => setTypeFilter(e.target.value)}>
                <option value="">All Types</option>
                {Object.keys(typeColor).map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="text-center py-5"><div className="spinner-border text-success" /></div>
      ) : (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 12 }}>
          <div className="table-responsive">
            <table className="table table-hover mb-0 align-middle" style={{ fontSize: 13 }}>
              <thead style={{ background: '#f8fafc', fontSize: 12 }}>
                <tr>
                  <th className="px-4 py-3 border-0">Type</th>
                  <th className="border-0">Vehicle</th>
                  <th className="border-0">Date</th>
                  <th className="border-0">Description</th>
                  <th className="border-0 text-end pe-4">Amount</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((r, i) => (
                  <tr key={r.id || i}>
                    <td className="px-4">
                      <span className="badge" style={{ background: typeColor[r.costType] || '#64748b', color: '#fff', fontSize: 10 }}>
                        {r.costType}
                      </span>
                    </td>
                    <td>{r.vehicleRegistrationNumber || r.vehicleCode || '—'}</td>
                    <td style={{ color: '#64748b' }}>{formatDate(r.date || r.incurredAt)}</td>
                    <td className="text-truncate" style={{ maxWidth: 200 }}>{r.description || '—'}</td>
                    <td className="text-end pe-4 fw-semibold" style={{ color: '#16a34a' }}>
                      ₹{parseFloat(r.amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 && (
                  <tr><td colSpan={5} className="text-center text-muted py-4">No cost records found.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
