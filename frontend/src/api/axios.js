import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

// Request interceptor — attach JWT bearer token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('trackfleet_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor — unwrap ApiResponse envelope and handle 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      const isAuthEndpoint = error.config?.url?.includes('/api/auth/');
      if (!isAuthEndpoint && window.location.pathname !== '/login') {
        localStorage.removeItem('trackfleet_token');
        localStorage.removeItem('trackfleet_user');
        window.location.href = '/login';
      }
    }
    console.error('API error:', error?.response?.data?.message || error.message);
    return Promise.reject(error);
  }
);

export default api;
