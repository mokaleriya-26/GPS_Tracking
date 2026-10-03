import api from './axios';
export const getAlerts = (params = {}) => api.get('/api/alerts', { params }).then(r => r.data?.data || r.data);
export const getAlertById = (id) => api.get(`/api/alerts/${id}`).then(r => r.data?.data);
export const resolveAlert = (id, resolvedBy = 'Fleet Admin') => api.put(`/api/alerts/${id}/resolve`, null, { params: { resolvedBy } }).then(r => r.data?.data);
