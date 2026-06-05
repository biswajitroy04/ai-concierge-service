/**
 * AI Concierge Hotel Platform - Frontend Application
 * Handles chat, QR onboarding, and admin dashboard
 */

const API_BASE = '';
let currentToken = null;
let sessionId = null;
let currentScreen = 'onboarding-screen';
let whatsappLink = null;

// ============ INITIALIZATION ============
document.addEventListener('DOMContentLoaded', () => {
    initEventListeners();
    checkExistingSession();
});

function initEventListeners() {
    // Onboarding
    document.getElementById('btn-connect').addEventListener('click', handleTokenConnect);
    document.getElementById('btn-demo').addEventListener('click', startDemoMode);
    document.getElementById('token-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') handleTokenConnect();
    });

    // QR Generation
    document.getElementById('btn-generate-qr').addEventListener('click', handleGenerateQr);
    loadReservations();

    // Chat
    document.getElementById('btn-send').addEventListener('click', sendMessage);
    document.getElementById('chat-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendMessage();
    });
    document.getElementById('btn-switch-whatsapp').addEventListener('click', startWhatsAppSession);
    document.getElementById('btn-back').addEventListener('click', () => {
        // Clear session so next login starts fresh
        currentToken = null;
        sessionId = null;
        whatsappLink = null;
        setWhatsAppLink(null);
        setWhatsAppNumber('', false);
        localStorage.removeItem('concierge_token');
        localStorage.removeItem('concierge_session');
        showScreen('onboarding-screen');
    });

    // Quick Actions
    document.querySelectorAll('.quick-btn').forEach(btn => {
        btn.addEventListener('click', () => handleQuickAction(btn.dataset.action));
    });

    // Navigation
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            showScreen(btn.dataset.screen);
        });
    });

    // Admin
    document.getElementById('btn-menu').addEventListener('click', showAdminLogin);
    document.getElementById('btn-admin-onboarding').addEventListener('click', showAdminLogin);
    document.getElementById('admin-login-form').addEventListener('submit', handleAdminLogin);
    document.getElementById('close-modal').addEventListener('click', () => {
        document.getElementById('admin-login-modal').style.display = 'none';
    });
    document.getElementById('btn-admin-logout').addEventListener('click', () => showScreen('onboarding-screen'));

    // Services toggle
    document.getElementById('btn-services-toggle').addEventListener('click', () => {
        const menu = document.getElementById('services-menu');
        const toggle = document.querySelector('.services-toggle');
        if (menu.style.display === 'none') {
            menu.style.display = 'grid';
            toggle.classList.add('open');
        } else {
            menu.style.display = 'none';
            toggle.classList.remove('open');
        }
    });
}

function setWhatsAppLink(link) {
    whatsappLink = link;
    const chatActions = document.getElementById('chat-actions');
    if (!chatActions) return;

    if (link || (currentToken && currentToken !== 'demo-token')) {
        chatActions.style.display = 'block';
    } else {
        chatActions.style.display = 'none';
    }
}

function setWhatsAppNumber(phone, fromReservation) {
    const input = document.getElementById('whatsapp-number-input');
    const hint = document.getElementById('whatsapp-number-hint');
    if (!input || !hint) return;

    if (phone && phone.trim()) {
        input.value = phone.trim();
        hint.textContent = fromReservation
            ? 'Pre-filled from the reservation. You can edit it before continuing.'
            : '';
    } else {
        input.value = '';
        hint.textContent = currentToken && currentToken !== 'demo-token'
            ? 'Enter the WhatsApp number you want to use for this stay.'
            : '';
    }
}

async function fetchWhatsAppLink(token) {
    if (!token || token === 'demo-token') {
        setWhatsAppLink(null);
        return;
    }

    try {
        const params = new URLSearchParams({ token });
        if (sessionId) {
            params.set('sessionId', sessionId);
        }
        const response = await fetch(`${API_BASE}/whatsapp/link?${params.toString()}`);
        if (!response.ok) throw new Error('No WhatsApp link available');
        const data = await response.json();
        if (data.sessionId) {
            sessionId = data.sessionId;
            localStorage.setItem('concierge_session', sessionId);
        }
        setWhatsAppLink(data.whatsappLink);
    } catch (error) {
        setWhatsAppLink(null);
    }
}

async function startWhatsAppSession() {
    if (!currentToken || currentToken === 'demo-token') {
        return;
    }

    const phoneInput = document.getElementById('whatsapp-number-input');
    const guestWhatsAppNumber = phoneInput.value.trim();
    if (!guestWhatsAppNumber) {
        phoneInput.focus();
        return;
    }

    const button = document.getElementById('btn-switch-whatsapp');
    const originalHtml = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Connecting...';

    try {
        const response = await fetch(`${API_BASE}/whatsapp/session`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                token: currentToken,
                sessionId,
                whatsappNumber: guestWhatsAppNumber
            })
        });

        if (!response.ok) throw new Error('Could not start WhatsApp session');

        const data = await response.json();
        if (data.sessionId) {
            sessionId = data.sessionId;
            localStorage.setItem('concierge_session', sessionId);
        }
        setWhatsAppLink(data.whatsappLink);
        if (data.whatsappLink) {
            window.open(data.whatsappLink, '_blank');
        }
    } catch (error) {
        if (whatsappLink) {
            window.open(whatsappLink, '_blank');
        } else {
            phoneInput.focus();
        }
    } finally {
        button.disabled = false;
        button.innerHTML = originalHtml;
    }
}

// ============ SCREEN MANAGEMENT ============
function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(screenId).classList.add('active');
    currentScreen = screenId;

    const nav = document.getElementById('bottom-nav');
    if (screenId === 'onboarding-screen') {
        nav.classList.remove('visible');
    } else {
        nav.classList.add('visible');
        // Highlight the correct nav button
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.screen === screenId);
        });
    }

    if (screenId === 'dashboard-screen') {
        loadDashboardData();
    }
}

function checkExistingSession() {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    if (!token) return;

    const tokenInput = document.getElementById('token-input');
    tokenInput.value = token;
    handleTokenConnect();
}

// ============ QR / TOKEN CONNECTION ============
async function handleTokenConnect() {
    const tokenInput = document.getElementById('token-input');
    const token = tokenInput.value.trim();
    if (!token) return;

    try {
        const response = await fetch(`${API_BASE}/chat/guest?token=${encodeURIComponent(token)}`);
        if (!response.ok) throw new Error('Invalid token');

        const data = await response.json();
        currentToken = token;
        sessionId = data.sessionId;

        localStorage.setItem('concierge_token', token);
        localStorage.setItem('concierge_session', sessionId);

        // Clear old chat messages
        document.getElementById('chat-messages').innerHTML = '';

        showScreen('chat-screen');
        addMessage('assistant', data.message);
        setWhatsAppNumber(data.guestPhone, Boolean(data.guestPhone));
        setWhatsAppLink(null);
    } catch (error) {
        // In demo mode, proceed anyway
        startDemoMode();
    }
}

// ============ DEMO MODE ============
function startDemoMode() {
    sessionId = 'demo-' + Date.now();
    currentToken = 'demo-token';
    localStorage.setItem('concierge_session', sessionId);

    showScreen('chat-screen');
    setWhatsAppLink(null);
    setWhatsAppNumber('', false);

    const welcomeMsg = `Welcome Mr. Roy! 🌟

Your stay is from May 20 to May 24 in Room 1708.

I'm your AI concierge, here to make your stay exceptional. I can help you with:
• 🛎️ Housekeeping requests
• 💆 Spa bookings
• 🍽️ Restaurant reservations
• 🕐 Late checkout requests
• 🗺️ Local recommendations

How may I assist you today?`;

    addMessage('assistant', welcomeMsg);
}

// ============ QR CODE GENERATION ============
async function loadReservations() {
    try {
        const response = await fetch(`${API_BASE}/qr/reservations`);
        if (!response.ok) throw new Error('Failed to load reservations');

        const reservations = await response.json();
        const select = document.getElementById('reservation-select');

        reservations.forEach(r => {
            const option = document.createElement('option');
            option.value = r.id;
            option.dataset.guestPhone = r.guestPhone || '';
            option.textContent = `${r.confirmationNumber} — ${r.guestName} (Room ${r.roomNumber})`;
            select.appendChild(option);
        });
    } catch (error) {
        console.log('Could not load reservations from API, using demo mode');
        // Populate with demo data if API is not available
        const select = document.getElementById('reservation-select');
        const demoReservations = [
            { id: 1, text: 'GM-2024-001 — Mr. Arjun Roy (Room 1708)' },
            { id: 2, text: 'GM-2024-002 — Ms. Sarah Mitchell (Room 1205)' },
            { id: 3, text: 'GM-2024-003 — Mr. James Chen (Room 0803)' },
            { id: 4, text: 'AP-2024-001 — Mrs. Elena Petrova (Room 2201)' },
            { id: 5, text: 'IS-2024-001 — Mr. Takeshi Yamamoto (Room 1501)' },
        ];
        demoReservations.forEach(r => {
            const option = document.createElement('option');
            option.value = r.id;
            option.textContent = r.text;
            select.appendChild(option);
        });
    }
}

async function handleGenerateQr() {
    const select = document.getElementById('reservation-select');
    const reservationId = select.value;

    if (!reservationId) {
        alert('Please select a reservation first.');
        return;
    }

    const qrDisplay = document.getElementById('qr-display');
    const guestInfo = document.getElementById('qr-guest-info');

    // Show loading state
    qrDisplay.innerHTML = '<i class="fas fa-spinner fa-spin" style="font-size:48px;color:var(--gold)"></i><p>Generating...</p>';

    try {
        const response = await fetch(`${API_BASE}/qr/generate/${reservationId}`, { method: 'POST' });
        if (!response.ok) throw new Error('Failed to generate QR code');

        const data = await response.json();

        // Display QR code image
        qrDisplay.innerHTML = `<img src="data:image/png;base64,${data.qrCodeBase64}" alt="QR Code" />`;

        // Show guest info
        guestInfo.style.display = 'block';
        guestInfo.innerHTML = `
            <div class="guest-name">${data.guestName}</div>
            <div class="guest-details">Confirmation: ${data.reservationId} • Expires in ${Math.floor(data.expiresInSeconds / 86400)} days</div>
        `;

        // Auto-fill the token input so user can also click to start chat
        document.getElementById('token-input').value = data.token;
        setWhatsAppNumber(data.guestPhone || select.options[select.selectedIndex].dataset.guestPhone, Boolean(data.guestPhone || select.options[select.selectedIndex].dataset.guestPhone));

    } catch (error) {
        // Demo fallback - show a placeholder QR
        qrDisplay.innerHTML = `
            <i class="fas fa-qrcode" style="font-size:120px;color:var(--gold)"></i>
            <p style="margin-top:8px;color:var(--gold-light);font-size:12px;">Demo QR — Start backend to generate real codes</p>
        `;
        guestInfo.style.display = 'block';
        guestInfo.innerHTML = `
            <div class="guest-name">${select.options[select.selectedIndex].text}</div>
            <div class="guest-details">Demo mode • Click "Try Demo Experience" to chat</div>
        `;
    }
}

// ============ CHAT FUNCTIONALITY ============
async function sendMessage() {
    const input = document.getElementById('chat-input');
    const message = input.value.trim();
    if (!message) return;

    input.value = '';
    addMessage('user', message);
    showTypingIndicator();

    try {
        if (currentToken === 'demo-token') {
            // Demo mode - simulate AI response
            setTimeout(() => {
                hideTypingIndicator();
                const response = getDemoResponse(message);
                addMessage('assistant', response);
            }, 1500);
            return;
        }

        const response = await fetch(`${API_BASE}/chat/message`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentToken}`
            },
            body: JSON.stringify({ message, sessionId })
        });

        hideTypingIndicator();

        if (!response.ok) throw new Error('Chat request failed');

        const data = await response.json();
        addMessage('assistant', data.message);

        if (data.recommendations && data.recommendations.length > 0) {
            addRecommendationCards(data.recommendations);
        }
    } catch (error) {
        hideTypingIndicator();
        addMessage('assistant', 'I apologize for the inconvenience. Let me connect you with our team.');
    }
}

function addMessage(role, content) {
    const container = document.getElementById('chat-messages');
    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    const messageHtml = `
        <div class="message ${role}">
            ${role === 'assistant' ? '<div class="message-avatar"><i class="fas fa-concierge-bell"></i></div>' : ''}
            <div>
                <div class="message-bubble">${escapeHtml(content)}</div>
                <div class="message-time">${time}</div>
            </div>
        </div>
    `;

    container.insertAdjacentHTML('beforeend', messageHtml);
    container.scrollTop = container.scrollHeight;
}

function addRecommendationCards(recommendations) {
    const container = document.getElementById('chat-messages');
    let cardsHtml = '<div class="recommendation-cards">';

    recommendations.forEach(rec => {
        cardsHtml += `
            <div class="rec-card">
                <div class="rec-card-img"><i class="fas fa-star"></i></div>
                <div class="rec-card-body">
                    <h4>${escapeHtml(rec.title)}</h4>
                    <p>${escapeHtml(rec.description)}</p>
                    ${rec.price ? `<div class="price">$${rec.price}</div>` : ''}
                </div>
            </div>
        `;
    });

    cardsHtml += '</div>';
    container.insertAdjacentHTML('beforeend', cardsHtml);
    container.scrollTop = container.scrollHeight;
}

function showTypingIndicator() {
    document.getElementById('typing-indicator').style.display = 'flex';
    const container = document.getElementById('chat-messages');
    container.scrollTop = container.scrollHeight;
}

function hideTypingIndicator() {
    document.getElementById('typing-indicator').style.display = 'none';
}

// ============ QUICK ACTIONS ============
function handleQuickAction(action) {
    const messages = {
        housekeeping: "I'd like to request housekeeping service for my room.",
        spa: "I'm interested in booking a spa treatment. What's available?",
        restaurant: "I'd like to make a restaurant reservation for tonight.",
        late_checkout: "Is it possible to get a late checkout?",
        attractions: "What are some good attractions or restaurants nearby?"
    };

    const input = document.getElementById('chat-input');
    input.value = messages[action] || '';
    sendMessage();
}

// ============ DEMO RESPONSES ============
function getDemoResponse(message) {
    const lower = message.toLowerCase();

    if (lower.includes('housekeeping') || lower.includes('towel') || lower.includes('clean')) {
        return `Of course, Mr. Roy! I'll arrange that right away. 🛎️

I've submitted a housekeeping request for Room 1708. Here's what I've arranged:
• Request: Room service/cleaning
• Priority: Normal
• Estimated time: 15-20 minutes

Our team will be there shortly. Is there anything specific you'd like them to bring?`;
    }

    if (lower.includes('spa') || lower.includes('massage')) {
        return `Wonderful choice! Our Meridian Spa offers exceptional treatments. 💆

Here are today's available services:
• Swedish Massage (60 min) - $150
• Deep Tissue Massage (60 min) - $180
• Hot Stone Therapy (75 min) - $200
• Couples Massage (90 min) - $350
• Facial Treatment (45 min) - $120

As a Platinum member, you receive a 20% discount on all spa services! Would you like me to book an appointment?`;
    }

    if (lower.includes('restaurant') || lower.includes('dinner') || lower.includes('food')) {
        return `I'd be happy to help with dining! 🍽️

Our restaurants tonight:
• **The Grand Brasserie** (Lobby) - International cuisine, open until 10 PM
• **Sky Lounge** (30th Floor) - Cocktails & small plates with city views
• **Sakura** (5th Floor) - Japanese fine dining, omakase available

Given your vegetarian preference, I'd recommend the Vegetable Tasting Menu at The Grand Brasserie ($65) or the Tempura Assortment at Sakura.

What time would you prefer, and for how many guests?`;
    }

    if (lower.includes('late checkout') || lower.includes('checkout')) {
        return `Great news, Mr. Roy! ✨

As a valued Platinum member, you're eligible for complimentary late checkout until 2:00 PM! 

I've noted this for your reservation. Your new checkout time is 2:00 PM on May 24th.

Would you like me to arrange anything else for your departure day?`;
    }

    if (lower.includes('attract') || lower.includes('nearby') || lower.includes('recommend')) {
        return `Here are my top recommendations near the hotel! 🗺️

🏛️ **City Art Museum** - 0.8 miles
World-class exhibitions, currently featuring Modern Masters

🌳 **Central Park** - 0.3 miles
Beautiful walking trails, perfect for morning strolls

🛍️ **Luxury Shopping District** - 0.5 miles
Designer boutiques including Hermès, Louis Vuitton

🍽️ **The Golden Plate** - 0.3 miles
Michelin-starred fine dining (vegetarian options available)

Would you like me to arrange transportation or make reservations at any of these?`;
    }

    if (lower.includes('thank') || lower.includes('great') || lower.includes('perfect')) {
        return `You're most welcome, Mr. Roy! It's my pleasure to assist. 😊

Don't hesitate to reach out anytime during your stay. Whether it's 3 AM or 3 PM, I'm here for you.

Enjoy your evening!`;
    }

    return `I'd be happy to help with that! Let me look into it for you.

Is there anything specific about "${message}" you'd like me to assist with? I can help with:
• Room services and housekeeping
• Spa and wellness bookings
• Restaurant reservations
• Local recommendations
• Transportation arrangements
• Any other requests

Just let me know how I can make your stay more comfortable!`;
}

// ============ ADMIN DASHBOARD ============
function showAdminLogin() {
    document.getElementById('admin-login-modal').style.display = 'flex';
}

async function handleAdminLogin(e) {
    e.preventDefault();
    const username = document.getElementById('admin-username').value;
    const password = document.getElementById('admin-password').value;

    try {
        const response = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) throw new Error('Login failed');

        const data = await response.json();
        localStorage.setItem('admin_token', data.token);
        document.getElementById('admin-login-modal').style.display = 'none';
        showScreen('dashboard-screen');
        loadDashboardData();
    } catch (error) {
        // Demo mode dashboard
        document.getElementById('admin-login-modal').style.display = 'none';
        showScreen('dashboard-screen');
        loadDemoDashboard();
    }
}

async function loadDashboardData() {
    try {
        const response = await fetch(`${API_BASE}/dashboard/stats/1`);
        if (!response.ok) throw new Error('Failed to load dashboard: ' + response.status);
        const data = await response.json();
        renderDashboard(data);
    } catch (error) {
        console.error('Dashboard API error:', error);
    }
    // Always load sub-panels regardless of stats API result
    loadHousekeepingTickets();
    loadSpaBookings();
    loadRestaurantBookings();
}

function renderDashboard(data) {
    document.getElementById('stat-active-convos').textContent = data.activeConversations || 0;
    document.getElementById('stat-sentiment').textContent = data.averageSentiment != null ? data.averageSentiment.toFixed(2) : '0.00';
    document.getElementById('stat-escalations').textContent = data.pendingEscalations || 0;
    document.getElementById('stat-housekeeping').textContent = data.housekeepingPending || 0;
    document.getElementById('convo-count').textContent = data.activeConversations || 0;
    document.getElementById('escalation-count').textContent = data.pendingEscalations || 0;

    // Render conversations list
    const convoList = document.getElementById('conversations-list');
    if (data.recentConversations && data.recentConversations.length > 0) {
        convoList.innerHTML = data.recentConversations.map(c => `
            <div class="convo-item">
                <div class="guest-name">${c.guestName || 'Guest'}</div>
                <div class="room-info">Room ${c.roomNumber || '-'} • ${c.status || 'Active'}</div>
                <span class="sentiment-badge ${getSentimentClass(c.sentiment)}">${c.sentiment || 'neutral'}</span>
            </div>
        `).join('');
    } else {
        convoList.innerHTML = '<p style="color:var(--text-muted);text-align:center;padding:20px;">No active conversations</p>';
    }

    // Render escalations list
    const escList = document.getElementById('escalations-list');
    if (data.activeEscalations && data.activeEscalations.length > 0) {
        escList.innerHTML = data.activeEscalations.map(e => `
            <div class="escalation-item priority-${(e.priority || 'medium').toLowerCase()}">
                <div class="guest-name">${e.guestName || 'Guest'}</div>
                <div class="ticket-info">Room ${e.roomNumber || '-'} • Priority: ${e.priority || 'MEDIUM'}</div>
                <div class="ticket-info">${e.reason || ''}</div>
            </div>
        `).join('');
    } else {
        escList.innerHTML = '<p style="color:var(--text-muted);text-align:center;padding:20px;">No escalations</p>';
    }

    // Render sentiment distribution
    if (data.sentimentDistribution) {
        const total = Object.values(data.sentimentDistribution).reduce((a, b) => a + b, 0) || 1;
        const chart = document.getElementById('sentiment-chart');
        const labels = ['VERY_POSITIVE', 'POSITIVE', 'NEUTRAL', 'NEGATIVE', 'VERY_NEGATIVE'];
        const displayNames = ['Very Positive', 'Positive', 'Neutral', 'Negative', 'Very Negative'];
        const classes = ['positive', 'positive', 'neutral', 'negative', 'very-negative'];

        chart.innerHTML = labels.map((label, i) => {
            const count = data.sentimentDistribution[label] || 0;
            const pct = Math.round((count / total) * 100);
            return `<div class="sentiment-bar">
                <span class="bar-label">${displayNames[i]}</span>
                <div class="bar-track"><div class="bar-fill ${classes[i]}" style="width: ${pct}%"></div></div>
                <span class="bar-value">${pct}%</span>
            </div>`;
        }).join('');
    }

    // Render housekeeping tickets
    // (loaded separately by loadDashboardData)
}

async function loadHousekeepingTickets() {
    try {
        const response = await fetch(`${API_BASE}/admin/housekeeping/hotel/1`);
        if (!response.ok) return;
        const tickets = await response.json();
        const hkList = document.getElementById('housekeeping-list');

        if (tickets.length > 0) {
            hkList.innerHTML = tickets.map(t => `
                <div class="hk-item">
                    <div class="guest-name">Room ${t.reservation?.roomNumber || '-'} - ${t.requestType || 'Request'}</div>
                    <div class="room-info">Priority: ${t.priority || 'NORMAL'} • Status: ${t.status || 'PENDING'}</div>
                </div>
            `).join('');
        } else {
            hkList.innerHTML = '<p style="color:var(--text-muted);text-align:center;padding:20px;">No pending requests</p>';
        }
    } catch (e) {
        console.log('Could not load housekeeping tickets');
    }
}

async function loadSpaBookings() {
    try {
        const response = await fetch(`${API_BASE}/admin/spa-bookings/all/1`);
        if (!response.ok) return;
        const bookings = await response.json();
        const list = document.getElementById('spa-bookings-list');

        if (bookings.length > 0) {
            list.innerHTML = bookings.map(b => `
                <div class="hk-item">
                    <div class="guest-name">${b.serviceName || 'Spa Service'}</div>
                    <div class="room-info">Room ${b.reservation?.roomNumber || '-'} • ${b.bookingDate} at ${b.startTime} • ${b.status}</div>
                    <div class="room-info">Duration: ${b.durationMinutes}min • $${b.price}</div>
                </div>
            `).join('');
        } else {
            list.innerHTML = '<p style="color:var(--text-muted);text-align:center;padding:20px;">No spa bookings</p>';
        }
    } catch (e) {
        console.log('Could not load spa bookings');
    }
}

async function loadRestaurantBookings() {
    try {
        const response = await fetch(`${API_BASE}/admin/restaurant-bookings/hotel/1`);
        if (!response.ok) return;
        const bookings = await response.json();
        const list = document.getElementById('restaurant-bookings-list');

        if (bookings.length > 0) {
            list.innerHTML = bookings.map(b => `
                <div class="hk-item">
                    <div class="guest-name">${b.restaurantName || 'Restaurant'}</div>
                    <div class="room-info">Room ${b.reservation?.roomNumber || '-'} • ${b.bookingDate} at ${b.bookingTime} • Party: ${b.partySize}</div>
                    <div class="room-info">Status: ${b.status}${b.specialRequests ? ' • ' + b.specialRequests : ''}</div>
                </div>
            `).join('');
        } else {
            list.innerHTML = '<p style="color:var(--text-muted);text-align:center;padding:20px;">No restaurant bookings</p>';
        }
    } catch (e) {
        console.log('Could not load restaurant bookings');
    }
}

function getSentimentClass(sentiment) {
    if (!sentiment) return 'neutral';
    const s = sentiment.toLowerCase();
    if (s.includes('positive')) return 'positive';
    if (s.includes('negative')) return 'negative';
    return 'neutral';
}

// ============ UTILITIES ============
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}


// ============ RAG MANAGEMENT ============
document.addEventListener('DOMContentLoaded', () => {
    // RAG event listeners
    const dropZone = document.getElementById('file-drop-zone');
    const fileInput = document.getElementById('rag-file-input');

    if (dropZone) {
        dropZone.addEventListener('click', () => fileInput.click());
        dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('dragover'); });
        dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) {
                fileInput.files = e.dataTransfer.files;
                showSelectedFile(e.dataTransfer.files[0].name);
            }
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files.length > 0) {
                showSelectedFile(fileInput.files[0].name);
            }
        });
    }

    document.getElementById('btn-upload-rag')?.addEventListener('click', handleRagUpload);
    document.getElementById('btn-ingest-defaults')?.addEventListener('click', handleIngestDefaults);
    document.getElementById('btn-refresh-docs')?.addEventListener('click', loadRagDocuments);
});

function showSelectedFile(name) {
    document.getElementById('selected-file-name').textContent = '📄 ' + name;
}

async function handleRagUpload() {
    const fileInput = document.getElementById('rag-file-input');
    const category = document.getElementById('rag-category').value;
    const resultDiv = document.getElementById('upload-result');

    if (!fileInput.files || fileInput.files.length === 0) {
        resultDiv.style.display = 'block';
        resultDiv.className = 'upload-result error';
        resultDiv.textContent = 'Please select a file first.';
        return;
    }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    formData.append('category', category);

    const uploadBtn = document.getElementById('btn-upload-rag');
    uploadBtn.disabled = true;
    uploadBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Uploading & Embedding...';

    resultDiv.style.display = 'block';
    resultDiv.className = 'upload-result';
    resultDiv.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing file... This may take a moment for large documents.';

    try {
        const response = await fetch(`${API_BASE}/rag/upload`, {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        uploadBtn.disabled = false;
        uploadBtn.innerHTML = '<i class="fas fa-upload"></i> Upload to Vector Store';

        if (response.ok && data.status === 'SUCCESS') {
            resultDiv.className = 'upload-result success';
            resultDiv.innerHTML = `✓ Uploaded <strong>${data.fileName}</strong> — ${data.chunksCreated} chunks created`;
            fileInput.value = '';
            document.getElementById('selected-file-name').textContent = '';
            loadRagDocuments();
            loadRagStats();
        } else if (data.status === 'DUPLICATE') {
            resultDiv.className = 'upload-result error';
            resultDiv.innerHTML = `⚠ Duplicate: <strong>${data.fileName}</strong> has already been uploaded.`;
        } else {
            resultDiv.className = 'upload-result error';
            resultDiv.textContent = '✗ ' + (data.error || 'Upload failed');
        }
    } catch (error) {
        const uploadBtn2 = document.getElementById('btn-upload-rag');
        uploadBtn2.disabled = false;
        uploadBtn2.innerHTML = '<i class="fas fa-upload"></i> Upload to Vector Store';
        resultDiv.className = 'upload-result error';
        resultDiv.textContent = '✗ Could not connect to server. Is the backend running?';
    }
}

async function handleIngestDefaults() {
    const resultDiv = document.getElementById('upload-result');
    const defaultsBtn = document.getElementById('btn-ingest-defaults');
    defaultsBtn.disabled = true;
    defaultsBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading...';

    resultDiv.style.display = 'block';
    resultDiv.className = 'upload-result';
    resultDiv.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading default hotel documents...';

    try {
        const response = await fetch(`${API_BASE}/rag/ingest-defaults`, { method: 'POST' });
        const data = await response.json();

        defaultsBtn.disabled = false;
        defaultsBtn.innerHTML = '<i class="fas fa-magic"></i> Load Default Hotel Documents';

        if (response.ok) {
            const successCount = data.filter(d => d.status === 'SUCCESS').length;
            const duplicateCount = data.filter(d => d.status === 'DUPLICATE').length;
            const totalChunks = data.filter(d => d.status === 'SUCCESS').reduce((sum, d) => sum + d.chunksCreated, 0);

            if (successCount === 0 && duplicateCount > 0) {
                resultDiv.className = 'upload-result error';
                resultDiv.innerHTML = `⚠ All ${duplicateCount} documents have already been uploaded. No duplicates ingested.`;
            } else {
                resultDiv.className = 'upload-result success';
                let msg = `✓ Loaded ${successCount} documents (${totalChunks} total chunks)`;
                if (duplicateCount > 0) msg += ` — ${duplicateCount} skipped (already uploaded)`;
                resultDiv.innerHTML = msg;
            }
            loadRagDocuments();
            loadRagStats();
        } else {
            resultDiv.className = 'upload-result error';
            resultDiv.textContent = '✗ Failed to load default documents';
        }
    } catch (error) {
        defaultsBtn.disabled = false;
        defaultsBtn.innerHTML = '<i class="fas fa-magic"></i> Load Default Hotel Documents';
        resultDiv.className = 'upload-result error';
        resultDiv.textContent = '✗ Could not connect to server. Is the backend running?';
    }
}

async function loadRagDocuments() {
    try {
        const response = await fetch(`${API_BASE}/rag/documents`);
        const docs = await response.json();

        const list = document.getElementById('rag-documents-list');
        if (docs.length === 0) {
            list.innerHTML = '<p style="color: var(--text-muted); text-align: center; padding: 20px;">No documents loaded yet.</p>';
            return;
        }

        list.innerHTML = docs.map(doc => `
            <div class="doc-item">
                <div class="doc-item-info">
                    <div class="doc-name">${doc.fileName}</div>
                    <div class="doc-meta">${doc.chunksCreated} chunks • ${doc.uploadedAt || ''}</div>
                </div>
                <span class="doc-item-badge ${doc.category}">${doc.category}</span>
                <button class="btn-delete-doc" onclick="handleDeleteDocument('${doc.fileName}')" title="Delete document">
                    <i class="fas fa-trash-alt"></i>
                </button>
            </div>
        `).join('');
    } catch (error) {
        console.log('Could not load RAG documents');
    }
}

async function loadRagStats() {
    try {
        const response = await fetch(`${API_BASE}/rag/stats`);
        const stats = await response.json();

        document.getElementById('rag-doc-count').textContent = stats.documentsUploaded || 0;
        document.getElementById('rag-chunk-count').textContent = stats.totalChunks || 0;
        document.getElementById('rag-class-name').textContent = stats.className || '-';
    } catch (error) {
        console.log('Could not load RAG stats');
    }
}

// Load RAG data when screen is shown
const originalShowScreen = showScreen;
showScreen = function(screenId) {
    originalShowScreen(screenId);
    if (screenId === 'rag-screen') {
        loadRagDocuments();
        loadRagStats();
    }
    if (screenId === 'dashboard-screen') {
        loadDashboardData();
    }
};


// ============ DELETE RAG DOCUMENT ============
async function handleDeleteDocument(fileName) {
    if (!confirm(`Delete "${fileName}" and all its chunks from the vector store?`)) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/rag/documents?fileName=${encodeURIComponent(fileName)}`, {
            method: 'DELETE'
        });

        const data = await response.json();
        const resultDiv = document.getElementById('upload-result');

        if (response.ok && data.status === 'SUCCESS') {
            resultDiv.style.display = 'block';
            resultDiv.className = 'upload-result success';
            resultDiv.innerHTML = `✓ Deleted <strong>${fileName}</strong> and all its chunks.`;
            loadRagDocuments();
            loadRagStats();
        } else {
            resultDiv.style.display = 'block';
            resultDiv.className = 'upload-result error';
            resultDiv.textContent = '✗ ' + (data.error || 'Delete failed');
        }
    } catch (error) {
        alert('Could not connect to server.');
    }
}
