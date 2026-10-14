import { useState, useRef, useEffect, useCallback } from 'react';

/**
 * Custom React hook for speech-to-text using Web Speech API (SpeechRecognition / webkitSpeechRecognition).
 * 
 * @param {Object} options
 * @param {string} [options.lang='en-IN'] Recognition language code (default en-IN)
 * @param {Function} [options.onTranscript] Callback invoked whenever transcript updates (interim or final)
 * @param {Function} [options.onError] Callback invoked on recognition errors
 */
export function useSpeechRecognition({
  lang = 'en-IN',
  onTranscript,
  onError
} = {}) {
  const [isListening, setIsListening] = useState(false);
  const [interimText, setInterimText] = useState('');
  const [error, setError] = useState(null);

  const recognitionRef = useRef(null);
  const isManuallyStoppedRef = useRef(false);
  const baseInputBeforeSpeechRef = useRef('');

  // Check browser support
  const isSupported = typeof window !== 'undefined' && Boolean(
    window.SpeechRecognition || window.webkitSpeechRecognition
  );

  // Clean stop handler
  const stopListening = useCallback(() => {
    isManuallyStoppedRef.current = true;
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch (err) {
        // Ignore if already stopped
      }
    }
    setIsListening(false);
    setInterimText('');
  }, []);

  const startListening = useCallback((currentInputValue = '') => {
    if (!isSupported) {
      const err = 'Voice input is not supported in this browser. Please use Chrome, Edge, or Safari.';
      setError(err);
      if (onError) onError(err);
      return;
    }

    // Clean up any ongoing session before starting a new one
    if (recognitionRef.current) {
      try {
        recognitionRef.current.abort();
      } catch (e) {
        // ignore
      }
      recognitionRef.current = null;
    }

    setError(null);
    setInterimText('');
    isManuallyStoppedRef.current = false;
    baseInputBeforeSpeechRef.current = currentInputValue;

    const SpeechRecognitionAPI = window.SpeechRecognition || window.webkitSpeechRecognition;
    const recognition = new SpeechRecognitionAPI();

    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = lang;

    recognition.onstart = () => {
      setIsListening(true);
      setError(null);
    };

    recognition.onresult = (event) => {
      let finalTranscript = '';
      let currentInterim = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcriptPart = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          finalTranscript += transcriptPart;
        } else {
          currentInterim += transcriptPart;
        }
      }

      setInterimText(currentInterim);

      if (finalTranscript) {
        const base = baseInputBeforeSpeechRef.current;
        const needsSpace = base && !base.endsWith(' ') && !finalTranscript.startsWith(' ');
        const combined = `${base}${needsSpace ? ' ' : ''}${finalTranscript.trim()}`;
        baseInputBeforeSpeechRef.current = combined;
        if (onTranscript) {
          onTranscript(combined);
        }
      }
    };

    recognition.onerror = (event) => {
      let message = 'Speech recognition error occurred.';
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        message = 'Microphone access was denied. Please allow microphone permissions in your browser settings.';
      } else if (event.error === 'no-speech') {
        message = 'No speech was detected. Please try speaking again.';
      } else if (event.error === 'audio-capture') {
        message = 'No microphone was found or microphone is busy.';
      } else if (event.error === 'network') {
        message = 'Speech recognition network error occurred.';
      } else if (event.error === 'aborted') {
        // User aborted or switched, not necessarily an error message to display
        return;
      }
      setError(message);
      if (onError) onError(message);
      setIsListening(false);
      setInterimText('');
    };

    recognition.onend = () => {
      setIsListening(false);
      setInterimText('');
    };

    recognitionRef.current = recognition;

    try {
      recognition.start();
    } catch (err) {
      const message = 'Unable to start speech recognition.';
      setError(message);
      if (onError) onError(message);
      setIsListening(false);
    }
  }, [isSupported, lang, onError, onTranscript]);

  const toggleListening = useCallback((currentInputValue = '') => {
    if (isListening) {
      stopListening();
    } else {
      startListening(currentInputValue);
    }
  }, [isListening, startListening, stopListening]);

  // Clean up on component unmount
  useEffect(() => {
    return () => {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.abort();
        } catch (e) {
          // ignore
        }
        recognitionRef.current = null;
      }
    };
  }, []);

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
