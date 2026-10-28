import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Form, Button, Table, Badge, Spinner, Alert } from 'react-bootstrap';
import {
  FileText, Printer, Share2, Calendar, CalendarDays, CalendarRange,
  BarChart3, SlidersHorizontal, AlertTriangle, ShieldCheck, IndianRupee,
  Clock, RefreshCw, CheckCircle2, Download, ExternalLink, Truck, MapPin,
  Gauge, AlertCircle, Check, CheckSquare, Square, Eye
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { driverPortalApi } from '../../api/driverPortal';
import {
  generateDriverReport,
  generateDriverAlertReport,
  getPdfUrl,
  getDownloadUrl
} from '../../api/reports';

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

export default function DriverReports() {
  const { user } = useAuth();
  const [periodType, setPeriodType] = useState('monthly');
  const [shareToast, setShareToast] = useState(null);
  const [vehicles, setVehicles] = useState([]);

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

  // Generation state & results
  const [generating, setGenerating] = useState(false);
  const [result, setResult]         = useState(null);
  const [error, setError]           = useState(null);
  const [activeTab, setActiveTab]   = useState('summary');
  const [alertViewMode, setAlertViewMode] = useState('summary');

  const weekRange = getWeekRange(week);

  // Load driver's authorized vehicles (current assigned + historically operated)
  useEffect(() => {
    driverPortalApi.getVehicles()
      .then(data => {
        setVehicles(Array.isArray(data) ? data : []);
      })
      .catch(err => {
        console.warn('Could not load driver vehicles:', err);
      });
  }, []);

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
      body.year  = parseInt(year, 10);
      body.month = parseInt(mon, 10);
    } else if (periodType === 'daily') {
      body.date = dailyDate;
    } else if (periodType === 'custom') {
      body.startDate = customFrom;
      body.endDate   = customTo;
    }
    return body;
  };

  const handleGenerate = async (e) => {
    if (e) e.preventDefault();
    setError(null);
    setResult(null);

    // 1. Alert-Based Report Flow
    if (periodType === 'alert_based') {
      if (!alertStartDate || !alertEndDate) {
        setError('Please select valid Start and End dates for the Alert Report.');
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
          driverId: user?.driverId ? Number(user.driverId) : null
        };

        const res = await driverPortalApi.generateReport(body);
        let formatted = res;
        if (res && res.reportData) {
          formatted = {
            ...res.reportData,
            reportId: res.reportId,
            pdfUrl: res.pdfUrl,
            downloadUrl: res.downloadUrl,
            reportType: 'ALERT_BASED',
            generatedAt: res.generatedAt,
            message: res.message
          };
        }
        setResult(formatted);
        setActiveTab('summary');
      } catch (err) {
        console.error('Failed to generate driver alert report:', err);
        setError(err?.response?.data?.message || err.message || 'Failed to generate Alert Report. Please try again.');
      } finally {
        setGenerating(false);
      }
      return;
    }

    // 2. Standard Period Flow (Monthly, Weekly, Daily, Custom)
    if (periodType === 'custom' && (!customFrom || !customTo)) {
      setError('Please select both Start and End dates for the Custom Report.');
      return;
    }

    setGenerating(true);
    try {
      const body = buildStandardRequestBody();
      body.driverId = user?.driverId ? Number(user.driverId) : null;

      const res = await driverPortalApi.generateReport(body);
      let formatted = res;
      if (res && res.reportData) {
        formatted = {
          ...res.reportData,
          reportId: res.reportId,
          pdfUrl: res.pdfUrl,
          downloadUrl: res.downloadUrl,
          reportType: res.reportType,
          generatedAt: res.generatedAt,
          message: res.message
        };
      }
      setResult(formatted);
      setActiveTab('summary');
    } catch (err) {
      console.error('Failed to generate driver report:', err);
      setError(err?.response?.data?.message || err.message || 'Failed to generate Report. Please try again.');
    } finally {
      setGenerating(false);
    }
  };

  // Initial load: generate current month report automatically
  useEffect(() => {
    handleGenerate();
  }, []);

  const fullPdfUrl      = result?.reportId ? getPdfUrl(result.reportId) : (result?.pdfUrl ? `${BASE}${result.pdfUrl}` : null);
  const fullDownloadUrl = result?.reportId ? getDownloadUrl(result.reportId) : (result?.downloadUrl ? `${BASE}${result.downloadUrl}` : null);
  const isAlertBasedResult = result?.reportType === 'ALERT_BASED' || periodType === 'alert_based';

  const copyToClipboard = (text) => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        setShareToast({ type: 'success', message: 'Report summary copied to clipboard!' });
        setTimeout(() => setShareToast(null), 4000);
      }).catch(() => {
        setShareToast({ type: 'danger', message: 'Failed to copy report to clipboard.' });
      });
    }
  };

  const handleShare = async () => {
    if (!result) return;
    const rep = result;
    const reportTitle = `TrackFleet Driver Report - ${rep?.driverName || user?.driverName || 'Driver'}`;
    const reportText = `TrackFleet Personal Report:
Driver: ${rep?.driverName || user?.driverName || 'Driver'} (${rep?.driverCode || user?.driverCode || '—'})
Report Type: ${result.reportType || periodType.toUpperCase()}
Period: ${rep?.startDate || rep?.periodStart || ''} to ${rep?.endDate || rep?.periodEnd || ''}
Safety Score: ${rep?.safetyScore ?? rep?.driverSafetyScore ?? '100'}/100
Total Trips: ${rep?.totalTrips ?? 0}
Total Distance: ${rep?.totalDistanceKm != null ? Number(rep.totalDistanceKm).toFixed(1) + ' km' : '0.0 km'}
Total Alerts: ${rep?.totalAlerts ?? 0}
Total Penalties: ₹${rep?.totalPenaltyAmount ?? rep?.penalties?.totalActiveAmount ?? 0}`;

    if (navigator.share) {
      try {
        await navigator.share({
          title: reportTitle,
          text: reportText,
          url: fullPdfUrl || window.location.href,
        });
        setShareToast({ type: 'success', message: 'Report shared successfully!' });
      } catch (err) {
        if (err.name !== 'AbortError') {
          copyToClipboard(reportText);
        }
      }
    } else {
      copyToClipboard(reportText);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  return (
    <div style={{ fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif" }}>
      {/* Toast Alert for Share Feedback */}
      {shareToast && (
        <Alert
          variant={shareToast.type === 'success' ? 'success' : 'danger'}
          dismissible
          onClose={() => setShareToast(null)}
          className="mb-4 rounded-3 d-flex align-items-center gap-2 no-print"
        >
          <CheckCircle2 size={16} />
          <span>{shareToast.message}</span>
        </Alert>
      )}

      {/* Header */}
      <div className="no-print d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
        <div>
          <h1 className="h3 mb-1 fw-bold" style={{ color: '#0f172a' }}>My Reports</h1>
          <p className="text-muted mb-0" style={{ fontSize: 13 }}>
            Generate, preview, and export all 5 official report types filtered exclusively to your driving records.
          </p>
        </div>

        {result && (
          <div className="d-flex align-items-center gap-2">
            <Button
              variant="outline-secondary"
              size="sm"
              onClick={handleShare}
              className="d-flex align-items-center gap-1.5"
              style={{ borderRadius: 8, fontSize: 12 }}
              title="Share report summary"
            >
              <Share2 size={14} color="#059669" />
              <span>Share</span>
            </Button>
            <Button
              variant="outline-dark"
              size="sm"
              onClick={handlePrint}
              className="d-flex align-items-center gap-1.5"
              style={{ borderRadius: 8, fontSize: 12 }}
              title="Print report on A4 paper"
            >
              <Printer size={14} />
              <span>Print</span>
            </Button>
            {fullDownloadUrl && (
              <Button
                variant="success"
                size="sm"
                as="a"
                href={fullDownloadUrl}
                download
                className="d-flex align-items-center gap-1.5 fw-semibold"
                style={{
                  borderRadius: 8,
                  fontSize: 12,
                  background: 'linear-gradient(135deg, #059669, #047857)',
                  border: 'none',
                  textDecoration: 'none'
                }}
                title="Download PDF file"
              >
                <Download size={14} />
                <span>Download PDF</span>
              </Button>
            )}
            {fullPdfUrl && (
              <Button
                variant="outline-primary"
                size="sm"
                as="a"
                href={fullPdfUrl}
                target="_blank"
                rel="noreferrer"
                className="d-flex align-items-center gap-1.5"
                style={{ borderRadius: 8, fontSize: 12, textDecoration: 'none' }}
                title="Open PDF in new browser tab"
              >
                <ExternalLink size={14} />
                <span>Open in Tab</span>
              </Button>
            )}
          </div>
        )}
      </div>

      {/* SECTION 1: REPORT TYPE SELECTION CARDS (All 5 Types Available) */}
      <div className="no-print card border-0 shadow-sm mb-4" style={{ borderRadius: 14 }}>
        <div className="card-body p-4">
          <div className="d-flex align-items-center justify-content-between mb-3">
            <h6 className="fw-bold mb-0 text-dark" style={{ fontSize: 14, letterSpacing: '-0.01em' }}>
              Select Report Type
            </h6>
            <span className="badge" style={{ backgroundColor: '#ecfdf5', color: '#059669', border: '1px solid #a7f3d0', fontSize: 11 }}>
              5 Official Reports Available
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 10 }}>
            {PERIOD_TYPES.map(p => {
              const isSelected = periodType === p.key;
              return (
                <button
                  key={p.key}
                  id={`btn-report-type-${p.key}`}
                  type="button"
                  onClick={() => {
                    setPeriodType(p.key);
                    setResult(null);
                    setError(null);
                  }}
                  style={{
                    padding: '12px 14px',
                    borderRadius: 12,
                    border: isSelected ? '2px solid #059669' : '1px solid #e2e8f0',
                    background: isSelected ? '#ecfdf5' : '#ffffff',
                    color: isSelected ? '#065f46' : '#334155',
                    textAlign: 'left',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 6,
                    transition: 'all 0.15s ease',
                    outline: 'none',
                    boxShadow: isSelected ? '0 4px 12px rgba(5,150,105,0.12)' : 'none'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ color: isSelected ? '#059669' : '#64748b' }}>{p.icon}</span>
                      <span style={{ fontSize: 13, fontWeight: 700 }}>{p.name}</span>
                    </div>
                    {isSelected && (
                      <span style={{
                        width: 18, height: 18, borderRadius: '50%',
                        background: '#059669', color: '#fff',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: 10, fontWeight: 800
                      }}>
                        <Check size={11} strokeWidth={3} />
                      </span>
                    )}
                  </div>
                  <span style={{ fontSize: 11, color: isSelected ? '#047857' : '#94a3b8', lineHeight: 1.35 }}>
                    {p.desc}
                  </span>
                </button>
              );
            })}
          </div>

          <hr className="my-4" style={{ borderColor: '#f1f5f9' }} />

          {/* SECTION 2: REPORT FILTERS CONFIGURATION */}
          <form onSubmit={handleGenerate}>
            {periodType === 'alert_based' ? (
              /* Alert-Based Report Filters */
              <div>
                <div className="d-flex align-items-center gap-2 mb-3">
                  <AlertTriangle size={16} color="#d97706" />
                  <span className="fw-bold" style={{ fontSize: 13, color: '#0f172a' }}>
                    Alert-Based Report Parameters
                  </span>
                </div>

                {/* Date Presets */}
                <div className="mb-3">
                  <label className="form-label text-muted fw-semibold mb-2" style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '.04em' }}>
                    Quick Date Range
                  </label>
                  <div className="d-flex flex-wrap gap-1.5">
                    {DATE_PRESETS.map(preset => (
                      <button
                        key={preset.key}
                        type="button"
                        onClick={() => handlePresetSelect(preset.key)}
                        style={{
                          border: alertPreset === preset.key ? '1px solid #059669' : '1px solid #e2e8f0',
                          background: alertPreset === preset.key ? '#059669' : '#ffffff',
                          color: alertPreset === preset.key ? '#ffffff' : '#475569',
                          borderRadius: 7,
                          padding: '4px 11px',
                          fontSize: 11,
                          fontWeight: 600,
                          cursor: 'pointer',
                          transition: 'all 0.15s ease'
                        }}
                      >
                        {preset.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Start & End Date Inputs */}
                <Row className="g-3 mb-3">
                  <Col xs={12} sm={6}>
                    <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                      Start Date
                    </label>
                    <input
                      type="date"
                      className="form-control form-control-sm"
                      value={alertStartDate}
                      onChange={(e) => {
                        setAlertStartDate(e.target.value);
                        setAlertPreset('custom');
                        setResult(null);
                      }}
                      style={{ borderRadius: 8 }}
                    />
                  </Col>
                  <Col xs={12} sm={6}>
                    <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                      End Date
                    </label>
                    <input
                      type="date"
                      className="form-control form-control-sm"
                      value={alertEndDate}
                      onChange={(e) => {
                        setAlertEndDate(e.target.value);
                        setAlertPreset('custom');
                        setResult(null);
                      }}
                      style={{ borderRadius: 8 }}
                    />
                  </Col>
                </Row>

                {/* Alert Types Multi-Select Checkboxes */}
                <div className="mb-3">
                  <div className="d-flex align-items-center justify-content-between mb-2">
                    <label className="form-label text-muted fw-semibold mb-0" style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '.04em' }}>
                      Alert Types ({selectedAlertTypes.length}/{SUPPORTED_ALERT_TYPES.length})
                    </label>
                    <div className="d-flex gap-2">
                      <button
                        type="button"
                        onClick={selectAllAlertTypes}
                        className="btn btn-link p-0 text-decoration-none"
                        style={{ fontSize: 11, color: '#059669', fontWeight: 600 }}
                      >
                        Select All
                      </button>
                      <span className="text-muted" style={{ fontSize: 11 }}>·</span>
                      <button
                        type="button"
                        onClick={clearAllAlertTypes}
                        className="btn btn-link p-0 text-decoration-none text-muted"
                        style={{ fontSize: 11, fontWeight: 600 }}
                      >
                        Clear All
                      </button>
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 8 }}>
                    {SUPPORTED_ALERT_TYPES.map(type => {
                      const checked = selectedAlertTypes.includes(type);
                      return (
                        <div
                          key={type}
                          onClick={() => toggleAlertType(type)}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            padding: '8px 12px',
                            borderRadius: 8,
                            border: checked ? '1px solid #a7f3d0' : '1px solid #f1f5f9',
                            background: checked ? '#ecfdf5' : '#f8fafc',
                            cursor: 'pointer',
                            transition: 'all 0.15s ease',
                            userSelect: 'none'
                          }}
                        >
                          <span style={{ color: checked ? '#059669' : '#94a3b8' }}>
                            {checked ? <CheckSquare size={16} /> : <Square size={16} />}
                          </span>
                          <span style={{ fontSize: 12, fontWeight: checked ? 600 : 500, color: checked ? '#065f46' : '#475569' }}>
                            {type}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Additional Filters: Vehicle, Severity, Status */}
                <Row className="g-3 mb-3">
                  <Col xs={12} md={4}>
                    <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                      Vehicle Filter (Optional)
                    </label>
                    <select
                      className="form-select form-select-sm"
                      value={selectedVehicleId}
                      onChange={(e) => {
                        setSelectedVehicleId(e.target.value);
                        setResult(null);
                      }}
                      style={{ borderRadius: 8 }}
                    >
                      <option value="">All Vehicles Operated by Me</option>
                      {vehicles.map(v => (
                        <option key={v.id} value={v.id}>
                          {v.registrationNumber} ({v.code}) — {v.assignmentStatus === 'CURRENT' ? 'Current Assigned' : 'Historically Driven'}
                        </option>
                      ))}
                    </select>
                  </Col>

                  <Col xs={6} md={4}>
                    <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                      Severity
                    </label>
                    <select
                      className="form-select form-select-sm"
                      value={alertSeverity}
                      onChange={(e) => {
                        setAlertSeverity(e.target.value);
                        setResult(null);
                      }}
                      style={{ borderRadius: 8 }}
                    >
                      <option value="ALL">All Severities</option>
                      <option value="CRITICAL">Critical</option>
                      <option value="WARNING">Warning</option>
                      <option value="INFO">Info</option>
                    </select>
                  </Col>

                  <Col xs={6} md={4}>
                    <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                      Status
                    </label>
                    <select
                      className="form-select form-select-sm"
                      value={alertStatus}
                      onChange={(e) => {
                        setAlertStatus(e.target.value);
                        setResult(null);
                      }}
                      style={{ borderRadius: 8 }}
                    >
                      <option value="ALL">All Statuses</option>
                      <option value="OPEN">Open</option>
                      <option value="RESOLVED">Resolved</option>
                      <option value="HISTORICAL">Historical</option>
                    </select>
                  </Col>
                </Row>
              </div>
            ) : (
              /* Standard Report Filters (Monthly, Weekly, Daily, Custom) */
              <Row className="g-3 align-items-end">
                {periodType === 'monthly' && (
                  <Col xs={12} sm="auto">
                    <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                      Select Month
                    </label>
                    <input
                      type="month"
                      className="form-control form-control-sm"
                      value={month}
                      onChange={(e) => { setMonth(e.target.value); setResult(null); }}
                      style={{ borderRadius: 8, minWidth: 160 }}
                    />
                  </Col>
                )}

                {periodType === 'weekly' && (
                  <Col xs={12} sm="auto">
                    <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                      Select Week
                    </label>
                    <input
                      type="week"
                      className="form-control form-control-sm"
                      value={week}
                      onChange={(e) => { setWeek(e.target.value); setResult(null); }}
                      style={{ borderRadius: 8, minWidth: 180 }}
                    />
                    {weekRange.start && (
                      <small className="text-muted d-block mt-1" style={{ fontSize: 11 }}>
                        Coverage: {weekRange.start} to {weekRange.end}
                      </small>
                    )}
                  </Col>
                )}

                {periodType === 'daily' && (
                  <Col xs={12} sm="auto">
                    <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                      Select Date
                    </label>
                    <input
                      type="date"
                      className="form-control form-control-sm"
                      value={dailyDate}
                      onChange={(e) => { setDailyDate(e.target.value); setResult(null); }}
                      style={{ borderRadius: 8, minWidth: 160 }}
                    />
                  </Col>
                )}

                {periodType === 'custom' && (
                  <>
                    <Col xs={12} sm="auto">
                      <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                        Start Date
                      </label>
                      <input
                        type="date"
                        className="form-control form-control-sm"
                        value={customFrom}
                        onChange={(e) => { setCustomFrom(e.target.value); setResult(null); }}
                        style={{ borderRadius: 8 }}
                      />
                    </Col>
                    <Col xs={12} sm="auto">
                      <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                        End Date
                      </label>
                      <input
                        type="date"
                        className="form-control form-control-sm"
                        value={customTo}
                        onChange={(e) => { setCustomTo(e.target.value); setResult(null); }}
                        style={{ borderRadius: 8 }}
                      />
                    </Col>
                  </>
                )}
              </Row>
            )}

            {/* Submit Action */}
            <div className="mt-3 d-flex align-items-center gap-3">
              <Button
                id="btn-generate-report"
                type="submit"
                variant="success"
                size="sm"
                disabled={generating}
                className="d-flex align-items-center gap-2 fw-semibold"
                style={{
                  borderRadius: 8,
                  padding: '8px 20px',
                  background: 'linear-gradient(135deg, #059669, #047857)',
                  border: 'none',
                  boxShadow: '0 2px 6px rgba(5,150,105,0.2)'
                }}
              >
                {generating ? (
                  <>
                    <RefreshCw size={14} className="spin" />
                    <span>Generating Official Report…</span>
                  </>
                ) : (
                  <>
                    <FileText size={15} />
                    <span>Generate Report</span>
                  </>
                )}
              </Button>
            </div>
          </form>

          {error && (
            <Alert variant="danger" dismissible onClose={() => setError(null)} className="mt-3 mb-0 rounded-3">
              <div className="d-flex align-items-center gap-2">
                <AlertCircle size={16} />
                <span style={{ fontSize: 13 }}>{error}</span>
              </div>
            </Alert>
          )}
        </div>
      </div>

      {/* SECTION 3: GENERATED REPORT VIEW & SUB-TABS */}
      {result && (
        <div className="report-print-container card border-0 shadow-sm" style={{ borderRadius: 14, overflow: 'hidden' }}>
          <div className="card-body p-4 p-md-5">
            {/* Report Header */}
            <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between pb-4 mb-4 border-bottom gap-3">
              <div>
                <div className="d-flex align-items-center gap-2 mb-2">
                  <span
                    className="badge"
                    style={{
                      backgroundColor: '#ecfdf5',
                      color: '#059669',
                      border: '1px solid #a7f3d0',
                      fontSize: 10,
                      fontWeight: 700,
                    }}
                  >
                    PERSONAL DRIVER STATEMENT · {result.reportType || periodType.toUpperCase()}
                  </span>
                  <span className="text-muted font-monospace small">
                    Ref: {result.reportId || `DRVRPT-${Date.now()}`}
                  </span>
                </div>
                <h2 className="fw-bold text-dark mb-1" style={{ fontSize: 22 }}>
                  {isAlertBasedResult
                    ? 'Driver Alert & Safety Analysis Report'
                    : 'Driver Performance & Safety Compliance Report'}
                </h2>
                <p className="text-muted small mb-0">
                  Period: {result.startDate || result.reportingPeriod || 'Selected Period'} {result.endDate ? `to ${result.endDate}` : ''} · Generated on{' '}
                  {result.generatedAt ? new Date(result.generatedAt).toLocaleString() : new Date().toLocaleString()}
                </p>
              </div>

              <div className="text-md-end">
                <div className="fw-bold text-dark" style={{ fontSize: 16 }}>
                  {result.driverName || user?.driverName || 'Driver'}
                </div>
                <div className="text-muted font-monospace small">
                  Driver Code: {result.driverCode || user?.driverCode || '—'}
                </div>
                <div className="text-muted small">
                  License: {result.licenseNumber || 'Active Commercial'}
                </div>
              </div>
            </div>

            {/* Metric Summary Cards */}
            <Row className="g-3 mb-4">
              <Col xs={6} md={3}>
                <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                  <div className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11 }}>
                    Safety Score
                  </div>
                  <div className="fw-bold text-success font-monospace mt-1" style={{ fontSize: 24 }}>
                    {result.safetyScore !== undefined && result.safetyScore !== null
                      ? `${typeof result.safetyScore === 'object' ? result.safetyScore.finalSafetyScore : Number(result.safetyScore).toFixed(1)}`
                      : (result.driverSafetyScore ?? '100')} <span className="text-muted small">/ 100</span>
                  </div>
                </div>
              </Col>

              <Col xs={6} md={3}>
                <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                  <div className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11 }}>
                    Total Trips
                  </div>
                  <div className="fw-bold text-dark font-monospace mt-1" style={{ fontSize: 24 }}>
                    {result.totalTrips || 0}
                  </div>
                </div>
              </Col>

              <Col xs={6} md={3}>
                <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                  <div className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11 }}>
                    Distance Driven
                  </div>
                  <div className="fw-bold text-dark font-monospace mt-1" style={{ fontSize: 24 }}>
                    {result.totalDistanceKm ? `${Number(result.totalDistanceKm).toFixed(1)} km` : '0.0 km'}
                  </div>
                </div>
              </Col>

              <Col xs={6} md={3}>
                <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                  <div className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11 }}>
                    Total Penalties
                  </div>
                  <div className="fw-bold text-danger font-monospace mt-1" style={{ fontSize: 24 }}>
                    ₹{Number(result.penalties?.totalActiveAmount ?? result.totalPenaltyAmount ?? 0).toLocaleString('en-IN')}
                  </div>
                </div>
              </Col>
            </Row>

            {/* Navigation Tabs for Preview (Hidden during browser Print) */}
            <div className="no-print mb-4">
              <ul className="nav nav-pills gap-1 p-1 bg-light rounded-3" style={{ fontSize: 12 }}>
                <li className="nav-item">
                  <button
                    type="button"
                    className={`nav-link py-1.5 px-3 fw-semibold ${activeTab === 'summary' ? 'active bg-success text-white' : 'text-muted'}`}
                    onClick={() => setActiveTab('summary')}
                    style={{ borderRadius: 6 }}
                  >
                    📊 Overview & Scoring
                  </button>
                </li>
                <li className="nav-item">
                  <button
                    type="button"
                    className={`nav-link py-1.5 px-3 fw-semibold ${activeTab === 'vehicles' ? 'active bg-success text-white' : 'text-muted'}`}
                    onClick={() => setActiveTab('vehicles')}
                    style={{ borderRadius: 6 }}
                  >
                    🚛 Vehicles Operated ({result.vehiclesOperated?.length || 0})
                  </button>
                </li>
                {!isAlertBasedResult && (
                  <li className="nav-item">
                    <button
                      type="button"
                      className={`nav-link py-1.5 px-3 fw-semibold ${activeTab === 'trips' ? 'active bg-success text-white' : 'text-muted'}`}
                      onClick={() => setActiveTab('trips')}
                      style={{ borderRadius: 6 }}
                    >
                      📍 Trip Tracking ({result.trips?.length || 0})
                    </button>
                  </li>
                )}
                <li className="nav-item">
                  <button
                    type="button"
                    className={`nav-link py-1.5 px-3 fw-semibold ${activeTab === 'alerts' ? 'active bg-success text-white' : 'text-muted'}`}
                    onClick={() => setActiveTab('alerts')}
                    style={{ borderRadius: 6 }}
                  >
                    ⚠️ Infractions & Alerts ({result.totalAlerts || result.alertEvents?.length || result.detailedAlerts?.length || 0})
                  </button>
                </li>
                {fullPdfUrl && (
                  <li className="nav-item">
                    <button
                      type="button"
                      className={`nav-link py-1.5 px-3 fw-semibold ${activeTab === 'pdf' ? 'active bg-success text-white' : 'text-muted'}`}
                      onClick={() => setActiveTab('pdf')}
                      style={{ borderRadius: 6 }}
                    >
                      📄 PDF Preview
                    </button>
                  </li>
                )}
              </ul>
            </div>

            {/* TAB CONTENT */}

            {/* 1. Overview & Scoring */}
            {activeTab === 'summary' && (
              <div>
                <Row className="g-3 mb-4">
                  <Col xs={12} md={6}>
                    <Card className="border rounded-3 h-100" style={{ borderColor: '#e2e8f0' }}>
                      <Card.Body className="p-3">
                        <div className="d-flex align-items-center justify-content-between mb-2">
                          <span className="fw-bold text-dark" style={{ fontSize: 13 }}>
                            Safety Score Deductions Breakdown
                          </span>
                          <span className="badge bg-light text-dark font-monospace" style={{ fontSize: 11 }}>
                            Base Score: 100
                          </span>
                        </div>
                        {result.safetyBreakdown || result.safetyScore?.deductions ? (
                          <div className="table-responsive">
                            <table className="table table-sm align-middle mb-0" style={{ fontSize: 12 }}>
                              <tbody>
                                <tr>
                                  <td className="text-muted">Overspeed Events</td>
                                  <td className="text-end font-monospace text-danger">
                                    -{result.safetyBreakdown?.overspeedDeduction ?? result.safetyScore?.deductions?.overspeed ?? 0} pts
                                  </td>
                                </tr>
                                <tr>
                                  <td className="text-muted">Harsh Braking Events</td>
                                  <td className="text-end font-monospace text-danger">
                                    -{result.safetyBreakdown?.harshBrakingDeduction ?? result.safetyScore?.deductions?.harshBraking ?? 0} pts
                                  </td>
                                </tr>
                                <tr>
                                  <td className="text-muted">Harsh Acceleration Events</td>
                                  <td className="text-end font-monospace text-danger">
                                    -{result.safetyBreakdown?.harshAccelDeduction ?? result.safetyScore?.deductions?.harshAcceleration ?? 0} pts
                                  </td>
                                </tr>
                                <tr>
                                  <td className="text-muted">Night Driving & Fatigue</td>
                                  <td className="text-end font-monospace text-danger">
                                    -{(result.safetyBreakdown?.nightDrivingDeduction ?? 0) + (result.safetyBreakdown?.fatigueDeduction ?? 0)} pts
                                  </td>
                                </tr>
                                <tr>
                                  <td className="text-muted">Clean Trips Bonus</td>
                                  <td className="text-end font-monospace text-success">
                                    +{result.safetyBreakdown?.cleanTripBonus ?? result.safetyScore?.cleanTripBonus ?? 0} pts
                                  </td>
                                </tr>
                              </tbody>
                            </table>
                          </div>
                        ) : (
                          <div className="text-muted small py-2">
                            Clean driving record maintained with 0 point deductions in this reporting window.
                          </div>
                        )}
                      </Card.Body>
                    </Card>
                  </Col>

                  <Col xs={12} md={6}>
                    <Card className="border rounded-3 h-100" style={{ borderColor: '#e2e8f0' }}>
                      <Card.Body className="p-3">
                        <div className="fw-bold text-dark mb-2" style={{ fontSize: 13 }}>
                          Activity & Vehicles Summary
                        </div>
                        <div className="table-responsive">
                          <table className="table table-sm align-middle mb-0" style={{ fontSize: 12 }}>
                            <tbody>
                              <tr>
                                <td className="text-muted">Vehicles Operated in Period</td>
                                <td className="text-end font-monospace fw-bold">
                                  {result.vehiclesOperated?.length || result.totalVehiclesEntered || 0}
                                </td>
                              </tr>
                              <tr>
                                <td className="text-muted">Total Time Driving</td>
                                <td className="text-end font-monospace">
                                  {result.totalDurationFormatted || '—'}
                                </td>
                              </tr>
                              <tr>
                                <td className="text-muted">Critical Violations</td>
                                <td className="text-end font-monospace text-danger fw-bold">
                                  {result.alertSeverityCounts?.CRITICAL ?? result.alertSeverityBreakdown?.CRITICAL ?? 0}
                                </td>
                              </tr>
                              <tr>
                                <td className="text-muted">Warning Violations</td>
                                <td className="text-end font-monospace text-warning fw-bold">
                                  {result.alertSeverityCounts?.WARNING ?? result.alertSeverityBreakdown?.WARNING ?? 0}
                                </td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      </Card.Body>
                    </Card>
                  </Col>
                </Row>
              </div>
            )}

            {/* 2. Vehicles Operated Tab */}
            {activeTab === 'vehicles' && (
              <div className="table-responsive rounded-3 border mb-4" style={{ borderColor: '#e2e8f0' }}>
                <table className="table table-hover align-middle mb-0" style={{ fontSize: 13 }}>
                  <thead className="table-light">
                    <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                      <th className="ps-3">Vehicle Number</th>
                      <th>Make / Model</th>
                      <th>Trips Driven</th>
                      <th>Distance</th>
                      <th>Driving Duration</th>
                      <th>Overspeed Alerts</th>
                      <th className="pe-3 text-end">Total Alerts</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.vehiclesOperated && result.vehiclesOperated.length > 0 ? (
                      result.vehiclesOperated.map((v, i) => (
                        <tr key={i}>
                          <td className="ps-3 fw-bold text-dark font-monospace">
                            <Truck size={14} className="me-1 text-success" />
                            {v.vehicleNumber || v.registrationNumber || 'Fleet Vehicle'}
                          </td>
                          <td className="text-muted">{v.vehicleType || v.makeModel || 'Commercial Heavy Truck'}</td>
                          <td className="font-monospace">{v.trips || 0}</td>
                          <td className="font-monospace">{v.distanceKm ? Number(v.distanceKm).toFixed(1) + ' km' : '0.0 km'}</td>
                          <td className="text-muted">{v.formattedDuration || '—'}</td>
                          <td className="font-monospace text-danger">{v.overspeedCount || 0}</td>
                          <td className="pe-3 text-end font-monospace fw-bold">{v.alerts || 0}</td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={7} className="py-4 text-center text-muted">
                          No vehicle operations recorded during this time frame.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}

            {/* 3. Trip Tracking Tab */}
            {!isAlertBasedResult && activeTab === 'trips' && (
              <div className="table-responsive rounded-3 border mb-4" style={{ borderColor: '#e2e8f0' }}>
                <table className="table table-hover align-middle mb-0" style={{ fontSize: 13 }}>
                  <thead className="table-light">
                    <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                      <th className="ps-3">Trip ID</th>
                      <th>Vehicle</th>
                      <th>Start Time</th>
                      <th>End Time</th>
                      <th>Distance</th>
                      <th>Max Speed</th>
                      <th>Primary Road</th>
                      <th className="pe-3 text-end">Alerts</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.trips && result.trips.length > 0 ? (
                      result.trips.map((t, i) => (
                        <tr key={i}>
                          <td className="ps-3 font-monospace fw-bold text-dark">TRIP-{t.tripId || i + 1}</td>
                          <td className="font-monospace text-muted">{t.vehicleNumber || '—'}</td>
                          <td className="small text-muted">{t.startTime || '—'}</td>
                          <td className="small text-muted">{t.endTime || '—'}</td>
                          <td className="font-monospace">{t.distanceKm ? Number(t.distanceKm).toFixed(1) + ' km' : '—'}</td>
                          <td className="font-monospace">{t.maxSpeedKmph ? Number(t.maxSpeedKmph).toFixed(0) + ' km/h' : '—'}</td>
                          <td className="small text-muted">{t.primaryRoad || 'Highway'}</td>
                          <td className="pe-3 text-end font-monospace fw-bold text-danger">{t.alertCount || 0}</td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={8} className="py-4 text-center text-muted">
                          No trips logged by this driver in the selected period.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}

            {/* 4. Infractions & Alerts Tab */}
            {activeTab === 'alerts' && (
              <div>
                <div className="table-responsive rounded-3 border mb-4" style={{ borderColor: '#e2e8f0' }}>
                  <table className="table table-hover align-middle mb-0" style={{ fontSize: 13 }}>
                    <thead className="table-light">
                      <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                        <th className="ps-3">Date / Time</th>
                        <th>Vehicle</th>
                        <th>Alert Type</th>
                        <th>Severity</th>
                        <th>Speed / Limit</th>
                        <th>Road Location</th>
                        <th className="pe-3 text-end">Penalty Amount</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(result.alertEvents || result.detailedAlerts || result.alertBreakdown || []).length > 0 ? (
                        (result.alertEvents || result.detailedAlerts || result.alertBreakdown).map((a, i) => (
                          <tr key={i}>
                            <td className="ps-3 small text-muted">
                              {a.date || a.timestamp || (a.occurredAt ? new Date(a.occurredAt).toLocaleString() : '—')}
                            </td>
                            <td className="font-monospace fw-semibold text-dark">
                              {a.vehicleNumber || a.vehicleRegistrationNumber || '—'}
                            </td>
                            <td className="fw-semibold text-dark">{a.alertType || a.type}</td>
                            <td>
                              <span
                                className={`badge ${
                                  a.severity === 'CRITICAL' ? 'bg-danger' : a.severity === 'WARNING' ? 'bg-warning text-dark' : 'bg-info text-dark'
                                }`}
                                style={{ fontSize: 10 }}
                              >
                                {a.severity || 'WARNING'}
                              </span>
                            </td>
                            <td className="font-monospace small">
                              {a.speedKmph ? `${Number(a.speedKmph).toFixed(0)} km/h` : '—'}
                              {a.speedLimitKmph ? ` (Limit ${Number(a.speedLimitKmph).toFixed(0)})` : ''}
                            </td>
                            <td className="small text-muted">{a.roadName || 'Highway Segment'}</td>
                            <td className="pe-3 text-end font-monospace fw-bold text-danger">
                              ₹{Number(a.penaltyAmount || 0).toLocaleString('en-IN')}
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={7} className="py-4 text-center text-muted">
                            No infractions or safety violations reported for this period.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* 5. PDF Inline Preview Tab */}
            {activeTab === 'pdf' && fullPdfUrl && (
              <div style={{ borderRadius: 10, overflow: 'hidden', border: '1px solid #e2e8f0' }}>
                <object data={fullPdfUrl} type="application/pdf" width="100%" height="600">
                  <div style={{ padding: 32, textAlign: 'center', color: '#64748b' }}>
                    <FileText size={36} style={{ marginBottom: 10, color: '#94a3b8' }} />
                    <div style={{ fontSize: 14, marginBottom: 12, fontWeight: 600 }}>
                      Inline PDF viewer not supported by your browser.
                    </div>
                    <div className="d-flex justify-content-center gap-2">
                      <Button variant="success" size="sm" as="a" href={fullPdfUrl} target="_blank" rel="noreferrer">
                        <ExternalLink size={14} className="me-1" /> Open PDF in New Tab
                      </Button>
                      {fullDownloadUrl && (
                        <Button variant="outline-success" size="sm" as="a" href={fullDownloadUrl} download>
                          <Download size={14} className="me-1" /> Download PDF File
                        </Button>
                      )}
                    </div>
                  </div>
                </object>
              </div>
            )}

            {/* Print Footer */}
            <div className="pt-3 border-top d-flex justify-content-between align-items-center text-muted" style={{ fontSize: 11 }}>
              <div>TrackFleet Autonomous Telematics System — Official Record</div>
              <div>Confidential · Personal Driver Access Only</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
