import React, { useState, useEffect } from 'react';
import {
  Car, CalendarDays, BarChart3, CalendarRange,
  SlidersHorizontal, Download, ExternalLink, FileText,
  Users, Loader, CheckCircle, AlertCircle, Globe, User,
  ArrowRight, AlertTriangle, ShieldCheck, Clock, MapPin, Gauge, Filter,
  RefreshCw
} from 'lucide-react';
import { getDrivers } from '../api/drivers';
import api from '../api/axios';
import {
  generateDriverReport,
  generateOverallReport,
  getPdfUrl,
  getDownloadUrl
} from '../api/reports';

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const PERIOD_TYPES = [
  { name: 'Monthly',  key: 'monthly', icon: <CalendarRange size={18} />, desc: 'Full month performance' },
  { name: 'Weekly',   key: 'weekly',  icon: <BarChart3 size={18} />,     desc: 'Standard 7-day week selector' },
  { name: 'Daily',    key: 'daily',   icon: <CalendarDays size={18} />,  desc: 'Single day breakdown' },
  { name: 'Custom',   key: 'custom',  icon: <SlidersHorizontal size={18} />, desc: 'Custom date range' },
];

function getISOWeekString(date = new Date()) {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const weekNo = Math.ceil((((d - yearStart) / 86400000) + 1) / 7);
  return `${d.getUTCFullYear()}-W${String(weekNo).padStart(2, '0')}`;
}

function getWeekRange(weekStr) {
  if (!weekStr || !weekStr.includes('-W')) return { start: '', end: '' };
  try {
    const [yStr, wStr] = weekStr.split('-W');
    const year = parseInt(yStr, 10);
    const week = parseInt(wStr, 10);
    const simple = new Date(Date.UTC(year, 0, 4));
    const dayOfWeek = simple.getUTCDay() || 7;
    simple.setUTCDate(simple.getUTCDate() - dayOfWeek + 1 + (week - 1) * 7);
    const start = simple.toISOString().slice(0, 10);
    const endDate = new Date(simple);
    endDate.setUTCDate(endDate.getUTCDate() + 6);
    const end = endDate.toISOString().slice(0, 10);
    return { start, end };
  } catch {
    return { start: '', end: '' };
  }
}

export default function Reports() {
  const [drivers, setDrivers]                   = useState([]);
  const [periodType, setPeriodType]             = useState('monthly');
  const [reportScope, setReportScope]           = useState('driver'); // 'driver' | 'overall'
  const [selectedDriverId, setSelectedDriverId] = useState('');

  // Period inputs
  const [month, setMonth] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const [week, setWeek] = useState(() => getISOWeekString());
  const [dailyDate, setDailyDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo]     = useState('');

  const [generating, setGenerating] = useState(false);
  const [result, setResult]         = useState(null);
  const [error, setError]           = useState(null);
  const [activeTab, setActiveTab]   = useState('summary'); // 'summary' | 'vehicles' | 'trips' | 'alerts' | 'pdf'
  const [alertViewMode, setAlertViewMode] = useState('summary'); // 'summary' | 'events'
  const [alertTypeFilter, setAlertTypeFilter] = useState('ALL');

  useEffect(() => {
    getDrivers()
      .then(data => {
        const list = Array.isArray(data) ? data : (data?.content || []);
        setDrivers(list);
        if (list.length > 0) setSelectedDriverId(String(list[0].id));
      })
      .catch(() => setError('Could not load drivers. Backend may be starting up.'));
  }, []);

  const isOverallReport = reportScope === 'overall';

  const weekRange = getWeekRange(week);

  const buildRequestBody = () => {
    const body = { reportType: periodType.toUpperCase() };

    if (periodType === 'weekly') {
      body.week = week;
      if (weekRange.start && weekRange.end) {
        body.startDate = weekRange.start;
        body.endDate   = weekRange.end;
      }
    } else if (periodType === 'monthly') {
      const [year, mon] = month.split('-');
      body.year  = parseInt(year);
      body.month = parseInt(mon);
    } else if (periodType === 'daily') {
      body.date = dailyDate;
    } else if (periodType === 'custom') {
      body.startDate = customFrom;
      body.endDate   = customTo;
    }
    return body;
  };

  const handleGenerate = async () => {
    const needsDriver = reportScope === 'driver' && !selectedDriverId;
    if (needsDriver) { setError('Please select a driver.'); return; }
    if (periodType === 'custom' && (!customFrom || !customTo)) {
      setError('Please select both Start and End dates.'); return;
    }
    setGenerating(true);
    setError(null);
    setResult(null);

    try {
      const body = buildRequestBody();
      let res;

      if (isOverallReport) {
        // Fleet report — route to /fleet/{type} so backend generates a full 4-section PDF
        const type = (body.reportType || 'MONTHLY').toLowerCase();
        const response = await api.post(`/api/reports/fleet/${type}`, body);
        res = response.data?.data;
        // Flatten reportData for fleet (reportData is the OverallReportDTO)
        if (res && res.reportData) {
          res = { ...res.reportData, reportId: res.reportId, pdfUrl: res.pdfUrl, downloadUrl: res.downloadUrl, reportType: res.reportType };
        }
      } else {
        res = await generateDriverReport(Number(selectedDriverId), body);
        // Flatten reportData for driver
        if (res && res.reportData) {
          res = { ...res.reportData, reportId: res.reportId, pdfUrl: res.pdfUrl, downloadUrl: res.downloadUrl, reportType: res.reportType, generatedAt: res.generatedAt };
        }
      }

      setResult(res);
      setActiveTab('summary');
    } catch (e) {
      const msg = e?.response?.data?.message || e?.response?.data || e.message;
      setError(`Report generation failed: ${msg}`);
    } finally {
      setGenerating(false);
    }
  };

  const fullPdfUrl      = result?.pdfUrl      ? `${BASE}${result.pdfUrl}`      : null;
  const fullDownloadUrl = result?.downloadUrl  ? `${BASE}${result.downloadUrl}` : null;
  // After flattening, result IS the reportData for both driver and fleet
  const reportData = result;

  // Filter alerts for preview
  const allAlertSummaries = reportData?.alertSummaries || [];
  const allAlertEvents    = reportData?.alertEvents    || [];
  const filteredAlertSummaries = allAlertSummaries.filter(a =>
    alertTypeFilter === 'ALL' || a.alertType === alertTypeFilter
  );
  const filteredAlertEvents = allAlertEvents.filter(a =>
    alertTypeFilter === 'ALL' || a.alertType === alertTypeFilter
  );

  // Alert type options from actual data
  const alertTypeOptions = [...new Set(allAlertSummaries.map(a => a.alertType))].filter(Boolean);

  return (
    <div style={{ width: '100%', maxWidth: '100%', minWidth: 0, paddingBottom: 40 }}>
      {/* Header */}
      <div className="mb-4">
        <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0f172a', margin: 0 }}>Fleet & Driver Reports</h1>
        <div style={{ color: '#64748b', fontSize: 13, marginTop: 4 }}>
          Standardized safety scoring, trip tracking, multi-vehicle associations, and PDF generation
        </div>
      </div>

      <div className="row g-4" style={{ margin: 0 }}>
        {/* ===== CONFIG PANEL ===== */}
        <div className="col-12 col-lg-4" style={{ minWidth: 0 }}>
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-body p-4">
              <div className="fw-bold mb-3" style={{ fontSize: 15, color: '#0f172a' }}>
                Configure Report
              </div>

              {/* Period Type Selection */}
              <div className="mb-3">
                <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                  Report Period
                </label>
                <div className="d-flex flex-column gap-2">
                  {PERIOD_TYPES.map(p => (
                    <button key={p.key}
                      onClick={() => {
                        setPeriodType(p.key);
                        setResult(null);
                        setError(null);
                      }}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 10,
                        padding: '9px 12px', borderRadius: 9, cursor: 'pointer',
                        background: periodType === p.key ? 'linear-gradient(135deg,#2563eb,#1d4ed8)' : '#f8fafc',
                        color: periodType === p.key ? '#fff' : '#374151',
                        border: periodType === p.key ? 'none' : '1px solid #e2e8f0',
                        fontWeight: 600, fontSize: 13, textAlign: 'left', transition: 'all .15s'
                      }}>
                      {React.cloneElement(p.icon, { color: periodType === p.key ? '#fff' : '#64748b' })}
                      <div>
                        <div>{p.name}</div>
                        <div style={{ fontSize: 10, fontWeight: 400, opacity: .8 }}>{p.desc}</div>
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              {/* Scope toggle: Driver vs Fleet Overview */}
              <div className="mb-3">
                <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                  Report Scope
                </label>
                <div className="d-flex gap-2">
                  {[
                    { val: 'driver',  label: 'Driver Report',  icon: <User size={13}/> },
                    { val: 'overall', label: 'Fleet Overview', icon: <Globe size={13}/> },
                  ].map(s => (
                    <button key={s.val}
                      onClick={() => { setReportScope(s.val); setResult(null); setError(null); }}
                      style={{
                        flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                        padding: '8px 10px', borderRadius: 8, cursor: 'pointer', fontSize: 12, fontWeight: 600,
                        background: reportScope === s.val ? '#2563eb' : '#f8fafc',
                        color: reportScope === s.val ? '#fff' : '#374151',
                        border: reportScope === s.val ? 'none' : '1px solid #e2e8f0',
                        transition: 'all .15s'
                      }}>
                      {s.icon} {s.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Driver selector */}
              {reportScope === 'driver' && (
                <div className="mb-3">
                  <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                    Select Driver
                  </label>
                  <select className="form-select form-select-sm"
                    value={selectedDriverId}
                    onChange={e => { setSelectedDriverId(e.target.value); setResult(null); }}>
                    <option value="">— Select driver —</option>
                    {drivers.map(d => (
                      <option key={d.id} value={d.id}>{d.name} ({d.code})</option>
                    ))}
                  </select>
                </div>
              )}

              {/* Period Inputs with strict field matching (Section 7) */}
              {periodType === 'daily' && (
                <div className="mb-3">
                  <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                    Selected Date
                  </label>
                  <input type="date" className="form-control form-control-sm"
                    value={dailyDate} onChange={e => { setDailyDate(e.target.value); setResult(null); }} />
                </div>
              )}

              {periodType === 'weekly' && (
                <div className="mb-3">
                  <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                    Select Week
                  </label>
                  <input type="week" className="form-control form-control-sm"
                    value={week} onChange={e => { setWeek(e.target.value); setResult(null); }} />
                  {weekRange.start && (
                    <div className="mt-1" style={{ fontSize: 11, color: '#2563eb', fontWeight: 500 }}>
                      🗓️ {weekRange.start} to {weekRange.end}
                    </div>
                  )}
                </div>
              )}

              {periodType === 'monthly' && (
                <div className="mb-3">
                  <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                    Select Month
                  </label>
                  <input type="month" className="form-control form-control-sm"
                    value={month} onChange={e => { setMonth(e.target.value); setResult(null); }} />
                </div>
              )}

              {periodType === 'custom' && (
                <>
                  <div className="mb-2">
                    <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>From Date</label>
                    <input type="date" className="form-control form-control-sm"
                      value={customFrom} onChange={e => { setCustomFrom(e.target.value); setResult(null); }} />
                  </div>
                  <div className="mb-3">
                    <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>To Date</label>
                    <input type="date" className="form-control form-control-sm"
                      value={customTo} onChange={e => { setCustomTo(e.target.value); setResult(null); }} />
                  </div>
                </>
              )}

              {error && (
                <div className="alert alert-danger py-2 mb-3" style={{ fontSize: 12 }}>
                  <AlertCircle size={13} className="me-1" /> {error}
                </div>
              )}

              <button className="btn btn-primary w-100 d-flex align-items-center justify-content-center gap-2"
                onClick={handleGenerate}
                disabled={generating || (reportScope === 'driver' && !selectedDriverId)}
                style={{ borderRadius: 9, fontWeight: 600, padding: '10px' }}>
                {generating
                  ? <><Loader size={15} className="spin" /> Generating Report...</>
                  : <><FileText size={15} /> Generate {isOverallReport ? 'Fleet' : 'Driver'} Report</>
                }
              </button>
            </div>
          </div>
        </div>

        {/* ===== REPORT VIEW & TABS ===== */}
        <div className="col-12 col-lg-8" style={{ minWidth: 0 }}>
          {result ? (
            <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
              <div className="card-body p-4">
                {/* Header Actions */}
                <div className="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3 pb-3 border-bottom">
                  <div>
                    <span className="badge bg-primary me-2">{result?.reportType || periodType.toUpperCase()}</span>
                    <span className="fw-bold" style={{ fontSize: 16 }}>
                      {isOverallReport ? 'Fleet Performance Report' : `${reportData?.driverName || 'Driver'} Report`}
                    </span>
                    <div className="text-muted" style={{ fontSize: 11, marginTop: 2 }}>
                      Period: {reportData?.startDate} to {reportData?.endDate}
                    </div>
                  </div>
                  <div className="d-flex gap-2">
                    {fullPdfUrl && (
                      <a href={fullPdfUrl} target="_blank" rel="noreferrer" className="btn btn-outline-primary btn-sm d-flex align-items-center gap-1" style={{ borderRadius: 7 }}>
                        <ExternalLink size={13} /> Open in Browser
                      </a>
                    )}
                    {fullDownloadUrl && (
                      <a href={fullDownloadUrl} download className="btn btn-primary btn-sm d-flex align-items-center gap-1" style={{ borderRadius: 7 }}>
                        <Download size={13} /> Download PDF
                      </a>
                    )}
                    <button className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1" style={{ borderRadius: 7 }}
                      onClick={() => { setResult(null); setError(null); setActiveTab('summary'); }}>
                      <RefreshCw size={13} /> Generate Another
                    </button>
                  </div>
                </div>

                {/* Sub-Tabs */}
                <ul className="nav nav-pills mb-3 gap-1" style={{ fontSize: 12 }}>
                  <li className="nav-item">
                    <button className={`nav-link py-1 px-3 ${activeTab === 'summary' ? 'active' : ''}`}
                      onClick={() => setActiveTab('summary')} style={{ borderRadius: 6 }}>
                      📊 Overview & Scoring
                    </button>
                  </li>
                  <li className="nav-item">
                    <button className={`nav-link py-1 px-3 ${activeTab === 'vehicles' ? 'active' : ''}`}
                      onClick={() => setActiveTab('vehicles')} style={{ borderRadius: 6 }}>
                      🚗 Vehicles Operated
                    </button>
                  </li>
                  <li className="nav-item">
                    <button className={`nav-link py-1 px-3 ${activeTab === 'trips' ? 'active' : ''}`}
                      onClick={() => setActiveTab('trips')} style={{ borderRadius: 6 }}>
                      📍 Trip Tracking
                    </button>
                  </li>
                  <li className="nav-item">
                    <button className={`nav-link py-1 px-3 ${activeTab === 'alerts' ? 'active' : ''}`}
                      onClick={() => setActiveTab('alerts')} style={{ borderRadius: 6 }}>
                      ⚠️ Alert Summary
                    </button>
                  </li>
                  {fullPdfUrl && (
                    <li className="nav-item">
                      <button className={`nav-link py-1 px-3 ${activeTab === 'pdf' ? 'active' : ''}`}
                        onClick={() => setActiveTab('pdf')} style={{ borderRadius: 6 }}>
                        📄 PDF Preview
                      </button>
                    </li>
                  )}
                </ul>

                {/* TAB 1: SUMMARY & SAFETY SCORING */}
                {activeTab === 'summary' && (
                  <div>
                    {/* Executive Metric Cards */}
                    {isOverallReport ? (
                      <div className="row g-2 mb-3">
                        {[
                          { label: 'Vehicles Entered', value: reportData?.totalVehiclesEntered, color: '#2563eb', bg: '#eff6ff' },
                          { label: 'Vehicles Exited',  value: reportData?.totalVehiclesExited,  color: '#16a34a', bg: '#f0fdf4' },
                          { label: 'Total Trips',      value: reportData?.totalTrips,           color: '#2563eb', bg: '#eff6ff' },
                          { label: 'Total Distance',   value: (reportData?.totalDistanceKm != null ? reportData.totalDistanceKm + ' km' : '—'), color: '#2563eb', bg: '#eff6ff' },
                          { label: 'Total Duration',   value: reportData?.totalDurationFormatted, color: '#7c3aed', bg: '#f5f3ff' },
                          { label: 'Total Alerts',     value: reportData?.totalAlerts,          color: '#dc2626', bg: '#fef2f2' },
                          { label: 'Avg Safety Score', value: reportData?.averageSafetyScore != null ? reportData.averageSafetyScore + ' / 100' : '—', color: reportData?.averageSafetyScore >= 80 ? '#16a34a' : reportData?.averageSafetyScore >= 60 ? '#d97706' : '#dc2626', bg: '#f0fdf4' },
                          { label: 'Fleet Category',   value: reportData?.performanceCategory,  color: '#0f172a', bg: '#f8fafc' },
                        ].map((m, i) => (
                          <div className="col-3" key={i}>
                            <div style={{ background: m.bg, borderRadius: 10, padding: 10, textAlign: 'center' }}>
                              <div style={{ fontSize: 18, fontWeight: 800, color: m.color }}>{m.value ?? '—'}</div>
                              <div style={{ fontSize: 10, color: '#64748b' }}>{m.label}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="row g-2 mb-3">
                        {[
                          { label: 'Safety Score',     value: (reportData?.safetyScore != null ? reportData.safetyScore + ' / 100' : '—'), color: reportData?.safetyScore >= 80 ? '#16a34a' : reportData?.safetyScore >= 60 ? '#d97706' : '#dc2626', bg: '#eff6ff' },
                          { label: 'Category',         value: reportData?.performanceCategory, color: '#0f172a', bg: '#f8fafc' },
                          { label: 'Total Trips',      value: reportData?.totalTrips,          color: '#2563eb', bg: '#eff6ff' },
                          { label: 'Total Distance',   value: (reportData?.totalDistanceKm != null ? reportData.totalDistanceKm + ' km' : '—'), color: '#2563eb', bg: '#eff6ff' },
                          { label: 'Total Duration',   value: reportData?.totalDurationFormatted, color: '#7c3aed', bg: '#f5f3ff' },
                          { label: 'Vehicles Entered', value: reportData?.totalVehiclesEntered, color: '#2563eb', bg: '#eff6ff' },
                          { label: 'Vehicles Exited',  value: reportData?.totalVehiclesExited,  color: '#16a34a', bg: '#f0fdf4' },
                          { label: 'Total Alerts',     value: reportData?.totalAlerts,          color: '#dc2626', bg: '#fef2f2' },
                        ].map((m, i) => (
                          <div className="col-3" key={i}>
                            <div style={{ background: m.bg, borderRadius: 10, padding: 10, textAlign: 'center' }}>
                              <div style={{ fontSize: 18, fontWeight: 800, color: m.color }}>{m.value ?? '—'}</div>
                              <div style={{ fontSize: 10, color: '#64748b' }}>{m.label}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}

                    {/* DRIVER SAFETY SCORE BREAKDOWN TABLE */}
                    {!isOverallReport && reportData?.safetyBreakdown && (
                      <div className="mb-4">
                        <div className="d-flex align-items-center justify-content-between mb-2">
                          <span className="fw-bold" style={{ fontSize: 13, color: '#0f172a' }}>
                            <ShieldCheck size={14} className="text-primary me-1" />
                            Driver Safety Score Breakdown (Standard Formula)
                          </span>
                          <span style={{ fontSize: 11, color: '#64748b' }}>
                            Safety Score = 100 - Σ(Normalized Event Rate × Weight)
                          </span>
                        </div>
                        <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8 }}>
                          <table className="table table-sm table-hover mb-0" style={{ fontSize: 12 }}>
                            <thead style={{ background: '#f8fafc' }}>
                              <tr>
                                <th>Metric</th><th>Count</th><th>Normalized Rate</th><th>Weight</th><th>Penalty Contribution</th>
                              </tr>
                            </thead>
                            <tbody>
                              {reportData.safetyBreakdown.metrics?.map(m => (
                                <tr key={m.metricName}>
                                  <td className="fw-semibold">{m.metricName}</td>
                                  <td>{m.count != null ? m.count : 'N/A'}</td>
                                  <td>{m.normalizedRate != null ? `${m.normalizedRate} ${m.rateUnit}` : 'N/A'}</td>
                                  <td>× {m.weight}</td>
                                  <td className="text-danger fw-semibold">-{m.penalty} pts</td>
                                </tr>
                              ))}
                              {reportData.safetyBreakdown.manualPenaltyDeductions > 0 && (
                                <tr>
                                  <td className="fw-semibold text-warning">Manager Manual Deductions</td>
                                  <td>—</td><td>Administrative penalties</td><td>—</td>
                                  <td className="text-danger fw-semibold">-{reportData.safetyBreakdown.manualPenaltyDeductions} pts</td>
                                </tr>
                              )}
                              <tr style={{ background: '#f1f5f9', fontWeight: 700 }}>
                                <td colSpan={4} className="text-end">Base Score: 100.00 | Total Deductions: -{reportData.safetyBreakdown.totalWeightedPenalty} pts | Final Score:</td>
                                <td className="text-primary">{reportData.safetyBreakdown.finalSafetyScore} / 100 ({reportData.safetyBreakdown.performanceCategory})</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}

                    {/* FLEET: Driver Performance & Vehicle Utilization Ranking */}
                    {isOverallReport && reportData?.driverAlertDetails && (
                      <div className="mb-3">
                        <div className="fw-bold mb-2" style={{ fontSize: 13, color: '#0f172a' }}>
                          <Users size={14} className="text-primary me-1" />
                          Driver Performance & Vehicle Utilization Ranking (by Safety Score)
                        </div>
                        <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8, maxHeight: 360 }}>
                          <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                            <thead style={{ background: '#f8fafc', position: 'sticky', top: 0 }}>
                              <tr>
                                <th>Driver</th><th>ID</th><th>Vehicles Operated</th><th>Trips</th>
                                <th>Distance</th><th>Duration</th><th>Alerts</th><th>Score</th><th>Category</th>
                              </tr>
                            </thead>
                            <tbody>
                              {reportData.driverAlertDetails.map(d => (
                                <tr key={d.driverId}>
                                  <td className="fw-semibold">{d.driverName}</td>
                                  <td className="text-muted">{d.driverCode}</td>
                                  <td className="text-primary" style={{ maxWidth: 180 }}>{d.vehicles}</td>
                                  <td>{d.tripCount}</td>
                                  <td>{d.totalDistanceKm} km</td>
                                  <td>{d.formattedDuration}</td>
                                  <td className="fw-bold">{d.totalAlerts}</td>
                                  <td className="fw-bold text-success">{d.safetyScore}</td>
                                  <td>
                                    <span className={`badge ${d.performanceCategory === 'Excellent' ? 'bg-success' : d.performanceCategory === 'Average' ? 'bg-warning text-dark' : 'bg-danger'}`}>
                                      {d.performanceCategory}
                                    </span>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* TAB 2: VEHICLES OPERATED */}
                {activeTab === 'vehicles' && (
                  <div>
                    <div className="fw-bold mb-2" style={{ fontSize: 13, color: '#0f172a' }}>
                      <Car size={14} className="text-primary me-1" />
                      {isOverallReport ? 'Fleet Driver-Vehicle Historical Association' : 'Vehicles Operated by Driver During This Period'}
                    </div>
                    <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8, maxHeight: 440 }}>
                      <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                        <thead style={{ background: '#f8fafc', position: 'sticky', top: 0 }}>
                          <tr>
                            <th>Driver ID</th>
                            <th>Driver Name</th>
                            <th>Vehicle Number</th>
                            <th>Trips</th>
                            <th>Distance (km)</th>
                            <th>Duration</th>
                            <th>Total Alerts</th>
                            <th>Overspeed</th>
                            <th>Harsh Braking</th>
                            <th>Other</th>
                          </tr>
                        </thead>
                        <tbody>
                          {(isOverallReport ? reportData?.driverVehicleDetails : reportData?.vehiclesOperated)?.map((v, i) => (
                            <tr key={i}>
                              <td className="text-muted">{v.driverCode || v.driverId}</td>
                              <td className="fw-semibold">{v.driverName}</td>
                              <td className="fw-bold text-primary">{v.vehicleNumber}</td>
                              <td>{v.trips}</td>
                              <td>{v.distanceKm} km</td>
                              <td className="font-monospace">{v.formattedDuration}</td>
                              <td className="fw-bold">{v.alerts}</td>
                              <td>{v.overspeedCount ?? 0}</td>
                              <td>{v.harshBrakingCount ?? 0}</td>
                              <td>{v.otherAlertCount ?? 0}</td>
                            </tr>
                          ))}
                          {!(isOverallReport ? reportData?.driverVehicleDetails : reportData?.vehiclesOperated)?.length && (
                            <tr><td colSpan={10} className="text-center text-muted py-3">No vehicle records found for the selected reporting period.</td></tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {/* TAB 3: TRIP TRACKING (SECTION 4) */}
                {activeTab === 'trips' && (
                  <div>
                    <div className="d-flex align-items-center justify-content-between mb-2">
                      <span className="fw-bold" style={{ fontSize: 13, color: '#0f172a' }}>
                        <MapPin size={14} className="text-primary me-1" />
                        Trip Entry/Exit Tracking (Duration format: HHh MMm SSs)
                      </span>
                      <span className="text-muted" style={{ fontSize: 10 }}>Start/End locations derived from GPS</span>
                    </div>
                    <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8, maxHeight: 440 }}>
                      <table className="table table-sm table-hover mb-0" style={{ fontSize: 10 }}>
                        <thead style={{ background: '#f8fafc', position: 'sticky', top: 0 }}>
                          <tr>
                            <th>Trip ID</th>
                            <th>Vehicle</th>
                            <th>Driver</th>
                            <th>Start Date</th>
                            <th>Start Time</th>
                            <th>Start Location</th>
                            <th>End Date</th>
                            <th>End Time</th>
                            <th>End Location</th>
                            <th>Destination</th>
                            <th>Distance</th>
                            <th>Duration</th>
                            <th>Avg Spd</th>
                            <th>Max Spd</th>
                            <th>Alerts</th>
                            <th>Status</th>
                          </tr>
                        </thead>
                        <tbody>
                          {(reportData?.trips || reportData?.tripDetails || []).map(t => (
                            <tr key={t.tripId}>
                              <td>#{t.tripId}</td>
                              <td className="fw-semibold text-primary">{t.vehicleNumber}</td>
                              <td>{t.driverName}</td>
                              <td>{t.tripStartDate}</td>
                              <td>{t.entryStartTime}</td>
                              <td>{t.startLocation}</td>
                              <td>{t.tripEndDate}</td>
                              <td>{t.exitEndTime}</td>
                              <td>{t.endLocation}</td>
                              <td>{t.destination}</td>
                              <td>{t.totalDistanceKm} km</td>
                              <td className="fw-bold font-monospace">{t.totalTripDurationFormatted}</td>
                              <td>{t.averageSpeedKmph} km/h</td>
                              <td>{t.maxSpeedKmph} km/h</td>
                              <td>{t.alertsDuringTrip}</td>
                              <td><span className="badge bg-light text-dark">{t.tripStatus}</span></td>
                            </tr>
                          ))}
                          {!(reportData?.trips || reportData?.tripDetails)?.length && (
                            <tr><td colSpan={16} className="text-center text-muted py-3">No trips recorded in this period.</td></tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {/* TAB 4: ALERT SUMMARY */}
                {activeTab === 'alerts' && (
                  <div>
                    <div className="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
                      <div className="btn-group btn-group-sm">
                        <button className={`btn ${alertViewMode === 'summary' ? 'btn-primary' : 'btn-outline-primary'}`}
                          onClick={() => setAlertViewMode('summary')}>
                          Vehicle & Type Summary
                        </button>
                        <button className={`btn ${alertViewMode === 'events' ? 'btn-primary' : 'btn-outline-primary'}`}
                          onClick={() => setAlertViewMode('events')}>
                          Individual Event Details
                        </button>
                      </div>
                      <div className="d-flex align-items-center gap-2">
                        <Filter size={13} className="text-muted" />
                        <select className="form-select form-select-sm" style={{ width: 180, fontSize: 11 }}
                          value={alertTypeFilter} onChange={e => setAlertTypeFilter(e.target.value)}>
                          <option value="ALL">All Alert Types</option>
                          {alertTypeOptions.map(t => (
                            <option key={t} value={t}>{t}</option>
                          ))}
                        </select>
                      </div>
                    </div>

                    <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8, maxHeight: 400 }}>
                      <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                        <thead style={{ background: '#f8fafc', position: 'sticky', top: 0 }}>
                          <tr>
                            <th>Vehicle Number</th>
                            <th>Date</th>
                            {alertViewMode === 'events' && <th>Time</th>}
                            <th>Alert Type</th>
                            <th>Severity</th>
                            <th>Count</th>
                            <th>Driver Name</th>
                            <th>Status</th>
                          </tr>
                        </thead>
                        <tbody>
                          {(alertViewMode === 'summary' ? filteredAlertSummaries : filteredAlertEvents).map((a, i) => (
                            <tr key={i}>
                              <td className="fw-semibold text-primary">{a.vehicleNumber}</td>
                              <td>{a.date}</td>
                              {alertViewMode === 'events' && <td>{a.time}</td>}
                              <td>{a.alertType}</td>
                              <td>
                                <span className={`badge ${a.severity === 'CRITICAL' ? 'bg-danger' : 'bg-warning text-dark'}`}>
                                  {a.severity}
                                </span>
                              </td>
                              <td className="fw-bold">{a.count}</td>
                              <td>{a.driverName}</td>
                              <td>{a.status}</td>
                            </tr>
                          ))}
                          {(alertViewMode === 'summary' ? filteredAlertSummaries : filteredAlertEvents).length === 0 && (
                            <tr><td colSpan={8} className="text-center text-muted py-3">No matching alert records in this period.</td></tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {/* TAB 5: PDF PREVIEW */}
                {activeTab === 'pdf' && fullPdfUrl && (
                  <div style={{ borderRadius: 10, overflow: 'hidden', border: '1px solid #e2e8f0' }}>
                    <object data={fullPdfUrl} type="application/pdf" width="100%" height="520">
                      <div style={{ padding: 24, textAlign: 'center', color: '#64748b' }}>
                        <FileText size={32} style={{ marginBottom: 8, color: '#cbd5e1' }} />
                        <div style={{ fontSize: 13, marginBottom: 12 }}>Inline preview not supported by your browser.</div>
                        <a href={fullPdfUrl} target="_blank" rel="noreferrer" className="btn btn-primary btn-sm">
                          <ExternalLink size={13} className="me-1" /> Open PDF
                        </a>
                      </div>
                    </object>
                  </div>
                )}
              </div>
            </div>
          ) : (
            /* Empty State */
            <div className="card border-0 shadow-sm d-flex align-items-center justify-content-center"
              style={{ borderRadius: 14, minHeight: 440 }}>
              <div className="text-center p-4">
                {generating ? (
                  <>
                    <div className="spinner-border text-primary mb-3" style={{ width: 40, height: 40 }} />
                    <h5 className="fw-semibold" style={{ color: '#0f172a' }}>Generating Report...</h5>
                    <p className="text-muted" style={{ fontSize: 13 }}>Compiling data, calculating normalized safety metrics, and creating PDF.</p>
                  </>
                ) : (
                  <>
                    <FileText size={48} className="mb-3 text-secondary" style={{ opacity: .5 }} />
                    <h5 className="fw-semibold" style={{ color: '#0f172a' }}>Select Report Parameters</h5>
                    <p className="text-muted" style={{ fontSize: 13, maxWidth: 360, margin: '0 auto' }}>
                      Choose your reporting period, select a driver or overall fleet scope, and click Generate.
                    </p>
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