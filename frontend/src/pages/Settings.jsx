import React, { useState, useEffect } from 'react';
import {
  Settings as SettingsIcon, Database, Server, Mail, Smartphone, Globe,
  CheckCircle, XCircle, RefreshCw, Scale, ShieldAlert, Plus, Trash2,
  AlertTriangle, DollarSign, Edit2, RotateCcw, Check, User
} from 'lucide-react';
import api from '../api/axios';
import { getDrivers } from '../api/drivers';
import {
  getPenaltyConfigs,
  updatePenaltyConfig,
  getAllPenalties,
  recordPenalty,
  revokePenalty
} from '../api/penalties';

const StatusDot = ({ ok }) => (
  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12 }}>
    {ok ? <CheckCircle size={14} color="#16a34a" /> : <XCircle size={14} color="#dc2626" />}
    <span style={{ color: ok ? '#16a34a' : '#dc2626', fontWeight: 600 }}>{ok ? 'Connected' : 'Unavailable'}</span>
  </span>
);

export default function Settings() {
  const [activeTab, setActiveTab] = useState('penalties'); // 'health' | 'weights' | 'penalties'

  // Health
  const [health, setHealth] = useState(null);
  const [healthLoading, setHealthLoading] = useState(true);

  // Alert Configs
  const [configs, setConfigs] = useState([]);
  const [configLoading, setConfigLoading] = useState(false);
  const [editingConfigId, setEditingConfigId] = useState(null);
  const [editWeight, setEditWeight] = useState('');
  const [editFine, setEditFine] = useState('');
  const [configSuccess, setConfigSuccess] = useState(null);

  // Driver Penalties
  const [drivers, setDrivers] = useState([]);
  const [penalties, setPenalties] = useState([]);
  const [selectedDriverFilter, setSelectedDriverFilter] = useState('ALL');
  const [penaltyLoading, setPenaltyLoading] = useState(false);
  const [penaltySuccess, setPenaltySuccess] = useState(null);
  const [penaltyError, setPenaltyError] = useState(null);

  // Record Penalty Form
  const [showAddModal, setShowAddModal] = useState(false);
  const [formDriverId, setFormDriverId] = useState('');
  const [formPenaltyType, setFormPenaltyType] = useState('MANUAL_SAFETY_DEDUCTION');
  const [formReason, setFormReason] = useState('');
  const [formPoints, setFormPoints] = useState('5.0');
  const [formFine, setFormFine] = useState('0.0');
  const [formAlertId, setFormAlertId] = useState('');
  const [formTripId, setFormTripId] = useState('');
  const [formDeductScore, setFormDeductScore] = useState(true);
  const [formNotes, setFormNotes] = useState('');

  // Revoke Dialog
  const [revokeTargetId, setRevokeTargetId] = useState(null);
  const [revokeReason, setRevokeReason] = useState('');

  const checkHealth = () => {
    setHealthLoading(true);
    api.get('/api/health')
      .then(r => setHealth(r.data))
      .catch(() => setHealth({ status: 'DOWN' }))
      .finally(() => setHealthLoading(false));
  };

  const loadConfigs = () => {
    setConfigLoading(true);
    getPenaltyConfigs()
      .then(data => setConfigs(data))
      .catch(err => console.error(err))
      .finally(() => setConfigLoading(false));
  };

  const loadPenalties = () => {
    setPenaltyLoading(true);
    getAllPenalties()
      .then(data => setPenalties(data))
      .catch(err => console.error(err))
      .finally(() => setPenaltyLoading(false));
  };

  useEffect(() => {
    checkHealth();
    loadConfigs();
    loadPenalties();
    getDrivers().then(data => {
      const list = Array.isArray(data) ? data : (data?.content || []);
      setDrivers(list);
      if (list.length > 0) setFormDriverId(String(list[0].id));
    }).catch(console.error);
  }, []);

  const handleSaveConfig = async (id) => {
    try {
      setConfigSuccess(null);
      await updatePenaltyConfig(id, {
        weight: parseFloat(editWeight),
        defaultFineAmount: parseFloat(editFine)
      });
      setEditingConfigId(null);
      setConfigSuccess('Penalty weight successfully updated!');
      loadConfigs();
      setTimeout(() => setConfigSuccess(null), 3000);
    } catch (e) {
      alert(`Failed to update weight: ${e?.response?.data?.message || e.message}`);
    }
  };

  const handleRecordPenalty = async (e) => {
    e.preventDefault();
    setPenaltyError(null);
    if (!formReason.trim()) {
      setPenaltyError('A reason is mandatory when recording a penalty.');
      return;
    }
    try {
      await recordPenalty({
        driverId: Number(formDriverId),
        penaltyType: formPenaltyType,
        reason: formReason.trim(),
        penaltyPoints: parseFloat(formPoints) || 0,
        monetaryAmount: parseFloat(formFine) || 0,
        alertId: formAlertId ? Number(formAlertId) : null,
        tripId: formTripId ? Number(formTripId) : null,
        deductFromScore: formDeductScore,
        notes: formNotes
      });
      setShowAddModal(false);
      setFormReason('');
      setFormNotes('');
      setFormAlertId('');
      setFormTripId('');
      setPenaltySuccess('Penalty successfully recorded and applied to driver score.');
      loadPenalties();
      setTimeout(() => setPenaltySuccess(null), 3000);
    } catch (err) {
      const msg = err?.response?.data?.message || err.message;
      setPenaltyError(`Cannot record penalty: ${msg}`);
    }
  };

  const handleRevokePenalty = async () => {
    if (!revokeReason.trim()) {
      alert('A revocation reason is required for auditing.');
      return;
    }
    try {
      await revokePenalty(revokeTargetId, revokeReason.trim());
      setRevokeTargetId(null);
      setRevokeReason('');
      setPenaltySuccess('Penalty revoked successfully. Safety deduction reversed.');
      loadPenalties();
      setTimeout(() => setPenaltySuccess(null), 3000);
    } catch (err) {
      alert(`Cannot revoke penalty: ${err?.response?.data?.message || err.message}`);
    }
  };

  const filteredPenalties = penalties.filter(p =>
    selectedDriverFilter === 'ALL' || String(p.driverId) === String(selectedDriverFilter)
  );

  const envRows = [
    { label: 'Backend URL', value: import.meta.env.VITE_API_URL || 'http://localhost:8080', icon: <Server size={14} /> },
    { label: 'Database', value: 'PostgreSQL 17 + PostGIS', icon: <Database size={14} /> },
    { label: 'Flyway Migration', value: 'V7 (Penalty Management & Audit Logs applied)', icon: <Database size={14} /> },
    { label: 'AI Service', value: import.meta.env.VITE_AI_URL || 'http://localhost:8000', icon: <Globe size={14} /> },
    { label: 'Email / SMS', value: 'Configured via SMTP & MSG91 gateway', icon: <Mail size={14} /> },
  ];

  return (
    <div className="container-fluid py-4" style={{ maxWidth: 1200 }}>
      {/* Page Title */}
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <SettingsIcon size={22} className="me-2 text-primary" />System & Fleet Settings
          </h1>
          <p className="text-muted mb-0" style={{ fontSize: 13 }}>
            Configurable alert weights, driver penalty management, and platform health
          </p>
        </div>
      </div>

      {/* Tabs */}
      <ul className="nav nav-tabs mb-4" style={{ borderBottom: '1px solid #e2e8f0' }}>
        <li className="nav-item">
          <button className={`nav-link py-2 px-4 ${activeTab === 'penalties' ? 'active fw-bold text-primary' : 'text-muted'}`}
            onClick={() => setActiveTab('penalties')}>
            <ShieldAlert size={15} className="me-2" /> Driver Penalty Management
          </button>
        </li>
        <li className="nav-item">
          <button className={`nav-link py-2 px-4 ${activeTab === 'weights' ? 'active fw-bold text-primary' : 'text-muted'}`}
            onClick={() => setActiveTab('weights')}>
            <Scale size={15} className="me-2" /> Alert Weights Configuration
          </button>
        </li>
        <li className="nav-item">
          <button className={`nav-link py-2 px-4 ${activeTab === 'health' ? 'active fw-bold text-primary' : 'text-muted'}`}
            onClick={() => setActiveTab('health')}>
            <Server size={15} className="me-2" /> System Health
          </button>
        </li>
      </ul>

      {/* TAB 1: DRIVER PENALTY MANAGEMENT (SECTION 9) */}
      {activeTab === 'penalties' && (
        <div>
          {penaltySuccess && (
            <div className="alert alert-success py-2 mb-3 d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
              <CheckCircle size={15} /> {penaltySuccess}
            </div>
          )}

          <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center justify-content-between flex-wrap gap-2">
              <div>
                <h5 className="mb-0 fw-bold" style={{ fontSize: 15, color: '#0f172a' }}>
                  Driver Penalty History & Enforcement
                </h5>
                <div className="text-muted" style={{ fontSize: 12 }}>
                  Auditable record of manager-applied safety deductions and financial fines
                </div>
              </div>
              <div className="d-flex align-items-center gap-2">
                <select className="form-select form-select-sm" style={{ width: 190, fontSize: 12 }}
                  value={selectedDriverFilter} onChange={e => setSelectedDriverFilter(e.target.value)}>
                  <option value="ALL">All Drivers</option>
                  {drivers.map(d => (
                    <option key={d.id} value={d.id}>{d.name} ({d.code})</option>
                  ))}
                </select>
                <button className="btn btn-primary btn-sm d-flex align-items-center gap-1"
                  onClick={() => setShowAddModal(true)} style={{ borderRadius: 7 }}>
                  <Plus size={14} /> Record Penalty
                </button>
              </div>
            </div>

            <div className="card-body p-0">
              <div className="table-responsive">
                <table className="table table-hover mb-0" style={{ fontSize: 12 }}>
                  <thead style={{ background: '#f8fafc' }}>
                    <tr>
                      <th className="px-3">ID</th>
                      <th>Driver</th>
                      <th>Vehicle</th>
                      <th>Type</th>
                      <th>Reason</th>
                      <th>Deduction</th>
                      <th>Fine Amount</th>
                      <th>Applied Date</th>
                      <th>Applied By</th>
                      <th>Status</th>
                      <th className="text-end px-3">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredPenalties.map(p => (
                      <tr key={p.id}>
                        <td className="px-3 text-muted">#{p.id}</td>
                        <td className="fw-semibold">{p.driverName} <span className="text-muted">({p.driverCode})</span></td>
                        <td>{p.vehicleRegistration || '—'}</td>
                        <td><span className="badge bg-light text-dark">{p.penaltyType}</span></td>
                        <td style={{ maxWidth: 220 }}>
                          <div>{p.reason}</div>
                          {p.notes && <div className="text-muted" style={{ fontSize: 10 }}>Note: {p.notes}</div>}
                        </td>
                        <td className="fw-bold text-danger">
                          {p.deductFromScore ? `-${p.penaltyPoints} pts` : '—'}
                        </td>
                        <td>{p.monetaryAmount > 0 ? `₹${p.monetaryAmount}` : '—'}</td>
                        <td>{new Date(p.dateApplied).toLocaleDateString('en-IN', { dateStyle: 'medium' })}</td>
                        <td className="text-muted">{p.appliedBy}</td>
                        <td>
                          <span className={`badge ${p.status === 'ACTIVE' ? 'bg-success' : 'bg-secondary'}`}>
                            {p.status}
                          </span>
                        </td>
                        <td className="text-end px-3">
                          {p.status === 'ACTIVE' && (
                            <button className="btn btn-outline-danger btn-sm py-0 px-2"
                              onClick={() => { setRevokeTargetId(p.id); setRevokeReason(''); }}
                              style={{ fontSize: 11, borderRadius: 5 }}>
                              <RotateCcw size={11} className="me-1" /> Revoke
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                    {filteredPenalties.length === 0 && (
                      <tr>
                        <td colSpan={11} className="text-center py-4 text-muted">
                          No penalty records found. Click <strong>Record Penalty</strong> to apply one.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: ALERT WEIGHTS CONFIGURATION (SECTION 9A) */}
      {activeTab === 'weights' && (
        <div>
          {configSuccess && (
            <div className="alert alert-success py-2 mb-3 d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
              <CheckCircle size={15} /> {configSuccess}
            </div>
          )}

          <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4">
              <h5 className="mb-0 fw-bold" style={{ fontSize: 15, color: '#0f172a' }}>
                Configurable Alert Penalty Weights & Fine Templates
              </h5>
              <div className="text-muted" style={{ fontSize: 12 }}>
                Configure multiplier weights used in the standardized Safety Score formula: <code>Safety Score = 100 - Σ(Normalized Event Rate × Weight)</code>
              </div>
            </div>
            <div className="card-body p-0">
              <div className="table-responsive">
                <table className="table table-hover mb-0" style={{ fontSize: 13 }}>
                  <thead style={{ background: '#f8fafc' }}>
                    <tr>
                      <th className="px-4">Alert Type</th>
                      <th>Description</th>
                      <th>Safety Weight (Multiplier)</th>
                      <th>Default Fine Amount</th>
                      <th>Status</th>
                      <th className="text-end px-4">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {configs.map(c => {
                      const isEditing = editingConfigId === c.id;
                      return (
                        <tr key={c.id}>
                          <td className="px-4 fw-bold text-primary">{c.alertType}</td>
                          <td className="text-muted" style={{ fontSize: 12 }}>{c.description}</td>
                          <td>
                            {isEditing ? (
                              <input type="number" step="0.5" className="form-control form-control-sm" style={{ width: 100 }}
                                value={editWeight} onChange={e => setEditWeight(e.target.value)} />
                            ) : (
                              <span className="fw-bold">{c.weight}</span>
                            )}
                          </td>
                          <td>
                            {isEditing ? (
                              <input type="number" step="50" className="form-control form-control-sm" style={{ width: 120 }}
                                value={editFine} onChange={e => setEditFine(e.target.value)} />
                            ) : (
                              <span>₹{c.defaultFineAmount}</span>
                            )}
                          </td>
                          <td>
                            <span className="badge bg-success">Active</span>
                          </td>
                          <td className="text-end px-4">
                            {isEditing ? (
                              <div className="d-flex justify-content-end gap-1">
                                <button className="btn btn-success btn-sm py-0 px-2" onClick={() => handleSaveConfig(c.id)}>
                                  <Check size={12} className="me-1" /> Save
                                </button>
                                <button className="btn btn-secondary btn-sm py-0 px-2" onClick={() => setEditingConfigId(null)}>
                                  Cancel
                                </button>
                              </div>
                            ) : (
                              <button className="btn btn-outline-secondary btn-sm py-0 px-2"
                                onClick={() => {
                                  setEditingConfigId(c.id);
                                  setEditWeight(String(c.weight));
                                  setEditFine(String(c.defaultFineAmount));
                                }}>
                                <Edit2 size={12} className="me-1" /> Edit
                              </button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: SYSTEM HEALTH */}
      {activeTab === 'health' && (
        <div className="row g-4">
          <div className="col-lg-6">
            <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
              <div className="card-header bg-white border-0 py-3 px-4 d-flex justify-content-between align-items-center">
                <h5 className="mb-0 fw-bold" style={{ fontSize: 15 }}>Service Health</h5>
                <button className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1" onClick={checkHealth}>
                  <RefreshCw size={13} /> Refresh
                </button>
              </div>
              <div className="card-body px-4 pb-4">
                {healthLoading ? <div className="spinner-border spinner-border-sm text-primary" /> : (
                  <div className="d-flex flex-column gap-3">
                    <div className="d-flex justify-content-between align-items-center py-2" style={{ borderBottom: '1px solid #f1f5f9' }}>
                      <span className="d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
                        <Server size={14} className="text-primary" /> Spring Boot Backend (Java 17)
                      </span>
                      <StatusDot ok={health?.status === 'UP' || !!health} />
                    </div>
                    <div className="d-flex justify-content-between align-items-center py-2" style={{ borderBottom: '1px solid #f1f5f9' }}>
                      <span className="d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
                        <Database size={14} className="text-success" /> PostgreSQL 17 + PostGIS
                      </span>
                      <StatusDot ok={health?.status === 'UP' || !!health} />
                    </div>
                    <div className="d-flex justify-content-between align-items-center py-2">
                      <span className="d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
                        <Globe size={14} className="text-warning" /> Python AI Service
                      </span>
                      <StatusDot ok={false} />
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="col-lg-6">
            <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
              <div className="card-header bg-white border-0 py-3 px-4">
                <h5 className="mb-0 fw-bold" style={{ fontSize: 15 }}>Environment Configuration</h5>
              </div>
              <div className="card-body px-4 pb-4">
                <div className="d-flex flex-column gap-3">
                  {envRows.map((row, i) => (
                    <div key={i} className="d-flex justify-content-between align-items-start py-2"
                      style={{ borderBottom: i < envRows.length - 1 ? '1px solid #f1f5f9' : 'none' }}>
                      <span className="d-flex align-items-center gap-2 text-muted" style={{ fontSize: 13 }}>
                        {row.icon} {row.label}
                      </span>
                      <span className="text-end" style={{ fontSize: 12, fontFamily: 'monospace', color: '#475569' }}>
                        {row.value}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* RECORD PENALTY MODAL */}
      {showAddModal && (
        <div className="modal show d-block" style={{ background: 'rgba(15,23,42,0.6)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow-lg" style={{ borderRadius: 14 }}>
              <div className="modal-header border-0 pb-0">
                <h5 className="modal-title fw-bold" style={{ fontSize: 16 }}>
                  <ShieldAlert size={18} className="text-danger me-2" />
                  Record Driver Penalty
                </h5>
                <button type="button" className="btn-close" onClick={() => setShowAddModal(false)} />
              </div>
              <form onSubmit={handleRecordPenalty}>
                <div className="modal-body">
                  {penaltyError && (
                    <div className="alert alert-danger py-2 mb-3" style={{ fontSize: 12 }}>
                      <AlertTriangle size={13} className="me-1" /> {penaltyError}
                    </div>
                  )}

                  <div className="mb-3">
                    <label className="form-label fw-semibold" style={{ fontSize: 12 }}>Driver</label>
                    <select className="form-select form-select-sm" value={formDriverId} onChange={e => setFormDriverId(e.target.value)} required>
                      {drivers.map(d => (
                        <option key={d.id} value={d.id}>{d.name} ({d.code})</option>
                      ))}
                    </select>
                  </div>

                  <div className="row g-2 mb-3">
                    <div className="col-6">
                      <label className="form-label fw-semibold" style={{ fontSize: 12 }}>Penalty Type</label>
                      <select className="form-select form-select-sm" value={formPenaltyType} onChange={e => setFormPenaltyType(e.target.value)}>
                        <option value="MANUAL_SAFETY_DEDUCTION">Safety Score Deduction</option>
                        <option value="FINANCIAL_FINE">Financial Fine Only</option>
                        <option value="POLICY_VIOLATION">Policy Violation</option>
                        <option value="WARNING">Official Warning</option>
                      </select>
                    </div>
                    <div className="col-6">
                      <label className="form-label fw-semibold" style={{ fontSize: 12 }}>Score Deduction (Points)</label>
                      <input type="number" step="0.5" min="0" max="100" className="form-control form-control-sm"
                        value={formPoints} onChange={e => setFormPoints(e.target.value)} />
                    </div>
                  </div>

                  <div className="row g-2 mb-3">
                    <div className="col-6">
                      <label className="form-label fw-semibold" style={{ fontSize: 12 }}>Monetary Fine (₹)</label>
                      <input type="number" step="50" min="0" className="form-control form-control-sm"
                        value={formFine} onChange={e => setFormFine(e.target.value)} />
                    </div>
                    <div className="col-6">
                      <label className="form-label fw-semibold" style={{ fontSize: 12 }}>Related Alert ID (Optional)</label>
                      <input type="number" className="form-control form-control-sm" placeholder="e.g. 1024"
                        value={formAlertId} onChange={e => setFormAlertId(e.target.value)} />
                    </div>
                  </div>

                  <div className="form-check mb-3">
                    <input className="form-check-input" type="checkbox" id="deductCheck"
                      checked={formDeductScore} onChange={e => setFormDeductScore(e.target.checked)} />
                    <label className="form-check-label" htmlFor="deductCheck" style={{ fontSize: 12 }}>
                      Deduct points from Driver Safety Score (prevents duplicate deduction if already in formula)
                    </label>
                  </div>

                  <div className="mb-3">
                    <label className="form-label fw-semibold" style={{ fontSize: 12 }}>Reason (Mandatory for audit)</label>
                    <textarea className="form-control form-control-sm" rows="2" placeholder="State reason for penalty..."
                      value={formReason} onChange={e => setFormReason(e.target.value)} required />
                  </div>

                  <div className="mb-2">
                    <label className="form-label fw-semibold" style={{ fontSize: 12 }}>Notes (Optional)</label>
                    <input type="text" className="form-control form-control-sm" placeholder="Internal remarks..."
                      value={formNotes} onChange={e => setFormNotes(e.target.value)} />
                  </div>
                </div>

                <div className="modal-footer border-0 pt-0">
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowAddModal(false)}>Cancel</button>
                  <button type="submit" className="btn btn-danger btn-sm">Record & Enforce Penalty</button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* REVOKE CONFIRMATION DIALOG */}
      {revokeTargetId && (
        <div className="modal show d-block" style={{ background: 'rgba(15,23,42,0.6)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow-lg" style={{ borderRadius: 14 }}>
              <div className="modal-header border-0 pb-0">
                <h5 className="modal-title fw-bold text-danger" style={{ fontSize: 16 }}>
                  <AlertTriangle size={18} className="me-2" /> Revoke Penalty #{revokeTargetId}
                </h5>
                <button type="button" className="btn-close" onClick={() => setRevokeTargetId(null)} />
              </div>
              <div className="modal-body">
                <p style={{ fontSize: 13, color: '#475569' }}>
                  Are you sure you want to revoke this penalty? The points will be restored to the driver's safety score.
                </p>
                <div className="mb-3">
                  <label className="form-label fw-semibold" style={{ fontSize: 12 }}>Revocation Reason (Mandatory for audit trail)</label>
                  <input type="text" className="form-control form-control-sm" placeholder="e.g. Validated route obstacle or false positive alert"
                    value={revokeReason} onChange={e => setRevokeReason(e.target.value)} required />
                </div>
              </div>
              <div className="modal-footer border-0 pt-0">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setRevokeTargetId(null)}>Cancel</button>
                <button type="button" className="btn btn-danger btn-sm" onClick={handleRevokePenalty}>Confirm Revocation</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
