import { OnGatewayInit, WebSocketGateway } from "@nestjs/websockets";

@WebSocketGateway()
export class WebsocketGateway implements OnGatewayInit
{
  afterInit(server: any)
  {
    server.on('connection', (ws: WebSocket) =>
    {
      // TODO: Generate a connection ID and log it

      ws.onmessage = (event: MessageEvent) =>
      {
        let payload = event.data;

        // Return data to sender
        ws.send(`got data: ${payload}`);
      }
    });
  }
}
