import api from './axios';

export const getVehicles = async () => {
  const response = await api.get('/data');
  return response.data;
};
