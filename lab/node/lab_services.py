#!/usr/bin/env python3
"""Demo services on the DMZ server: HTTP on 80 and 8080, a TCP banner on 22 and 3306, UDP echo on 5353."""
import socket
import socketserver
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Web(BaseHTTPRequestHandler):
    def do_GET(self):
        body = (f"NEXUS lab server {socket.gethostname()} – port {self.server.server_address[1]} – "
                f"client {self.client_address[0]}\n").encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print(f"http:{self.server.server_address[1]} {self.client_address[0]} {fmt % args}", flush=True)


class Banner(socketserver.BaseRequestHandler):
    def handle(self):
        port = self.server.server_address[1]
        print(f"tcp:{port} connection from {self.client_address[0]}", flush=True)
        self.request.sendall(f"NEXUS-LAB service on {port}\r\n".encode())


class UdpEcho(socketserver.BaseRequestHandler):
    def handle(self):
        data, sock = self.request
        print(f"udp:5353 datagram from {self.client_address[0]}", flush=True)
        sock.sendto(b"echo:" + data[:512], self.client_address)


class TTCP(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def serve(srv):
    threading.Thread(target=srv.serve_forever, daemon=True).start()


if __name__ == "__main__":
    for port in (80, 8080):
        serve(ThreadingHTTPServer(("0.0.0.0", port), Web))
    for port in (22, 3306):
        serve(TTCP(("0.0.0.0", port), Banner))
    serve(socketserver.ThreadingUDPServer(("0.0.0.0", 5353), UdpEcho))
    print("lab services: http 80,8080 | tcp 22,3306 | udp 5353", flush=True)
    threading.Event().wait()
