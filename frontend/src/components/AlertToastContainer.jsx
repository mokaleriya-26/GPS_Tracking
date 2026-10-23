import React from 'react';
import { useAlertToast } from '../context/AlertToastContext';
import { AlertCircle, AlertTriangle, ShieldAlert, X, DollarSign, Clock, User, Car } from 'lucide-react';

const formatTime = (ts) => {
  if (!ts) return '';
  try {
    const d = new Date(ts);
    return d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true });
  } catch {
    return '';
  }
};

const AlertToastContainer = () => {
  const { toasts, dismissToast } = useAlertToast();

  if (!toasts || toasts.length === 0) return null;

  return (
    <div
      className="alert-toast-container"
      style={{
        position: 'fixed',
        top: 20,
        right: 20,
        zIndex: 999999,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        width: 380,
        maxWidth: 'calc(100vw - 32px)',
        pointerEvents: 'none',
      }}
    >
      {toasts.map((toast) => {
        const isCritical = toast.isCritical;
        const severity = toast.severity;

        // Visual scheme
        let bgStyle = 'linear-gradient(135deg, #ffffff, #fff5f5)';
        let borderStyle = '2px solid #ef4444';
        let iconBg = '#fee2e2';
        let iconColor = '#dc2626';
        let badgeBg = '#dc2626';
        let shadowStyle = '0 10px 25px -5px rgba(220, 38, 38, 0.4), 0 0 15px rgba(239, 68, 68, 0.3)';

        if (severity === 'CRITICAL' || isCritical) {
          bgStyle = 'linear-gradient(135deg, #1e1b2e, #2d1522)';
          borderStyle = '2px solid #ef4444';
          iconBg = 'rgba(239, 68, 68, 0.25)';
          iconColor = '#f87171';
          badgeBg = '#ef4444';
          shadowStyle = '0 12px 30px rgba(239, 68, 68, 0.45), 0 0 20px rgba(239, 68, 68, 0.3)';
        } else if (severity === 'WARNING' || severity === 'HIGH') {
          bgStyle = 'linear-gradient(135deg, #ffffff, #fffbeb)';
          borderStyle = '2px solid #f59e0b';
          iconBg = '#fef3c7';
          iconColor = '#d97706';
          badgeBg = '#f59e0b';
          shadowStyle = '0 10px 22px rgba(245, 158, 11, 0.25)';
        } else if (severity === 'MEDIUM') {
          bgStyle = 'linear-gradient(135deg, #ffffff, #eff6ff)';
          borderStyle = '2px solid #3b82f6';
          iconBg = '#dbeafe';
          iconColor = '#2563eb';
          badgeBg = '#3b82f6';
          shadowStyle = '0 8px 20px rgba(59, 130, 246, 0.2)';
        } else {
          bgStyle = 'linear-gradient(135deg, #ffffff, #f8fafc)';
          borderStyle = '2px solid #94a3b8';
          iconBg = '#f1f5f9';
          iconColor = '#64748b';
          badgeBg = '#64748b';
          shadowStyle = '0 6px 16px rgba(0, 0, 0, 0.1)';
        }

        const isDark = isCritical;
        const textColor = isDark ? '#ffffff' : '#0f172a';
        const subColor = isDark ? '#cbd5e1' : '#475569';

        return (
          <div
            key={toast.toastId}
            className={`alert-toast-card ${isCritical ? 'critical-pulse' : ''}`}
            style={{
              pointerEvents: 'auto',
              background: bgStyle,
              border: borderStyle,
              borderRadius: 14,
              padding: '14px 16px',
              boxShadow: shadowStyle,
              animation: 'slideInRight 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
              fontFamily: 'Inter, system-ui, sans-serif',
              position: 'relative',
              overflow: 'hidden',
              backdropFilter: 'blur(8px)',
            }}
          >
            {/* Top Row: Severity, Alert Type, Time, Close */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: 9,
                    background: iconBg,
                    color: iconColor,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  {isCritical ? <ShieldAlert size={18} /> : severity === 'WARNING' ? <AlertTriangle size={17} /> : <AlertCircle size={17} />}
                </div>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span
                      style={{
                        background: badgeBg,
                        color: '#fff',
                        fontSize: 10,
                        fontWeight: 800,
                        padding: '2px 7px',
                        borderRadius: 20,
                        letterSpacing: '0.04em',
                        textTransform: 'uppercase',
                      }}
                    >
                      {severity}
                    </span>
                    <span style={{ fontSize: 13, fontWeight: 800, color: textColor }}>
                      {toast.alertType}
                    </span>
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: 11, color: subColor, display: 'flex', alignItems: 'center', gap: 3 }}>
                  <Clock size={11} /> {formatTime(toast.occurredAt)}
                </span>
                <button
                  onClick={() => dismissToast(toast.toastId)}
                  style={{
                    background: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.05)',
                    border: 'none',
                    borderRadius: '50%',
                    width: 22,
                    height: 22,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    color: isDark ? '#fff' : '#64748b',
                    padding: 0,
                    transition: 'all 0.15s ease',
                  }}
                  title="Dismiss notification"
                >
                  <X size={13} />
                </button>
              </div>
            </div>

            {/* Middle Row: Driver & Vehicle */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 8,
                padding: '6px 10px',
                background: isDark ? 'rgba(0,0,0,0.3)' : 'rgba(255,255,255,0.6)',
                borderRadius: 8,
                marginBottom: 8,
                fontSize: 12,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 5, color: subColor }}>
                <User size={13} color={isDark ? '#93c5fd' : '#2563eb'} />
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  <strong style={{ color: textColor }}>{toast.driverName}</strong>
                </span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 5, color: subColor }}>
                <Car size={13} color={isDark ? '#cbd5e1' : '#475569'} />
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  Vehicle: <strong style={{ color: textColor }}>{toast.vehicleId}</strong>
                </span>
              </div>
            </div>

            {/* Bottom Row: Applicable Penalty Info */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                fontSize: 11,
                fontWeight: 600,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                {toast.occurrenceNumber ? (
                  <span
                    style={{
                      background: toast.occurrenceNumber <= 2 ? 'rgba(59, 130, 246, 0.18)' : 'rgba(239, 68, 68, 0.2)',
                      color: toast.occurrenceNumber <= 2 ? '#3b82f6' : '#ef4444',
                      padding: '2px 6px',
                      borderRadius: 4,
                      fontSize: 10,
                      fontWeight: 700,
                    }}
                  >
                    Occurrence #{toast.occurrenceNumber}
                  </span>
                ) : null}
              </div>

              <div>
                {toast.penaltyAmount != null && parseFloat(toast.penaltyAmount) > 0 ? (
                  <span
                    style={{
                      background: 'rgba(220, 38, 38, 0.2)',
                      color: '#ef4444',
                      padding: '2px 8px',
                      borderRadius: 6,
                      fontSize: 11,
                      fontWeight: 800,
                      border: '1px solid rgba(239, 68, 68, 0.4)',
                    }}
                  >
                    Penalty: ₹{parseFloat(toast.penaltyAmount).toFixed(2)}
                  </span>
                ) : (
                  <span
                    style={{
                      background: isDark ? 'rgba(16, 185, 129, 0.2)' : '#ecfdf5',
                      color: '#10b981',
                      padding: '2px 8px',
                      borderRadius: 6,
                      fontSize: 11,
                      fontWeight: 700,
                      border: '1px solid rgba(16, 185, 129, 0.3)',
                    }}
                  >
                    {toast.occurrenceNumber <= 2 ? '₹0 (Warning Rule Applied)' : '₹0 Penalty'}
                  </span>
                )}
              </div>
            </div>
          </div>
        );
      })}

      <style>{`
        @keyframes slideInRight {
          from {
            transform: translateX(110%);
            opacity: 0;
          }
          to {
            transform: translateX(0);
            opacity: 1;
          }
        }
        .critical-pulse {
          box-shadow: 0 0 25px rgba(239, 68, 68, 0.5) !important;
          animation: slideInRight 0.3s cubic-bezier(0.16, 1, 0.3, 1), criticalPulseGlow 2s infinite ease-in-out !important;
        }
        @keyframes criticalPulseGlow {
          0%, 100% { box-shadow: 0 8px 25px rgba(239, 68, 68, 0.3), 0 0 12px rgba(239, 68, 68, 0.2); }
          50% { box-shadow: 0 12px 35px rgba(239, 68, 68, 0.7), 0 0 24px rgba(239, 68, 68, 0.5); }
        }
      `}</style>
    </div>
  );
};

export default AlertToastContainer;
