import TcpSocket from 'react-native-tcp-socket';
import type { ClientMessage, ServerMessage } from '../types/protocol';

type MessageListener = (msg: ServerMessage) => void;
type StatusListener = (connected: boolean, error?: string) => void;

/**
 * Native raw TCP socket client matching CMPE 436 lecture4-2.pdf TCPClient:
 * - Connects directly to our plain-Java ServerSocket (default port 6789)
 * - Writes newline-delimited UTF-8 JSON lines
 * - Buffers incoming TCP byte stream chunks and splits on '\n'
 */
class UndercookedTcpClient {
  private socket: ReturnType<typeof TcpSocket.createConnection> | null = null;
  private rxBuffer = '';
  private messageListeners = new Set<MessageListener>();
  private statusListeners = new Set<StatusListener>();
  private isConnected = false;

  connect(host: string, port: number): void {
    this.disconnect();
    this.rxBuffer = '';

    const sock = TcpSocket.createConnection(
      { host: host.trim(), port },
      () => {
        this.isConnected = true;
        this.notifyStatus(true);
      },
    );

    this.socket = sock;

    sock.on('data', (chunk: string | { toString(encoding?: string): string }) => {
      const text = typeof chunk === 'string' ? chunk : chunk.toString('utf8');
      this.rxBuffer += text;

      const lines = this.rxBuffer.split('\n');
      // Keep the last possibly-incomplete chunk for the next TCP data event
      this.rxBuffer = lines.pop() ?? '';

      for (const rawLine of lines) {
        const trimmed = rawLine.trim();
        if (!trimmed) {
          continue;
        }
        try {
          const parsed = JSON.parse(trimmed) as ServerMessage;
          for (const listener of this.messageListeners) {
            listener(parsed);
          }
        } catch (err) {
          console.warn('[TcpClient] Failed to parse JSON line:', trimmed, err);
        }
      }
    });

    sock.on('error', (error: Error) => {
      const wasConnected = this.isConnected;
      this.isConnected = false;
      this.notifyStatus(false, error?.message || (wasConnected ? 'Connection lost' : 'Unable to reach TCP server'));
    });

    sock.on('close', () => {
      this.isConnected = false;
      this.socket = null;
      this.notifyStatus(false);
    });
  }

  send(msg: ClientMessage): boolean {
    if (!this.socket || !this.isConnected) {
      return false;
    }
    try {
      this.socket.write(JSON.stringify(msg) + '\n', 'utf8');
      return true;
    } catch (err) {
      console.warn('[TcpClient] Write error:', err);
      return false;
    }
  }

  disconnect(): void {
    if (this.socket) {
      try {
        this.socket.destroy();
      } catch {
        // Ignore close errors
      }
      this.socket = null;
    }
    this.isConnected = false;
    this.rxBuffer = '';
  }

  onMessage(listener: MessageListener): () => void {
    this.messageListeners.add(listener);
    return () => this.messageListeners.delete(listener);
  }

  onStatus(listener: StatusListener): () => void {
    this.statusListeners.add(listener);
    return () => this.statusListeners.delete(listener);
  }

  private notifyStatus(connected: boolean, error?: string): void {
    for (const listener of this.statusListeners) {
      listener(connected, error);
    }
  }
}

export const tcpClient = new UndercookedTcpClient();
