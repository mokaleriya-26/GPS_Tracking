import api from './axios';
export const getDrivers = (params = {}) => api.get('/api/drivers', { params }).then(r => r.data?.data || r.data);
export const getDriverById = (id) => api.get(`/api/drivers/${id}`).then(r => r.data?.data);
export const getDriverByCode = (code) => api.get(`/api/drivers/code/${code}`).then(r => r.data?.data);
export const searchDrivers = (name) => api.get('/api/drivers/search', { params: { name } }).then(r => r.data?.data);
export const getDriverRanking = (from, to) => api.get('/api/drivers/ranking', { params: { from, to } }).then(r => r.data?.data || []);
export const getDriverStats = (driverId, from, to) => api.get(`/api/stats/driver/${driverId}`, { params: { from, to } }).then(r => r.data?.data || []);
export const createDriver = (dto) => api.post('/api/drivers', dto).then(r => r.data?.data);
export const updateDriver = (id, dto) => api.put(`/api/drivers/${id}`, dto).then(r => r.data?.data);
