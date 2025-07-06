const WebSocket = require('ws');
const net = require('net');
const https = require('https');
const fs = require('fs');
const path = require('path');

const WS_PORT = 8080;
const TCP_HOST = '98.84.151.65';
const TCP_PORT = 1138;

// SSL/TLS configuration
const sslOptions = {
    key: fs.readFileSync('playstickarena.com-key.pem'),
    cert: fs.readFileSync('playstickarena.com-crt.pem')
};
console.log("SSL certs loaded:", 
    fs.existsSync('playstickarena.com-key.pem'), 
    fs.existsSync('playstickarena.com-crt.pem'));

// Create HTTPS server to serve the HTML file
const server = https.createServer(sslOptions, (req, res) => {
  console.log(`🔥 HIT: ${req.method} ${req.url}`); // <--- ADD THIS

    
    // Set CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    if (req.url === '/' || req.url === '/index.html') {
        fs.readFile(path.join(__dirname, 'index.html'), (err, data) => {
            if (err) {
                console.error('Error reading index.html:', err);
                res.writeHead(500);
                res.end('Error loading index.html');
                return;
            }
            res.writeHead(200, { 'Content-Type': 'text/html' });
            res.end(data);
        });
    } else if (req.url.endsWith('.swf')) {
        // Serve SWF files
        const swfPath = path.join(__dirname, req.url);
        fs.readFile(swfPath, (err, data) => {
            if (err) {
                console.error('Error reading SWF:', err);
                res.writeHead(404);
                res.end('SWF not found');
                return;
            }
            res.writeHead(200, { 'Content-Type': 'application/x-shockwave-flash' });
            res.end(data);
        });
    } else {
        res.writeHead(404);
        res.end('Not found');
    }
});

// Create WebSocket server with WSS
const wss = new WebSocket.Server({ 
    server,
    verifyClient: (info, callback) => {
        console.log('New WebSocket connection attempt from:', info.origin);
        callback(true);
    }
});

console.log(`Server started on port ${WS_PORT}`);

wss.on('connection', (ws, req) => {
    const realIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    console.log('New WebSocket connection from:', realIp);

    // Create TCP connection to Java server
    const tcp = net.createConnection({ 
        host: TCP_HOST, 
        port: TCP_PORT 
    }, () => {
        console.log('Connected to Java server');

        setTimeout(() => {
            console.log('Sending IP message to Java server:', realIp);
            tcp.write(`[IP:${realIp}]\n`);
        }, 500);
    });

    // Handle WebSocket messages
    ws.on('message', (msg) => {
        if (tcp.writable) {
            console.log('WS -> TCP:', msg.length, 'bytes');
            tcp.write(msg);
        } else {
            console.log('TCP socket not writable');
        }
    });

    // Handle TCP data
    tcp.on('data', (data) => {
        if (ws.readyState === WebSocket.OPEN) {
            console.log('TCP -> WS:', data.length, 'bytes');
            console.log('Content:', data.toString('utf8'));
            ws.send(data);
        } else {
            console.log('WebSocket not open, state:', ws.readyState);
        }
    });

    // Handle WebSocket close
    ws.on('close', () => {
        console.log('WebSocket connection closed');
        tcp.end();
    });

    // Handle TCP close
    tcp.on('close', () => {
        console.log('TCP connection closed');
        if (ws.readyState === WebSocket.OPEN) {
            ws.close();
        }
    });

    // Handle errors
    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
        tcp.end();
    });

    tcp.on('error', (error) => {
        console.error('TCP error:', error);
        if (ws.readyState === WebSocket.OPEN) {
            ws.close();
        }
    });
});

// Start the server
server.listen(WS_PORT, '0.0.0.0', () => {
  console.log(`HTTPS server listening on port ${WS_PORT}`);
});

const policy = `<?xml version="1.0"?><!DOCTYPE cross-domain-policy SYSTEM "http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd"><cross-domain-policy><allow-access-from domain="*" to-ports="*" /></cross-domain-policy>\0`;

net.createServer((socket) => {
    console.log('⚡ Policy file requested');
    socket.write(policy);
    socket.end();
}).listen(843, () => {
    console.log('🔐 Flash policy server running on port 843');
});
