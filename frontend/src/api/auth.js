import api from './axios';

export const authApi = {
  login: async (username, password) => {
    const res = await api.post('/api/auth/login', { username, password });
    return res.data; // ApiResponse<LoginResponseDTO>
  },
  getCurrentUser: async () => {
    const res = await api.get('/api/auth/me');
    return res.data;
  },
  logout: async () => {
    try {
      await api.post('/api/auth/logout');
    } finally {
      localStorage.removeItem('trackfleet_token');
      localStorage.removeItem('trackfleet_user');
    }
  }
};

export default authApi;
