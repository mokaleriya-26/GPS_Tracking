import React, { createContext, useContext, useState, useEffect } from 'react';
import authApi from '../api/auth';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [token, setToken] = useState(() => localStorage.getItem('trackfleet_token') || null);
  const [user, setUser] = useState(() => {
    try {
      const saved = localStorage.getItem('trackfleet_user');
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(false);

  const login = async (username, password) => {
    setLoading(true);
    try {
      const res = await authApi.login(username, password);
      if (res?.success && res.data) {
        const authData = res.data;
        const userData = {
          role: authData.role,
          username: authData.username,
          driverId: authData.driverId,
          driverCode: authData.driverCode,
          driverName: authData.driverName,
          vehicleId: authData.vehicleId,
          vehicleCode: authData.vehicleCode,
          vehicleRegistrationNumber: authData.vehicleRegistrationNumber,
        };
        localStorage.setItem('trackfleet_token', authData.token);
        localStorage.setItem('trackfleet_user', JSON.stringify(userData));
        setToken(authData.token);
        setUser(userData);
        return { success: true, user: userData };
      }
      return { success: false, message: res?.message || 'Login failed' };
    } catch (err) {
      const message = err?.response?.data?.message || err.message || 'Authentication failed';
      return { success: false, message };
    } finally {
      setLoading(false);
    }
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch (e) {
      console.warn('Logout error', e);
    } finally {
      localStorage.removeItem('trackfleet_token');
      localStorage.removeItem('trackfleet_user');
      setToken(null);
      setUser(null);
      window.location.href = '/login';
    }
  };

  const value = {
    token,
    user,
    isAuthenticated: Boolean(token && user),
    isAdmin: user?.role === 'ADMIN',
    isDriver: user?.role === 'USER',
    login,
    logout,
    loading
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
};
