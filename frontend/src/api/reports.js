import api from './axios';

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const generateDriverReport = (driverId, body) => {
  const type = (body.reportType || 'monthly').toLowerCase();
  return api.post(`/api/reports/driver/${driverId}/${type}`, body).then(r => r.data?.data);
};

export const generateDriverDailyReport = (driverId, body) =>
  api.post(`/api/reports/driver/${driverId}/daily`, body).then(r => r.data?.data);

export const generateDriverWeeklyReport = (driverId, body) =>
  api.post(`/api/reports/driver/${driverId}/weekly`, body).then(r => r.data?.data);

export const generateDriverMonthlyReport = (driverId, body) =>
  api.post(`/api/reports/driver/${driverId}/monthly`, body).then(r => r.data?.data);

export const generateDriverCustomReport = (driverId, body) =>
  api.post(`/api/reports/driver/${driverId}/custom`, body).then(r => r.data?.data);

export const generateFleetReport = (body) =>
  api.post('/api/reports/overall', body).then(r => r.data?.data);

export const generateOverallReport = (body) =>
  api.post('/api/reports/overall', body).then(r => r.data?.data);

export const generateDriverAlertReport = (driverId, body) =>
  api.post(`/api/reports/driver/${driverId}/alert-based`, body).then(r => r.data?.data);

export const generateFleetAlertReport = (body) =>
  api.post('/api/reports/fleet/alert-based', body).then(r => r.data?.data);

export const getPdfUrl = (reportId) => `${BASE}/api/reports/${reportId}/pdf`;
export const getDownloadUrl = (reportId) => `${BASE}/api/reports/${reportId}/download`;
