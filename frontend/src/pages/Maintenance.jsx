import React, { useState, useEffect } from 'react';
import { Wrench, PlusCircle, Search, Calendar, Car, AlertCircle } from 'lucide-react';
import api from '../api/axios';

const formatDate = (d) => d ? new Date(d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
const statusColor = { SCHEDULED: '#f59e0b', IN_PROGRESS: '#2563eb', COMPLETED: '#16a34a', OVERDUE: '#dc2626' };

export default function Maintenance() {
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    api.get('/api/maintenance?size=100').then(r => {
      const data = r.data?.data;
      setRecords(Array.isArray(data) ? data : (data?.content || []));
    }).catch(() => setError('Backend not reachable or no maintenance records yet.')).finally(() => setLoading(false));
  }, []);

  const filtered = records.filter(r =>
    !search || [r.vehicleCode, r.vehicleRegistrationNumber, r.maintenanceType, r.description]
      .some(v => v?.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="container-fluid py-4">
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <Wrench size={22} className="me-2 text-warning" />Maintenance
          </h1>
          <p className="text-muted mb-0">{records.length} maintenance records</p>
        </div>
      </div>

      {error && <div className="alert alert-info"><AlertCircle size={16} className="me-2" />{error}</div>}

      <div className="card border-0 shadow-sm mb-3" style={{ borderRadius: 12 }}>
        <div className="card-body py-2">
          <div className="input-group input-group-sm">
            <span className="input-group-text bg-white border-end-0"><Search size={14} className="text-muted" /></span>
            <input className="form-control border-start-0" placeholder="Search by vehicle, type, description..."
              value={search} onChange={e => setSearch(e.target.value)} />
          </div>
        </div>
      </div>

      {loading ? (
        <div className="text-center py-5"><div className="spinner-border text-warning" /></div>
      ) : (
        <div className="row g-3">
          {filtered.map((r, i) => (
            <div key={r.id || i} className="col-md-6 col-xl-4">
              <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 12, borderLeft: `4px solid ${statusColor[r.status] || '#94a3b8'}` }}>
                <div className="card-body p-3">
                  <div className="d-flex justify-content-between align-items-start mb-2">
                    <span className="fw-bold" style={{ fontSize: 14 }}>{r.maintenanceType || 'Maintenance'}</span>
                    <span className="badge" style={{ background: statusColor[r.status] || '#94a3b8', color: '#fff', fontSize: 10 }}>
                      {r.status || 'UNKNOWN'}
                    </span>
                  </div>
                  <div style={{ fontSize: 12, color: '#475569' }}>
                    <div className="mb-1"><Car size={12} className="me-1" />{r.vehicleRegistrationNumber || r.vehicleCode || '—'}</div>
                    <div className="mb-1"><Calendar size={12} className="me-1" />{formatDate(r.scheduledDate)}</div>
                    {r.description && <div className="text-muted mt-1" style={{ fontSize: 11 }}>{r.description}</div>}
                    {r.costEstimate && <div className="mt-1 text-muted">Estimated: ₹{parseFloat(r.costEstimate).toLocaleString('en-IN')}</div>}
                  </div>
                </div>
              </div>
            </div>
          ))}
          {filtered.length === 0 && !loading && (
            <div className="col-12 text-center py-5 text-muted">
              <Wrench size={40} className="mb-2 opacity-25" />
              <p>{search ? `No records matching "${search}"` : 'No maintenance records found. Records will appear here when vehicles are logged for service.'}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
