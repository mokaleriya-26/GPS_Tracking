import api from './axios.js';

export const getVehicles = async () => {
  const response = await api.get('/api/alerts/');
  return response.data;
};
