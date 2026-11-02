#!/usr/bin/env python3
"""A blind mailbox relay for Arena challenges (ADR-0011).

It stores opaque bytes by mailbox ID and returns them by cursor. It holds no keys and cannot
read anything. Messages live in memory and are lost on restart. This is a reference for testing
and self-hosting: put real deployments behind HTTPS, and add your own rate limits.

Usage: arena-relay.py PORT [DUMP_FILE]. DUMP_FILE receives each message's raw bytes, for testing.
"""
import base64
import json
import re
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1])
DUMP_FILE = sys.argv[2] if len(sys.argv) > 2 else None
ROUTE = re.compile(r"^/v1/mailbox/([0-9a-f]{64})/(append|fetch)$")
MAX_MESSAGE_BYTES = 256 * 1024
MAX_MESSAGES_PER_MAILBOX = 50_000
PAGE_SIZE = 200
mailboxes = {}
lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        match = ROUTE.match(self.path)
        length = int(self.headers.get("Content-Length", 0))
        if not match or length > MAX_MESSAGE_BYTES:
            self.reply(404 if not match else 413, {})
            return
        body = self.rfile.read(length)
        mailbox, action = match.groups()
        if action == "append":
            self.append(mailbox, body)
        else:
            self.fetch(mailbox, body)

    def append(self, mailbox, body):
        with lock:
            messages = mailboxes.setdefault(mailbox, [])
            if len(messages) >= MAX_MESSAGES_PER_MAILBOX:
                self.reply(429, {})
                return
            messages.append(body)
            cursor = len(messages)
        if DUMP_FILE:
            with open(DUMP_FILE, "ab") as dump:
                dump.write(f"MSG {mailbox} {len(body)}\n".encode() + body + b"\n")
        self.reply(200, {"cursor": cursor})

    def fetch(self, mailbox, body):
        try:
            after = int(json.loads(body)["after"])
        except (ValueError, KeyError, TypeError):
            self.reply(400, {})
            return
        with lock:
            page = mailboxes.get(mailbox, [])[after:after + PAGE_SIZE]
        self.reply(200, {"messages": [
            {"cursor": after + index + 1, "data": base64.b64encode(item).decode()}
            for index, item in enumerate(page)
        ]})

    def reply(self, status, content):
        data = json.dumps(content).encode()
        self.send_response(status)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
