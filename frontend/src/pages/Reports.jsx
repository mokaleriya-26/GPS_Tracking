import React, { useState, useEffect } from 'react';
import {
  Car, CalendarDays, BarChart3, CalendarRange,
  SlidersHorizontal, Download, ExternalLink, FileText,
  Users, Loader, CheckCircle, AlertCircle, Globe, User,
  ArrowRight, AlertTriangle
} from 'lucide-react';
import { getDrivers } from '../api/drivers';
import {
  generateDriverReport,
  generateDriverCustomReport,
  generateFleetReport,
  generateOverallReport,
  getPdfUrl,
  getDownloadUrl
} from '../api/reports';

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const PERIOD_TYPES = [
  { name: 'Monthly',  key: 'monthly', icon: <CalendarRange size={20} />, desc: 'Full month performance' },
  { name: 'Weekly',   key: 'weekly',  icon: <BarChart3 size={20} />,     desc: 'One week summary' },
  { name: 'Daily',    key: 'daily',   icon: <CalendarDays size={20} />,  desc: 'Single day breakdown' },
  { name: 'Custom',   key: 'custom',  icon: <SlidersHorizontal size={20} />, desc: 'Your own date range' },
  { name: 'Fleet',    key: 'fleet',   icon: <Users size={20} />,         desc: 'All drivers ranked' },
];

export default function Reports() {
  const [drivers, setDrivers]             = useState([]);
  const [periodType, setPeriodType]       = useState('monthly');
  const [reportScope, setReportScope]     = useState('driver'); // 'driver' | 'overall'
  const [selectedDriverId, setSelectedDriverId] = useState('');
  const [month, setMonth]                 = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const [customFrom, setCustomFrom]       = useState('');
  const [customTo, setCustomTo]           = useState('');
  const [generating, setGenerating]       = useState(false);
  const [result, setResult]               = useState(null);   // ReportResponseDTO or OverallReportDTO
  const [isOverall, setIsOverall]         = useState(false);  // was the last result an overall report?
  const [error, setError]                 = useState(null);

  useEffect(() => {
    getDrivers()
      .then(data => {
        const list = Array.isArray(data) ? data : (data?.content || []);
        setDrivers(list);
        if (list.length > 0) setSelectedDriverId(String(list[0].id));
      })
      .catch(() => setError('Could not load drivers. Is the backend running?'));
  }, []);

  const isFleetPeriod = periodType === 'fleet';
  // fleet always = driver scope (no overall option)
  const showScopeToggle = !isFleetPeriod;

  const selectedDriver = drivers.find(d => String(d.id) === String(selectedDriverId));

  const buildRequestBody = () => {
    const body = { reportType: periodType.toUpperCase() };
    if (periodType === 'monthly' || periodType === 'fleet' || periodType === 'weekly') {
      const [year, mon] = month.split('-');
      body.year  = parseInt(year);
      body.month = parseInt(mon);
    } else if (periodType === 'daily') {
      body.date = customFrom || new Date().toISOString().slice(0, 10);
    } else if (periodType === 'custom') {
      body.startDate = customFrom;
      body.endDate   = customTo;
    }
    return body;
  };

  const handleGenerate = async () => {
    const needsDriver = !isFleetPeriod && reportScope === 'driver' && !selectedDriverId;
    if (needsDriver) { setError('Please select a driver.'); return; }
    if (periodType === 'custom' && (!customFrom || !customTo)) {
      setError('Please select both From and To dates.'); return;
    }
    setGenerating(true);
    setError(null);
    setResult(null);
    setIsOverall(false);

    try {
      const body = buildRequestBody();
      let res;

      if (isFleetPeriod) {
        res = await generateFleetReport(body);
        setIsOverall(false);
      } else if (reportScope === 'overall') {
        res = await generateOverallReport(body);
        setIsOverall(true);
      } else if (periodType === 'custom') {
        res = await generateDriverCustomReport(Number(selectedDriverId), body);
        setIsOverall(false);
      } else {
        res = await generateDriverReport(Number(selectedDriverId), body);
        setIsOverall(false);
      }

      setResult(res);
    } catch (e) {
      const msg = e?.response?.data?.message || e?.response?.data || e.message;
      setError(`Report generation failed: ${msg}`);
    } finally {
      setGenerating(false);
    }
  };

  const fullPdfUrl      = result?.pdfUrl      ? `${BASE}${result.pdfUrl}`      : null;
  const fullDownloadUrl = result?.downloadUrl  ? `${BASE}${result.downloadUrl}` : null;

  return (
    <div style={{ width: '100%', maxWidth: '100%', minWidth: 0 }}>
      {/* Page Header */}
      <div className="mb-4">
        <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0f172a', margin: 0 }}>Reports</h1>
        <div style={{ color: '#64748b', fontSize: 13, marginTop: 4 }}>
          Generate driver and fleet reports as PDF
        </div>
      </div>

      <div className="row g-4" style={{ margin: 0 }}>
        {/* ===== LEFT: Config Panel ===== */}
        <div className="col-12 col-lg-4" style={{ minWidth: 0 }}>
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-body p-4">
              <div className="fw-bold mb-3" style={{ fontSize: 15, color: '#0f172a' }}>
                Configure Report
              </div>

              {/* Period selector */}
              <div className="mb-3">
                <label className="form-label fw-semibold" style={{ fontSize: 12, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                  Period Type
                </label>
                <div className="d-flex flex-column gap-2">
                  {PERIOD_TYPES.map(p => (
                    <button key={p.key}
                      onClick={() => { setPeriodType(p.key); setResult(null); setError(null); if (p.key === 'fleet') setReportScope('driver'); }}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 10,
                        padding: '10px 14px', borderRadius: 10, cursor: 'pointer',
                        background: periodType === p.key ? 'linear-gradient(135deg,#2563eb,#1d4ed8)' : '#f8fafc',
                        color: periodType === p.key ? '#fff' : '#374151',
                        border: periodType === p.key ? 'none' : '1px solid #e2e8f0',
                        fontWeight: 600, fontSize: 13, textAlign: 'left', transition: 'all .15s'
                      }}>
                      {React.cloneElement(p.icon, { color: periodType === p.key ? '#fff' : '#64748b' })}
                      <div>
                        <div>{p.name}</div>
                        <div style={{ fontSize: 10, fontWeight: 400, opacity: .75 }}>{p.desc}</div>
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              {/* Scope toggle: Overall vs Driver (not for fleet) */}
              {showScopeToggle && (
                <div className="mb-3">
                  <label className="form-label fw-semibold" style={{ fontSize: 12, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                    Report Scope
                  </label>
                  <div className="d-flex gap-2">
                    {[
                      { val: 'driver',  label: 'Driver Report',  icon: <User size={14}/> },
                      { val: 'overall', label: 'Overall Report', icon: <Globe size={14}/> },
                    ].map(s => (
                      <button key={s.val}
                        onClick={() => { setReportScope(s.val); setResult(null); setError(null); }}
                        style={{
                          flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                          padding: '8px 12px', borderRadius: 8, cursor: 'pointer', fontSize: 12, fontWeight: 600,
                          background: reportScope === s.val ? '#2563eb' : '#f8fafc',
                          color: reportScope === s.val ? '#fff' : '#374151',
                          border: reportScope === s.val ? 'none' : '1px solid #e2e8f0',
                          transition: 'all .15s'
                        }}>
                        {s.icon} {s.label}
                      </button>
                    ))}
                  </div>
                  {reportScope === 'overall' && (
                    <div style={{ fontSize: 11, color: '#64748b', marginTop: 6, padding: '6px 8px', background: '#f0f9ff', borderRadius: 6 }}>
                      📊 Shows fleet-wide vehicle entries/exits, total alerts, and per-driver breakdown.
                    </div>
                  )}
                </div>
              )}

              {/* Driver selector — only when scope = driver */}
              {!isFleetPeriod && reportScope === 'driver' && (
                <div className="mb-3">
                  <label className="form-label fw-semibold" style={{ fontSize: 12, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                    Driver
                  </label>
                  <select className="form-select form-select-sm"
                    value={selectedDriverId}
                    onChange={e => { setSelectedDriverId(e.target.value); setResult(null); }}>
                    <option value="">— Select driver —</option>
                    {drivers.map(d => (
                      <option key={d.id} value={d.id}>{d.name} ({d.code})</option>
                    ))}
                  </select>
                  {drivers.length === 0 && (
                    <div className="text-muted mt-1" style={{ fontSize: 11 }}>No drivers loaded. Backend may be offline.</div>
                  )}
                </div>
              )}

              {/* Date inputs */}
              {(periodType === 'monthly' || periodType === 'weekly' || periodType === 'fleet') && (
                <div className="mb-3">
                  <label className="form-label fw-semibold" style={{ fontSize: 12, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>Month</label>
                  <input type="month" className="form-control form-control-sm"
                    value={month} onChange={e => { setMonth(e.target.value); setResult(null); }} />
                </div>
              )}
              {periodType === 'daily' && (
                <div className="mb-3">
                  <label className="form-label fw-semibold" style={{ fontSize: 12, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>Date</label>
                  <input type="date" className="form-control form-control-sm"
                    value={customFrom} onChange={e => { setCustomFrom(e.target.value); setResult(null); }} />
                </div>
              )}
              {periodType === 'custom' && (
                <>
                  <div className="mb-2">
                    <label className="form-label fw-semibold" style={{ fontSize: 12, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>From</label>
                    <input type="date" className="form-control form-control-sm"
                      value={customFrom} onChange={e => { setCustomFrom(e.target.value); setResult(null); }} />
                  </div>
                  <div className="mb-3">
                    <label className="form-label fw-semibold" style={{ fontSize: 12, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>To</label>
                    <input type="date" className="form-control form-control-sm"
                      value={customTo} onChange={e => { setCustomTo(e.target.value); setResult(null); }} />
                  </div>
                </>
              )}

              {/* Error */}
              {error && (
                <div className="alert alert-danger py-2 mb-3" style={{ fontSize: 12 }}>
                  <AlertCircle size={13} className="me-1" /> {error}
                </div>
              )}

              {/* Generate button */}
              <button className="btn btn-primary w-100 d-flex align-items-center justify-content-center gap-2"
                onClick={handleGenerate}
                disabled={generating || (!isFleetPeriod && reportScope === 'driver' && !selectedDriverId)}
                style={{ borderRadius: 10, fontWeight: 600, padding: '10px' }}>
                {generating
                  ? <><Loader size={15} className="spin" /> Generating PDF...</>
                  : <><FileText size={15} /> Generate {
                      isFleetPeriod ? 'Fleet' : reportScope === 'overall' ? 'Overall' : 'Driver'
                    } {periodType.charAt(0).toUpperCase() + periodType.slice(1)} Report</>
                }
              </button>
            </div>
          </div>
        </div>

        {/* ===== RIGHT: Result ===== */}
        <div className="col-12 col-lg-8" style={{ minWidth: 0 }}>
          {result ? (
            <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
              <div className="card-body p-4">
                {/* Success header */}
                <div className="d-flex align-items-center gap-3 mb-4">
                  <div style={{ width: 44, height: 44, borderRadius: 12, background: '#dcfce7',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <CheckCircle size={22} color="#16a34a" />
                  </div>
                  <div>
                    <div className="fw-bold" style={{ fontSize: 16 }}>Report Generated</div>
                    <div className="text-muted" style={{ fontSize: 13 }}>{result.message}</div>
                  </div>
                  <button className="btn btn-outline-secondary btn-sm ms-auto"
                    onClick={() => setResult(null)} style={{ borderRadius: 8, fontSize: 12 }}>
                    Generate Another
                  </button>
                </div>

                {/* Metadata row */}
                <div className="d-flex flex-wrap gap-3 mb-4">
                  {[
                    result.reportType && { label: 'Type', val: result.reportType },
                    result.startDate  && { label: 'From', val: result.startDate },
                    result.endDate    && { label: 'To',   val: result.endDate },
                    result.generatedAt && {
                      label: 'Generated',
                      val: new Date(result.generatedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })
                    },
                  ].filter(Boolean).map(m => (
                    <div key={m.label} style={{ background: '#f8fafc', borderRadius: 8, padding: '6px 12px', fontSize: 12 }}>
                      <div style={{ color: '#94a3b8', fontSize: 10 }}>{m.label}</div>
                      <div style={{ fontWeight: 600, color: '#1e293b' }}>{m.val}</div>
                    </div>
                  ))}
                </div>

                {/* ===== OVERALL REPORT: data tables ===== */}
                {isOverall && (
                  <div className="mb-4">
                    {/* Vehicle counts */}
                    <div className="row g-3 mb-4">
                      {[
                        { label: 'Vehicles Entered', val: result.totalVehiclesEntered, color: '#2563eb', bg: '#eff6ff' },
                        { label: 'Vehicles Exited',  val: result.totalVehiclesExited,  color: '#16a34a', bg: '#f0fdf4' },
                        { label: 'Total Alerts',     val: result.totalAlerts,           color: '#dc2626', bg: '#fef2f2' },
                      ].map(s => (
                        <div key={s.label} className="col-12 col-sm-4">
                          <div style={{ background: s.bg, borderRadius: 12, padding: '16px 20px', textAlign: 'center' }}>
                            <div style={{ fontSize: 32, fontWeight: 800, color: s.color }}>{s.val}</div>
                            <div style={{ fontSize: 12, color: '#64748b', marginTop: 2 }}>{s.label}</div>
                          </div>
                        </div>
                      ))}
                    </div>

                    {/* Alert type summary */}
                    {result.alertTypeSummary && Object.keys(result.alertTypeSummary).length > 0 && (
                      <div className="mb-4">
                        <div className="fw-semibold mb-2" style={{ fontSize: 14, color: '#1e293b' }}>
                          <AlertTriangle size={15} className="me-1" style={{ color: '#f59e0b' }} />
                          Alert Type Summary
                        </div>
                        <div className="table-responsive">
                          <table className="table table-sm table-hover mb-0" style={{ fontSize: 13 }}>
                            <thead style={{ background: '#f8fafc' }}>
                              <tr>
                                <th style={{ fontWeight: 600, color: '#64748b', border: 'none', padding: '8px 12px' }}>Alert Type</th>
                                <th style={{ fontWeight: 600, color: '#64748b', border: 'none', padding: '8px 12px' }}>Count</th>
                              </tr>
                            </thead>
                            <tbody>
                              {Object.entries(result.alertTypeSummary).map(([type, count]) => (
                                <tr key={type}>
                                  <td style={{ padding: '8px 12px', color: '#374151' }}>{type}</td>
                                  <td style={{ padding: '8px 12px', fontWeight: 700, color: '#1e293b' }}>{count}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}

                    {/* Driver alert details */}
                    {result.driverAlertDetails && result.driverAlertDetails.length > 0 && (
                      <div className="mb-4">
                        <div className="fw-semibold mb-2" style={{ fontSize: 14, color: '#1e293b' }}>
                          <Users size={15} className="me-1" style={{ color: '#2563eb' }} />
                          Driver Alert Details
                        </div>
                        <div className="table-responsive">
                          <table className="table table-sm table-hover mb-0" style={{ fontSize: 12 }}>
                            <thead style={{ background: '#f8fafc' }}>
                              <tr>
                                {['Driver', 'ID', 'Vehicle(s)', 'Total', 'Overspeed', 'Harsh Braking', 'GPS Disc.', 'Night', 'Ignition', 'Other'].map(h => (
                                  <th key={h} style={{ fontWeight: 600, color: '#64748b', border: 'none', padding: '8px 10px', whiteSpace: 'nowrap' }}>{h}</th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              {result.driverAlertDetails.map(d => (
                                <tr key={d.driverId}>
                                  <td style={{ padding: '7px 10px' }}>{d.driverName}</td>
                                  <td style={{ padding: '7px 10px', color: '#64748b' }}>{d.driverCode}</td>
                                  <td style={{ padding: '7px 10px', color: '#64748b' }}>{d.vehicles}</td>
                                  <td style={{ padding: '7px 10px', fontWeight: 700 }}>{d.totalAlerts}</td>
                                  <td style={{ padding: '7px 10px' }}>{d.overspeed}</td>
                                  <td style={{ padding: '7px 10px' }}>{d.harshBraking}</td>
                                  <td style={{ padding: '7px 10px' }}>{d.gpsDisconnect}</td>
                                  <td style={{ padding: '7px 10px' }}>{d.nightDriving}</td>
                                  <td style={{ padding: '7px 10px' }}>{d.ignition}</td>
                                  <td style={{ padding: '7px 10px' }}>{d.other}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* ===== PDF PREVIEW (both driver and overall reports) ===== */}
                {fullPdfUrl && (
                  <>
                    <div className="fw-semibold mb-2" style={{ fontSize: 13, color: '#1e293b' }}>
                      <FileText size={14} className="me-1" />
                      PDF Preview
                    </div>
                    {/* Use <object> — more reliable cross-browser than <iframe> for PDFs */}
                    <div className="mb-3" style={{ borderRadius: 10, overflow: 'hidden', border: '1px solid #e2e8f0', background: '#f1f5f9' }}>
                      <object
                        data={fullPdfUrl}
                        type="application/pdf"
                        width="100%"
                        height="520"
                        style={{ display: 'block', border: 'none' }}>
                        {/* Fallback for browsers that don't support inline PDF */}
                        <div style={{ padding: 24, textAlign: 'center', color: '#64748b' }}>
                          <FileText size={32} style={{ marginBottom: 8, color: '#cbd5e1' }} />
                          <div style={{ fontSize: 13, marginBottom: 12 }}>
                            Your browser cannot preview PDFs inline.
                          </div>
                          <a href={fullPdfUrl} target="_blank" rel="noreferrer"
                            className="btn btn-outline-primary btn-sm">
                            <ExternalLink size={13} className="me-1" /> Open PDF
                          </a>
                        </div>
                      </object>
                    </div>
                  </>
                )}

                {/* Action buttons */}
                <div className="d-flex gap-2 flex-wrap">
                  {fullPdfUrl && (
                    <a href={fullPdfUrl} target="_blank" rel="noreferrer"
                      className="btn btn-outline-primary btn-sm d-flex align-items-center gap-2"
                      style={{ borderRadius: 8 }}>
                      <ExternalLink size={13} /> Open in Browser
                    </a>
                  )}
                  {fullDownloadUrl && (
                    <a href={fullDownloadUrl} download
                      className="btn btn-primary btn-sm d-flex align-items-center gap-2"
                      style={{ borderRadius: 8 }}>
                      <Download size={13} /> Download PDF
                    </a>
                  )}
                </div>

                {/* Report ID */}
                {result.reportId && (
                  <div className="mt-3" style={{ fontSize: 11, color: '#94a3b8' }}>
                    Report ID: <code>{result.reportId}</code>
                  </div>
                )}
              </div>
            </div>
          ) : (
            /* Empty state */
            <div className="card border-0 shadow-sm d-flex align-items-center justify-content-center"
              style={{ borderRadius: 14, minHeight: 400 }}>
              <div className="text-center p-4">
                {generating ? (
                  <>
                    <div className="d-flex justify-content-center mb-3">
                      <div className="spinner-border text-primary" style={{ width: 40, height: 40 }} />
                    </div>
                    <h5 className="fw-semibold" style={{ color: '#64748b' }}>Generating report...</h5>
                    <p className="text-muted" style={{ fontSize: 13 }}>Spring Boot is compiling data and building the PDF.</p>
                  </>
                ) : (
                  <>
                    <FileText size={52} className="mb-3" style={{ color: '#cbd5e1' }} />
                    <h5 className="fw-semibold" style={{ color: '#64748b' }}>Report preview will appear here</h5>
                    <p className="text-muted" style={{ fontSize: 13 }}>
                      Select a period, scope, and driver on the left, then click Generate.
                    </p>
                    <div className="d-flex align-items-center justify-content-center gap-2 mt-2"
                      style={{ fontSize: 12, color: '#94a3b8' }}>
                      <Globe size={13} /> Overall Report gives fleet-wide stats
                      <span>·</span>
                      <User size={13} /> Driver Report gives individual PDF
                    </div>
                  </>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}