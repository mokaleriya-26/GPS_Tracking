import api from './axios.js';

export const getVehicles = async () => {
  const response = await api.get('/api/vehicles/');
  return response.data;
};
