import api from './axios';

export const simulationApi = {
  getRoutes: () => api.get('/api/simulation/routes').then(r => r.data),
  getStatus: () => api.get('/api/simulation/status').then(r => r.data),
  startSimulation: (routeId, multiplier) =>
    api.post('/api/simulation/start', { routeId, multiplier }).then(r => r.data),
  pauseSimulation: () => api.post('/api/simulation/pause').then(r => r.data),
  resumeSimulation: () => api.post('/api/simulation/resume').then(r => r.data),
  stopSimulation: () => api.post('/api/simulation/stop').then(r => r.data),
  resetSimulation: () => api.post('/api/simulation/reset').then(r => r.data),
  stepSimulation: () => api.post('/api/simulation/step').then(r => r.data),
  triggerEvent: (eventType, overrideSpeedKmph = null, eventId = null) =>
    api.post('/api/simulation/trigger-event', { eventType, overrideSpeedKmph, eventId }).then(r => r.data),
  getSummary: () => api.get('/api/simulation/summary').then(r => r.data),
};

export default simulationApi;
