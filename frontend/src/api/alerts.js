import api from './axios';
export const getAlerts = (params = {}) => api.get('/api/alerts', { params }).then(r => r.data?.data || r.data);
export const createAlert = (data) => api.post('/api/alerts', data).then(r => r.data?.data);
export const resolveAlert = (id, resolvedBy = 'Fleet Admin') => api.put(`/api/alerts/${id}/resolve`, null, { params: { resolvedBy } }).then(r => r.data?.data);
export const getAlertSummary = () => api.get('/api/alerts/summary').then(r => r.data?.data || r.data);
