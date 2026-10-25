import { useEffect, useRef, useState } from "react";

/** Ephemeral notifications trigger bounded REST refreshes; REST remains the authoritative state. */
export function useAdmissionStream(admission, onEvent) {
  const callback = useRef(onEvent);
  callback.current = onEvent;
  const [connection, setConnection] = useState("CONNECTING");
  useEffect(() => {
    let disposed = false,
      socket,
      reconnect,
      flush,
      delay = 1000,
      latest = null;
    const emit = (event) => {
      latest = event;
      if (!flush)
        flush = setTimeout(() => {
          flush = null;
          if (!disposed) callback.current?.(latest);
        }, 500);
    };
    const connect = () => {
      setConnection("CONNECTING");
      socket = new WebSocket(
        `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws/monitoring?admissionId=${admission}`,
      );
      socket.onopen = () => {
        delay = 1000;
        setConnection("CONNECTED");
        emit({ type: "RESYNC" });
      };
      socket.onmessage = (e) => {
        try {
          const event = JSON.parse(e.data);
          if (
            event.type === "READING" ||
            event.type === "ALERT" ||
            event.type === "DEVICE_STATUS"
          )
            emit(event);
        } catch {
          /* Ignore malformed transport notifications; REST state is retained. */
        }
      };
      socket.onclose = () => {
        if (!disposed) {
          setConnection("DISCONNECTED");
          reconnect = setTimeout(connect, delay);
          delay = Math.min(delay * 2, 30000);
        }
      };
      socket.onerror = () => socket.close();
    };
    connect();
    return () => {
      disposed = true;
      clearTimeout(reconnect);
      clearTimeout(flush);
      socket?.close();
    };
  }, [admission]);
  return connection;
}
