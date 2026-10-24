import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  ShieldCheck, Truck, Lock, User, Eye, EyeOff,
  AlertCircle, ArrowRight, Gauge, Check
} from 'lucide-react';

export default function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();

  // Selected role tab: 'admin' or 'driver'
  const [selectedRole, setSelectedRole] = useState('admin');

  // Form states
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Switch role tab
  const handleRoleChange = (role) => {
    if (selectedRole !== role) {
      setSelectedRole(role);
      setUsername('');
      setPassword('');
      setError(null);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmedUser = username.trim();
    const trimmedPass = password.trim();

    if (!trimmedUser || !trimmedPass) {
      setError(
        selectedRole === 'admin'
          ? 'Please enter both your Admin username and password.'
          : 'Please enter your Vehicle ID in both username and password fields.'
      );
      return;
    }

    setLoading(true);
    setError(null);

    const res = await login(trimmedUser, trimmedPass);
    setLoading(false);

    if (res.success) {
      if (res.user.role === 'ADMIN') {
        navigate('/', { replace: true });
      } else {
        navigate('/driver/dashboard', { replace: true });
      }
    } else {
      setError(res.message || 'Authentication failed. Please verify your credentials.');
    }
  };

  const isAdmin = selectedRole === 'admin';

  return (
    <div
      style={{
        minHeight: '100vh',
        width: '100vw',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#f8fafc',
        fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
        padding: '32px 16px',
        color: '#0f172a',
      }}
    >
      {/* Centered Login Card */}
      <div
        style={{
          width: '100%',
          maxWidth: 520,
          background: '#ffffff',
          borderRadius: 20,
          border: '1px solid #e2e8f0',
          boxShadow: '0 10px 25px -5px rgba(15, 23, 42, 0.06), 0 8px 10px -6px rgba(15, 23, 42, 0.04)',
          padding: '36px 32px',
          margin: '0 auto',
        }}
      >
        {/* Brand Header */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              background: 'linear-gradient(135deg, #1d4ed8, #0ea5e9)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 8px 16px -4px rgba(29, 78, 216, 0.25)',
              marginBottom: 12,
            }}
          >
            <Gauge size={24} color="#ffffff" />
          </div>
          <h1
            style={{
              fontSize: 22,
              fontWeight: 800,
              color: '#0f172a',
              margin: 0,
              letterSpacing: '-0.02em',
            }}
          >
            TrackFleet
          </h1>
          <p style={{ fontSize: 13, color: '#64748b', margin: '4px 0 0' }}>
            Fleet Intelligence & Telemetry Management
          </p>
        </div>

        {/* Role Selection Cards (Visible side-by-side on desktop, stacked on mobile) */}
        <div
          className="role-selector-grid"
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: 12,
            marginBottom: 24,
          }}
        >
          {/* Admin Option Card */}
          <button
            type="button"
            id="role-tab-admin"
            onClick={() => handleRoleChange('admin')}
            style={{
              padding: '14px 16px',
              borderRadius: 14,
              border: isAdmin ? '2px solid #2563eb' : '1px solid #e2e8f0',
              background: isAdmin ? '#eff6ff' : '#ffffff',
              textAlign: 'left',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              transition: 'all 0.15s ease',
              outline: 'none',
              boxShadow: isAdmin ? '0 4px 12px rgba(37, 99, 235, 0.1)' : 'none',
            }}
          >
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 10,
                background: isAdmin ? '#2563eb' : '#f1f5f9',
                color: isAdmin ? '#ffffff' : '#64748b',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
                transition: 'all 0.15s ease',
              }}
            >
              <ShieldCheck size={20} />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  fontSize: 14,
                  fontWeight: 700,
                  color: isAdmin ? '#1d4ed8' : '#334155',
                  lineHeight: 1.2,
                }}
              >
                Admin
              </div>
              <div
                style={{
                  fontSize: 11,
                  color: isAdmin ? '#3b82f6' : '#94a3b8',
                  marginTop: 2,
                }}
              >
                Fleet Manager
              </div>
            </div>
            {isAdmin && (
              <div
                style={{
                  width: 18,
                  height: 18,
                  borderRadius: '50%',
                  background: '#2563eb',
                  color: '#ffffff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <Check size={12} strokeWidth={3} />
              </div>
            )}
          </button>

          {/* Driver Option Card */}
          <button
            type="button"
            id="role-tab-driver"
            onClick={() => handleRoleChange('driver')}
            style={{
              padding: '14px 16px',
              borderRadius: 14,
              border: !isAdmin ? '2px solid #059669' : '1px solid #e2e8f0',
              background: !isAdmin ? '#ecfdf5' : '#ffffff',
              textAlign: 'left',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              transition: 'all 0.15s ease',
              outline: 'none',
              boxShadow: !isAdmin ? '0 4px 12px rgba(5, 150, 105, 0.1)' : 'none',
            }}
          >
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 10,
                background: !isAdmin ? '#059669' : '#f1f5f9',
                color: !isAdmin ? '#ffffff' : '#64748b',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
                transition: 'all 0.15s ease',
              }}
            >
              <Truck size={20} />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  fontSize: 14,
                  fontWeight: 700,
                  color: !isAdmin ? '#047857' : '#334155',
                  lineHeight: 1.2,
                }}
              >
                Driver
              </div>
              <div
                style={{
                  fontSize: 11,
                  color: !isAdmin ? '#10b981' : '#94a3b8',
                  marginTop: 2,
                }}
              >
                User Portal
              </div>
            </div>
            {!isAdmin && (
              <div
                style={{
                  width: 18,
                  height: 18,
                  borderRadius: '50%',
                  background: '#059669',
                  color: '#ffffff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <Check size={12} strokeWidth={3} />
              </div>
            )}
          </button>
        </div>

        {/* Role Context Heading & Description */}
        <div
          style={{
            padding: '14px 16px',
            background: '#f8fafc',
            border: '1px solid #edf2f7',
            borderRadius: 12,
            marginBottom: 20,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            {isAdmin ? (
              <ShieldCheck size={17} color="#2563eb" />
            ) : (
              <Truck size={17} color="#059669" />
            )}
            <h2
              style={{
                fontSize: 15,
                fontWeight: 700,
                color: '#0f172a',
                margin: 0,
              }}
            >
              {isAdmin ? 'Admin Login' : 'Driver Login'}
            </h2>
          </div>
          <p style={{ fontSize: 12, color: '#64748b', margin: 0, lineHeight: 1.45 }}>
            {isAdmin
              ? 'Manage your fleet, drivers, vehicles and monitoring activities.'
              : 'Access your personal driving dashboard, alerts and penalties.'}
          </p>
        </div>

        {/* Error Alert */}
        {error && (
          <div
            id="login-error-alert"
            style={{
              background: '#fef2f2',
              border: '1px solid #fecaca',
              borderRadius: 10,
              padding: '10px 14px',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 10,
              color: '#991b1b',
              fontSize: 13,
              marginBottom: 18,
              lineHeight: 1.4,
            }}
          >
            <AlertCircle size={18} style={{ flexShrink: 0, marginTop: 1, color: '#dc2626' }} />
            <span>{error}</span>
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleSubmit}>
          {/* Username / Vehicle ID */}
          <div style={{ marginBottom: 16 }}>
            <label
              htmlFor="login-username"
              style={{
                display: 'block',
                fontSize: 13,
                fontWeight: 600,
                color: '#334155',
                marginBottom: 6,
              }}
            >
              {isAdmin ? 'Username' : 'Vehicle ID'}
            </label>
            <div style={{ position: 'relative' }}>
              <div
                style={{
                  position: 'absolute',
                  left: 14,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  color: '#94a3b8',
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                {isAdmin ? <User size={18} /> : <Truck size={18} />}
              </div>
              <input
                id="login-username"
                name="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder={isAdmin ? 'Enter admin username' : 'Enter your Vehicle ID (e.g. VH001)'}
                disabled={loading}
                autoFocus
                style={{
                  width: '100%',
                  background: '#ffffff',
                  border: '1px solid #cbd5e1',
                  borderRadius: 10,
                  padding: '11px 14px 11px 42px',
                  color: '#0f172a',
                  fontSize: 14,
                  outline: 'none',
                  transition: 'border-color 0.15s, box-shadow 0.15s',
                }}
                onFocus={(e) => {
                  e.target.style.borderColor = isAdmin ? '#2563eb' : '#059669';
                  e.target.style.boxShadow = isAdmin
                    ? '0 0 0 3px rgba(37, 99, 235, 0.12)'
                    : '0 0 0 3px rgba(5, 150, 105, 0.12)';
                }}
                onBlur={(e) => {
                  e.target.style.borderColor = '#cbd5e1';
                  e.target.style.boxShadow = 'none';
                }}
              />
            </div>
          </div>

          {/* Password */}
          <div style={{ marginBottom: 22 }}>
            <label
              htmlFor="login-password"
              style={{
                display: 'block',
                fontSize: 13,
                fontWeight: 600,
                color: '#334155',
                marginBottom: 6,
              }}
            >
              {isAdmin ? 'Password' : 'Password (Vehicle ID)'}
            </label>
            <div style={{ position: 'relative' }}>
              <div
                style={{
                  position: 'absolute',
                  left: 14,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  color: '#94a3b8',
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                <Lock size={18} />
              </div>
              <input
                id="login-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={isAdmin ? 'Enter password' : 'Enter your Vehicle ID'}
                disabled={loading}
                style={{
                  width: '100%',
                  background: '#ffffff',
                  border: '1px solid #cbd5e1',
                  borderRadius: 10,
                  padding: '11px 42px 11px 42px',
                  color: '#0f172a',
                  fontSize: 14,
                  outline: 'none',
                  transition: 'border-color 0.15s, box-shadow 0.15s',
                }}
                onFocus={(e) => {
                  e.target.style.borderColor = isAdmin ? '#2563eb' : '#059669';
                  e.target.style.boxShadow = isAdmin
                    ? '0 0 0 3px rgba(37, 99, 235, 0.12)'
                    : '0 0 0 3px rgba(5, 150, 105, 0.12)';
                }}
                onBlur={(e) => {
                  e.target.style.borderColor = '#cbd5e1';
                  e.target.style.boxShadow = 'none';
                }}
              />
              <button
                type="button"
                id="btn-toggle-password"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute',
                  right: 12,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'transparent',
                  border: 'none',
                  color: '#94a3b8',
                  cursor: 'pointer',
                  padding: 4,
                  display: 'flex',
                  alignItems: 'center',
                }}
                title={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            id="btn-login-submit"
            disabled={loading}
            style={{
              width: '100%',
              background: isAdmin
                ? 'linear-gradient(135deg, #2563eb, #1d4ed8)'
                : 'linear-gradient(135deg, #059669, #047857)',
              border: 'none',
              borderRadius: 10,
              padding: '12px 16px',
              color: '#ffffff',
              fontSize: 14,
              fontWeight: 700,
              cursor: loading ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              boxShadow: isAdmin
                ? '0 4px 12px rgba(37, 99, 235, 0.25)'
                : '0 4px 12px rgba(5, 150, 105, 0.25)',
              transition: 'all 0.15s ease',
              opacity: loading ? 0.75 : 1,
            }}
          >
            {loading ? (
              <>
                <div
                  style={{
                    width: 16,
                    height: 16,
                    border: '2px solid rgba(255,255,255,0.3)',
                    borderTopColor: '#ffffff',
                    borderRadius: '50%',
                    animation: 'spin 0.8s linear infinite',
                  }}
                />
                <span>Authenticating…</span>
              </>
            ) : (
              <>
                <span>{isAdmin ? 'Sign In as Admin' : 'Sign In as Driver'}</span>
                <ArrowRight size={16} />
              </>
            )}
          </button>
        </form>

        {/* Footer */}
        <div
          style={{
            marginTop: 24,
            textAlign: 'center',
            borderTop: '1px solid #f1f5f9',
            paddingTop: 16,
          }}
        >
          <p style={{ fontSize: 11, color: '#94a3b8', margin: 0 }}>
            TrackFleet Enterprise v3.0 · Role-Based Security
          </p>
        </div>
      </div>

      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
        @media (max-width: 480px) {
          .role-selector-grid {
            grid-template-columns: 1fr !important;
          }
        }
      `}</style>
    </div>
  );
}
