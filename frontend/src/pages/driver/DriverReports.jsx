import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Form, Button, Table, Badge, Spinner, Alert } from 'react-bootstrap';
import { 
  FileText, Printer, Share2, Calendar, 
  ShieldCheck, AlertTriangle, IndianRupee, Clock, RefreshCw, CheckCircle2, Copy
} from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

export default function DriverReports() {
  const [periodType, setPeriodType] = useState('MONTHLY'); // MONTHLY, CUSTOM
  const [month, setMonth] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [generating, setGenerating] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const [shareToast, setShareToast] = useState(null);

  const handleGenerate = async (e) => {
    if (e) e.preventDefault();
    try {
      setGenerating(true);
      setError(null);

      let payload = {
        reportType: periodType,
        period: periodType.toLowerCase()
      };

      if (periodType === 'MONTHLY') {
        const [yearStr, monthStr] = month.split('-');
        const y = parseInt(yearStr, 10);
        const m = parseInt(monthStr, 10);
        const start = new Date(Date.UTC(y, m - 1, 1));
        const end = new Date(Date.UTC(y, m, 0, 23, 59, 59));
        payload.startDate = start.toISOString();
        payload.endDate = end.toISOString();
      } else if (periodType === 'CUSTOM') {
        if (!dateFrom || !dateTo) {
          setError('Please select both Start Date and End Date for a custom report.');
          setGenerating(false);
          return;
        }
        payload.startDate = new Date(dateFrom).toISOString();
        payload.endDate = new Date(dateTo).toISOString();
      } else {
        const end = new Date();
        const start = new Date();
        start.setDate(end.getDate() - 30);
        payload.startDate = start.toISOString();
        payload.endDate = end.toISOString();
      }

      const res = await driverPortalApi.generateReport(payload);
      setReport(res);
    } catch (err) {
      console.error('Failed to generate driver report:', err);
      setError(err?.response?.data?.message || 'Failed to generate report. Please try again.');
    } finally {
      setGenerating(false);
    }
  };

  useEffect(() => {
    handleGenerate();
  }, []);

  const handlePrint = () => {
    window.print();
  };

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
    if (!report) return;

    const reportTitle = `TrackFleet Driver Report - ${report.driverName || 'Driver'}`;
    const reportText = `TrackFleet Personal Report:
Driver: ${report.driverName || 'Driver'} (${report.driverCode || '—'})
Safety Score: ${report.driverSafetyScore ?? '100'}/100
Total Trips: ${report.totalTrips ?? 0}
Distance: ${report.totalDistanceKm ? Number(report.totalDistanceKm).toFixed(1) : 0} km
Total Penalties: ₹${report.totalPenaltyAmount ?? 0}
Alerts Logged: ${report.totalAlerts ?? 0}
Period: ${report.periodStart ? new Date(report.periodStart).toLocaleDateString() : ''} - ${report.periodEnd ? new Date(report.periodEnd).toLocaleDateString() : ''}`;

    if (navigator.share) {
      try {
        await navigator.share({
          title: reportTitle,
          text: reportText,
          url: window.location.href,
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

  return (
    <div style={{ fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif" }}>
      {/* Toast Alert for Share Feedback */}
      {shareToast && (
        <Alert
          variant={shareToast.type === 'success' ? 'success' : 'danger'}
          dismissible
          onClose={() => setShareToast(null)}
          className="mb-4 rounded-3 d-flex align-items-center gap-2"
        >
          <CheckCircle2 size={16} />
          <span>{shareToast.message}</span>
        </Alert>
      )}

      {/* Header (Hidden in Print) */}
      <div className="no-print d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
        <div>
          <h1 className="h3 mb-1 fw-bold" style={{ color: '#0f172a' }}>My Reports</h1>
          <p className="text-muted mb-0" style={{ fontSize: 13 }}>
            Generate and export personal driver performance, compliance, and penalty reports.
          </p>
        </div>

        {report && (
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
              variant="success"
              size="sm"
              onClick={handlePrint}
              className="d-flex align-items-center gap-1.5 fw-semibold"
              style={{
                borderRadius: 8,
                fontSize: 12,
                background: 'linear-gradient(135deg, #059669, #047857)',
                border: 'none',
              }}
              title="Print report on A4 paper"
            >
              <Printer size={14} />
              <span>Print Report</span>
            </Button>
          </div>
        )}
      </div>

      {/* Filter Controls Card (Hidden in Print) */}
      <div className="no-print card border-0 shadow-sm mb-4" style={{ borderRadius: 14 }}>
        <div className="card-body p-4">
          <form onSubmit={handleGenerate} className="row g-3 align-items-end">
            <div className="col-12 col-sm-auto">
              <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                Period Type
              </label>
              <select
                className="form-select form-select-sm"
                value={periodType}
                onChange={(e) => setPeriodType(e.target.value)}
                style={{ borderRadius: 8, minWidth: 140 }}
              >
                <option value="MONTHLY">Monthly</option>
                <option value="CUSTOM">Custom Range</option>
              </select>
            </div>

            {periodType === 'MONTHLY' ? (
              <div className="col-12 col-sm-auto">
                <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                  Select Month
                </label>
                <input
                  type="month"
                  className="form-control form-control-sm"
                  value={month}
                  onChange={(e) => setMonth(e.target.value)}
                  style={{ borderRadius: 8 }}
                />
              </div>
            ) : (
              <>
                <div className="col-12 col-sm-auto">
                  <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                    Start Date
                  </label>
                  <input
                    type="date"
                    className="form-control form-control-sm"
                    value={dateFrom}
                    onChange={(e) => setDateFrom(e.target.value)}
                    style={{ borderRadius: 8 }}
                  />
                </div>
                <div className="col-12 col-sm-auto">
                  <label className="form-label text-muted fw-semibold" style={{ fontSize: 12 }}>
                    End Date
                  </label>
                  <input
                    type="date"
                    className="form-control form-control-sm"
                    value={dateTo}
                    onChange={(e) => setDateTo(e.target.value)}
                    style={{ borderRadius: 8 }}
                  />
                </div>
              </>
            )}

            <div className="col-12 col-sm-auto">
              <Button
                type="submit"
                variant="success"
                size="sm"
                disabled={generating}
                className="d-flex align-items-center gap-1.5 fw-semibold"
                style={{
                  borderRadius: 8,
                  padding: '7px 16px',
                  background: 'linear-gradient(135deg, #059669, #047857)',
                  border: 'none',
                }}
              >
                {generating ? (
                  <>
                    <RefreshCw size={14} className="spin" />
                    <span>Generating…</span>
                  </>
                ) : (
                  <>
                    <FileText size={14} />
                    <span>Generate Report</span>
                  </>
                )}
              </Button>
            </div>
          </form>

          {error && (
            <div className="mt-3 text-danger small">
              {error}
            </div>
          )}
        </div>
      </div>

      {/* Generated Report View (A4 Print Ready) */}
      {report && (
        <div
          className="report-print-container card border-0 shadow-sm"
          style={{ borderRadius: 14, overflow: 'hidden' }}
        >
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
                    PERSONAL DRIVER STATEMENT
                  </span>
                  <span className="text-muted font-monospace small">
                    Ref: TRK-{report.id || Math.floor(Math.random() * 90000 + 10000)}
                  </span>
                </div>
                <h2 className="fw-bold text-dark mb-1" style={{ fontSize: 22 }}>
                  Driver Performance & Safety Compliance Report
                </h2>
                <p className="text-muted small mb-0">
                  Period:{' '}
                  {report.periodStart ? new Date(report.periodStart).toLocaleDateString() : 'Start'} to{' '}
                  {report.periodEnd ? new Date(report.periodEnd).toLocaleDateString() : 'End'} · Generated on{' '}
                  {new Date().toLocaleString()}
                </p>
              </div>

              <div className="text-md-end">
                <div className="fw-bold text-dark" style={{ fontSize: 16 }}>
                  {report.driverName || 'Driver'}
                </div>
                <div className="text-muted font-monospace small">
                  Driver ID: {report.driverCode || '—'}
                </div>
                <div className="text-muted small">
                  Vehicle: {report.vehicleCode || 'Fleet Vehicle'}
                </div>
              </div>
            </div>

            {/* Metric Summary Cards */}
            <Row className="g-3 mb-4">
              <Col xs={6} md={3}>
                <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                  <div className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11 }}>
                    Safety Index
                  </div>
                  <div className="fw-bold text-success font-monospace mt-1" style={{ fontSize: 24 }}>
                    {report.driverSafetyScore !== undefined && report.driverSafetyScore !== null
                      ? `${Number(report.driverSafetyScore).toFixed(1)}`
                      : '100'} <span className="text-muted small">/ 100</span>
                  </div>
                </div>
              </Col>

              <Col xs={6} md={3}>
                <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                  <div className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11 }}>
                    Total Trips
                  </div>
                  <div className="fw-bold text-dark font-monospace mt-1" style={{ fontSize: 24 }}>
                    {report.totalTrips || 0}
                  </div>
                </div>
              </Col>

              <Col xs={6} md={3}>
                <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                  <div className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11 }}>
                    Distance Driven
                  </div>
                  <div className="fw-bold text-dark font-monospace mt-1" style={{ fontSize: 24 }}>
                    {report.totalDistanceKm ? `${Number(report.totalDistanceKm).toFixed(1)} km` : '0.0 km'}
                  </div>
                </div>
              </Col>

              <Col xs={6} md={3}>
                <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                  <div className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11 }}>
                    Penalties Assessed
                  </div>
                  <div className="fw-bold text-danger font-monospace mt-1" style={{ fontSize: 24 }}>
                    ₹{Number(report.totalPenaltyAmount || 0).toLocaleString('en-IN')}
                  </div>
                </div>
              </Col>
            </Row>

            {/* Infraction & Safety Event Breakdown Table */}
            <div className="mb-4">
              <h5 className="fw-bold text-dark mb-3" style={{ fontSize: 15 }}>
                Infraction & Safety Event Breakdown
              </h5>
              <div className="table-responsive rounded-3 border" style={{ borderColor: '#e2e8f0' }}>
                <table className="table table-hover align-middle mb-0" style={{ fontSize: 13 }}>
                  <thead className="table-light">
                    <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                      <th className="ps-3">Event Type</th>
                      <th>Occurrences</th>
                      <th>Severity Level</th>
                      <th className="pe-3 text-end">Penalty Incurred</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.alertBreakdown && report.alertBreakdown.length > 0 ? (
                      report.alertBreakdown.map((item, i) => (
                        <tr key={i}>
                          <td className="ps-3 fw-semibold text-dark">
                            {item.alertType || item.type}
                          </td>
                          <td className="font-monospace text-dark">{item.count}</td>
                          <td>
                            <span className="badge bg-light text-secondary border" style={{ fontSize: 10 }}>
                              {item.severity || 'STANDARD'}
                            </span>
                          </td>
                          <td className="pe-3 text-end text-danger font-monospace fw-bold">
                            ₹{Number(item.penaltyAmount || 0).toFixed(0)}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={4} className="py-4 text-center text-muted">
                          No infractions or safety violations reported during this time frame.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

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
