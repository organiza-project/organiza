#!/usr/bin/env python3
"""A local stand-in for Groq's chat endpoint, used by verify-android.sh.

It records every request so the script can check exactly what left the device.
Usage: fake-groq.py PORT LOG_FILE MODE_FILE. The mode file holds grant, garbage, error or demo.
Demo mode answers the copilot with believable sample advice, for screenshots only.
Transcriptions return the contents of MODE_FILE.transcript.
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT, LOG_FILE, MODE_FILE = int(sys.argv[1]), sys.argv[2], sys.argv[3]


def mode():
    try:
        with open(MODE_FILE, encoding="utf-8") as handle:
            return handle.read().strip()
    except FileNotFoundError:
        return "grant"


def content_for(body):
    system = body["messages"][0]["content"]
    if "fixed grammar" in system:
        hallucinated = "Thank you" in body["messages"][1]["content"]
        return {"command": None if hallucinated else "mark stretch as done"}
    if "focus copilot" in system and mode() == "demo":
        return {
            "focusBlocks": [
                {"day": "TUESDAY", "start": "09:30", "minutes": 90, "label": "Deep work before messages"},
                {"day": "WEDNESDAY", "start": "20:30", "minutes": 60, "label": "Evening wind-down, phone away"},
            ],
            "warnings": ["Most blocked attempts land in the hour after 10:00. Start a session at 09:50 to cover it."],
            "habitTips": ["Read 20 pages slips on Thursdays. Try reading right after dinner instead of in bed."],
        }
    if "focus copilot" in system:
        return {"focusBlocks": [{"day": "MONDAY", "start": "21:00", "minutes": 45, "label": "Test"}],
                "warnings": ["Test warning"], "habitTips": []}
    if "opening a blocked app anyway" in system:
        return {"reflection": "Test reflection"}
    if "plan focus blocks" in system:
        return {"blocks": [{"day": "MONDAY", "start": "09:30", "minutes": 60, "label": "Test"}], "note": ""}
    return {"summary": "Test summary", "suggestions": ["Test suggestion"]}


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        data = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if self.path.endswith("/audio/transcriptions"):
            with open(LOG_FILE, "a", encoding="utf-8") as log:
                log.write(f"AUDIO bytes={len(data)} wav={b'RIFF' in data} model={b'whisper-large-v3-turbo' in data}\n")
            with open(MODE_FILE + ".transcript", encoding="utf-8") as handle:
                self.reply(200, json.dumps({"text": handle.read().strip()}))
            return
        raw = data.decode("utf-8")
        with open(LOG_FILE, "a", encoding="utf-8") as log:
            log.write(f"AUTH {self.headers.get('Authorization')}\nBODY {raw}\n")
        if mode() == "error":
            self.reply(500, "{}")
            return
        text = "not json" if mode() == "garbage" else json.dumps(content_for(json.loads(raw)))
        self.reply(200, json.dumps({"choices": [{"message": {"role": "assistant", "content": text}}]}))

    def reply(self, status, text):
        data = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
