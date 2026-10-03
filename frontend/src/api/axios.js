import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

// Response interceptor — unwrap ApiResponse envelope
api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API error:', error?.response?.data?.message || error.message);
    return Promise.reject(error);
  }
);

export default api;
