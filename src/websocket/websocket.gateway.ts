import { OnGatewayDisconnect, OnGatewayInit, WebSocketGateway } from "@nestjs/websockets";
import { randomBytes } from "crypto"; // This is probably a shit way to import this lmao
import { Connection } from "../types/connection";
import {GeneralPayload} from "../types/general-payload";

@WebSocketGateway()
export class WebsocketGateway implements OnGatewayInit, OnGatewayDisconnect
{
  private connections: Connection[] = [];

  private createConnectionId(): string
  {
    // TODO: Make sure this connection ID has not already been used
    return randomBytes(8).toString('hex').toUpperCase();
  }

  afterInit(server: any)
  {
    server.on('connection', (ws: WebSocket) =>
    {
      // Declare the connection and add it to the connection list
      let thisConnection: Connection = new Connection(this.createConnectionId(), ws);
      this.connections.push(thisConnection);
      console.log(`New connection: ${thisConnection.id}, ${this.connections.length} total`);

      // Ask the client for credentials
      ws.send(new GeneralPayload('status', 'auth').toString());

      // Register a callback for getting a message
      ws.onmessage = (event: MessageEvent) =>
      {
        let payload = event.data;

        // Return data to sender
        ws.send(`got data: ${payload}`);
      }
    });
  }

  handleDisconnect(client: WebSocket)
  {
    // Get the connection from the list and its index
    const connection: Connection = this.connections.find(c => c.ws === client);
    const connectionIndex: number = this.connections.indexOf(connection);

    // Remove the connection from the list
    this.connections.splice(connectionIndex, 1);

    console.log(`Client ${connection.id} disconnected, ${this.connections.length} remaining`);
  }
}
