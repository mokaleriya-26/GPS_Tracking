import React, { useState, useEffect } from 'react';
import { Settings as SettingsIcon, Database, Server, Mail, Smartphone, Globe, CheckCircle, XCircle, RefreshCw } from 'lucide-react';
import api from '../api/axios';

const StatusDot = ({ ok }) => (
  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12 }}>
    {ok ? <CheckCircle size={14} color="#16a34a" /> : <XCircle size={14} color="#dc2626" />}
    <span style={{ color: ok ? '#16a34a' : '#dc2626', fontWeight: 600 }}>{ok ? 'Connected' : 'Unavailable'}</span>
  </span>
);

export default function Settings() {
  const [health, setHealth] = useState(null);
  const [loading, setLoading] = useState(true);

  const checkHealth = () => {
    setLoading(true);
    api.get('/api/health').then(r => setHealth(r.data)).catch(() => setHealth({ status: 'DOWN' })).finally(() => setLoading(false));
  };

  useEffect(() => { checkHealth(); }, []);

  const envRows = [
    { label: 'Backend URL', value: import.meta.env.VITE_API_URL || 'http://localhost:8080', icon: <Server size={14} /> },
    { label: 'Database', value: 'PostgreSQL 17 + PostGIS', icon: <Database size={14} /> },
    { label: 'AI Service', value: import.meta.env.VITE_AI_URL || 'http://localhost:8000', icon: <Globe size={14} /> },
    { label: 'Email (SMTP)', value: 'Configured via SMTP_HOST env', icon: <Mail size={14} /> },
    { label: 'SMS Gateway', value: 'MSG91 (configured via MSG91_AUTH_KEY env)', icon: <Smartphone size={14} /> },
  ];

  return (
    <div className="container-fluid py-4">
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <SettingsIcon size={22} className="me-2 text-secondary" />System Settings
          </h1>
          <p className="text-muted mb-0">Configuration and connectivity status</p>
        </div>
        <button className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1" onClick={checkHealth}>
          <RefreshCw size={14} /> Check Status
        </button>
      </div>

      <div className="row g-4">
        {/* Health */}
        <div className="col-lg-6">
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4">
              <h5 className="mb-0 fw-bold" style={{ fontSize: 15 }}>Service Health</h5>
            </div>
            <div className="card-body px-4 pb-4">
              {loading ? <div className="spinner-border spinner-border-sm text-primary" /> : (
                <div className="d-flex flex-column gap-3">
                  <div className="d-flex justify-content-between align-items-center py-2" style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <span className="d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
                      <Server size={14} className="text-primary" /> Spring Boot Backend
                    </span>
                    <StatusDot ok={health?.status === 'UP' || !!health} />
                  </div>
                  <div className="d-flex justify-content-between align-items-center py-2" style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <span className="d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
                      <Database size={14} className="text-success" /> PostgreSQL + PostGIS
                    </span>
                    <StatusDot ok={health?.status === 'UP' || !!health} />
                  </div>
                  <div className="d-flex justify-content-between align-items-center py-2" style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <span className="d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
                      <Globe size={14} className="text-warning" /> Python AI Service
                    </span>
                    <StatusDot ok={false} />
                  </div>
                  <div className="d-flex justify-content-between align-items-center py-2">
                    <span className="d-flex align-items-center gap-2" style={{ fontSize: 13 }}>
                      <Mail size={14} className="text-info" /> Email / SMS Gateway
                    </span>
                    <span style={{ fontSize: 11, color: '#94a3b8' }}>Configured via .env</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Config */}
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
                    <span className="text-end" style={{ fontSize: 12, fontFamily: 'monospace', color: '#475569', maxWidth: '55%', wordBreak: 'break-all' }}>
                      {row.value}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Version info */}
        <div className="col-12">
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14, background: 'linear-gradient(135deg,#0f172a,#1e3a5f)' }}>
            <div className="card-body px-4 py-4 text-white">
              <div className="row g-4 text-center">
                {[
                  { label: 'Frontend', value: 'React 19 + Vite 8' },
                  { label: 'Backend', value: 'Spring Boot 3.2.5' },
                  { label: 'Database', value: 'PostgreSQL 17 + PostGIS' },
                  { label: 'Migrations', value: 'Flyway V1–V6' },
                ].map((item, i) => (
                  <div key={i} className="col-6 col-lg-3">
                    <div style={{ fontSize: 20, fontWeight: 800 }}>{item.value}</div>
                    <div style={{ fontSize: 11, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{item.label}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
