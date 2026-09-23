import api from './axios.js';

export const getAlerts = async () => {
  const response = await api.get('/api/alerts/');
  return response.data;
};
