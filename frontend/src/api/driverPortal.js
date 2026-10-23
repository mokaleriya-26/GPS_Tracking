import api from './axios';

export const driverPortalApi = {
  getDashboard: async () => {
    const res = await api.get('/api/driver-portal/dashboard');
    return res.data?.data;
  },
  getProfile: async () => {
    const res = await api.get('/api/driver-portal/profile');
    return res.data?.data;
  },
  getVehicles: async () => {
    const res = await api.get('/api/driver-portal/vehicles');
    return res.data?.data || [];
  },
  getAlerts: async (page = 0, size = 50) => {
    const res = await api.get(`/api/driver-portal/alerts?page=${page}&size=${size}`);
    return res.data?.data;
  },
  getPenalties: async (params = {}) => {
    const res = await api.get('/api/driver-portal/penalties', { params });
    return res.data?.data || [];
  },
  getPenaltySummary: async () => {
    const res = await api.get('/api/driver-portal/penalties/summary');
    return res.data?.data;
  },
  getTrips: async (page = 0, size = 50) => {
    const res = await api.get(`/api/driver-portal/trips?page=${page}&size=${size}`);
    return res.data?.data;
  },
  generateReport: async (payload) => {
    const res = await api.post('/api/driver-portal/reports/generate', payload);
    return res.data?.data;
  }
};

export default driverPortalApi;
