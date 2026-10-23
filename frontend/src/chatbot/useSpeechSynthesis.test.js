import assert from 'node:assert';
import { stripMarkdownAndFormatForSpeech, getBestDefaultVoice } from './useSpeechSynthesis.js';

console.log('Testing stripMarkdownAndFormatForSpeech...');

// 1. Basic formatting removal
const input1 = '**Vikram Singh (DRV008)** has generated the highest number of alerts with **1,644 alerts**.';
const out1 = stripMarkdownAndFormatForSpeech(input1);
console.log('Test 1 output:', out1);
assert.strictEqual(out1.includes('**'), false, 'Should strip **');
assert.strictEqual(out1.includes('Vikram Singh (DRV008) has generated the highest number of alerts with 1,644 alerts.'), true);

// 2. Table conversion
const input2 = `🏆 **Top 5 Drivers by Alert Count (All-Time)**

| Rank | Driver Code | Driver Name | Total Alerts |
|:---:|:---:|:---|:---:|
| 1 | DRV008 | Vikram Singh | 1,644 |
| 2 | DRV007 | Priya Nair | 1,135 |`;
const out2 = stripMarkdownAndFormatForSpeech(input2);
console.log('Test 2 output:', out2);
assert.strictEqual(out2.includes('|'), false, 'Should convert markdown table');
assert.strictEqual(out2.includes('Rank 1, DRV008, Vikram Singh, with 1,644 alerts'), true);
assert.strictEqual(out2.includes('Rank 2, DRV007, Priya Nair, with 1,135 alerts'), true);

// 3. Bullets and links
const input3 = 'Here are options:\n• [Fleet Report](http://localhost:8080/report/1)\n• Ask about **DRV009**';
const out3 = stripMarkdownAndFormatForSpeech(input3);
console.log('Test 3 output:', out3);
assert.strictEqual(out3.includes('['), false, 'Should strip markdown links');
assert.strictEqual(out3.includes('•'), false, 'Should strip bullets');
assert.strictEqual(out3.includes('Fleet Report'), true);

// 4. Edge cases
assert.strictEqual(stripMarkdownAndFormatForSpeech(''), '');
assert.strictEqual(stripMarkdownAndFormatForSpeech(null), '');
assert.strictEqual(stripMarkdownAndFormatForSpeech(undefined), '');

console.log('Testing getBestDefaultVoice...');
const mockVoices = [
  { name: 'Alex', lang: 'en-US', voiceURI: 'alex' },
  { name: 'Samantha', lang: 'en-US', voiceURI: 'samantha' },
  { name: 'Rishi', lang: 'en-IN', voiceURI: 'rishi' },
];
const chosen = getBestDefaultVoice(mockVoices);
assert.strictEqual(chosen.name, 'Samantha', 'Samantha should be picked as preferred clear voice');

assert.strictEqual(getBestDefaultVoice([]), null);
assert.strictEqual(getBestDefaultVoice(null), null);

console.log('All speech formatting and voice selection assertions passed successfully!');

