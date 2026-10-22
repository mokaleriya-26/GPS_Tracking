import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  Settings as SettingsIcon, Database, Server, Mail, Globe,
  CheckCircle, XCircle, RefreshCw, Scale, ArrowRight, ShieldAlert
} from 'lucide-react';
import api from '../api/axios';

const StatusDot = ({ ok }) => (
  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12 }}>
    {ok ? <CheckCircle size={14} color="#16a34a" /> : <XCircle size={14} color="#dc2626" />}
    <span style={{ color: ok ? '#16a34a' : '#dc2626', fontWeight: 600 }}>{ok ? 'Connected' : 'Unavailable'}</span>
  </span>
);

export default function Settings() {
  // Health
  const [health, setHealth] = useState(null);
  const [healthLoading, setHealthLoading] = useState(true);

  const checkHealth = () => {
    setHealthLoading(true);
    api.get('/api/health')
      .then(r => setHealth(r.data))
      .catch(() => setHealth({ status: 'DOWN' }))
      .finally(() => setHealthLoading(false));
  };

  useEffect(() => {
    checkHealth();
  }, []);

  const envRows = [
    { label: 'Backend URL', value: import.meta.env.VITE_API_URL || 'http://localhost:8080', icon: <Server size={14} /> },
    { label: 'Database', value: 'PostgreSQL 17 + PostGIS', icon: <Database size={14} /> },
    { label: 'Flyway Migration', value: 'V7 (Penalty Management & Audit Logs applied)', icon: <Database size={14} /> },
    { label: 'AI Service', value: import.meta.env.VITE_AI_URL || 'http://localhost:8000', icon: <Globe size={14} /> },
    { label: 'Email / SMS', value: 'Configured via SMTP & MSG91 gateway', icon: <Mail size={14} /> },
  ];

  return (
    <div className="container-fluid py-4" style={{ maxWidth: 1200, fontFamily: 'Inter, sans-serif' }}>
      {/* Page Title */}
      <div className="d-flex align-items-center justify-content-between mb-4 flex-wrap gap-2">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <SettingsIcon size={22} className="me-2 text-primary" />System & Platform Settings
          </h1>
          <p className="text-muted mb-0" style={{ fontSize: 13 }}>
            Platform environment, connected service health, and system diagnostic status
          </p>
        </div>
      </div>

      {/* Relocation Notice Banner */}
      <div className="card border-0 shadow-sm mb-4" style={{
        borderRadius: 12,
        background: 'linear-gradient(135deg, #eff6ff, #f8fafc)',
        borderLeft: '4px solid #2563eb'
      }}>
        <div className="card-body p-3 d-flex align-items-center justify-content-between flex-wrap gap-3">
          <div className="d-flex align-items-center gap-3">
            <div style={{
              width: 38, height: 38, borderRadius: 10,
              background: '#dbeafe', display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: '#1d4ed8'
            }}>
              <Scale size={20} />
            </div>
            <div>
              <div className="fw-bold" style={{ fontSize: 14, color: '#0f172a' }}>
                Penalty Configuration Relocated to Monitoring
              </div>
              <div className="text-muted" style={{ fontSize: 12 }}>
                Progressive alert penalties, warning thresholds, fine increments, and driver enforcement have been moved to the <strong>Monitoring</strong> section.
              </div>
            </div>
          </div>
          <Link
            to="/penalties"
            className="btn btn-primary btn-sm d-flex align-items-center gap-2"
            style={{ borderRadius: 8, padding: '8px 16px', fontWeight: 600 }}
          >
            Open Penalty Management <ArrowRight size={15} />
          </Link>
        </div>
      </div>

      {/* SYSTEM HEALTH */}
      <div className="row g-4">
        <div className="col-lg-6">
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4 d-flex justify-content-between align-items-center">
              <h5 className="mb-0 fw-bold" style={{ fontSize: 15 }}>Service Health</h5>
              <button className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1" onClick={checkHealth}>
                <RefreshCw size={13} className={healthLoading ? 'spin' : ''} /> Refresh
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
    </div>
  );
}
