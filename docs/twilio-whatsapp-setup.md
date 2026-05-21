# Twilio WhatsApp Integration - Setup Guide

This guide walks you through setting up Twilio WhatsApp messaging for the AI Concierge platform.

---

## Overview

The flow:
1. Guest scans QR code → gets a WhatsApp link with a token
2. Guest sends first message to the Twilio WhatsApp number
3. Twilio forwards the message to our webhook (`/webhook/whatsapp`)
4. Our backend processes it through the AI concierge
5. Response is sent back to the guest via Twilio WhatsApp API

---

## Step 1: Create a Twilio Account

1. Go to [https://www.twilio.com/try-twilio](https://www.twilio.com/try-twilio)
2. Sign up for a free account
3. Verify your email and phone number
4. Once logged in, note your:
   - **Account SID** (starts with `AC...`)
   - **Auth Token** (click to reveal)

These are on the Twilio Console dashboard: [https://console.twilio.com](https://console.twilio.com)

---

## Step 2: Activate the WhatsApp Sandbox (Development)

For development/testing, Twilio provides a WhatsApp Sandbox:

1. Go to **Messaging** → **Try it out** → **Send a WhatsApp message**
   - Direct link: [https://console.twilio.com/us1/develop/sms/try-it-out/whatsapp-learn](https://console.twilio.com/us1/develop/sms/try-it-out/whatsapp-learn)

2. You'll see a sandbox number: `+1 415 523 8886` (this is the default Twilio sandbox number)

3. To join the sandbox, send the displayed join code from your WhatsApp:
   - Open WhatsApp on your phone
   - Send a message to `+14155238886`
   - Message: `join <your-sandbox-code>` (e.g., `join hungry-cat`)

4. You should get a confirmation: "You're connected to the sandbox!"

---

## Step 3: Configure the Webhook URL

Twilio needs to know where to send incoming messages.

### For Local Development (using ngrok)

1. Install ngrok: [https://ngrok.com/download](https://ngrok.com/download)

2. Start ngrok to tunnel to your local server:
   ```bash
   ngrok http 9090
   ```

3. Copy the HTTPS URL (e.g., `https://abc123.ngrok-free.app`)

4. In Twilio Console → **Messaging** → **Settings** → **WhatsApp Sandbox Settings**:
   - **When a message comes in**: `https://abc123.ngrok-free.app/webhook/whatsapp`
   - **Method**: POST
   - Click **Save**

### For Production

Use your actual server URL:
```
https://your-domain.com/webhook/whatsapp
```

---

## Step 4: Configure Application Properties

Update `application.yml` with your Twilio credentials:

```yaml
twilio:
  account-sid: ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
  auth-token: your_auth_token_here
  whatsapp-number: +14155238886
```

Or set as environment variables:
```bash
export TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export TWILIO_AUTH_TOKEN=your_auth_token_here
export TWILIO_WHATSAPP_NUMBER=+14155238886
```

---

## Step 5: Test the Integration

### Test 1: Send a message from WhatsApp

1. Make sure your app is running (`mvn spring-boot:run`)
2. Make sure ngrok is running and webhook URL is configured in Twilio
3. From your WhatsApp, send a message to `+14155238886`:
   ```
   TOKEN:<your-qr-token-here>
   ```
4. You should receive the welcome message back

### Test 2: Regular conversation

After connecting with a token, send any message:
```
What spa treatments do you offer?
```

You should get an AI-powered response from the concierge.

### Test 3: Verify webhook is receiving (check logs)

In your application logs, you should see:
```
WhatsApp webhook received from: whatsapp:+1234567890
WhatsApp message from whatsapp:+1234567890: <message content>
```

---

## Step 6: Production Setup (Twilio WhatsApp Business)

For production, you need a dedicated WhatsApp Business number:

1. Go to **Messaging** → **Senders** → **WhatsApp Senders**
2. Click **Register a WhatsApp Sender**
3. Follow the process:
   - Submit your Facebook Business Manager ID
   - Provide your business display name
   - Wait for Meta/WhatsApp approval (1-7 days)
4. Once approved, you'll get a dedicated WhatsApp number
5. Update `twilio.whatsapp-number` in your config

---

## How the Webhook Works

When a guest sends a WhatsApp message, Twilio POSTs to `/webhook/whatsapp` with:

| Parameter | Description |
|---|---|
| `From` | Sender's WhatsApp number (e.g., `whatsapp:+1234567890`) |
| `Body` | Message text |
| `ProfileName` | WhatsApp display name |

Our backend:
1. Checks if the message starts with `TOKEN:` → initializes session with reservation context
2. Otherwise, routes through the AI concierge with the existing session
3. Returns a TwiML XML response that Twilio sends back to the guest

---

## Troubleshooting

| Issue | Solution |
|---|---|
| No response from WhatsApp | Check ngrok is running and webhook URL is correct in Twilio |
| "You're not connected to sandbox" | Re-send the join code to the sandbox number |
| 403 from webhook | Ensure `/webhook/**` is in the security permit list |
| Token not recognized | Ensure the token hasn't expired (7-day default) |
| Messages delayed | Twilio sandbox has rate limits; production numbers are faster |

---

## Useful Twilio Console Links

- Dashboard: [https://console.twilio.com](https://console.twilio.com)
- WhatsApp Sandbox: [https://console.twilio.com/us1/develop/sms/try-it-out/whatsapp-learn](https://console.twilio.com/us1/develop/sms/try-it-out/whatsapp-learn)
- Webhook Logs: **Monitor** → **Logs** → **Messaging**
- API Keys: **Account** → **API keys & tokens**

---

## Cost

- **Sandbox (development)**: Free
- **Production**: ~$0.005-$0.05 per message depending on region
- See: [https://www.twilio.com/whatsapp/pricing](https://www.twilio.com/whatsapp/pricing)
