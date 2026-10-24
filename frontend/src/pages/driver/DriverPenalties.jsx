import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Table, Badge, Button, Spinner, Alert } from 'react-bootstrap';
import { 
  Scale, AlertTriangle, ShieldAlert, CheckCircle2, 
  Calendar, RefreshCw, IndianRupee, Truck, FileText
} from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

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

export default function DriverPenalties() {
  const [penalties, setPenalties] = useState([]);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchPenaltiesData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [list, sum] = await Promise.all([
        driverPortalApi.getPenalties(),
        driverPortalApi.getPenaltySummary().catch(() => null)
      ]);
      setPenalties(list || []);
      setSummary(sum);
    } catch (err) {
      console.error('Failed to load penalties data:', err);
      setError('Unable to fetch penalty history. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPenaltiesData();
  }, []);

  const totalAmount = summary?.totalMonetaryAmount !== undefined 
    ? summary.totalMonetaryAmount 
    : penalties.reduce((acc, p) => acc + (Number(p.monetaryAmount) || 0), 0);

  const activeCount = summary?.activeCount ?? penalties.filter(p => p.status === 'ACTIVE').length;
  const waivedCount = summary?.waivedCount ?? penalties.filter(p => p.status === 'WAIVED' || p.status === 'REVOKED').length;

  return (
    <div style={{ fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif" }}>
      {/* Page Header */}
      <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
        <div>
          <h1 className="h3 mb-1 fw-bold" style={{ color: '#0f172a' }}>My Penalties</h1>
          <p className="text-muted mb-0" style={{ fontSize: 13 }}>
            Progressive infraction deductions, fine records, and safety disciplinary status.
          </p>
        </div>

        <Button
          variant="outline-secondary"
          size="sm"
          onClick={fetchPenaltiesData}
          disabled={loading}
          className="d-flex align-items-center gap-1.5"
          style={{ borderRadius: 8, fontSize: 12 }}
        >
          <RefreshCw size={13} className={loading ? 'spin' : ''} />
          <span>Refresh</span>
        </Button>
      </div>

      {error && (
        <Alert variant="danger" className="mb-4 rounded-3 d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center gap-2">
            <AlertTriangle size={18} />
            <span>{error}</span>
          </div>
          <Button variant="outline-danger" size="sm" onClick={fetchPenaltiesData}>
            Retry
          </Button>
        </Alert>
      )}

      {/* Summary KPI Cards */}
      <Row className="g-3 mb-4">
        {/* Total Penalties Amount */}
        <Col xs={12} sm={6} lg={3}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Total Fines Assessed
                </span>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#fef2f2', color: '#dc2626', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <IndianRupee size={18} />
                </div>
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#dc2626', lineHeight: 1.2 }}>
                ₹{Number(totalAmount).toLocaleString('en-IN', { minimumFractionDigits: 0 })}
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Cumulative financial liability
              </div>
            </div>
          </div>
        </Col>

        {/* Total Infractions */}
        <Col xs={12} sm={6} lg={3}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Total Infractions
                </span>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#fffbeb', color: '#d97706', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <AlertTriangle size={18} />
                </div>
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#0f172a', lineHeight: 1.2 }}>
                {penalties.length}
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Recorded violation incidents
              </div>
            </div>
          </div>
        </Col>

        {/* Active Records */}
        <Col xs={12} sm={6} lg={3}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Active Records
                </span>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#fff7ed', color: '#ea580c', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <ShieldAlert size={18} />
                </div>
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#ea580c', lineHeight: 1.2 }}>
                {activeCount}
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Pending or active deductions
              </div>
            </div>
          </div>
        </Col>

        {/* Waived or Resolved */}
        <Col xs={12} sm={6} lg={3}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-3 d-flex flex-column justify-content-between">
              <div className="d-flex align-items-center justify-content-between mb-2">
                <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Waived / Resolved
                </span>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#ecfdf5', color: '#059669', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <CheckCircle2 size={18} />
                </div>
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#059669', lineHeight: 1.2 }}>
                {waivedCount}
              </div>
              <div className="mt-2 text-muted" style={{ fontSize: 11 }}>
                Appealed or excused penalties
              </div>
            </div>
          </div>
        </Col>
      </Row>

      {/* Penalties List Table Card */}
      <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
        <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center gap-2">
            <Scale size={18} color="#dc2626" />
            <span className="fw-bold text-dark" style={{ fontSize: 15 }}>Progressive Penalty Records</span>
          </div>
          <span className="badge bg-light text-secondary border" style={{ fontSize: 11 }}>
            {penalties.length} Total Records
          </span>
        </div>

        <div className="card-body p-0">
          {loading ? (
            <div className="py-5 text-center text-muted">
              <Spinner animation="border" variant="success" size="sm" className="mb-2" />
              <div style={{ fontSize: 13 }}>Loading penalty history…</div>
            </div>
          ) : penalties.length === 0 ? (
            <div className="py-5 text-center">
              <CheckCircle2 size={36} className="text-success mb-2 opacity-75" />
              <h5 className="fw-bold text-dark mt-1">Clean Record — No Penalties</h5>
              <p className="text-muted small mb-0">You currently have no progressive penalties or fines on record.</p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0" style={{ fontSize: 13 }}>
                <thead className="table-light">
                  <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                    <th className="ps-4">Violation Details</th>
                    <th>Occurrence</th>
                    <th>Vehicle</th>
                    <th>Date Applied</th>
                    <th>Fine Amount</th>
                    <th className="pe-4">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {penalties.map((penalty, idx) => (
                    <tr key={penalty.id || idx}>
                      {/* Violation Details */}
                      <td className="ps-4">
                        <strong className="text-dark">
                          {penalty.alertType?.replace(/_/g, ' ') || penalty.penaltyType || 'Violation'}
                        </strong>
                        <div className="text-muted" style={{ fontSize: 11, maxWidth: 360 }}>
                          {penalty.reason || penalty.notes || `Progressive penalty record #${penalty.occurrenceNumber || 1}`}
                        </div>
                      </td>

                      {/* Occurrence */}
                      <td>
                        <span
                          className="badge"
                          style={{
                            backgroundColor: '#f1f5f9',
                            color: '#334155',
                            border: '1px solid #e2e8f0',
                            fontSize: 11,
                            fontWeight: 700,
                          }}
                        >
                          {penalty.occurrenceNumber ? `Occurrence #${penalty.occurrenceNumber}` : '1st Offense'}
                        </span>
                      </td>

                      {/* Vehicle */}
                      <td>
                        <div className="d-flex align-items-center gap-1.5">
                          <Truck size={14} className="text-muted" />
                          <span className="font-monospace text-dark" style={{ fontSize: 12 }}>
                            {penalty.vehicleRegistration || penalty.vehicleCode || 'Assigned Unit'}
                          </span>
                        </div>
                      </td>

                      {/* Date Applied */}
                      <td className="text-muted" style={{ fontSize: 12 }}>
                        {formatDateTime(penalty.dateApplied || penalty.calculationTimestamp || penalty.createdAt)}
                      </td>

                      {/* Fine Amount */}
                      <td>
                        <span
                          className="badge"
                          style={{
                            backgroundColor: '#fef2f2',
                            color: '#dc2626',
                            border: '1px solid #fee2e2',
                            fontFamily: 'monospace',
                            fontSize: 12,
                            fontWeight: 700,
                            padding: '4px 8px',
                          }}
                        >
                          ₹{Number(penalty.monetaryAmount || 0).toLocaleString('en-IN')}
                        </span>
                      </td>

                      {/* Status */}
                      <td className="pe-4">
                        <span
                          className="badge"
                          style={{
                            backgroundColor:
                              penalty.status === 'WAIVED' || penalty.status === 'REVOKED'
                                ? '#ecfdf5'
                                : penalty.status === 'ACTIVE'
                                ? '#fef2f2'
                                : '#fffbeb',
                            color:
                              penalty.status === 'WAIVED' || penalty.status === 'REVOKED'
                                ? '#059669'
                                : penalty.status === 'ACTIVE'
                                ? '#dc2626'
                                : '#d97706',
                            border: `1px solid ${
                              penalty.status === 'WAIVED' || penalty.status === 'REVOKED'
                                ? '#a7f3d0'
                                : penalty.status === 'ACTIVE'
                                ? '#fee2e2'
                                : '#fef3c7'
                            }`,
                            fontSize: 10,
                            fontWeight: 700,
                            textTransform: 'uppercase',
                          }}
                        >
                          {penalty.status || 'ACTIVE'}
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
    </div>
  );
}
