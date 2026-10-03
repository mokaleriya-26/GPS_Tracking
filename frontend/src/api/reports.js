import api from './axios';

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const generateDriverReport = (driverId, body) =>
  api.post(`/api/reports/driver/${driverId}/monthly`, body).then(r => r.data?.data);

export const generateDriverCustomReport = (driverId, body) =>
  api.post(`/api/reports/driver/${driverId}/custom`, body).then(r => r.data?.data);

export const generateFleetReport = (body) =>
  api.post('/api/reports/fleet/monthly', body).then(r => r.data?.data);

/** NEW: Overall report — returns OverallReportDTO from DB */
export const generateOverallReport = (body) =>
  api.post('/api/reports/overall', body).then(r => r.data?.data);

export const getPdfUrl   = (reportId) => `${BASE}/api/reports/${reportId}/pdf`;
export const getDownloadUrl = (reportId) => `${BASE}/api/reports/${reportId}/download`;
