import React, { useState, useRef, useEffect } from 'react';
import { MessageCircle, X, Send, Bot, User, FileText, Download, ExternalLink, RefreshCw, Zap } from 'lucide-react';
import { sendChatMessage, clearSession } from '../api/chat';

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const WELCOME = {
  role: 'bot',
  text: `👋 **Hi! I'm your Fleet Assistant.**\n\nI have live access to your fleet database — vehicles, drivers, trips, alerts and PDF reports.\n\nTry asking:\n• *"Give me recommendation of drivers"*\n• *"Which vehicle has the most alerts?"*\n• *"Who is the safest driver?"*\n• *"Who should I choose for a long trip?"*\n• *"Tell me about VH003"*\n• *"Generate fleet report for September 2026"*`,
  ts: new Date(),
  quickActions: ['🌟 Recommend Drivers', '📊 Fleet Summary', '🏆 Safest Driver', '🚗 VH001 Details']
};

/** Render plain text line with bold/italic/table-row support */
function RenderLine({ line, isFirstRow }) {
  if (line.startsWith('|') && line.endsWith('|')) {
    if (/^\|[-:\s|]+\|$/.test(line)) return null;
    const cells = line.slice(1, -1).split('|').map(c => c.trim());
    const isHeader = isFirstRow || line.includes('**') || cells.some(c => /^[*_]/.test(c));
    return (
      <tr style={{ borderBottom: '1px solid #e2e8f0', background: isHeader ? '#f1f5f9' : 'transparent' }}>
        {cells.map((cell, i) => {
          const Tag = isHeader ? 'th' : 'td';
          return (
            <Tag key={i} style={{
              padding: '6px 8px', textAlign: i === 0 ? 'center' : (i === 1 ? 'left' : 'right'),
              fontSize: 11, fontWeight: isHeader ? 700 : 400,
              color: isHeader ? '#0f172a' : '#334155',
              whiteSpace: 'nowrap'
            }}>
              <InlineText text={cell} />
            </Tag>
          );
        })}
      </tr>
    );
  }
  return <span><InlineText text={line} /></span>;
}

/** Inline bold/italic renderer */
function InlineText({ text }) {
  const parts = String(text || '').split(/(\*\*.*?\*\*|\*.*?\*)/g);
  return (
    <>
      {parts.map((part, i) => {
        if (part.startsWith('**') && part.endsWith('**'))
          return <strong key={i}>{part.slice(2, -2)}</strong>;
        if (part.startsWith('*') && part.endsWith('*'))
          return <em key={i}>{part.slice(1, -1)}</em>;
        return <span key={i}>{part}</span>;
      })}
    </>
  );
}

/** Full message renderer: handles tables, bullets, headings, normal lines */
function RenderText({ text }) {
  if (!text) return null;
  const lines = String(text).split('\n');

  // Group consecutive table rows into a <table>
  const groups = [];
  let tableBuffer = [];

  const flushTable = () => {
    if (tableBuffer.length > 0) {
      groups.push({ type: 'table', lines: tableBuffer });
      tableBuffer = [];
    }
  };

  for (const line of lines) {
    if (line.startsWith('|') && line.endsWith('|')) {
      tableBuffer.push(line);
    } else {
      flushTable();
      groups.push({ type: 'line', content: line });
    }
  }
  flushTable();

  return (
    <>
      {groups.map((g, gi) => {
        if (g.type === 'table') {
          // Filter out markdown separator rows
          const rows = g.lines.filter(l => !/^\|[-:\s|]+\|$/.test(l));
          return (
            <div key={gi} style={{
              overflowX: 'auto', margin: '8px 0',
              border: '1px solid #e2e8f0', borderRadius: 8,
              background: '#fff', boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
            }}>
              <table style={{ borderCollapse: 'collapse', width: '100%', fontSize: 11 }}>
                <tbody>
                  {rows.map((row, ri) => <RenderLine key={ri} line={row} isFirstRow={ri === 0} />)}
                </tbody>
              </table>
            </div>
          );
        }
        const line = g.content;
        // Empty line → spacing
        if (!line.trim()) return <div key={gi} style={{ height: 6 }} />;
        // Heading: starts with #
        if (line.startsWith('# ')) return <div key={gi} style={{ fontWeight: 800, fontSize: 14, color: '#1e3a5f', marginTop: 8 }}><InlineText text={line.slice(2)} /></div>;
        if (line.startsWith('## ')) return <div key={gi} style={{ fontWeight: 700, fontSize: 13, color: '#1e3a5f', marginTop: 6 }}><InlineText text={line.slice(3)} /></div>;
        // Bullet
        if (line.startsWith('• ') || line.startsWith('- ') || line.startsWith('* ')) {
          return (
            <div key={gi} style={{ display: 'flex', gap: 6, alignItems: 'flex-start', marginLeft: 4, marginBottom: 2 }}>
              <span style={{ color: '#2563eb', fontSize: 14, lineHeight: '1.4', flexShrink: 0 }}>•</span>
              <span style={{ fontSize: 13, lineHeight: 1.45 }}><InlineText text={line.slice(2)} /></span>
            </div>
          );
        }
        return <div key={gi} style={{ fontSize: 13, lineHeight: 1.5, marginBottom: 2 }}><InlineText text={line} /></div>;
      })}
    </>
  );
}

/** Quick action chip button */
function QuickAction({ label, onClick }) {
  const [hover, setHover] = useState(false);
  return (
    <button
      onClick={() => onClick(label)}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        background: hover ? '#2563eb' : '#eff6ff',
        color: hover ? '#fff' : '#2563eb',
        border: '1.5px solid #bfdbfe',
        borderRadius: 20,
        padding: '4px 12px',
        fontSize: 12,
        fontFamily: 'inherit',
        fontWeight: 600,
        cursor: 'pointer',
        transition: 'background 0.15s, color 0.15s',
        whiteSpace: 'nowrap',
        lineHeight: '1.4'
      }}
    >
      {label}
    </button>
  );
}

export default function ChatbotWidget() {
  const [open, setOpen]         = useState(false);
  const [messages, setMessages] = useState([WELCOME]);
  const [input, setInput]       = useState('');
  const [loading, setLoading]   = useState(false);
  const [unread, setUnread]     = useState(0);
  const [error, setError]       = useState(null);
  const bottomRef = useRef(null);
  const inputRef  = useRef(null);

  useEffect(() => {
    if (open) { setUnread(0); setError(null); setTimeout(() => inputRef.current?.focus(), 120); }
  }, [open]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const sendMsg = async (text) => {
    const msg = (text || input).trim();
    if (!msg || loading) return;
    setInput('');
    setError(null);
    setMessages(prev => [...prev, { role: 'user', text: msg, ts: new Date() }]);
    setLoading(true);

    try {
      const res = await sendChatMessage(msg);
      if (!res) throw new Error('Empty response from server');

      const botMsg = {
        role: 'bot',
        text: res.message || '(No response)',
        intent: res.intent,
        reportId: res.reportId,
        pdfUrl: res.pdfUrl,
        downloadUrl: res.downloadUrl,
        data: res.data,
        askingForClarification: res.askingForClarification,
        quickActions: res.quickActions || [],
        ts: new Date()
      };
      setMessages(prev => [...prev, botMsg]);
      if (!open) setUnread(u => u + 1);
    } catch (e) {
      const isNetworkErr = !e?.response || e?.response?.status === 0;
      const errText = isNetworkErr
        ? '⚠️ Cannot reach backend. Make sure Spring Boot is running on port 8080.'
        : `⚠️ ${e?.response?.data?.message || e.message || 'Unknown error'}`;
      setError(errText);
      setMessages(prev => [...prev, { role: 'bot', text: errText, ts: new Date() }]);
    } finally {
      setLoading(false);
    }
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMsg(); }
  };

  const reset = () => { setMessages([WELCOME]); setError(null); clearSession(); };

  const ts = (date) => date?.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  return (
    <>
      {/* ===== Floating trigger button ===== */}
      <div className="chatbot-trigger">
        <button
          id="chatbot-toggle-btn"
          onClick={() => setOpen(o => !o)}
          title="Fleet Assistant"
          style={{
            width: 54, height: 54, borderRadius: '50%', border: 'none', cursor: 'pointer',
            background: 'linear-gradient(135deg,#2563eb,#1d4ed8)',
            boxShadow: '0 8px 24px rgba(37,99,235,0.45)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'transform .2s', position: 'relative'
          }}
          onMouseEnter={e => e.currentTarget.style.transform = 'scale(1.08)'}
          onMouseLeave={e => e.currentTarget.style.transform = 'scale(1)'}
        >
          {open ? <X size={22} color="#fff" /> : <MessageCircle size={22} color="#fff" />}
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
        <div className="chatbot-window" id="chatbot-window">
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
              <div style={{ color: '#fff', fontWeight: 700, fontSize: 14, display: 'flex', alignItems: 'center', gap: 6 }}>
                Fleet Assistant
                <Zap size={11} color="#fbbf24" />
              </div>
              <div style={{ color: 'rgba(255,255,255,.65)', fontSize: 10 }}>
                {loading ? '⟳ Thinking...' : '● Live Fleet Data'}
              </div>
            </div>
            <button id="chatbot-clear-btn" onClick={reset} title="Clear chat"
              style={{ background: 'none', border: 'none', color: 'rgba(255,255,255,.65)',
                cursor: 'pointer', padding: 4, fontSize: 11, borderRadius: 4,
                display: 'flex', alignItems: 'center', gap: 4 }}>
              <RefreshCw size={13} /> Clear
            </button>
            <button id="chatbot-close-btn" onClick={() => setOpen(false)}
              style={{ background: 'none', border: 'none', color: '#fff', cursor: 'pointer', padding: 4 }}>
              <X size={17} />
            </button>
          </div>

          {/* Messages area */}
          <div style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto', padding: '12px 14px', background: '#f8fafc' }}>
            {messages.map((m, i) => (
              <div key={i} style={{
                display: 'flex',
                flexDirection: m.role === 'user' ? 'row-reverse' : 'row',
                alignItems: 'flex-start', gap: 8, marginBottom: 14
              }}>
                {/* Avatar */}
                <div style={{
                  width: 26, height: 26, borderRadius: '50%', flexShrink: 0, marginTop: 2,
                  background: m.role === 'user' ? '#2563eb' : '#1e3a5f',
                  display: 'flex', alignItems: 'center', justifyContent: 'center'
                }}>
                  {m.role === 'user' ? <User size={13} color="#fff" /> : <Bot size={13} color="#fff" />}
                </div>

                <div style={{ maxWidth: '82%', minWidth: 0 }}>
                  {/* Bubble */}
                  <div style={{
                    background: m.role === 'user'
                      ? 'linear-gradient(135deg,#2563eb,#1d4ed8)' : '#fff',
                    color: m.role === 'user' ? '#fff' : '#1e293b',
                    borderRadius: m.role === 'user' ? '16px 16px 4px 16px' : '16px 16px 16px 4px',
                    padding: '10px 13px',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
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

                  {/* Quick action chips */}
                  {m.role === 'bot' && m.quickActions && m.quickActions.length > 0 && (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, marginTop: 8 }}>
                      {m.quickActions.map((action, qi) => (
                        <QuickAction key={qi} label={action} onClick={sendMsg} />
                      ))}
                    </div>
                  )}

                  {/* Clarification hint */}
                  {m.askingForClarification && (
                    <div style={{ marginTop: 4, fontSize: 10, color: '#94a3b8', fontStyle: 'italic' }}>
                      Waiting for your reply…
                    </div>
                  )}

                  <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 4,
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
              id="chatbot-input"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={handleKey}
              placeholder="Ask about drivers, alerts, vehicles, reports…"
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
            <button
              id="chatbot-send-btn"
              onClick={() => sendMsg()}
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
