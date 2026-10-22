import { useState, useRef, useEffect, useCallback } from 'react';

// ─────────────────────────────────────────────────────────────────────────────
// Fleet-domain vocabulary hints for better recognition accuracy
// Loaded via SpeechGrammarList (Chrome/Edge only – ignored gracefully elsewhere)
// ─────────────────────────────────────────────────────────────────────────────
const FLEET_GRAMMAR = `#JSGF V1.0; grammar fleet;
public <fleet> =
  driver | drivers | vehicle | vehicles | fleet | alert | alerts | trip | trips |
  report | reports | overspeed | harsh braking | harsh acceleration | fatigue |
  GPS disconnect | night driving | ignition | geofence |
  safety score | penalty | ranking | leaderboard |
  DRV001 | DRV002 | DRV003 | DRV004 | DRV005 |
  DRV006 | DRV007 | DRV008 | DRV009 | DRV010 |
  VH001 | VH002 | VH003 | VH004 | VH005 |
  Rohan | Priya | Amit | Sunita | Rajesh | Kavitha |
  Suresh | Deepak | Vikram | Neha | Anil | Pooja |
  Manoj | Anita | Rahul | Preeti | Ravi | Kunal | Sneha |
  today | yesterday | this week | last week | this month | last month |
  September | October | November | generate | download | PDF |
  critical | warning | open | resolved | historical | unresolved |
  give me | show me | tell me | generate | fleet summary | driver report;
`;

/**
 * useSpeechRecognition — Enhanced Web Speech API hook for TrackFleet chatbot.
 *
 * Improvements over v1:
 *  • maxAlternatives = 3  →  picks the highest-confidence result
 *  • SpeechGrammarList   →  hints fleet domain vocabulary to the recogniser
 *  • Silence-based auto-stop (configurable silenceMs)
 *  • Auto-send trigger callback once speech goes quiet
 *  • Auto-restart on unexpected recognition end (browser quirk workaround)
 *  • Accurate interim→final stitching (accumulates across result events)
 *  • Confidence threshold guard (ignores results below threshold)
 *
 * @param {Object} options
 * @param {string}   [options.lang='en-IN']          Recognition locale
 * @param {number}   [options.maxAlternatives=3]     Candidates considered per result
 * @param {number}   [options.confidenceThreshold=0.25] Minimum confidence to accept
 * @param {number}   [options.silenceMs=1800]        ms of silence before auto-stop
 * @param {boolean}  [options.autoSend=false]        Call onAutoSend after silence stop
 * @param {Function} [options.onTranscript]          Called on every final transcript update
 * @param {Function} [options.onAutoSend]            Called with final text when auto-stopped
 * @param {Function} [options.onError]               Called on recognition errors
 */
export function useSpeechRecognition({
  lang = 'en-IN',
  maxAlternatives = 3,
  confidenceThreshold = 0.25,
  silenceMs = 1800,
  autoSend = false,
  onTranscript,
  onAutoSend,
  onError
} = {}) {
  const [isListening, setIsListening]   = useState(false);
  const [interimText, setInterimText]   = useState('');
  const [error, setError]               = useState(null);

  const recognitionRef          = useRef(null);
  const isManuallyStoppedRef    = useRef(false);
  const baseInputRef            = useRef('');      // text in input box before mic was pressed
  const accumulatedFinalRef     = useRef('');      // all final text collected this session
  const silenceTimerRef         = useRef(null);
  const autoRestartRef          = useRef(false);

  const isSupported = typeof window !== 'undefined' && Boolean(
    window.SpeechRecognition || window.webkitSpeechRecognition
  );

  // ── helpers ──────────────────────────────────────────────────────────────
  const clearSilenceTimer = useCallback(() => {
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
  }, []);

  const resetSilenceTimer = useCallback((textSoFar) => {
    clearSilenceTimer();
    silenceTimerRef.current = setTimeout(() => {
      // Silence detected — stop recognition
      isManuallyStoppedRef.current = true;
      autoRestartRef.current = false;
      if (recognitionRef.current) {
        try { recognitionRef.current.stop(); } catch (_) {}
      }
      setIsListening(false);
      setInterimText('');

      // Trigger auto-send if enabled
      if (autoSend && onAutoSend && textSoFar && textSoFar.trim()) {
        onAutoSend(textSoFar.trim());
      }
    }, silenceMs);
  }, [clearSilenceTimer, silenceMs, autoSend, onAutoSend]);

  // ── Stop ─────────────────────────────────────────────────────────────────
  const stopListening = useCallback(() => {
    isManuallyStoppedRef.current = true;
    autoRestartRef.current = false;
    clearSilenceTimer();
    if (recognitionRef.current) {
      try { recognitionRef.current.stop(); } catch (_) {}
    }
    setIsListening(false);
    setInterimText('');
  }, [clearSilenceTimer]);

  // ── Start ─────────────────────────────────────────────────────────────────
  const startListening = useCallback((currentInputValue = '') => {
    if (!isSupported) {
      const err = 'Voice input is not supported in this browser. Please use Chrome or Edge.';
      setError(err);
      if (onError) onError(err);
      return;
    }

    // Abort any existing session
    if (recognitionRef.current) {
      try { recognitionRef.current.abort(); } catch (_) {}
      recognitionRef.current = null;
    }

    setError(null);
    setInterimText('');
    isManuallyStoppedRef.current = false;
    autoRestartRef.current = true;
    baseInputRef.current = currentInputValue;
    accumulatedFinalRef.current = currentInputValue; // carry over existing text

    const SpeechAPI = window.SpeechRecognition || window.webkitSpeechRecognition;
    const recognition = new SpeechAPI();

    // ── Core settings ────────────────────────────────────────────────────
    recognition.continuous      = true;
    recognition.interimResults  = true;
    recognition.lang            = lang;
    recognition.maxAlternatives = maxAlternatives;

    // ── Grammar hints (Chrome/Edge only) ─────────────────────────────────
    const GrammarList = window.SpeechGrammarList || window.webkitSpeechGrammarList;
    if (GrammarList) {
      try {
        const grammarList = new GrammarList();
        grammarList.addFromString(FLEET_GRAMMAR, 1);
        recognition.grammars = grammarList;
      } catch (_) {
        // Grammar hints unsupported — ignore silently
      }
    }

    // ── Event handlers ────────────────────────────────────────────────────
    recognition.onstart = () => {
      setIsListening(true);
      setError(null);
      resetSilenceTimer(accumulatedFinalRef.current);
    };

    recognition.onresult = (event) => {
      let sessionFinal = '';
      let currentInterim = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];

        // Pick best alternative above confidence threshold
        let bestTranscript = '';
        let bestConfidence = -1;
        for (let alt = 0; alt < result.length; alt++) {
          const conf = result[alt].confidence ?? 1.0; // interim results often have 0
          if (conf > bestConfidence) {
            bestConfidence = conf;
            bestTranscript = result[alt].transcript;
          }
        }

        // Skip very low-confidence results (noise)
        const effectiveConf = result[0].confidence ?? 1.0;
        if (result.isFinal && effectiveConf < confidenceThreshold && effectiveConf > 0) {
          continue;
        }

        if (result.isFinal) {
          sessionFinal += bestTranscript;
        } else {
          currentInterim += bestTranscript;
        }
      }

      setInterimText(currentInterim);

      if (sessionFinal) {
        // Append to accumulated text, adding space if needed
        const base = accumulatedFinalRef.current;
        const trimmed = sessionFinal.trim();
        const needsSpace = base && !base.endsWith(' ') && trimmed;
        const combined = base + (needsSpace ? ' ' : '') + trimmed;
        accumulatedFinalRef.current = combined;

        if (onTranscript) onTranscript(combined);

        // Reset silence timer — speech was detected
        resetSilenceTimer(combined);
      } else if (currentInterim) {
        // Interim text means user is still speaking — reset silence window
        resetSilenceTimer(accumulatedFinalRef.current);
      }
    };

    recognition.onerror = (event) => {
      clearSilenceTimer();
      if (event.error === 'aborted') return; // not a real error

      let message;
      switch (event.error) {
        case 'not-allowed':
        case 'service-not-allowed':
          message = 'Microphone access denied. Please allow microphone permissions and try again.';
          autoRestartRef.current = false;
          break;
        case 'no-speech':
          // Soft error — just reset the timer and let onend handle restart
          message = null;
          break;
        case 'audio-capture':
          message = 'No microphone found or microphone is in use by another app.';
          autoRestartRef.current = false;
          break;
        case 'network':
          message = 'Speech recognition network error. Check your internet connection.';
          break;
        default:
          message = `Speech recognition error: ${event.error}`;
      }
      if (message) {
        setError(message);
        if (onError) onError(message);
        setIsListening(false);
        setInterimText('');
      }
    };

    recognition.onend = () => {
      clearSilenceTimer();
      // Auto-restart if the browser dropped the session unexpectedly (common Chrome behaviour)
      if (autoRestartRef.current && !isManuallyStoppedRef.current) {
        // Small delay before restarting to avoid rapid loops
        setTimeout(() => {
          if (autoRestartRef.current && !isManuallyStoppedRef.current) {
            try {
              recognition.start();
            } catch (_) {
              setIsListening(false);
            }
          }
        }, 200);
      } else {
        setIsListening(false);
        setInterimText('');
      }
    };

    recognitionRef.current = recognition;

    try {
      recognition.start();
    } catch (err) {
      const message = 'Unable to start speech recognition. Please try again.';
      setError(message);
      if (onError) onError(message);
      setIsListening(false);
    }
  }, [isSupported, lang, maxAlternatives, confidenceThreshold, onTranscript, onError, resetSilenceTimer, clearSilenceTimer]);

  const toggleListening = useCallback((currentInputValue = '') => {
    if (isListening) {
      stopListening();
    } else {
      startListening(currentInputValue);
    }
  }, [isListening, startListening, stopListening]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      clearSilenceTimer();
      if (recognitionRef.current) {
        try { recognitionRef.current.abort(); } catch (_) {}
        recognitionRef.current = null;
      }
    };
  }, [clearSilenceTimer]);

  return {
    isListening,
    interimText,
    error,
    setError,
    isSupported,
    startListening,
    stopListening,
    toggleListening
  };
}
