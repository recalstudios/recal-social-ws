export interface Connection
{
  id: string;
  ws: WebSocket;
  rooms: number[];
}
