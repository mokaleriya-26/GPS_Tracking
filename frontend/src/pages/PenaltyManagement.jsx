import React, { useState, useEffect } from 'react';
import {
  Scale, ShieldAlert, Plus, Edit2, Check, RefreshCw, CheckCircle,
  AlertTriangle, DollarSign, Filter, RotateCcw, User, Info, FileText
} from 'lucide-react';
import { getDrivers } from '../api/drivers';
import {
  getPenaltyConfigs,
  updatePenaltyConfig,
  getAllPenalties,
  recordPenalty,
  revokePenalty
} from '../api/penalties';

export default function PenaltyManagement() {
  const [activeTab, setActiveTab] = useState('rules'); // 'rules' | 'history'

  // Alert Configs
  const [configs, setConfigs] = useState([]);
  const [configLoading, setConfigLoading] = useState(false);
  const [editingConfigId, setEditingConfigId] = useState(null);
  const [editWeight, setEditWeight] = useState('');
  const [editFine, setEditFine] = useState('');
  const [editIncrement, setEditIncrement] = useState('30.00');
  const [editThreshold, setEditThreshold] = useState('2');
  const [editEnabled, setEditEnabled] = useState(true);
  const [configSuccess, setConfigSuccess] = useState(null);

  // Driver Penalties
  const [drivers, setDrivers] = useState([]);
  const [penalties, setPenalties] = useState([]);
  const [selectedDriverFilter, setSelectedDriverFilter] = useState('ALL');
  const [penaltyLoading, setPenaltyLoading] = useState(false);
  const [penaltySuccess, setPenaltySuccess] = useState(null);
  const [penaltyError, setPenaltyError] = useState(null);

  // Record Penalty Form Modal
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
        weight: parseFloat(editWeight) || 0,
        defaultFineAmount: parseFloat(editFine) || 0,
        penaltyIncrement: parseFloat(editIncrement) || 0,
        warningThreshold: parseInt(editThreshold, 10) || 0,
        penaltyEnabled: Boolean(editEnabled)
      });
      setEditingConfigId(null);
      setConfigSuccess('Penalty configuration successfully updated!');
      loadConfigs();
      setTimeout(() => setConfigSuccess(null), 3000);
    } catch (e) {
      alert(`Failed to update config: ${e?.response?.data?.message || e.message}`);
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

  return (
    <div className="container-fluid py-4" style={{ maxWidth: 1200, fontFamily: 'Inter, sans-serif' }}>
      {/* Header */}
      <div className="d-flex align-items-center justify-content-between mb-4 flex-wrap gap-3">
        <div>
          <div className="d-flex align-items-center gap-2">
            <div style={{
              width: 36, height: 36, borderRadius: 10,
              background: 'linear-gradient(135deg, #dc2626, #b91c1c)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 2px 8px rgba(220, 38, 38, 0.3)'
            }}>
              <Scale size={20} color="#fff" />
            </div>
            <h1 className="h3 fw-bold mb-0" style={{ color: '#0f172a' }}>
              Penalty Management
            </h1>
          </div>
          <p className="text-muted mb-0 mt-1" style={{ fontSize: 13 }}>
            Progressive alert fines, warning thresholds, driver enforcement, and safety deductions
          </p>
        </div>

        <div className="d-flex align-items-center gap-2">
          <button
            className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1"
            onClick={() => { loadConfigs(); loadPenalties(); }}
            disabled={configLoading || penaltyLoading}
          >
            <RefreshCw size={13} className={configLoading || penaltyLoading ? 'spin' : ''} />
            Refresh
          </button>
          <button
            className="btn btn-primary btn-sm d-flex align-items-center gap-1"
            onClick={() => setShowAddModal(true)}
            style={{ background: '#2563eb', border: 'none' }}
          >
            <Plus size={14} /> Record Penalty
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="row g-3 mb-4">
        <div className="col-12 col-sm-6 col-xl-3">
          <div className="card border-0 shadow-sm p-3" style={{ borderRadius: 12, background: '#fff' }}>
            <div className="d-flex justify-content-between align-items-center mb-1">
              <span className="text-muted" style={{ fontSize: 12 }}>Configured Rules</span>
              <Scale size={16} color="#2563eb" />
            </div>
            <div className="h4 fw-bold mb-0" style={{ color: '#0f172a' }}>{configs.length}</div>
            <div className="text-muted" style={{ fontSize: 11 }}>Active alert penalty types</div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl-3">
          <div className="card border-0 shadow-sm p-3" style={{ borderRadius: 12, background: '#fff' }}>
            <div className="d-flex justify-content-between align-items-center mb-1">
              <span className="text-muted" style={{ fontSize: 12 }}>Warning Threshold</span>
              <ShieldAlert size={16} color="#f59e0b" />
            </div>
            <div className="h4 fw-bold mb-0" style={{ color: '#f59e0b' }}>
              {configs[0]?.warningThreshold ?? 2} Occurrences
            </div>
            <div className="text-muted" style={{ fontSize: 11 }}>Occ 1 & 2: Warning only (₹0)</div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl-3">
          <div className="card border-0 shadow-sm p-3" style={{ borderRadius: 12, background: '#fff' }}>
            <div className="d-flex justify-content-between align-items-center mb-1">
              <span className="text-muted" style={{ fontSize: 12 }}>Progressive Step</span>
              <DollarSign size={16} color="#dc2626" />
            </div>
            <div className="h4 fw-bold mb-0" style={{ color: '#dc2626' }}>
              +₹{configs[0]?.penaltyIncrement ?? 30.00}
            </div>
            <div className="text-muted" style={{ fontSize: 11 }}>Added per occurrence past #3</div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl-3">
          <div className="card border-0 shadow-sm p-3" style={{ borderRadius: 12, background: '#fff' }}>
            <div className="d-flex justify-content-between align-items-center mb-1">
              <span className="text-muted" style={{ fontSize: 12 }}>Enforcements</span>
              <FileText size={16} color="#7c3aed" />
            </div>
            <div className="h4 fw-bold mb-0" style={{ color: '#0f172a' }}>{penalties.length}</div>
            <div className="text-muted" style={{ fontSize: 11 }}>Total recorded penalty actions</div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <ul className="nav nav-tabs mb-4" style={{ borderBottom: '1px solid #e2e8f0' }}>
        <li className="nav-item">
          <button
            className={`nav-link py-2 px-4 ${activeTab === 'rules' ? 'active fw-bold text-primary' : 'text-muted'}`}
            onClick={() => setActiveTab('rules')}
            style={{ fontSize: 13 }}
          >
            <Scale size={15} className="me-2" /> Progressive Penalty Rules
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link py-2 px-4 ${activeTab === 'history' ? 'active fw-bold text-primary' : 'text-muted'}`}
            onClick={() => setActiveTab('history')}
            style={{ fontSize: 13 }}
          >
            <ShieldAlert size={15} className="me-2" /> Driver Penalty History & Enforcement
          </button>
        </li>
      </ul>

      {/* TAB 1: PROGRESSIVE PENALTY RULES */}
      {activeTab === 'rules' && (
        <div>
          {configSuccess && (
            <div className="alert alert-success py-2 mb-3 d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
              <CheckCircle size={15} /> {configSuccess}
            </div>
          )}

          <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4">
              <h5 className="mb-1 fw-bold" style={{ fontSize: 15, color: '#0f172a' }}>
                Configurable Progressive Alert Penalty & Weight System
              </h5>
              <div className="text-muted" style={{ fontSize: 12 }}>
                Configure warning thresholds, base penalties, progressive fine increments (₹/occurrence), and safety score multiplier weights.
                <br />
                <span className="badge bg-light text-dark border mt-1">
                  Rule Logic: Occurrences 1 to {configs[0]?.warningThreshold ?? 2} generate warnings (₹0). Occurrence 3 incurs Base Penalty. Occurrence 4+ adds ₹{configs[0]?.penaltyIncrement ?? 30}/occurrence.
                </span>
              </div>
            </div>

            <div className="card-body p-0">
              <div className="table-responsive">
                <table className="table table-hover mb-0" style={{ fontSize: 12 }}>
                  <thead style={{ background: '#f8fafc' }}>
                    <tr>
                      <th className="px-3">Alert Type</th>
                      <th>Description</th>
                      <th>Safety Weight</th>
                      <th>Base Penalty (₹)</th>
                      <th>Warning Threshold</th>
                      <th>Increment (₹/occ)</th>
                      <th>Penalty Status</th>
                      <th className="text-end px-3">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {configs.map(c => {
                      const isEditing = editingConfigId === c.id;
                      return (
                        <tr key={c.id}>
                          <td className="px-3 fw-bold text-primary">{c.alertType}</td>
                          <td className="text-muted" style={{ fontSize: 11, maxWidth: 220 }}>{c.description}</td>
                          <td>
                            {isEditing ? (
                              <input type="number" step="0.5" className="form-control form-control-sm" style={{ width: 80, fontSize: 12 }}
                                value={editWeight} onChange={e => setEditWeight(e.target.value)} />
                            ) : (
                              <span className="fw-semibold">{c.weight}</span>
                            )}
                          </td>
                          <td>
                            {isEditing ? (
                              <div className="input-group input-group-sm" style={{ width: 100 }}>
                                <span className="input-group-text py-0 px-1">₹</span>
                                <input type="number" step="10" className="form-control form-control-sm py-0 px-1" style={{ fontSize: 12 }}
                                  value={editFine} onChange={e => setEditFine(e.target.value)} />
                              </div>
                            ) : (
                              <span className="fw-bold" style={{ color: Number(c.defaultFineAmount) > 0 ? '#b91c1c' : '#64748b' }}>
                                ₹{c.defaultFineAmount}
                              </span>
                            )}
                          </td>
                          <td>
                            {isEditing ? (
                              <input type="number" min="0" max="10" className="form-control form-control-sm" style={{ width: 70, fontSize: 12 }}
                                value={editThreshold} onChange={e => setEditThreshold(e.target.value)} />
                            ) : (
                              <span className="badge bg-light text-dark border">
                                {c.warningThreshold ?? 2} warnings
                              </span>
                            )}
                          </td>
                          <td>
                            {isEditing ? (
                              <div className="input-group input-group-sm" style={{ width: 100 }}>
                                <span className="input-group-text py-0 px-1">+₹</span>
                                <input type="number" step="5" className="form-control form-control-sm py-0 px-1" style={{ fontSize: 12 }}
                                  value={editIncrement} onChange={e => setEditIncrement(e.target.value)} />
                              </div>
                            ) : (
                              <span className="text-muted fw-semibold">
                                +₹{c.penaltyIncrement ?? 30.00}
                              </span>
                            )}
                          </td>
                          <td>
                            {isEditing ? (
                              <select className="form-select form-select-sm py-0" style={{ width: 105, fontSize: 11 }}
                                value={editEnabled ? 'true' : 'false'} onChange={e => setEditEnabled(e.target.value === 'true')}>
                                <option value="true">Enabled</option>
                                <option value="false">Disabled</option>
                              </select>
                            ) : (
                              <span className={`badge ${c.penaltyEnabled !== false ? 'bg-success' : 'bg-secondary'}`}>
                                {c.penaltyEnabled !== false ? 'Active' : 'Disabled'}
                              </span>
                            )}
                          </td>
                          <td className="text-end px-3">
                            {isEditing ? (
                              <div className="d-flex justify-content-end gap-1">
                                <button className="btn btn-success btn-sm py-0 px-2" onClick={() => handleSaveConfig(c.id)} style={{ fontSize: 11 }}>
                                  <Check size={11} className="me-1" /> Save
                                </button>
                                <button className="btn btn-secondary btn-sm py-0 px-2" onClick={() => setEditingConfigId(null)} style={{ fontSize: 11 }}>
                                  Cancel
                                </button>
                              </div>
                            ) : (
                              <button className="btn btn-outline-secondary btn-sm py-0 px-2"
                                onClick={() => {
                                  setEditingConfigId(c.id);
                                  setEditWeight(String(c.weight ?? '1.0'));
                                  setEditFine(String(c.defaultFineAmount ?? '0'));
                                  setEditIncrement(String(c.penaltyIncrement ?? '30.00'));
                                  setEditThreshold(String(c.warningThreshold ?? '2'));
                                  setEditEnabled(c.penaltyEnabled !== false);
                                }}
                                style={{ fontSize: 11 }}>
                                <Edit2 size={11} className="me-1" /> Edit
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

      {/* TAB 2: DRIVER PENALTY ENFORCEMENT */}
      {activeTab === 'history' && (
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
                      <th>Score Penalty</th>
                      <th>Fine (₹)</th>
                      <th>Status</th>
                      <th>Date</th>
                      <th className="text-end px-3">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredPenalties.length > 0 ? (
                      filteredPenalties.map(p => (
                        <tr key={p.id}>
                          <td className="px-3 fw-bold text-muted">#{p.id}</td>
                          <td>
                            <div className="fw-semibold">{p.driverName || `Driver #${p.driverId}`}</div>
                            <div className="text-muted" style={{ fontSize: 11 }}>{p.driverCode}</div>
                          </td>
                          <td>{p.vehicleRegistration || '—'}</td>
                          <td><span className="badge bg-light text-dark">{p.penaltyType}</span></td>
                          <td style={{ maxWidth: 220 }} className="text-truncate" title={p.reason}>{p.reason}</td>
                          <td>
                            {p.deductFromScore ? (
                              <span className="text-danger fw-bold">-{p.penaltyPoints} pts</span>
                            ) : '—'}
                          </td>
                          <td>
                            {p.monetaryAmount > 0 ? (
                              <span className="text-danger fw-bold">₹{Number(p.monetaryAmount).toFixed(2)}</span>
                            ) : '₹0.00'}
                          </td>
                          <td>
                            <span className={`badge ${p.status === 'APPLIED' ? 'bg-success' : 'bg-secondary'}`}>
                              {p.status}
                            </span>
                          </td>
                          <td className="text-muted">
                            {p.occurredAt ? new Date(p.occurredAt).toLocaleDateString() : '—'}
                          </td>
                          <td className="text-end px-3">
                            {p.status === 'APPLIED' && (
                              <button className="btn btn-outline-danger btn-sm py-0 px-2"
                                onClick={() => { setRevokeTargetId(p.id); setRevokeReason(''); }}
                                style={{ fontSize: 11 }}>
                                <RotateCcw size={11} className="me-1" /> Revoke
                              </button>
                            )}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan="10" className="text-center py-4 text-muted">
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

      {/* RECORD PENALTY MODAL */}
      {showAddModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ background: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow">
              <form onSubmit={handleRecordPenalty}>
                <div className="modal-header bg-primary text-white py-2 px-3">
                  <h6 className="modal-title fw-bold mb-0">Record Driver Penalty</h6>
                  <button type="button" className="btn-close btn-close-white" onClick={() => setShowAddModal(false)}></button>
                </div>
                <div className="modal-body p-3">
                  {penaltyError && <div className="alert alert-danger py-1 mb-2" style={{ fontSize: 12 }}>{penaltyError}</div>}
                  <div className="mb-2">
                    <label className="form-label mb-1" style={{ fontSize: 12 }}>Driver</label>
                    <select className="form-select form-select-sm" value={formDriverId} onChange={e => setFormDriverId(e.target.value)} required>
                      {drivers.map(d => (
                        <option key={d.id} value={d.id}>{d.name} ({d.code})</option>
                      ))}
                    </select>
                  </div>
                  <div className="mb-2">
                    <label className="form-label mb-1" style={{ fontSize: 12 }}>Penalty Type</label>
                    <select className="form-select form-select-sm" value={formPenaltyType} onChange={e => setFormPenaltyType(e.target.value)}>
                      <option value="MANUAL_SAFETY_DEDUCTION">Manual Safety Deduction</option>
                      <option value="POLICY_VIOLATION">Company Policy Violation</option>
                      <option value="REPEATED_OVERSPEEDING">Repeated Overspeeding</option>
                      <option value="HARSH_DRIVING_REPEATED">Repeated Harsh Driving</option>
                      <option value="UNAUTHORIZED_STOP">Unauthorized Route Deviation</option>
                    </select>
                  </div>
                  <div className="mb-2">
                    <label className="form-label mb-1" style={{ fontSize: 12 }}>Reason *</label>
                    <input type="text" className="form-control form-control-sm" placeholder="Detailed violation reason"
                      value={formReason} onChange={e => setFormReason(e.target.value)} required />
                  </div>
                  <div className="row g-2 mb-2">
                    <div className="col-6">
                      <label className="form-label mb-1" style={{ fontSize: 12 }}>Safety Score Deduction (pts)</label>
                      <input type="number" step="0.5" className="form-control form-control-sm"
                        value={formPoints} onChange={e => setFormPoints(e.target.value)} />
                    </div>
                    <div className="col-6">
                      <label className="form-label mb-1" style={{ fontSize: 12 }}>Financial Fine (₹)</label>
                      <input type="number" step="10" className="form-control form-control-sm"
                        value={formFine} onChange={e => setFormFine(e.target.value)} />
                    </div>
                  </div>
                  <div className="form-check mb-2">
                    <input className="form-check-input" type="checkbox" id="deductCheck" checked={formDeductScore}
                      onChange={e => setFormDeductScore(e.target.checked)} />
                    <label className="form-check-label" htmlFor="deductCheck" style={{ fontSize: 12 }}>
                      Deduct from driver safety score immediately
                    </label>
                  </div>
                </div>
                <div className="modal-footer py-2 px-3">
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowAddModal(false)}>Cancel</button>
                  <button type="submit" className="btn btn-primary btn-sm">Save Penalty</button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* REVOCATION MODAL */}
      {revokeTargetId && (
        <div className="modal show d-block" tabIndex="-1" style={{ background: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow">
              <div className="modal-header bg-danger text-white py-2 px-3">
                <h6 className="modal-title fw-bold mb-0">Revoke Driver Penalty #{revokeTargetId}</h6>
                <button type="button" className="btn-close btn-close-white" onClick={() => setRevokeTargetId(null)}></button>
              </div>
              <div className="modal-body p-3">
                <p style={{ fontSize: 12 }}>
                  Revoking this penalty will mark it as REVOKED and reverse the associated safety deduction from the driver score.
                </p>
                <div className="mb-2">
                  <label className="form-label mb-1" style={{ fontSize: 12 }}>Auditable Reason for Revocation *</label>
                  <input type="text" className="form-control form-control-sm" placeholder="e.g., Driver appeal approved by fleet supervisor"
                    value={revokeReason} onChange={e => setRevokeReason(e.target.value)} required />
                </div>
              </div>
              <div className="modal-footer py-2 px-3">
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
