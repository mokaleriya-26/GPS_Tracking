import React, { createContext, useContext, useState, useRef, useCallback } from 'react';

const AlertToastContext = createContext(null);

export const AlertToastProvider = ({ children }) => {
  const [toasts, setToasts] = useState([]);
  const seenAlertIds = useRef(new Set());

  const dismissToast = useCallback((toastId) => {
    setToasts((prev) => prev.filter((t) => t.toastId !== toastId));
  }, []);

  const triggerAlertToast = useCallback((alert) => {
    if (!alert) return;

    // Deduplication key: alert.id or alert.reference
    const alertKey = alert.id ? `id_${alert.id}` : alert.reference ? `ref_${alert.reference}` : null;

    if (alertKey) {
      if (seenAlertIds.current.has(alertKey)) {
        return; // Already displayed — prevent duplicate popups
      }
      seenAlertIds.current.add(alertKey);
    }

    const toastId = 'toast_' + Date.now() + '_' + Math.random().toString(36).slice(2, 7);
    const severity = (alert.severity || 'WARNING').toUpperCase();
    const isCritical = severity === 'CRITICAL' ||
      (alert.alertType && (alert.alertType.toLowerCase().includes('overspeed') || alert.alertType.toLowerCase().includes('disconnect')));

    const newToast = {
      toastId,
      alertId: alert.id,
      alertType: alert.alertType || alert.alert_type || 'Safety Alert',
      driverName: alert.driverName || 'Assigned Driver',
      vehicleId: alert.vehicleCode || alert.vehicleRegistrationNumber || (alert.vehicleId ? `VH#${alert.vehicleId}` : 'Vehicle'),
      severity,
      isCritical,
      occurredAt: alert.occurredAt || new Date().toISOString(),
      penaltyAmount: alert.penaltyAmount,
      occurrenceNumber: alert.occurrenceNumber,
      message: alert.message,
    };

    setToasts((prev) => [newToast, ...prev].slice(0, 8)); // keep up to 8 recent toasts

    // Auto dismiss: critical stays 14s, normal stays 6s
    const timeoutMs = isCritical ? 14000 : 6000;
    setTimeout(() => {
      dismissToast(toastId);
    }, timeoutMs);
  }, [dismissToast]);

  return (
    <AlertToastContext.Provider value={{ toasts, triggerAlertToast, dismissToast }}>
      {children}
    </AlertToastContext.Provider>
  );
};

export const useAlertToast = () => {
  const ctx = useContext(AlertToastContext);
  if (!ctx) {
    throw new Error('useAlertToast must be used within an AlertToastProvider');
  }
  return ctx;
};
