export class Connection
{
  constructor(id: string, ws: WebSocket)
  {
    this.id = id;
    this.ws = ws;
  }

  id: string;
  ws: WebSocket;
}
