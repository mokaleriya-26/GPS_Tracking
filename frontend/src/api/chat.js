import api from './axios';
let sessionId = localStorage.getItem('chatSessionId') || null;
export const sendChatMessage = async (message) => {
  const res = await api.post('/api/chat/message', { sessionId, message }).then(r => r.data?.data);
  if (res?.sessionId) { sessionId = res.sessionId; localStorage.setItem('chatSessionId', sessionId); }
  return res;
};
export const clearSession = () => { sessionId = null; localStorage.removeItem('chatSessionId'); };
