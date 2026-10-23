import React, { useState, useEffect } from 'react';
import {
  Car, CalendarDays, BarChart3, CalendarRange,
  SlidersHorizontal, Download, ExternalLink, FileText,
  Users, Loader, CheckCircle, AlertCircle, Globe, User,
  ArrowRight, AlertTriangle, ShieldCheck, Clock, MapPin, Gauge, Filter,
  RefreshCw, CheckSquare, Square, Share2, Printer, Check
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { getDrivers } from '../api/drivers';
import { getVehicles } from '../api/vehicles';
import api from '../api/axios';
import {
  generateDriverReport,
  generateOverallReport,
  generateDriverAlertReport,
  generateFleetAlertReport,
  getPdfUrl,
  getDownloadUrl
} from '../api/reports';

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const SUPPORTED_ALERT_TYPES = [
  'Overspeed Alert',
  'Harsh Braking Alert',
  'Harsh Acceleration Alert',
  'GPS Disconnect Alert',
  'Night Driving Alert',
  'Ignition Alert',
  'Fatigue Alert',
  'Geofence Alert'
];

const PERIOD_TYPES = [
  { name: 'Monthly',  key: 'monthly', icon: <CalendarRange size={18} />, desc: 'Full month performance' },
  { name: 'Weekly',   key: 'weekly',  icon: <BarChart3 size={18} />,     desc: 'Standard 7-day week selector' },
  { name: 'Daily',    key: 'daily',   icon: <CalendarDays size={18} />,  desc: 'Single day breakdown' },
  { name: 'Custom',   key: 'custom',  icon: <SlidersHorizontal size={18} />, desc: 'Custom date range' },
  { name: 'Alert-Based Report', key: 'alert_based', icon: <AlertTriangle size={18} />, desc: 'Generate reports based on selected alert types.' },
];

const DATE_PRESETS = [
  { key: 'today',      label: 'Today' },
  { key: 'yesterday',  label: 'Yesterday' },
  { key: 'this_week',  label: 'This Week' },
  { key: 'last_week',  label: 'Last Week' },
  { key: 'this_month', label: 'This Month' },
  { key: 'last_month', label: 'Last Month' },
  { key: 'custom',     label: 'Custom' },
];

function getDatePresetRange(presetKey) {
  const now = new Date();
  const formatYMD = (d) => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  };

  if (presetKey === 'today') {
    const s = formatYMD(now);
    return { start: s, end: s };
  }
  if (presetKey === 'yesterday') {
    const y = new Date(now);
    y.setDate(y.getDate() - 1);
    const s = formatYMD(y);
    return { start: s, end: s };
  }
  if (presetKey === 'this_week') {
    const current = new Date(now);
    const day = current.getDay();
    const diffToMonday = current.getDate() - day + (day === 0 ? -6 : 1);
    const monday = new Date(current.setDate(diffToMonday));
    return { start: formatYMD(monday), end: formatYMD(now) };
  }
  if (presetKey === 'last_week') {
    const current = new Date(now);
    const day = current.getDay();
    const diffToMonday = current.getDate() - day + (day === 0 ? -6 : 1) - 7;
    const monday = new Date(current.setDate(diffToMonday));
    const sunday = new Date(monday);
    sunday.setDate(sunday.getDate() + 6);
    return { start: formatYMD(monday), end: formatYMD(sunday) };
  }
  if (presetKey === 'this_month') {
    const start = new Date(now.getFullYear(), now.getMonth(), 1);
    return { start: formatYMD(start), end: formatYMD(now) };
  }
  if (presetKey === 'last_month') {
    const start = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const end = new Date(now.getFullYear(), now.getMonth(), 0);
    return { start: formatYMD(start), end: formatYMD(end) };
  }
  return { start: '', end: '' };
}

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
  const { user, isDriver } = useAuth();
  const [shareToast, setShareToast]             = useState(null);
  const [drivers, setDrivers]                   = useState([]);
  const [vehicles, setVehicles]                 = useState([]);
  const [periodType, setPeriodType]             = useState('monthly');
  const [reportScope, setReportScope]           = useState('driver'); // 'driver' | 'overall'
  const [selectedDriverId, setSelectedDriverId] = useState('');

  // Standard period inputs
  const [month, setMonth] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const [week, setWeek] = useState(() => getISOWeekString());
  const [dailyDate, setDailyDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo]     = useState('');

  // Alert-Based Report inputs
  const [selectedAlertTypes, setSelectedAlertTypes] = useState(SUPPORTED_ALERT_TYPES);
  const [alertPreset, setAlertPreset]               = useState('this_month');
  const [alertStartDate, setAlertStartDate]         = useState(() => getDatePresetRange('this_month').start);
  const [alertEndDate, setAlertEndDate]             = useState(() => getDatePresetRange('this_month').end);
  const [selectedVehicleId, setSelectedVehicleId]   = useState('');
  const [alertSeverity, setAlertSeverity]           = useState('ALL');
  const [alertStatus, setAlertStatus]               = useState('ALL');

  const [generating, setGenerating] = useState(false);
  const [result, setResult]         = useState(null);
  const [error, setError]           = useState(null);
  const [activeTab, setActiveTab]   = useState('summary'); // 'summary' | 'vehicles' | 'trips' | 'alerts' | 'pdf'
  const [alertViewMode, setAlertViewMode] = useState('summary'); // 'summary' | 'events'
  const [alertTypeFilter, setAlertTypeFilter] = useState('ALL');

  useEffect(() => {
    if (isDriver && user?.driverId) {
      setReportScope('driver');
      setSelectedDriverId(String(user.driverId));
      getDrivers()
        .then(data => {
          const list = Array.isArray(data) ? data : (data?.content || []);
          const selfList = list.filter(d => String(d.id) === String(user.driverId));
          setDrivers(selfList.length > 0 ? selfList : [{ id: user.driverId, name: user.driverName, code: user.driverCode }]);
        })
        .catch(() => {});
    } else {
      getDrivers()
        .then(data => {
          const list = Array.isArray(data) ? data : (data?.content || []);
          setDrivers(list);
          if (list.length > 0) setSelectedDriverId(String(list[0].id));
        })
        .catch(() => setError('Could not load drivers. Backend may be starting up.'));
    }

    getVehicles()
      .then(data => {
        const list = Array.isArray(data) ? data : (data?.content || []);
        setVehicles(list);
      })
      .catch(() => {});
  }, [isDriver, user?.driverId, user?.driverName, user?.driverCode]);

  const isOverallReport = !isDriver && reportScope === 'overall';
  const weekRange = getWeekRange(week);

  const handlePresetSelect = (presetKey) => {
    setAlertPreset(presetKey);
    if (presetKey !== 'custom') {
      const range = getDatePresetRange(presetKey);
      if (range.start) setAlertStartDate(range.start);
      if (range.end) setAlertEndDate(range.end);
    }
    setResult(null);
  };

  const toggleAlertType = (type) => {
    setResult(null);
    if (selectedAlertTypes.includes(type)) {
      setSelectedAlertTypes(selectedAlertTypes.filter(t => t !== type));
    } else {
      setSelectedAlertTypes([...selectedAlertTypes, type]);
    }
  };

  const selectAllAlertTypes = () => {
    setSelectedAlertTypes(SUPPORTED_ALERT_TYPES);
    setResult(null);
  };

  const clearAllAlertTypes = () => {
    setSelectedAlertTypes([]);
    setResult(null);
  };

  const buildStandardRequestBody = () => {
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
    setError(null);
    setResult(null);

    // Alert-Based Report Flow
    if (periodType === 'alert_based') {
      if (reportScope === 'driver' && !selectedDriverId) {
        setError('Please select a driver for Driver Alert Report.');
        return;
      }
      if (!alertStartDate || !alertEndDate) {
        setError('Please select valid Start and End dates.');
        return;
      }
      if (selectedAlertTypes.length === 0) {
        setError('Please select at least one Alert Type.');
        return;
      }

      setGenerating(true);
      try {
        const body = {
          reportType: 'ALERT_BASED',
          startDate: alertStartDate,
          endDate: alertEndDate,
          datePreset: alertPreset !== 'custom' ? alertPreset.toUpperCase() : null,
          alertTypes: selectedAlertTypes.length === SUPPORTED_ALERT_TYPES.length ? [] : selectedAlertTypes,
          severity: alertSeverity,
          status: alertStatus,
          vehicleId: selectedVehicleId ? Number(selectedVehicleId) : null,
          driverId: reportScope === 'driver' ? Number(selectedDriverId) : null
        };

        let res;
        if (reportScope === 'driver') {
          res = await generateDriverAlertReport(Number(selectedDriverId), body);
        } else {
          res = await generateFleetAlertReport(body);
        }

        if (res && res.reportData) {
          res = {
            ...res.reportData,
            reportId: res.reportId,
            pdfUrl: res.pdfUrl,
            downloadUrl: res.downloadUrl,
            reportType: 'ALERT_BASED',
            generatedAt: res.generatedAt,
            message: res.message
          };
        }

        setResult(res);
        setActiveTab('summary');
      } catch (e) {
        const msg = e?.response?.data?.message || e?.response?.data || e.message;
        setError(`Alert Report generation failed: ${msg}`);
      } finally {
        setGenerating(false);
      }
      return;
    }

    // Standard Period Flow
    const needsDriver = reportScope === 'driver' && !selectedDriverId;
    if (needsDriver) { setError('Please select a driver.'); return; }
    if (periodType === 'custom' && (!customFrom || !customTo)) {
      setError('Please select both Start and End dates.'); return;
    }

    setGenerating(true);
    try {
      const body = buildStandardRequestBody();
      let res;

      if (isOverallReport) {
        const type = (body.reportType || 'MONTHLY').toLowerCase();
        const response = await api.post(`/api/reports/fleet/${type}`, body);
        res = response.data?.data;
        if (res && res.reportData) {
          res = { ...res.reportData, reportId: res.reportId, pdfUrl: res.pdfUrl, downloadUrl: res.downloadUrl, reportType: res.reportType };
        }
      } else {
        res = await generateDriverReport(Number(selectedDriverId), body);
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
  const reportData = result;

  const handleShareReport = async () => {
    if (!result) return;
    const rep = reportData || result;
    const title = rep?.title || (isOverallReport ? 'Fleet Performance Report' : `${rep?.driverName || 'Driver'} Report`);
    const dateStr = `${rep?.startDate || ''} to ${rep?.endDate || ''}`;
    const driverStr = rep?.driverName ? `Driver: ${rep.driverName} (${rep.driverCode || ''})` : 'Fleet Wide';
    const summaryText = `TrackFleet Report: ${title}\nPeriod: ${dateStr}\n${driverStr}\nTotal Distance: ${rep?.totalDistanceKm != null ? rep.totalDistanceKm + ' km' : '—'}\nTotal Alerts: ${rep?.totalAlerts || '0'}\nTotal Penalties: ₹${rep?.totalPenaltyAmount || '0.00'}`;

    const shareUrl = fullPdfUrl || fullDownloadUrl || window.location.href;

    if (navigator.share) {
      try {
        await navigator.share({
          title,
          text: summaryText,
          url: shareUrl,
        });
        return;
      } catch (err) {
        if (err.name !== 'AbortError') {
          console.warn('Web Share API error, using clipboard fallback', err);
        } else {
          return;
        }
      }
    }

    try {
      const textToCopy = `${summaryText}\n\nReport Link: ${shareUrl}`;
      await navigator.clipboard.writeText(textToCopy);
      setShareToast('Report summary and link copied to clipboard!');
      setTimeout(() => setShareToast(null), 4000);
    } catch {
      alert(`Report Link:\n${shareUrl}`);
    }
  };

  const handlePrintReport = () => {
    window.print();
  };

  // Filter alerts for preview in standard reports
  const allAlertSummaries = reportData?.alertSummaries || [];
  const allAlertEvents    = reportData?.alertEvents    || [];
  const filteredAlertSummaries = allAlertSummaries.filter(a =>
    alertTypeFilter === 'ALL' || a.alertType === alertTypeFilter
  );
  const filteredAlertEvents = allAlertEvents.filter(a =>
    alertTypeFilter === 'ALL' || a.alertType === alertTypeFilter
  );
  const alertTypeOptions = [...new Set(allAlertSummaries.map(a => a.alertType))].filter(Boolean);

  const isAlertBasedResult = result?.reportType === 'ALERT_BASED';

  return (
    <div style={{ width: '100%', maxWidth: '100%', minWidth: 0, paddingBottom: 40 }}>
      {/* Header */}
      <div className="mb-4">
        <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0f172a', margin: 0 }}>Fleet & Driver Reports</h1>
        <div style={{ color: '#64748b', fontSize: 13, marginTop: 4 }}>
          Standardized safety scoring, alert-based violation audits, multi-vehicle associations, and server-side PDF generation
        </div>
      </div>

      <div className="row g-4">
        {/* ===== LEFT CONFIGURATION PANEL ===== */}
        <div className="col-12 col-lg-4" style={{ minWidth: 0 }}>
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-body p-4">
              <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: '#0f172a', fontSize: 14 }}>
                <FileText size={16} className="text-primary" /> Report Period
              </h6>

              {/* Period Type Selection Cards */}
              <div className="mb-3">
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 8 }}>
                  {PERIOD_TYPES.map(p => (
                    <button key={p.key}
                      onClick={() => {
                        setPeriodType(p.key);
                        setResult(null);
                        setError(null);
                      }}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 9,
                        padding: '9px 10px', borderRadius: 9, cursor: 'pointer',
                        background: periodType === p.key ? 'linear-gradient(135deg,#2563eb,#1d4ed8)' : '#f8fafc',
                        color: periodType === p.key ? '#fff' : '#374151',
                        border: periodType === p.key ? 'none' : '1px solid #e2e8f0',
                        fontWeight: 600, fontSize: 12, textAlign: 'left', transition: 'all .15s'
                      }}>
                      {React.cloneElement(p.icon, { color: periodType === p.key ? '#fff' : '#64748b' })}
                      <div style={{ minWidth: 0 }}>
                        <div style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.name}</div>
                        <div style={{ fontSize: 9.5, fontWeight: 400, opacity: .8, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.desc}</div>
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
                    ...(!isDriver ? [{ val: 'overall', label: 'Fleet Overview', icon: <Globe size={13}/> }] : []),
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

              {/* ============================================================ */}
              {/* ALERT-BASED CONFIGURATION SECTION                            */}
              {/* ============================================================ */}
              {periodType === 'alert_based' ? (
                <div>
                  {/* Driver selector (Driver Scope) */}
                  {reportScope === 'driver' && (
                    <div className="mb-3">
                      <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                        Driver *
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

                  {/* Alert Types Multi-Select */}
                  <div className="mb-3">
                    <div className="d-flex align-items-center justify-content-between mb-1">
                      <label className="form-label fw-semibold mb-0" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                        Alert Types ({selectedAlertTypes.length}/{SUPPORTED_ALERT_TYPES.length})
                      </label>
                      <div className="d-flex gap-2">
                        <button type="button" className="btn btn-link btn-sm p-0 text-decoration-none" style={{ fontSize: 11 }}
                          onClick={selectAllAlertTypes}>Select All</button>
                        <span className="text-muted" style={{ fontSize: 11 }}>·</span>
                        <button type="button" className="btn btn-link btn-sm p-0 text-decoration-none text-muted" style={{ fontSize: 11 }}
                          onClick={clearAllAlertTypes}>Clear</button>
                      </div>
                    </div>
                    <div style={{ maxHeight: 155, overflowY: 'auto', border: '1px solid #e2e8f0', borderRadius: 8, padding: '6px 8px', background: '#f8fafc' }}>
                      {SUPPORTED_ALERT_TYPES.map(type => {
                        const checked = selectedAlertTypes.includes(type);
                        return (
                          <div key={type}
                            onClick={() => toggleAlertType(type)}
                            style={{
                              display: 'flex', alignItems: 'center', gap: 8, padding: '4px 6px',
                              borderRadius: 5, cursor: 'pointer', fontSize: 12, userSelect: 'none',
                              background: checked ? '#eff6ff' : 'transparent',
                              color: checked ? '#1e40af' : '#475569',
                              marginBottom: 2
                            }}>
                            {checked ? <CheckSquare size={14} className="text-primary" /> : <Square size={14} className="text-muted" />}
                            <span>{type}</span>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {/* Date Range Presets */}
                  <div className="mb-2">
                    <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                      Date Presets
                    </label>
                    <div className="d-flex flex-wrap gap-1">
                      {DATE_PRESETS.map(p => (
                        <button key={p.key}
                          type="button"
                          onClick={() => handlePresetSelect(p.key)}
                          style={{
                            padding: '3px 8px', borderRadius: 6, fontSize: 11, fontWeight: 500,
                            background: alertPreset === p.key ? '#1e293b' : '#f1f5f9',
                            color: alertPreset === p.key ? '#fff' : '#475569',
                            border: 'none', cursor: 'pointer', transition: 'all .15s'
                          }}>
                          {p.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Start & End Dates */}
                  <div className="row g-2 mb-3">
                    <div className="col-6">
                      <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>Start Date</label>
                      <input type="date" className="form-control form-control-sm"
                        value={alertStartDate}
                        onChange={e => { setAlertStartDate(e.target.value); setAlertPreset('custom'); setResult(null); }} />
                    </div>
                    <div className="col-6">
                      <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>End Date</label>
                      <input type="date" className="form-control form-control-sm"
                        value={alertEndDate}
                        onChange={e => { setAlertEndDate(e.target.value); setAlertPreset('custom'); setResult(null); }} />
                    </div>
                  </div>

                  {/* Vehicle Filter */}
                  <div className="mb-3">
                    <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>
                      Vehicle Filter (Optional)
                    </label>
                    <select className="form-select form-select-sm"
                      value={selectedVehicleId}
                      onChange={e => { setSelectedVehicleId(e.target.value); setResult(null); }}>
                      <option value="">{reportScope === 'driver' ? 'All Vehicles Operated by Driver' : 'All Fleet Vehicles'}</option>
                      {vehicles.map(v => (
                        <option key={v.id} value={v.id}>{v.registrationNumber} ({v.code})</option>
                      ))}
                    </select>
                  </div>

                  {/* Severity & Status Filters */}
                  <div className="row g-2 mb-3">
                    <div className="col-6">
                      <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>Severity</label>
                      <select className="form-select form-select-sm"
                        value={alertSeverity}
                        onChange={e => { setAlertSeverity(e.target.value); setResult(null); }}>
                        <option value="ALL">All Severities</option>
                        <option value="CRITICAL">Critical</option>
                        <option value="WARNING">Warning</option>
                        <option value="INFO">Info</option>
                      </select>
                    </div>
                    <div className="col-6">
                      <label className="form-label fw-semibold" style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.05em' }}>Status</label>
                      <select className="form-select form-select-sm"
                        value={alertStatus}
                        onChange={e => { setAlertStatus(e.target.value); setResult(null); }}>
                        <option value="ALL">All Statuses</option>
                        <option value="OPEN">Open</option>
                        <option value="RESOLVED">Resolved</option>
                        <option value="HISTORICAL">Historical</option>
                      </select>
                    </div>
                  </div>
                </div>
              ) : (
                /* ============================================================ */
                /* STANDARD PERIOD CONFIGURATION SECTION (Preserved)           */
                /* ============================================================ */
                <div>
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
                </div>
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
                {generating ? (
                  <><Loader size={15} className="spin" /> Generating Report...</>
                ) : periodType === 'alert_based' ? (
                  <><AlertTriangle size={15} /> Generate Alert Report</>
                ) : (
                  <><FileText size={15} /> Generate {isOverallReport ? 'Fleet' : 'Driver'} Report</>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* ===== REPORT VIEW & TABS ===== */}
        <div className="col-12 col-lg-8" style={{ minWidth: 0 }}>
          {result ? (
            <div className="card border-0 shadow-sm printable-report-area" style={{ borderRadius: 14 }}>
              <div className="card-body p-4">
                {/* Header Actions */}
                <div className="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3 pb-3 border-bottom">
                  <div>
                    <span className="badge bg-primary me-2">
                      {isAlertBasedResult ? 'ALERT-BASED REPORT' : (result?.reportType || periodType.toUpperCase())}
                    </span>
                    <span className="fw-bold" style={{ fontSize: 16 }}>
                      {reportData?.title || (isOverallReport ? 'Fleet Performance Report' : `${reportData?.driverName || 'Driver'} Report`)}
                    </span>
                    <div className="text-muted" style={{ fontSize: 11, marginTop: 2 }}>
                      Period: {reportData?.startDate} to {reportData?.endDate}
                      {reportData?.vehicleNumberFilter && ` · Vehicle: ${reportData.vehicleNumberFilter}`}
                      {reportData?.severityFilter && reportData.severityFilter !== 'All' && ` · Severity: ${reportData.severityFilter}`}
                      {reportData?.statusFilter && reportData.statusFilter !== 'All' && ` · Status: ${reportData.statusFilter}`}
                    </div>
                  </div>
                  <div className="d-flex gap-2 flex-wrap d-print-none">
                    <button
                      id="btn-share-report"
                      className="btn btn-outline-success btn-sm d-flex align-items-center gap-1"
                      style={{ borderRadius: 7 }}
                      onClick={handleShareReport}
                      title="Share report summary or native share"
                    >
                      <Share2 size={13} /> Share
                    </button>
                    <button
                      id="btn-print-report"
                      className="btn btn-outline-dark btn-sm d-flex align-items-center gap-1"
                      style={{ borderRadius: 7 }}
                      onClick={handlePrintReport}
                      title="Print A4 report"
                    >
                      <Printer size={13} /> Print
                    </button>
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

                {shareToast && (
                  <div className="alert alert-success py-2 px-3 mb-3 d-flex align-items-center gap-2 d-print-none" style={{ fontSize: 13, borderRadius: 8 }}>
                    <Check size={16} /> {shareToast}
                  </div>
                )}

                {/* ============================================================ */}
                {/* SUB-TABS: ALERT-BASED REPORT                                 */}
                {/* ============================================================ */}
                {isAlertBasedResult ? (
                  <div>
                    <ul className="nav nav-pills mb-3 gap-1" style={{ fontSize: 12 }}>
                      <li className="nav-item">
                        <button className={`nav-link py-1 px-3 ${activeTab === 'summary' ? 'active' : ''}`}
                          onClick={() => setActiveTab('summary')} style={{ borderRadius: 6 }}>
                          📊 Executive Summary
                        </button>
                      </li>
                      {reportScope === 'driver' ? (
                        <li className="nav-item">
                          <button className={`nav-link py-1 px-3 ${activeTab === 'vehicles' ? 'active' : ''}`}
                            onClick={() => setActiveTab('vehicles')} style={{ borderRadius: 6 }}>
                            🚗 Vehicles Operated ({reportData?.vehiclesOperated?.length || 0})
                          </button>
                        </li>
                      ) : (
                        <>
                          <li className="nav-item">
                            <button className={`nav-link py-1 px-3 ${activeTab === 'vehicle_summary' ? 'active' : ''}`}
                              onClick={() => setActiveTab('vehicle_summary')} style={{ borderRadius: 6 }}>
                              🚗 Vehicle Summary ({reportData?.vehicleSummaries?.length || 0})
                            </button>
                          </li>
                          <li className="nav-item">
                            <button className={`nav-link py-1 px-3 ${activeTab === 'driver_summary' ? 'active' : ''}`}
                              onClick={() => setActiveTab('driver_summary')} style={{ borderRadius: 6 }}>
                              👤 Driver Summary ({reportData?.driverSummaries?.length || 0})
                            </button>
                          </li>
                        </>
                      )}
                      <li className="nav-item">
                        <button className={`nav-link py-1 px-3 ${activeTab === 'alert_dist' ? 'active' : ''}`}
                          onClick={() => setActiveTab('alert_dist')} style={{ borderRadius: 6 }}>
                          ⚠️ Alert Distributions
                        </button>
                      </li>
                      <li className="nav-item">
                        <button className={`nav-link py-1 px-3 ${activeTab === 'details' ? 'active' : ''}`}
                          onClick={() => setActiveTab('details')} style={{ borderRadius: 6 }}>
                          📋 Detailed Events ({reportData?.detailedAlerts?.length || 0})
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

                    {/* TAB 1: EXECUTIVE SUMMARY */}
                    {activeTab === 'summary' && (
                      <div>
                        {reportScope === 'driver' ? (
                          <>
                            <div className="row g-2 mb-3">
                              {[
                                { label: 'Matching Alerts', value: reportData?.totalAlerts, color: reportData?.totalAlerts === 0 ? '#16a34a' : '#dc2626', bg: '#fef2f2' },
                                { label: 'Safety Score',    value: (reportData?.safetyScore?.finalSafetyScore != null ? reportData.safetyScore.finalSafetyScore + ' / 100' : '—'), color: (reportData?.safetyScore?.finalSafetyScore >= 80 ? '#16a34a' : reportData?.safetyScore?.finalSafetyScore >= 60 ? '#d97706' : '#dc2626'), bg: '#eff6ff' },
                                { label: 'Category',        value: reportData?.safetyScore?.performanceCategory || '—', color: '#0f172a', bg: '#f8fafc' },
                                { label: 'Vehicles Operated', value: reportData?.vehiclesOperated?.length || 0, color: '#2563eb', bg: '#eff6ff' },
                                { label: 'Distance Driven', value: (reportData?.safetyScore?.totalDistanceKm != null ? reportData.safetyScore.totalDistanceKm + ' km' : '—'), color: '#2563eb', bg: '#eff6ff' },
                                { label: 'Critical Alerts', value: reportData?.alertSeverityBreakdown?.CRITICAL || 0, color: (reportData?.alertSeverityBreakdown?.CRITICAL > 0 ? '#dc2626' : '#16a34a'), bg: '#fef2f2' },
                                { label: 'Warning Alerts',  value: reportData?.alertSeverityBreakdown?.WARNING || 0, color: '#d97706', bg: '#fffbeb' },
                                { label: 'Active Penalties', value: reportData?.penalties?.length || 0, color: (reportData?.penalties?.length > 0 ? '#dc2626' : '#16a34a'), bg: '#f8fafc' },
                              ].map((m, i) => (
                                <div className="col-3" key={i}>
                                  <div style={{ background: m.bg, borderRadius: 10, padding: 10, textAlign: 'center' }}>
                                    <div style={{ fontSize: 17, fontWeight: 800, color: m.color }}>{m.value ?? '—'}</div>
                                    <div style={{ fontSize: 10, color: '#64748b' }}>{m.label}</div>
                                  </div>
                                </div>
                              ))}
                            </div>

                            {/* Recommendations */}
                            {reportData?.recommendations && reportData.recommendations.length > 0 && (
                              <div className="card mb-3 border-0" style={{ background: '#f8fafc', borderRadius: 10 }}>
                                <div className="card-body p-3">
                                  <h6 className="fw-bold mb-2 text-primary" style={{ fontSize: 13 }}>💡 Driving Recommendations</h6>
                                  <div style={{ fontSize: 12 }}>
                                    {reportData.recommendations.map((r, i) => (
                                      <div key={i} className="mb-1 text-secondary">{r}</div>
                                    ))}
                                  </div>
                                </div>
                              </div>
                            )}

                            {/* Penalties Summary */}
                            <div className="card border-0 mb-3" style={{ background: '#f8fafc', borderRadius: 10 }}>
                              <div className="card-body p-3">
                                <h6 className="fw-bold mb-2" style={{ fontSize: 13, color: '#0f172a' }}>⚠️ Driver Penalty Records</h6>
                                {reportData?.penalties && reportData.penalties.length > 0 ? (
                                  <div className="table-responsive" style={{ maxHeight: 200 }}>
                                    <table className="table table-sm mb-0" style={{ fontSize: 11 }}>
                                      <thead>
                                        <tr>
                                          <th>ID</th>
                                          <th>Type</th>
                                          <th>Reason</th>
                                          <th>Points</th>
                                          <th>Fine (₹)</th>
                                          <th>Status</th>
                                        </tr>
                                      </thead>
                                      <tbody>
                                        {reportData.penalties.map(p => (
                                          <tr key={p.id}>
                                            <td>#{p.id}</td>
                                            <td>{p.penaltyType}</td>
                                            <td>{p.reason}</td>
                                            <td className="fw-bold text-danger">-{p.penaltyPoints} pts</td>
                                            <td>₹{p.monetaryAmount}</td>
                                            <td><span className="badge bg-light text-dark">{p.status}</span></td>
                                          </tr>
                                        ))}
                                      </tbody>
                                    </table>
                                  </div>
                                ) : (
                                  <div className="text-muted" style={{ fontSize: 11 }}>No active manager penalties registered for this driver in this period.</div>
                                )}
                              </div>
                            </div>
                          </>
                        ) : (
                          /* Fleet Scope Summary */
                          <>
                            <div className="row g-2 mb-3">
                              {[
                                { label: 'Total Alerts',     value: reportData?.totalAlerts, color: reportData?.totalAlerts === 0 ? '#16a34a' : '#dc2626', bg: '#fef2f2' },
                                { label: 'Vehicles Covered', value: reportData?.totalVehiclesCovered, color: '#2563eb', bg: '#eff6ff' },
                                { label: 'Drivers Covered',  value: reportData?.totalDriversCovered,  color: '#2563eb', bg: '#eff6ff' },
                                { label: 'Critical Alerts',  value: reportData?.alertSeverityBreakdown?.CRITICAL || 0, color: (reportData?.alertSeverityBreakdown?.CRITICAL > 0 ? '#dc2626' : '#16a34a'), bg: '#fef2f2' },
                                { label: 'Warning Alerts',   value: reportData?.alertSeverityBreakdown?.WARNING || 0, color: '#d97706', bg: '#fffbeb' },
                                { label: 'Info Alerts',      value: reportData?.alertSeverityBreakdown?.INFO || 0, color: '#64748b', bg: '#f8fafc' },
                                { label: 'Open Alerts',      value: reportData?.alertStatusBreakdown?.OPEN || 0, color: (reportData?.alertStatusBreakdown?.OPEN > 0 ? '#dc2626' : '#16a34a'), bg: '#fef2f2' },
                                { label: 'Resolved Alerts',  value: reportData?.alertStatusBreakdown?.RESOLVED || 0, color: '#16a34a', bg: '#f0fdf4' },
                              ].map((m, i) => (
                                <div className="col-3" key={i}>
                                  <div style={{ background: m.bg, borderRadius: 10, padding: 10, textAlign: 'center' }}>
                                    <div style={{ fontSize: 17, fontWeight: 800, color: m.color }}>{m.value ?? '—'}</div>
                                    <div style={{ fontSize: 10, color: '#64748b' }}>{m.label}</div>
                                  </div>
                                </div>
                              ))}
                            </div>

                            {/* Daily Trends */}
                            {reportData?.dailyTrends && reportData.dailyTrends.length > 0 && (
                              <div className="card border-0 mb-3" style={{ background: '#f8fafc', borderRadius: 10 }}>
                                <div className="card-body p-3">
                                  <h6 className="fw-bold mb-2 text-primary" style={{ fontSize: 13 }}>📅 Daily Alert Volume Trends</h6>
                                  <div className="d-flex flex-wrap gap-2">
                                    {reportData.dailyTrends.map(tr => (
                                      <div key={tr.date} style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 8, padding: '6px 12px', minWidth: 100, textAlign: 'center' }}>
                                        <div style={{ fontSize: 10, color: '#64748b' }}>{tr.date}</div>
                                        <div style={{ fontSize: 14, fontWeight: 700, color: '#dc2626' }}>{tr.count} alerts</div>
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              </div>
                            )}
                          </>
                        )}
                      </div>
                    )}

                    {/* TAB: VEHICLES OPERATED (Driver Scope) */}
                    {activeTab === 'vehicles' && reportScope === 'driver' && (
                      <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8 }}>
                        <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                          <thead style={{ background: '#f8fafc' }}>
                            <tr>
                              <th>Vehicle Registration</th>
                              <th>Vehicle Code</th>
                              <th>Trips Completed</th>
                              <th>Distance Driven</th>
                              <th>Driving Duration</th>
                              <th>Alerts Generated</th>
                              <th>Overspeed</th>
                              <th>Harsh Braking</th>
                            </tr>
                          </thead>
                          <tbody>
                            {(reportData?.vehiclesOperated || []).map(v => (
                              <tr key={v.vehicleNumber}>
                                <td className="fw-semibold text-primary">{v.vehicleNumber}</td>
                                <td>{v.vehicleCode || '—'}</td>
                                <td>{v.trips}</td>
                                <td>{v.distanceKm} km</td>
                                <td>{v.formattedDuration}</td>
                                <td className="fw-bold text-danger">{v.alerts}</td>
                                <td>{v.overspeedCount}</td>
                                <td>{v.harshBrakingCount}</td>
                              </tr>
                            ))}
                            {(!reportData?.vehiclesOperated || reportData.vehiclesOperated.length === 0) && (
                              <tr><td colSpan={8} className="text-center text-muted py-3">No vehicles operated during this period.</td></tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                    )}

                    {/* TAB: VEHICLE SUMMARY (Fleet Scope) */}
                    {activeTab === 'vehicle_summary' && reportScope === 'overall' && (
                      <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8, maxHeight: 420 }}>
                        <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                          <thead style={{ background: '#f8fafc', position: 'sticky', top: 0 }}>
                            <tr>
                              <th>Vehicle Reg</th>
                              <th>Code</th>
                              <th>Total Alerts</th>
                              <th>Overspeed</th>
                              <th>Harsh Braking</th>
                              <th>Harsh Accel</th>
                              <th>GPS Disconnect</th>
                              <th>Night Driving</th>
                              <th>Other</th>
                            </tr>
                          </thead>
                          <tbody>
                            {(reportData?.vehicleSummaries || []).map(v => (
                              <tr key={v.vehicleId}>
                                <td className="fw-semibold text-primary">{v.vehicleNumber}</td>
                                <td>{v.vehicleCode}</td>
                                <td className="fw-bold text-danger">{v.totalAlerts}</td>
                                <td>{v.overspeedCount}</td>
                                <td>{v.harshBrakingCount}</td>
                                <td>{v.harshAccelCount}</td>
                                <td>{v.gpsDisconnectCount}</td>
                                <td>{v.nightDrivingCount}</td>
                                <td>{v.otherCount}</td>
                              </tr>
                            ))}
                            {(!reportData?.vehicleSummaries || reportData.vehicleSummaries.length === 0) && (
                              <tr><td colSpan={9} className="text-center text-muted py-3">No vehicle alerts matching the active filters.</td></tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                    )}

                    {/* TAB: DRIVER SUMMARY (Fleet Scope) */}
                    {activeTab === 'driver_summary' && reportScope === 'overall' && (
                      <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8, maxHeight: 420 }}>
                        <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                          <thead style={{ background: '#f8fafc', position: 'sticky', top: 0 }}>
                            <tr>
                              <th>Driver Name</th>
                              <th>Driver ID</th>
                              <th>Vehicles Operated</th>
                              <th>Total Alerts</th>
                              <th>Overspeed</th>
                              <th>Harsh Braking</th>
                              <th>Harsh Accel</th>
                              <th>GPS Disc</th>
                              <th>Night/Other</th>
                            </tr>
                          </thead>
                          <tbody>
                            {(reportData?.driverSummaries || []).map(d => (
                              <tr key={d.driverId}>
                                <td className="fw-semibold text-primary">{d.driverName}</td>
                                <td>{d.driverCode}</td>
                                <td style={{ maxWidth: 200, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={d.vehiclesOperated}>
                                  {d.vehiclesOperated}
                                </td>
                                <td className="fw-bold text-danger">{d.totalAlerts}</td>
                                <td>{d.overspeedCount}</td>
                                <td>{d.harshBrakingCount}</td>
                                <td>{d.harshAccelCount}</td>
                                <td>{d.gpsDisconnectCount}</td>
                                <td>{(d.nightDrivingCount || 0) + (d.otherCount || 0) + (d.ignitionCount || 0)}</td>
                              </tr>
                            ))}
                            {(!reportData?.driverSummaries || reportData.driverSummaries.length === 0) && (
                              <tr><td colSpan={9} className="text-center text-muted py-3">No driver alerts matching the active filters.</td></tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                    )}

                    {/* TAB: ALERT DISTRIBUTIONS */}
                    {activeTab === 'alert_dist' && (
                      <div className="row g-3">
                        <div className="col-12 col-md-6">
                          <div className="card border-0 shadow-sm" style={{ background: '#f8fafc', borderRadius: 10 }}>
                            <div className="card-body p-3">
                              <h6 className="fw-bold text-primary mb-2" style={{ fontSize: 12 }}>Alert Category Breakdown</h6>
                              <div className="table-responsive">
                                <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                                  <thead>
                                    <tr><th>Category</th><th className="text-end">Count</th></tr>
                                  </thead>
                                  <tbody>
                                    {Object.entries(reportData?.alertTypeBreakdown || {}).map(([k, v]) => (
                                      <tr key={k}>
                                        <td>{k}</td>
                                        <td className="text-end fw-bold">{v}</td>
                                      </tr>
                                    ))}
                                    {Object.keys(reportData?.alertTypeBreakdown || {}).length === 0 && (
                                      <tr><td colSpan={2} className="text-center text-muted">No alerts recorded</td></tr>
                                    )}
                                  </tbody>
                                </table>
                              </div>
                            </div>
                          </div>
                        </div>

                        <div className="col-12 col-md-6">
                          <div className="card border-0 shadow-sm" style={{ background: '#f8fafc', borderRadius: 10 }}>
                            <div className="card-body p-3">
                              <h6 className="fw-bold text-primary mb-2" style={{ fontSize: 12 }}>Severity & Lifecycle Breakdown</h6>
                              <div className="table-responsive">
                                <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                                  <thead>
                                    <tr><th>Classification</th><th className="text-end">Count</th></tr>
                                  </thead>
                                  <tbody>
                                    {Object.entries(reportData?.alertSeverityBreakdown || {}).map(([k, v]) => (
                                      <tr key={k}>
                                        <td>Severity: {k}</td>
                                        <td className="text-end fw-bold">{v}</td>
                                      </tr>
                                    ))}
                                    {Object.entries(reportData?.alertStatusBreakdown || {}).map(([k, v]) => (
                                      <tr key={k}>
                                        <td>Status: {k}</td>
                                        <td className="text-end fw-bold">{v}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    )}

                    {/* TAB: DETAILED EVENTS */}
                    {activeTab === 'details' && (
                      <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8, maxHeight: 420 }}>
                        <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                          <thead style={{ background: '#f8fafc', position: 'sticky', top: 0 }}>
                            <tr>
                              <th>Sr</th>
                              <th>Date</th>
                              <th>Time</th>
                              <th>Vehicle</th>
                              {reportScope === 'overall' && <th>Driver</th>}
                              <th>Alert Type</th>
                              <th>Severity</th>
                              <th>Location</th>
                              <th>Status</th>
                            </tr>
                          </thead>
                          <tbody>
                            {(reportData?.detailedAlerts || []).map(a => (
                              <tr key={a.srNo}>
                                <td>#{a.srNo}</td>
                                <td>{a.date}</td>
                                <td>{a.time}</td>
                                <td className="fw-semibold text-primary">{a.vehicleNumber}</td>
                                {reportScope === 'overall' && <td>{a.driverName}</td>}
                                <td>{a.alertType}</td>
                                <td>
                                  <span className={`badge ${a.severity === 'CRITICAL' ? 'bg-danger' : a.severity === 'WARNING' ? 'bg-warning text-dark' : 'bg-info text-dark'}`}>
                                    {a.severity}
                                  </span>
                                </td>
                                <td style={{ maxWidth: 220, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={a.location}>
                                  {a.location}
                                </td>
                                <td>
                                  <span className={`badge ${a.status === 'RESOLVED' ? 'bg-success' : a.status === 'OPEN' ? 'bg-primary' : 'bg-secondary'}`}>
                                    {a.status}
                                  </span>
                                </td>
                              </tr>
                            ))}
                            {(!reportData?.detailedAlerts || reportData.detailedAlerts.length === 0) && (
                              <tr><td colSpan={9} className="text-center text-muted py-3">No matching alert events in this period.</td></tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                    )}

                    {/* TAB: PDF PREVIEW */}
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
                ) : (
                  /* ============================================================ */
                  /* SUB-TABS: STANDARD REPORTS (Preserved)                       */
                  /* ============================================================ */
                  <div>
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

                        {/* SECTION B: Vehicles Operated by Driver (or Fleet Overview) */}
                        <div className="card border-0 mb-3" style={{ background: '#f8fafc', borderRadius: 10 }}>
                          <div className="card-body p-3">
                            <h6 className="fw-bold mb-2 text-primary" style={{ fontSize: 13 }}>
                              🚗 {isOverallReport ? 'Vehicles Covered & Operating Details' : 'Vehicles Operated in This Period'}
                            </h6>
                            <div className="table-responsive">
                              <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                                <thead>
                                  <tr>
                                    {isOverallReport && <th>Driver</th>}
                                    <th>Vehicle Number</th>
                                    <th>Trips</th>
                                    <th>Distance</th>
                                    <th>Duration</th>
                                    <th>Alerts</th>
                                    <th>Overspeed</th>
                                    <th>Harsh Brake</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {(reportData?.driverVehicleDetails || reportData?.vehiclesOperated || []).map((v, i) => (
                                    <tr key={i}>
                                      {isOverallReport && <td>{v.driverName}</td>}
                                      <td className="fw-semibold text-primary">{v.vehicleNumber}</td>
                                      <td>{v.trips}</td>
                                      <td>{v.distanceKm} km</td>
                                      <td>{v.formattedDuration}</td>
                                      <td>{v.alerts}</td>
                                      <td>{v.overspeedCount}</td>
                                      <td>{v.harshBrakingCount}</td>
                                    </tr>
                                  ))}
                                  {!(reportData?.driverVehicleDetails || reportData?.vehiclesOperated)?.length && (
                                    <tr><td colSpan={8} className="text-center text-muted">No vehicle operations in this period</td></tr>
                                  )}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        </div>
                      </div>
                    )}

                    {/* TAB 2: VEHICLES */}
                    {activeTab === 'vehicles' && (
                      <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8 }}>
                        <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
                          <thead style={{ background: '#f8fafc' }}>
                            <tr>
                              <th>Vehicle Number</th>
                              <th>Vehicle Code</th>
                              <th>Trips</th>
                              <th>Distance (km)</th>
                              <th>Duration</th>
                              <th>Alerts</th>
                              <th>Overspeed</th>
                              <th>Harsh Brake</th>
                            </tr>
                          </thead>
                          <tbody>
                            {(reportData?.vehiclesOperated || reportData?.driverVehicleDetails || []).map(v => (
                              <tr key={v.vehicleNumber}>
                                <td className="fw-semibold text-primary">{v.vehicleNumber}</td>
                                <td>{v.vehicleCode || '—'}</td>
                                <td>{v.trips}</td>
                                <td>{v.distanceKm} km</td>
                                <td>{v.formattedDuration}</td>
                                <td>{v.alerts}</td>
                                <td>{v.overspeedCount}</td>
                                <td>{v.harshBrakingCount}</td>
                              </tr>
                            ))}
                            {!(reportData?.vehiclesOperated || reportData?.driverVehicleDetails)?.length && (
                              <tr><td colSpan={8} className="text-center text-muted py-3">No vehicles operated in this period.</td></tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                    )}

                    {/* TAB 3: TRIPS */}
                    {activeTab === 'trips' && (
                      <div className="table-responsive" style={{ border: '1px solid #e2e8f0', borderRadius: 8, maxHeight: 420 }}>
                        <table className="table table-sm table-hover mb-0" style={{ fontSize: 11 }}>
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