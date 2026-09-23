import api from './axios';

export const getAlerts = async () => {
  const response = await api.get('/alerts');
  return response.data;
};
