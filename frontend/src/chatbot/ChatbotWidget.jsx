import React, { useState, useRef, useEffect } from 'react';
import { MessageCircle, X, Send, Bot, User, FileText, Download, ExternalLink, RefreshCw } from 'lucide-react';
import { sendChatMessage, clearSession } from '../api/chat';

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const WELCOME = {
  role: 'bot',
  text: `👋 Hi! I'm your **Fleet Assistant**.\n\nTry asking:\n• *"Who is the safest driver?"*\n• *"Monthly report for DRV001"*\n• *"Rohan's September report"*\n• *"Show open alerts"*\n• *"How many trips this month?"*\n• *"Generate fleet report for October"*`,
  ts: new Date()
};

/** Render bot text: bold (**...**), italic (*...*), newlines */
function RenderText({ text }) {
  if (!text) return null;
  const lines = String(text).split('\n');
  return (
    <>
      {lines.map((line, li) => {
        const parts = line.split(/(\*\*.*?\*\*|\*.*?\*)/g);
        return (
          <span key={li}>
            {parts.map((part, pi) => {
              if (part.startsWith('**') && part.endsWith('**'))
                return <strong key={pi}>{part.slice(2, -2)}</strong>;
              if (part.startsWith('*') && part.endsWith('*'))
                return <em key={pi}>{part.slice(1, -1)}</em>;
              return <span key={pi}>{part}</span>;
            })}
            {li < lines.length - 1 && <br />}
          </span>
        );
      })}
    </>
  );
}

export default function ChatbotWidget() {
  const [open, setOpen]       = useState(false);
  const [messages, setMessages] = useState([WELCOME]);
  const [input, setInput]     = useState('');
  const [loading, setLoading] = useState(false);
  const [unread, setUnread]   = useState(0);
  const bottomRef = useRef(null);
  const inputRef  = useRef(null);

  useEffect(() => {
    if (open) { setUnread(0); setTimeout(() => inputRef.current?.focus(), 120); }
  }, [open]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const send = async () => {
    const msg = input.trim();
    if (!msg || loading) return;          // prevent duplicate sends
    setInput('');
    setMessages(prev => [...prev, { role: 'user', text: msg, ts: new Date() }]);
    setLoading(true);

    try {
      const res = await sendChatMessage(msg);
      if (!res) throw new Error('Empty response from server');

      // Backend ChatResponseDTO fields: message, intent, reportId, pdfUrl, downloadUrl, data, askingForClarification
      const botMsg = {
        role: 'bot',
        text: res.message || '(No response)',
        intent: res.intent,
        reportId: res.reportId,
        pdfUrl: res.pdfUrl,
        downloadUrl: res.downloadUrl,
        data: res.data,
        askingForClarification: res.askingForClarification,
        ts: new Date()
      };
      setMessages(prev => [...prev, botMsg]);
      if (!open) setUnread(u => u + 1);
    } catch (e) {
      const errText = e?.response?.status === 0
        ? '⚠️ Cannot reach backend. Make sure Spring Boot is running on port 8080.'
        : `⚠️ ${e?.response?.data?.message || e.message || 'Unknown error'}`;
      setMessages(prev => [...prev, { role: 'bot', text: errText, ts: new Date() }]);
    } finally {
      setLoading(false);
    }
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
  };

  const reset = () => { setMessages([WELCOME]); clearSession(); };

  const ts = (date) => date?.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  return (
    <>
      {/* ===== Floating trigger button ===== */}
      <div className="chatbot-trigger">
        <button onClick={() => setOpen(o => !o)}
          title="Fleet Assistant"
          style={{
            width: 54, height: 54, borderRadius: '50%', border: 'none', cursor: 'pointer',
            background: 'linear-gradient(135deg,#2563eb,#1d4ed8)',
            boxShadow: '0 8px 24px rgba(37,99,235,0.45)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'transform .2s'
          }}
          onMouseEnter={e => e.currentTarget.style.transform = 'scale(1.08)'}
          onMouseLeave={e => e.currentTarget.style.transform = 'scale(1)'}>
          {open
            ? <X size={22} color="#fff" />
            : <MessageCircle size={22} color="#fff" />}
          {!open && unread > 0 && (
            <span style={{
              position: 'absolute', top: -2, right: -2, background: '#ef4444',
              color: '#fff', fontSize: 10, fontWeight: 700,
              width: 18, height: 18, borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              border: '2px solid #fff'
            }}>{unread}</span>
          )}
        </button>
      </div>

      {/* ===== Chat window ===== */}
      {open && (
        <div className="chatbot-window">
          {/* Header */}
          <div style={{
            background: 'linear-gradient(135deg,#1e3a5f,#2563eb)',
            padding: '12px 16px', display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0
          }}>
            <div style={{
              width: 34, height: 34, borderRadius: '50%',
              background: 'rgba(255,255,255,0.15)',
              display: 'flex', alignItems: 'center', justifyContent: 'center'
            }}>
              <Bot size={17} color="#fff" />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ color: '#fff', fontWeight: 700, fontSize: 14 }}>Fleet Assistant</div>
              <div style={{ color: 'rgba(255,255,255,.6)', fontSize: 10 }}>
                {loading ? '⟳ Thinking...' : '● Online'}
              </div>
            </div>
            <button onClick={reset} title="Clear chat"
              style={{ background: 'none', border: 'none', color: 'rgba(255,255,255,.65)',
                cursor: 'pointer', padding: 4, fontSize: 11, borderRadius: 4,
                display: 'flex', alignItems: 'center', gap: 4 }}>
              <RefreshCw size={13} /> Clear
            </button>
            <button onClick={() => setOpen(false)}
              style={{ background: 'none', border: 'none', color: '#fff', cursor: 'pointer', padding: 4 }}>
              <X size={17} />
            </button>
          </div>

          {/* Messages area */}
          <div style={{ flex: '1 1 0', overflowY: 'auto', padding: '12px 14px', background: '#f8fafc' }}>
            {messages.map((m, i) => (
              <div key={i} style={{
                display: 'flex',
                flexDirection: m.role === 'user' ? 'row-reverse' : 'row',
                alignItems: 'flex-end', gap: 8, marginBottom: 12
              }}>
                {/* Avatar */}
                <div style={{
                  width: 26, height: 26, borderRadius: '50%', flexShrink: 0,
                  background: m.role === 'user' ? '#2563eb' : '#1e3a5f',
                  display: 'flex', alignItems: 'center', justifyContent: 'center'
                }}>
                  {m.role === 'user'
                    ? <User size={13} color="#fff" />
                    : <Bot size={13} color="#fff" />}
                </div>

                <div style={{ maxWidth: '80%', minWidth: 0 }}>
                  {/* Bubble */}
                  <div style={{
                    background: m.role === 'user'
                      ? 'linear-gradient(135deg,#2563eb,#1d4ed8)' : '#fff',
                    color: m.role === 'user' ? '#fff' : '#1e293b',
                    borderRadius: m.role === 'user'
                      ? '16px 16px 4px 16px' : '16px 16px 16px 4px',
                    padding: '9px 13px', fontSize: 13, lineHeight: 1.55,
                    boxShadow: '0 2px 8px rgba(0,0,0,0.07)',
                    border: m.role === 'bot' ? '1px solid #e2e8f0' : 'none',
                    wordBreak: 'break-word', overflowWrap: 'break-word'
                  }}>
                    <RenderText text={m.text} />
                  </div>

                  {/* PDF card if report was generated */}
                  {m.reportId && (
                    <div style={{
                      marginTop: 8, background: '#fff', border: '1px solid #bfdbfe',
                      borderRadius: 10, padding: '10px 12px',
                      display: 'flex', gap: 10, alignItems: 'center'
                    }}>
                      <FileText size={18} color="#2563eb" style={{ flexShrink: 0 }} />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 12, fontWeight: 600, color: '#1e293b' }}>Report Ready</div>
                        <div style={{ fontSize: 10, color: '#64748b', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {m.reportId}
                        </div>
                      </div>
                      {m.pdfUrl && (
                        <a href={`${BASE}${m.pdfUrl}`} target="_blank" rel="noreferrer"
                          style={{
                            background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 6,
                            padding: '4px 8px', fontSize: 11, color: '#2563eb',
                            textDecoration: 'none', display: 'flex', alignItems: 'center', gap: 3, flexShrink: 0
                          }}>
                          <ExternalLink size={11} /> View
                        </a>
                      )}
                      {m.downloadUrl && (
                        <a href={`${BASE}${m.downloadUrl}`} download
                          style={{
                            background: '#2563eb', border: 'none', borderRadius: 6,
                            padding: '4px 8px', fontSize: 11, color: '#fff',
                            textDecoration: 'none', display: 'flex', alignItems: 'center', gap: 3, flexShrink: 0
                          }}>
                          <Download size={11} /> Save
                        </a>
                      )}
                    </div>
                  )}

                  {/* Clarification hint */}
                  {m.askingForClarification && (
                    <div style={{ marginTop: 4, fontSize: 10, color: '#94a3b8', fontStyle: 'italic' }}>
                      Waiting for your reply…
                    </div>
                  )}

                  <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 3,
                    textAlign: m.role === 'user' ? 'right' : 'left' }}>
                    {ts(m.ts)}
                  </div>
                </div>
              </div>
            ))}

            {/* Typing indicator */}
            {loading && (
              <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', marginBottom: 12 }}>
                <div style={{
                  width: 26, height: 26, borderRadius: '50%', background: '#1e3a5f',
                  display: 'flex', alignItems: 'center', justifyContent: 'center'
                }}>
                  <Bot size={13} color="#fff" />
                </div>
                <div style={{
                  background: '#fff', border: '1px solid #e2e8f0',
                  borderRadius: '16px 16px 16px 4px', padding: '12px 16px',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.07)'
                }}>
                  <div style={{ display: 'flex', gap: 4 }}>
                    {[0, 1, 2].map(i => (
                      <div key={i} style={{
                        width: 7, height: 7, borderRadius: '50%', background: '#94a3b8',
                        animation: `chatPulse 1.2s ease-in-out ${i * 0.2}s infinite`
                      }} />
                    ))}
                  </div>
                </div>
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          {/* Input bar */}
          <div style={{
            padding: '10px 14px', borderTop: '1px solid #e2e8f0', background: '#fff',
            display: 'flex', gap: 8, alignItems: 'center', flexShrink: 0
          }}>
            <textarea
              ref={inputRef}
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={handleKey}
              placeholder="Ask about drivers, alerts, reports..."
              rows={1}
              disabled={loading}
              style={{
                flex: 1, resize: 'none', border: '1.5px solid #e2e8f0',
                borderRadius: 10, padding: '8px 12px', fontSize: 13,
                fontFamily: 'inherit', outline: 'none', lineHeight: 1.4,
                maxHeight: 80, overflowY: 'auto', background: '#f8fafc',
                transition: 'border-color .2s'
              }}
              onFocus={e => e.target.style.borderColor = '#2563eb'}
              onBlur={e => e.target.style.borderColor = '#e2e8f0'}
            />
            <button onClick={send}
              disabled={!input.trim() || loading}
              style={{
                width: 38, height: 38, borderRadius: '50%', border: 'none', cursor: 'pointer',
                background: input.trim() && !loading
                  ? 'linear-gradient(135deg,#2563eb,#1d4ed8)' : '#e2e8f0',
                color: input.trim() && !loading ? '#fff' : '#94a3b8',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                transition: 'background .2s', flexShrink: 0
              }}>
              <Send size={15} />
            </button>
          </div>
        </div>
      )}

      <style>{`
        @keyframes chatPulse {
          0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
          40%            { transform: scale(1);   opacity: 1; }
        }
      `}</style>
    </>
  );
}
