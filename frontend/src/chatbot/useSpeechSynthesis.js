import { useState, useEffect, useRef, useCallback } from 'react';

const STORAGE_KEY = 'trackfleet_voice_enabled';

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

  // Stop speech immediately
  const stop = useCallback(() => {
    if (!isSupported) return;
    try {
      window.speechSynthesis.cancel();
    } catch (_) {}
    setIsSpeaking(false);
    utteranceRef.current = null;
  }, [isSupported]);

  // Internal speak function
  const speakText = useCallback((text, force = false) => {
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

    // Pick suitable voice: prefer en-IN, en-GB, en-US, or default
    if (voices.length > 0) {
      const preferred = voices.find(v => v.lang === 'en-IN')
        || voices.find(v => v.lang.startsWith('en'))
        || voices[0];
      if (preferred) utterance.voice = preferred;
    }

    utterance.rate = 1.0;
    utterance.pitch = 1.0;

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
  }, [isSupported, voiceEnabled, voices]);

  // Replay speaks text even if automatic speech is muted
  const replay = useCallback((text) => {
    speakText(text, true);
  }, [speakText]);

  return {
    isSupported,
    isSpeaking,
    voiceEnabled,
    setVoiceEnabled,
    speak: speakText,
    stop,
    replay,
  };
}
