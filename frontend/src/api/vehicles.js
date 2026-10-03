import api from './axios';
export const getVehicles = (params = {}) => api.get('/api/vehicles', { params }).then(r => r.data?.data || r.data);
export const getVehicleById = (id) => api.get(`/api/vehicles/${id}`).then(r => r.data?.data);
export const createVehicle = (dto) => api.post('/api/vehicles', dto).then(r => r.data?.data);
export const updateVehicle = (id, dto) => api.put(`/api/vehicles/${id}`, dto).then(r => r.data?.data);
