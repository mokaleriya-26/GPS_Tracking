import api from './axios';
export const getTrips = (params = {}) => api.get('/api/trips', { params }).then(r => r.data?.data || r.data);
export const getTripById = (id) => api.get(`/api/trips/${id}`).then(r => r.data?.data);
export const getTripsByVehicle = (vehicleId, params = {}) => api.get(`/api/trips/vehicle/${vehicleId}`, { params }).then(r => r.data?.data || r.data);
export const getTripsByDriver = (driverId, params = {}) => api.get(`/api/trips/driver/${driverId}`, { params }).then(r => r.data?.data || r.data);
