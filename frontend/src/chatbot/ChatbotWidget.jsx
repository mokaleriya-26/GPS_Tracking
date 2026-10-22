import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  MessageCircle, X, Send, Bot, User, FileText, Download,
  ExternalLink, RefreshCw, Zap, Mic, MicOff, AlertCircle, ChevronRight,
  Volume2, VolumeX, Square, RotateCcw
} from 'lucide-react';
import { sendChatMessage, clearSession } from '../api/chat';
import { useSpeechRecognition } from './useSpeechRecognition';
import { useSpeechSynthesis } from './useSpeechSynthesis';

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

// ─────────────────────────────────────────────────────────────────────────────
// Suggestion chips shown in the welcome message
// ─────────────────────────────────────────────────────────────────────────────
const WELCOME_CHIPS = [
  '🌟 Recommend Drivers',
  '📊 Fleet Summary',
  '🏆 Safest Driver',
  '⚠️ Show Open Alerts',
  '📄 Generate Alert Report',
  '🚗 VH001 Details',
];

const WELCOME = {
  role: 'bot',
  text: `👋 **Hi! I'm your Fleet Assistant.**\n\nI have live access to your fleet database — vehicles, drivers, trips, alerts and PDF reports.\n\nTry asking:\n• *"Give me all harsh braking alerts for September"*\n• *"Generate overspeed alert report this week"*\n• *"Who is the safest driver?"*\n• *"Who should I choose for a long trip?"*\n• *"Generate fleet report for October 2026"*\n• *"Show critical alerts from last month"*`,
  ts: new Date(),
  quickActions: WELCOME_CHIPS,
};

// ─────────────────────────────────────────────────────────────────────────────
// Inline text renderer  (bold / italic)
// ─────────────────────────────────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
// Table row renderer
// ─────────────────────────────────────────────────────────────────────────────
function RenderLine({ line, isFirstRow }) {
  if (line.startsWith('|') && line.endsWith('|')) {
    if (/^\|[-:\s|]+\|$/.test(line)) return null;
    const cells = line.slice(1, -1).split('|').map(c => c.trim());
    const isHeader = isFirstRow || cells.some(c => /^[*_]/.test(c));
    return (
      <tr style={{ borderBottom: '1px solid #e2e8f0', background: isHeader ? '#f1f5f9' : 'transparent' }}>
        {cells.map((cell, i) => {
          const Tag = isHeader ? 'th' : 'td';
          return (
            <Tag key={i} style={{
              padding: '6px 8px',
              textAlign: i === 0 ? 'center' : (i === 1 ? 'left' : 'right'),
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

// ─────────────────────────────────────────────────────────────────────────────
// Full message renderer: tables, bullets, headings, normal lines
// ─────────────────────────────────────────────────────────────────────────────
function RenderText({ text }) {
  if (!text) return null;
  const lines = String(text).split('\n');
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
        if (!line.trim()) return <div key={gi} style={{ height: 6 }} />;
        if (line.startsWith('# '))  return <div key={gi} style={{ fontWeight: 800, fontSize: 14, color: '#1e3a5f', marginTop: 8 }}><InlineText text={line.slice(2)} /></div>;
        if (line.startsWith('## ')) return <div key={gi} style={{ fontWeight: 700, fontSize: 13, color: '#1e3a5f', marginTop: 6 }}><InlineText text={line.slice(3)} /></div>;
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

// ─────────────────────────────────────────────────────────────────────────────
// Quick action chip
// ─────────────────────────────────────────────────────────────────────────────
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
        lineHeight: '1.4',
        display: 'flex',
        alignItems: 'center',
        gap: 3,
      }}
    >
      {label}
    </button>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Typing suggestion strip (shown above input when idle)
// ─────────────────────────────────────────────────────────────────────────────
const SUGGESTIONS = [
  'Give me all harsh braking alerts for September',
  'Who is the safest driver?',
  'Generate overspeed alert report this week',
  'Fleet summary for today',
  'Generate monthly fleet report for October 2026',
  'Show Rohan\'s performance this month',
  'Which vehicle has the most alerts?',
  'Recommend drivers for a long trip',
  'Show all critical alerts from last month',
  'Compare Rohan and Deepak',
];

// ─────────────────────────────────────────────────────────────────────────────
// Main ChatbotWidget component
// ─────────────────────────────────────────────────────────────────────────────
export default function ChatbotWidget() {
  const [open, setOpen]               = useState(false);
  const [messages, setMessages]       = useState([WELCOME]);
  const [input, setInput]             = useState('');
  const [loading, setLoading]         = useState(false);
  const [unread, setUnread]           = useState(0);
  const [error, setError]             = useState(null);
  const [suggIdx, setSuggIdx]         = useState(0);
  const [showSugg, setShowSugg]       = useState(false);

  const bottomRef  = useRef(null);
  const inputRef   = useRef(null);
  const suggTimer  = useRef(null);

  // ── Speech synthesis (Voice output) ──────────────────────────────────────
  const {
    isSupported: isTtsSupported,
    isSpeaking,
    voiceEnabled,
    setVoiceEnabled,
    speak,
    stop: stopSpeaking,
    replay: replaySpeech,
  } = useSpeechSynthesis();

  // ── Voice hook (Speech input) ─────────────────────────────────────────────
  const {
    isListening,
    interimText,
    error: speechError,
    setError: setSpeechError,
    isSupported: isSpeechSupported,
    toggleListening: baseToggleListening,
    stopListening,
  } = useSpeechRecognition({
    lang: 'en-IN',
    maxAlternatives: 3,
    confidenceThreshold: 0.2,
    silenceMs: 2000,       // 2 s of silence → auto-stop
    autoSend: true,        // after silence, auto-send the message
    onTranscript: (finalText) => {
      setInput(finalText);
      // Keep caret at end
      setTimeout(() => {
        if (inputRef.current) {
          inputRef.current.focus();
          const len = inputRef.current.value.length;
          inputRef.current.setSelectionRange(len, len);
        }
      }, 50);
    },
    onAutoSend: (text) => {
      // Called after silence timer fires — send automatically
      if (text && text.trim()) {
        sendMsg(text);
      }
    },
  });

  const toggleListening = (currentInput) => {
    // If chatbot is currently speaking, stop speech when user opens mic
    if (isSpeaking) {
      stopSpeaking();
    }
    baseToggleListening(currentInput);
  };

  // ── Rotating suggestion strip ─────────────────────────────────────────────
  useEffect(() => {
    if (!open || loading || isListening) {
      clearInterval(suggTimer.current);
      setShowSugg(false);
      return;
    }
    // Show suggestion strip when idle
    const idle = setTimeout(() => setShowSugg(true), 2000);
    suggTimer.current = setInterval(() => {
      setSuggIdx(i => (i + 1) % SUGGESTIONS.length);
    }, 4000);
    return () => {
      clearTimeout(idle);
      clearInterval(suggTimer.current);
    };
  }, [open, loading, isListening]);

  useEffect(() => {
    if (open) {
      setUnread(0);
      setError(null);
      setTimeout(() => inputRef.current?.focus(), 120);
    } else {
      stopListening();
      stopSpeaking();
    }
  }, [open, stopListening, stopSpeaking]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  // ── Send message ──────────────────────────────────────────────────────────
  const sendMsg = useCallback(async (text) => {
    stopListening();
    setShowSugg(false);
    const msg = (text || input).trim();
    if (!msg || loading) return;
    setInput('');
    setError(null);
    setSpeechError(null);
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
        ts: new Date(),
      };
      setMessages(prev => [...prev, botMsg]);
      if (!open) setUnread(u => u + 1);

      // Automatically speak the response if voice is enabled
      if (res.message && res.message.trim()) {
        speak(res.message);
      }
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
  }, [input, loading, open, stopListening, setSpeechError, speak]);

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMsg(); }
  };

  const reset = () => {
    stopSpeaking();
    stopListening();
    setMessages([WELCOME]);
    setError(null);
    clearSession();
  };

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

          {/* ── Header ── */}
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
              <div style={{ color: 'rgba(255,255,255,.75)', fontSize: 10, display: 'flex', alignItems: 'center', gap: 4 }}>
                {loading ? '⟳ Thinking...' : isSpeaking ? '🔊 Speaking aloud...' : isListening ? '🎙️ Listening...' : '● Live Fleet Data'}
              </div>
            </div>

            {/* Speaker Toggle */}
            {isTtsSupported && (
              <button
                id="chatbot-speaker-toggle-btn"
                onClick={() => setVoiceEnabled(v => !v)}
                title={voiceEnabled ? 'Automatic speech: ON (Click to mute)' : 'Automatic speech: OFF (Click to unmute)'}
                style={{
                  background: voiceEnabled ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.2)',
                  border: 'none', color: '#fff',
                  cursor: 'pointer', padding: '4px 8px', fontSize: 11, borderRadius: 6,
                  display: 'flex', alignItems: 'center', gap: 4, transition: 'all .2s'
                }}
              >
                {voiceEnabled ? <Volume2 size={13} color="#93c5fd" /> : <VolumeX size={13} color="rgba(255,255,255,0.6)" />}
                <span style={{ fontSize: 10, fontWeight: 600 }}>{voiceEnabled ? 'Voice' : 'Muted'}</span>
              </button>
            )}

            {/* Stop ongoing speech button */}
            {isSpeaking && (
              <button
                id="chatbot-stop-speech-btn"
                onClick={stopSpeaking}
                title="Stop speaking"
                style={{
                  background: '#ef4444', border: 'none', color: '#fff',
                  cursor: 'pointer', padding: '4px 7px', fontSize: 10, fontWeight: 700, borderRadius: 6,
                  display: 'flex', alignItems: 'center', gap: 3
                }}
              >
                <Square size={9} fill="#fff" /> Stop
              </button>
            )}

            <button id="chatbot-clear-btn" onClick={reset} title="Clear chat"
              style={{
                background: 'none', border: 'none', color: 'rgba(255,255,255,.7)',
                cursor: 'pointer', padding: 4, fontSize: 11, borderRadius: 4,
                display: 'flex', alignItems: 'center', gap: 3
              }}>
              <RefreshCw size={12} /> Clear
            </button>
            <button id="chatbot-close-btn" onClick={() => setOpen(false)}
              style={{ background: 'none', border: 'none', color: '#fff', cursor: 'pointer', padding: 4 }}>
              <X size={17} />
            </button>
          </div>

          {/* ── Speaking indicator banner ── */}
          {isSpeaking && (
            <div style={{
              padding: '6px 14px', background: 'linear-gradient(90deg, #ecfdf5, #d1fae5)',
              borderBottom: '1px solid #a7f3d0',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              fontSize: 11, color: '#065f46', flexShrink: 0
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ display: 'flex', gap: 2, alignItems: 'center' }}>
                  <span style={{ width: 3, height: 10, background: '#10b981', borderRadius: 2, animation: 'soundWave 0.8s ease-in-out infinite' }} />
                  <span style={{ width: 3, height: 14, background: '#10b981', borderRadius: 2, animation: 'soundWave 0.8s ease-in-out 0.2s infinite' }} />
                  <span style={{ width: 3, height: 8, background: '#10b981', borderRadius: 2, animation: 'soundWave 0.8s ease-in-out 0.4s infinite' }} />
                </span>
                <span style={{ fontWeight: 600 }}>Speaking answer aloud…</span>
              </div>
              <button
                onClick={stopSpeaking}
                style={{
                  background: '#10b981', border: 'none', borderRadius: 4,
                  color: '#fff', cursor: 'pointer', padding: '2px 8px', fontSize: 10, fontWeight: 600,
                  display: 'flex', alignItems: 'center', gap: 3
                }}
              >
                <Square size={8} fill="#fff" /> Stop
              </button>
            </div>
          )}

          {/* ── Messages area ── */}
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

                  {/* PDF action card */}
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

                  <div style={{
                    display: 'flex', alignItems: 'center',
                    justifyContent: m.role === 'user' ? 'flex-end' : 'space-between',
                    marginTop: 4, fontSize: 10, color: '#94a3b8'
                  }}>
                    {m.role === 'bot' && isTtsSupported && m.text && (
                      <button
                        onClick={() => replaySpeech(m.text)}
                        title="Replay this response aloud"
                        style={{
                          background: 'none', border: 'none', cursor: 'pointer',
                          color: '#64748b', display: 'flex', alignItems: 'center', gap: 3,
                          padding: '2px 4px', borderRadius: 4, fontSize: 10,
                          transition: 'color .15s'
                        }}
                        onMouseEnter={e => e.currentTarget.style.color = '#2563eb'}
                        onMouseLeave={e => e.currentTarget.style.color = '#64748b'}
                      >
                        <RotateCcw size={10} /> Replay
                      </button>
                    )}
                    <span>{ts(m.ts)}</span>
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

          {/* ── Rotating suggestion strip (shown when idle) ── */}
          {showSugg && !loading && !isListening && !input && (
            <div
              onClick={() => setInput(SUGGESTIONS[suggIdx])}
              style={{
                padding: '6px 14px',
                background: 'linear-gradient(90deg,#f0f9ff,#eff6ff)',
                borderTop: '1px solid #dbeafe',
                display: 'flex', alignItems: 'center', gap: 6,
                cursor: 'pointer',
                fontSize: 11, color: '#1e40af',
                flexShrink: 0,
                transition: 'background .2s',
              }}
              title="Click to use this suggestion"
            >
              <ChevronRight size={12} style={{ flexShrink: 0, opacity: 0.7 }} />
              <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                Try: <em>"{SUGGESTIONS[suggIdx]}"</em>
              </span>
            </div>
          )}

          {/* ── Speech error banner ── */}
          {speechError && (
            <div style={{
              padding: '6px 12px', background: '#fef2f2', borderTop: '1px solid #fecaca',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              fontSize: 11, color: '#dc2626', flexShrink: 0
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                <AlertCircle size={13} style={{ flexShrink: 0 }} />
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{speechError}</span>
              </div>
              <button
                onClick={() => setSpeechError(null)}
                style={{ background: 'none', border: 'none', color: '#dc2626', cursor: 'pointer', padding: 2, display: 'flex' }}
                title="Dismiss"
              >
                <X size={12} />
              </button>
            </div>
          )}

          {/* ── Live listening banner ── */}
          {isListening && (
            <div style={{
              padding: '6px 14px', background: '#eff6ff', borderTop: '1px solid #bfdbfe',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              fontSize: 11, color: '#1e40af', flexShrink: 0
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                <span style={{
                  width: 8, height: 8, borderRadius: '50%', background: '#ef4444',
                  animation: 'micDotPulse 1s infinite', flexShrink: 0
                }} />
                <span style={{ fontWeight: 600 }}>Listening…</span>
                {interimText && (
                  <span style={{ fontStyle: 'italic', color: '#64748b', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    &ldquo;{interimText}&rdquo;
                  </span>
                )}
              </div>
              <button
                onClick={stopListening}
                style={{
                  background: '#dbeafe', border: '1px solid #bfdbfe', borderRadius: 4,
                  color: '#1e40af', cursor: 'pointer', padding: '2px 6px', fontSize: 10, fontWeight: 600,
                  flexShrink: 0,
                }}
              >
                Done
              </button>
            </div>
          )}

          {/* ── Input bar ── */}
          <div style={{
            padding: '10px 14px',
            borderTop: speechError || isListening ? 'none' : '1px solid #e2e8f0',
            background: '#fff',
            display: 'flex', gap: 8, alignItems: 'center', flexShrink: 0
          }}>
            <textarea
              ref={inputRef}
              id="chatbot-input"
              value={isListening ? (input + (interimText ? ` ${interimText}` : '')) : input}
              onChange={e => { if (!isListening) setInput(e.target.value); }}
              onKeyDown={handleKey}
              placeholder={isListening ? 'Listening… speak now (auto-sends after 2 s silence)' : 'Ask about drivers, alerts, vehicles, reports…'}
              rows={1}
              disabled={loading}
              style={{
                flex: 1, resize: 'none',
                border: isListening ? '1.5px solid #ef4444' : '1.5px solid #e2e8f0',
                borderRadius: 10, padding: '8px 12px', fontSize: 13,
                fontFamily: 'inherit', outline: 'none', lineHeight: 1.4,
                maxHeight: 80, overflowY: 'auto',
                background: isListening ? '#fff5f5' : '#f8fafc',
                transition: 'border-color .2s, background .2s',
                color: isListening && interimText ? '#64748b' : '#1e293b',
                fontStyle: isListening && interimText ? 'italic' : 'normal',
              }}
              onFocus={e => e.target.style.borderColor = isListening ? '#ef4444' : '#2563eb'}
              onBlur={e => e.target.style.borderColor = isListening ? '#ef4444' : '#e2e8f0'}
            />

            {/* Microphone button */}
            <button
              id="chatbot-mic-btn"
              type="button"
              onClick={() => toggleListening(input)}
              disabled={loading}
              title={
                !isSpeechSupported
                  ? 'Voice input not supported in this browser (use Chrome or Edge)'
                  : isListening
                  ? 'Stop listening (or wait 2 s for auto-send)'
                  : 'Start voice input'
              }
              style={{
                width: 38, height: 38, borderRadius: '50%', border: 'none',
                cursor: loading ? 'not-allowed' : 'pointer',
                background: isListening
                  ? '#ef4444'
                  : isSpeechSupported
                  ? '#eff6ff'
                  : '#f1f5f9',
                color: isListening ? '#fff' : isSpeechSupported ? '#2563eb' : '#94a3b8',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                transition: 'all .2s', flexShrink: 0,
                boxShadow: isListening ? '0 0 0 4px rgba(239,68,68,0.25)' : 'none',
                animation: isListening ? 'micPulse 1.4s infinite' : 'none',
              }}
            >
              {isListening ? <MicOff size={16} /> : <Mic size={16} />}
            </button>

            {/* Send button */}
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
                transition: 'background .2s', flexShrink: 0,
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
        @keyframes micPulse {
          0%   { box-shadow: 0 0 0 0   rgba(239,68,68,0.6); }
          70%  { box-shadow: 0 0 0 8px rgba(239,68,68,0);   }
          100% { box-shadow: 0 0 0 0   rgba(239,68,68,0);   }
        }
        @keyframes micDotPulse {
          0%, 100% { transform: scale(1);   opacity: 1;   }
          50%      { transform: scale(1.3); opacity: 0.6; }
        }
        @keyframes soundWave {
          0%, 100% { transform: scaleY(0.4); }
          50%      { transform: scaleY(1.2); }
        }
      `}</style>
    </>
  );
}
