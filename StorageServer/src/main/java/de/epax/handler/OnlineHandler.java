package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
public class OnlineHandler implements HttpHandler {

    private final String serverName;

    public OnlineHandler(String serverName) {
        this.serverName = serverName;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>""" + serverName + """
                     — Online</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link href="https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=Syne:wght@400;700;800&display=swap" rel="stylesheet">
                    <style>
                        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

                        body {
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            background-color: #080b0f;
                            background-image:
                                radial-gradient(ellipse 80% 60% at 50% -10%, rgba(0, 210, 120, 0.12), transparent),
                                linear-gradient(180deg, #080b0f 0%, #0a0f14 100%);
                            font-family: 'Syne', sans-serif;
                            color: #e8f5ef;
                            overflow: hidden;
                        }

                        .grid-overlay {
                            position: fixed;
                            inset: 0;
                            background-image:
                                linear-gradient(rgba(0, 210, 120, 0.03) 1px, transparent 1px),
                                linear-gradient(90deg, rgba(0, 210, 120, 0.03) 1px, transparent 1px);
                            background-size: 48px 48px;
                            pointer-events: none;
                        }

                        .card {
                            position: relative;
                            padding: 56px 64px;
                            border: 1px solid rgba(0, 210, 120, 0.15);
                            border-radius: 4px;
                            background: rgba(8, 18, 14, 0.85);
                            backdrop-filter: blur(24px);
                            text-align: center;
                            max-width: 520px;
                            width: 90%;
                            animation: fadeUp 0.7s cubic-bezier(0.16, 1, 0.3, 1) both;
                        }

                        .card::before {
                            content: '';
                            position: absolute;
                            inset: 0;
                            border-radius: 4px;
                            padding: 1px;
                            background: linear-gradient(135deg, rgba(0, 210, 120, 0.4), transparent 60%);
                            -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
                            -webkit-mask-composite: xor;
                            mask-composite: exclude;
                            pointer-events: none;
                        }

                        .tag {
                            display: inline-block;
                            font-family: 'Space Mono', monospace;
                            font-size: 11px;
                            letter-spacing: 0.12em;
                            text-transform: uppercase;
                            color: #00d278;
                            background: rgba(0, 210, 120, 0.08);
                            border: 1px solid rgba(0, 210, 120, 0.2);
                            padding: 5px 14px;
                            border-radius: 2px;
                            margin-bottom: 32px;
                        }

                        .server-name {
                            font-size: clamp(26px, 5vw, 38px);
                            font-weight: 800;
                            letter-spacing: -0.02em;
                            line-height: 1.1;
                            color: #f0faf5;
                            margin-bottom: 10px;
                        }

                        .subtitle {
                            font-size: 13px;
                            font-family: 'Space Mono', monospace;
                            color: rgba(200, 230, 215, 0.4);
                            letter-spacing: 0.05em;
                            margin-bottom: 44px;
                        }

                        .status-row {
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            gap: 10px;
                        }

                        .pulse-ring {
                            position: relative;
                            width: 10px;
                            height: 10px;
                        }

                        .pulse-ring::before,
                        .pulse-ring::after {
                            content: '';
                            position: absolute;
                            inset: 0;
                            border-radius: 50%;
                            background: #00d278;
                        }

                        .pulse-ring::after {
                            animation: ping 1.6s cubic-bezier(0,0,0.2,1) infinite;
                            background: transparent;
                            border: 1px solid #00d278;
                        }

                        .status-text {
                            font-family: 'Space Mono', monospace;
                            font-size: 13px;
                            letter-spacing: 0.08em;
                            color: #00d278;
                            text-transform: uppercase;
                        }

                        .divider {
                            height: 1px;
                            background: linear-gradient(90deg, transparent, rgba(0, 210, 120, 0.2), transparent);
                            margin: 40px 0;
                        }

                        .meta {
                            font-family: 'Space Mono', monospace;
                            font-size: 11px;
                            color: rgba(200, 230, 215, 0.25);
                            letter-spacing: 0.05em;
                        }

                        @keyframes fadeUp {
                            from { opacity: 0; transform: translateY(20px); }
                            to   { opacity: 1; transform: translateY(0); }
                        }

                        @keyframes ping {
                            0%   { transform: scale(1);   opacity: 0.8; }
                            100% { transform: scale(2.8); opacity: 0;   }
                        }
                    </style>
                </head>
                <body>
                    <div class="grid-overlay"></div>
                    <div class="card">
                        <div class="tag">Storage Server</div>
                        <h1 class="server-name">""" + serverName + """
                        </h1>
                        <p class="subtitle">High Performance Java Storage System</p>
                        <div class="status-row">
                            <div class="pulse-ring"></div>
                            <span class="status-text">Online</span>
                        </div>
                        <div class="divider"></div>
                        <p class="meta">Powered by Vibax Engine</p>
                    </div>
                </body>
                </html>
                """;

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}