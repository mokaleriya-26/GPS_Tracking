import api from './axios';

export const getPenaltyConfigs = () =>
  api.get('/api/penalties/configs').then(r => r.data?.data || []);

export const updatePenaltyConfig = (id, data) =>
  api.put(`/api/penalties/configs/${id}`, data, {
    headers: { 'X-User-Role': 'MANAGER', 'X-User-Name': 'Fleet Manager' }
  }).then(r => r.data?.data);

export const getAllPenalties = () =>
  api.get('/api/penalties').then(r => r.data?.data || []);

export const getDriverPenalties = (driverId) =>
  api.get(`/api/penalties/driver/${driverId}`).then(r => r.data?.data || []);

export const recordPenalty = (data) =>
  api.post('/api/penalties', data, {
    headers: { 'X-User-Role': 'MANAGER', 'X-User-Name': 'Fleet Manager' }
  }).then(r => r.data?.data);

export const revokePenalty = (id, reason) =>
  api.put(`/api/penalties/${id}/revoke`, { reason }, {
    headers: { 'X-User-Role': 'MANAGER', 'X-User-Name': 'Fleet Manager' }
  }).then(r => r.data?.data);
