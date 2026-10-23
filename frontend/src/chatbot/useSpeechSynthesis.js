import { useState, useEffect, useRef, useCallback } from 'react';

const STORAGE_KEY = 'trackfleet_voice_enabled';
const STORAGE_VOICE_URI_KEY = 'trackfleet_voice_uri';
const STORAGE_VOICE_PITCH_KEY = 'trackfleet_voice_pitch';
const STORAGE_VOICE_RATE_KEY = 'trackfleet_voice_rate';

/**
 * Clean markdown, links, emojis, and convert tables into spoken natural English sentences.
 */
export function stripMarkdownAndFormatForSpeech(text) {
  if (!text || typeof text !== 'string') return '';

  const lines = text.split('\n');
  const resultLines = [];
  let tableRows = [];

  const processTable = (rows) => {
    if (rows.length === 0) return;
    // Filter out separator lines like |:---:|:---:|
    const validRows = rows.filter(r => !/^\|[-:\s|]+\|$/.test(r.trim()));
    if (validRows.length <= 1) return;

    // Header row
    const headers = validRows[0].slice(1, -1).split('|').map(c => c.trim().replace(/[*_~`]/g, ''));
    const dataRows = validRows.slice(1);

    const spokenRows = dataRows.map(rowStr => {
      const cells = rowStr.slice(1, -1).split('|').map(c => c.trim().replace(/[*_~`]/g, ''));
      const parts = [];
      cells.forEach((val, idx) => {
        if (!val) return;
        const h = headers[idx] || '';
        if (/rank/i.test(h)) {
          parts.push(`Rank ${val}`);
        } else if (/name/i.test(h)) {
          parts.push(`${val}`);
        } else if (/alert/i.test(h)) {
          parts.push(`with ${val} alerts`);
        } else if (/code/i.test(h)) {
          parts.push(val);
        } else if (h) {
          parts.push(`${h}: ${val}`);
        } else {
          parts.push(val);
        }
      });
      return parts.join(', ');
    });

    resultLines.push(spokenRows.join('. ') + '.');
  };

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      tableRows.push(trimmed);
    } else {
      if (tableRows.length > 0) {
        processTable(tableRows);
        tableRows = [];
      }
      if (trimmed) {
        resultLines.push(trimmed);
      }
    }
  }
  if (tableRows.length > 0) {
    processTable(tableRows);
  }

  let clean = resultLines.join(' ');

  // Strip markdown links: [text](url) -> text
  clean = clean.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');
  // Strip URLs: https://...
  clean = clean.replace(/https?:\/\/\S+/g, '');
  // Strip bold/italics/strikethrough: **text**, *text*, ~~text~~
  clean = clean.replace(/[*_~`#]/g, '');
  // Strip bullet markers: •, ·, -, +
  clean = clean.replace(/[•·]/g, '');
  clean = clean.replace(/(^|\s)[-+*]\s+/g, '$1');
  // Strip JSON blocks or code blocks
  clean = clean.replace(/\{[^}]+\}/g, '');
  // Remove emojis and pictographs
  clean = clean.replace(/[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu, '');
  // Clean multiple punctuation and spaces
  clean = clean.replace(/\s+/g, ' ').trim();

  return clean;
}

/**
 * Pick the best natural, clear, pleasant sounding default voice.
 * Prioritizes high quality voices like Samantha, Google Natural, Karen, Victoria, Daniel, etc.
 * over robotic/monotone fallback voices.
 */
export function getBestDefaultVoice(voices) {
  if (!voices || voices.length === 0) return null;

  // 1. High priority natural / pleasant sounding English voices
  const preferredNames = [
    'samantha',
    'google uk english female',
    'google us english',
    'victoria',
    'karen',
    'serena',
    'moira',
    'fiona',
    'tessa',
    'daniel',
    'alex',
  ];

  for (const name of preferredNames) {
    const match = voices.find(v => v.name && v.name.toLowerCase().includes(name));
    if (match) return match;
  }

  // 2. High-quality natural / neural / enhanced English voices
  const naturalMatch = voices.find(v =>
    v.lang && v.lang.startsWith('en') &&
    /(natural|neural|enhanced|premium)/i.test(v.name)
  );
  if (naturalMatch) return naturalMatch;

  // 3. Clear en-US or en-GB voices
  const standardEn = voices.find(v => v.lang === 'en-US' || v.lang === 'en-GB');
  if (standardEn) return standardEn;

  // 4. Any English voice
  const anyEn = voices.find(v => v.lang && v.lang.startsWith('en'));
  if (anyEn) return anyEn;

  // 5. Fallback
  return voices[0];
}

/**
 * useSpeechSynthesis — Built-in Web Speech API SpeechSynthesis hook.
 */
export function useSpeechSynthesis() {
  const isSupported = typeof window !== 'undefined' && 'speechSynthesis' in window;

  const [isSpeaking, setIsSpeaking] = useState(false);
  const [voices, setVoices] = useState([]);
  const [voiceEnabled, setVoiceEnabledState] = useState(() => {
    if (typeof window === 'undefined') return true;
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved !== null ? saved === 'true' : true;
  });

  const [selectedVoiceURI, setSelectedVoiceURIState] = useState(() => {
    if (typeof window === 'undefined') return '';
    return localStorage.getItem(STORAGE_VOICE_URI_KEY) || '';
  });

  const [pitch, setPitchState] = useState(() => {
    if (typeof window === 'undefined') return 1.0;
    const p = parseFloat(localStorage.getItem(STORAGE_VOICE_PITCH_KEY));
    return !isNaN(p) && p >= 0.5 && p <= 2.0 ? p : 1.0;
  });

  const [rate, setRateState] = useState(() => {
    if (typeof window === 'undefined') return 1.0;
    const r = parseFloat(localStorage.getItem(STORAGE_VOICE_RATE_KEY));
    return !isNaN(r) && r >= 0.5 && r <= 2.0 ? r : 1.0;
  });

  const utteranceRef = useRef(null);

  // Load available voices
  useEffect(() => {
    if (!isSupported) return;

    const updateVoices = () => {
      try {
        const vList = window.speechSynthesis.getVoices();
        if (vList && vList.length > 0) {
          setVoices(vList);
        }
      } catch (_) {}
    };

    updateVoices();
    if (window.speechSynthesis.onvoiceschanged !== undefined) {
      window.speechSynthesis.onvoiceschanged = updateVoices;
    }

    return () => {
      if (isSupported) {
        try { window.speechSynthesis.cancel(); } catch (_) {}
      }
    };
  }, [isSupported]);

  // Persist voice toggle
  const setVoiceEnabled = useCallback((val) => {
    const nextVal = typeof val === 'function' ? val(voiceEnabled) : val;
    setVoiceEnabledState(nextVal);
    try {
      localStorage.setItem(STORAGE_KEY, String(nextVal));
    } catch (_) {}
    if (!nextVal && isSupported) {
      try {
        window.speechSynthesis.cancel();
        setIsSpeaking(false);
      } catch (_) {}
    }
  }, [voiceEnabled, isSupported]);

  // Persist selected voice URI
  const setSelectedVoiceURI = useCallback((uri) => {
    setSelectedVoiceURIState(uri || '');
    try {
      if (uri) localStorage.setItem(STORAGE_VOICE_URI_KEY, uri);
      else localStorage.removeItem(STORAGE_VOICE_URI_KEY);
    } catch (_) {}
  }, []);

  // Persist pitch
  const setPitch = useCallback((p) => {
    const val = Number(p);
    setPitchState(val);
    try {
      localStorage.setItem(STORAGE_VOICE_PITCH_KEY, String(val));
    } catch (_) {}
  }, []);

  // Persist rate/speed
  const setRate = useCallback((r) => {
    const val = Number(r);
    setRateState(val);
    try {
      localStorage.setItem(STORAGE_VOICE_RATE_KEY, String(val));
    } catch (_) {}
  }, []);

  // Reset to default voice & speed
  const resetVoiceDefaults = useCallback(() => {
    setSelectedVoiceURI('');
    setPitch(1.0);
    setRate(1.0);
  }, [setSelectedVoiceURI, setPitch, setRate]);

  // Stop speech immediately
  const stop = useCallback(() => {
    if (!isSupported) return;
    try {
      window.speechSynthesis.cancel();
    } catch (_) {}
    setIsSpeaking(false);
    utteranceRef.current = null;
  }, [isSupported]);

  // Internal speak function with support for voice, pitch, and rate
  const speakText = useCallback((text, force = false, options = {}) => {
    if (!isSupported) return;
    if (!force && !voiceEnabled) return;

    const cleaned = stripMarkdownAndFormatForSpeech(text);
    if (!cleaned) return;

    // Cancel ongoing speech first to prevent overlap
    try {
      window.speechSynthesis.cancel();
    } catch (_) {}

    const utterance = new SpeechSynthesisUtterance(cleaned);
    utteranceRef.current = utterance;

    // Pick user-chosen voice or best natural default voice
    let activeVoice = null;
    const targetURI = options.voiceURI !== undefined ? options.voiceURI : selectedVoiceURI;
    if (voices.length > 0) {
      if (targetURI) {
        activeVoice = voices.find(v => v.voiceURI === targetURI || v.name === targetURI);
      }
      if (!activeVoice) {
        activeVoice = getBestDefaultVoice(voices);
      }
      if (activeVoice) {
        utterance.voice = activeVoice;
      }
    }

    utterance.rate = options.rate !== undefined ? options.rate : rate;
    utterance.pitch = options.pitch !== undefined ? options.pitch : pitch;

    utterance.onstart = () => {
      setIsSpeaking(true);
    };

    utterance.onend = () => {
      setIsSpeaking(false);
      utteranceRef.current = null;
    };

    utterance.onerror = (e) => {
      // Ignore 'canceled' or 'interrupted' as these occur on user stop/navigation
      if (e.error !== 'canceled' && e.error !== 'interrupted') {
        console.warn('SpeechSynthesis error:', e.error);
      }
      setIsSpeaking(false);
      utteranceRef.current = null;
    };

    try {
      window.speechSynthesis.speak(utterance);
    } catch (e) {
      console.warn('Failed to speak:', e);
      setIsSpeaking(false);
    }
  }, [isSupported, voiceEnabled, voices, selectedVoiceURI, pitch, rate]);

  // Replay speaks text even if automatic speech is muted
  const replay = useCallback((text) => {
    speakText(text, true);
  }, [speakText]);

  // Test current or specified voice settings
  const testVoice = useCallback((customText, testOpts = {}) => {
    const sample = customText || "Hello! I am your fleet assistant. This is how my voice sounds.";
    speakText(sample, true, testOpts);
  }, [speakText]);

  const currentVoice = (voices.length > 0 && selectedVoiceURI)
    ? (voices.find(v => v.voiceURI === selectedVoiceURI || v.name === selectedVoiceURI) || getBestDefaultVoice(voices))
    : getBestDefaultVoice(voices);

  return {
    isSupported,
    isSpeaking,
    voiceEnabled,
    setVoiceEnabled,
    voices,
    selectedVoiceURI,
    setSelectedVoiceURI,
    pitch,
    setPitch,
    rate,
    setRate,
    resetVoiceDefaults,
    currentVoice,
    speak: speakText,
    stop,
    replay,
    testVoice,
  };
}
