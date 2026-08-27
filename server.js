require('dotenv').config();
const express = require('express');
const fs = require('fs');
const path = require('path');
const csv = require('csv-parser');
const nodemailer = require('nodemailer');

const app = express();
const PORT = process.env.PORT || 3000;
const CSV_FILE_PATH = path.join(__dirname, 'vehicle_fleet_alerts_fake_dataset.csv');
const STATE_FILE_PATH = path.join(__dirname, 'state.json');

// Serve static files (HTML, CSS, JS) from the current directory
app.use(express.static(__dirname));

// Function to read the CSV file
const readCSV = () => {
    return new Promise((resolve, reject) => {
        const results = [];
        if (!fs.existsSync(CSV_FILE_PATH)) {
            return resolve([]);
        }
        fs.createReadStream(CSV_FILE_PATH)
            .pipe(csv())
            .on('data', (data) => results.push(data))
            .on('end', () => resolve(results))
            .on('error', (error) => reject(error));
    });
};

// API Endpoint to get all data
app.get('/api/data', async (req, res) => {
    try {
        const data = await readCSV();
        res.json(data);
    } catch (error) {
        console.error('Error reading CSV:', error);
        res.status(500).json({ error: 'Failed to read dataset' });
    }
});

// API Endpoint to get only active alerts, newest first
app.get('/api/alerts', async (req, res) => {
    try {
        const data = await readCSV();
        const activeAlerts = [];
        
        data.forEach(record => {
            const hasAlert = record['Overspeed Alert'] === 'Yes' ||
                             record['Harsh Braking Alert'] === 'Yes' ||
                             record['GPS Disconnect Alert'] === 'Yes' ||
                             record['Night Driving Alert'] === 'Yes';
                             // 'Ignition On/Off Alert' logic can be added if needed, usually 'Off' might be an alert if vehicle is running, but let's stick to the others for critical alerts.
                             
            if (hasAlert) {
                // Determine alert types
                const types = [];
                if (record['Overspeed Alert'] === 'Yes') types.push('Overspeed');
                if (record['Harsh Braking Alert'] === 'Yes') types.push('Harsh Braking');
                if (record['GPS Disconnect Alert'] === 'Yes') types.push('GPS Disconnect');
                if (record['Night Driving Alert'] === 'Yes') types.push('Night Driving');

                activeAlerts.push({
                    record,
                    alertTypes: types,
                    timestamp: new Date(record['Time']).getTime() // for sorting
                });
            }
        });

        // Sort descending
        activeAlerts.sort((a, b) => b.timestamp - a.timestamp);
        
        res.json(activeAlerts.map(a => ({ ...a.record, _alertTypes: a.alertTypes })));
    } catch (error) {
        console.error('Error reading CSV for alerts:', error);
        res.status(500).json({ error: 'Failed to read dataset' });
    }
});

// Email transporter configuration
const transporter = nodemailer.createTransport({
    host: process.env.EMAIL_HOST || 'smtp.ethereal.email',
    port: process.env.EMAIL_PORT || 587,
    secure: process.env.EMAIL_PORT == 465, 
    auth: {
        user: process.env.EMAIL_USER,
        pass: process.env.EMAIL_PASSWORD
    }
});

// Function to send alert email
const sendAlertEmail = async (record, alertTypes) => {
    if (!process.env.FLEET_EMAIL || !process.env.MANAGER_EMAIL) {
        console.log('Skipping email send: FLEET_EMAIL or MANAGER_EMAIL not configured.');
        throw new Error('Email recipients not configured');
    }

    const to = `${process.env.FLEET_EMAIL}, ${process.env.MANAGER_EMAIL}`;
    const subject = `New Fleet Alert - ${alertTypes.join(', ')} - ${record['Vehicle Number']}`;
    
    // Formatting Event Time
    let eventTimeStr = record['Time'];
    try {
        const d = new Date(eventTimeStr);
        if (!isNaN(d.getTime())) {
            eventTimeStr = d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }) + ', ' + 
                           d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true });
        }
    } catch(e) {}

    const body = `A new fleet alert has been detected.

Alert Type: ${alertTypes.join(', ')}
Vehicle ID: ${record['Vehicle ID']}
Vehicle Number: ${record['Vehicle Number']}
Driver: ${record['Driver Name']}
Location: ${record['Location']}
Speed: ${record['Speed Max (km/h)']} km/h
Event Time: ${eventTimeStr}
Trip Duration: ${record['Trip Duration (min)']} min
Trip Distance: ${record['Trip Distance (km)']} km
`;

    try {
        await transporter.sendMail({
            from: process.env.EMAIL_USER || '"Fleet System" <no-reply@trackfleet.com>',
            to,
            subject,
            text: body
        });
        console.log(`Alert email sent for vehicle ${record['Vehicle Number']} (${alertTypes.join(', ')})`);
    } catch (error) {
        console.error('Failed to send email:', error);
        throw error; // Rethrow to handle in polling logic
    }
};

// Polling Logic
const POLLING_INTERVAL_MS = 30000; // 30 seconds

const loadState = () => {
    if (fs.existsSync(STATE_FILE_PATH)) {
        try {
            const data = fs.readFileSync(STATE_FILE_PATH, 'utf8');
            const state = JSON.parse(data);
            if (!state.alerts) state.alerts = {}; // Migration
            if (!state.lastEvaluatedTime && state.lastProcessedTime) {
                state.lastEvaluatedTime = state.lastProcessedTime; // Migration
            }
            return state;
        } catch (e) {
            console.error('Error reading state file:', e);
            return { lastEvaluatedTime: '', alerts: {} };
        }
    }
    return { lastEvaluatedTime: '', alerts: {} };
};

const saveState = (state) => {
    fs.writeFileSync(STATE_FILE_PATH, JSON.stringify(state, null, 2));
};

const pollCSV = async () => {
    try {
        const data = await readCSV();
        if (data.length === 0) return;

        const state = loadState();
        let lastTime = state.lastEvaluatedTime || '';
        let newLastTime = lastTime;
        let stateChanged = false;

        for (const record of data) {
            const recordTime = record['Time'];
            const vehicleId = record['Vehicle ID'];
            const alertKey = `${vehicleId}_${recordTime}`;
            
            // Track max time to move the evaluated window forward safely
            if (recordTime > newLastTime) {
                newLastTime = recordTime;
            }

            // Check if it's a new record or a failed record we need to retry
            const isNew = recordTime > lastTime;
            const alertStatus = state.alerts[alertKey];
            const needsRetry = alertStatus && alertStatus.status === 'failed';

            if (isNew || needsRetry) {
                // Determine if this record actually has an alert
                const types = [];
                if (record['Overspeed Alert'] === 'Yes') types.push('Overspeed Alert');
                if (record['Harsh Braking Alert'] === 'Yes') types.push('Harsh Braking Alert');
                if (record['GPS Disconnect Alert'] === 'Yes') types.push('GPS Disconnect Alert');
                if (record['Night Driving Alert'] === 'Yes') types.push('Night Driving Alert');
                
                if (types.length > 0) {
                    try {
                        await sendAlertEmail(record, types);
                        // Success! Mark as sent
                        state.alerts[alertKey] = {
                            status: 'sent',
                            sentAt: new Date().toISOString()
                        };
                        stateChanged = true;
                    } catch (error) {
                        // Failed to send. Mark as failed to retry safely on next poll
                        state.alerts[alertKey] = {
                            status: 'failed',
                            lastAttempt: new Date().toISOString(),
                            error: error.message
                        };
                        stateChanged = true;
                        console.log(`Email failed for ${alertKey}. Marked as pending for retry.`);
                    }
                }
            }
        }

        if (newLastTime > lastTime) {
            state.lastEvaluatedTime = newLastTime;
            stateChanged = true;
        }

        if (stateChanged) {
            saveState(state);
        }

    } catch (error) {
        console.error('Error during CSV polling:', error);
    }
};

// Initial state load
const initializeState = async () => {
    if (!fs.existsSync(STATE_FILE_PATH)) {
        try {
            const data = await readCSV();
            if (data.length > 0) {
                let maxTime = '';
                for (const record of data) {
                    if (record['Time'] > maxTime) {
                        maxTime = record['Time'];
                    }
                }
                saveState({ lastEvaluatedTime: maxTime, alerts: {} });
                console.log(`Initialized state with lastEvaluatedTime: ${maxTime}`);
            }
        } catch (e) {
            console.error('Error initializing state:', e);
        }
    }
};

// Start Server
app.listen(PORT, async () => {
    console.log(`Server is running on http://localhost:${PORT}`);
    await initializeState();
    
    // Start polling loop
    setInterval(pollCSV, POLLING_INTERVAL_MS);
    console.log(`Started CSV polling every ${POLLING_INTERVAL_MS / 1000} seconds.`);
});
