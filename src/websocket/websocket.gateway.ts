import { OnGatewayDisconnect, OnGatewayInit, WebSocketGateway } from "@nestjs/websockets";
import { randomBytes } from "crypto"; // This is probably a shit way to import this lmao
import { Connection } from "../types/connection";
import {GeneralPayload} from "../types/payloads/general-payload";
import {AuthPayload} from "../types/payloads/auth-payload";
import {AuthorizedConnection} from "../types/authorized-connection";

@WebSocketGateway()
export class WebsocketGateway implements OnGatewayInit, OnGatewayDisconnect
{
  private connections: (Connection | AuthorizedConnection)[] = [];

  private createConnectionId(): string
  {
    // TODO: Make sure this connection ID has not already been used
    return randomBytes(8).toString('hex').toUpperCase();
  }

  afterInit(server: any): void
  {
    server.on('connection', (ws: WebSocket): void =>
    {
      // Declare the connection and add it to the connection list
      let thisConnection: Connection | AuthorizedConnection = new Connection(this.createConnectionId(), ws);
      this.connections.push(thisConnection);
      console.log(`New connection: ${thisConnection.id}, ${this.connections.length} total`);

      // Ask the client for credentials
      ws.send(new GeneralPayload('status', 'auth').toString());

      // Register a callback for getting a message
      ws.onmessage = async (event: MessageEvent): Promise<void> =>
      {
        // Store the received data
        let payload: GeneralPayload = JSON.parse(event.data);

        // Let the client know if it sent invalid data
        if (!payload.type) return ws.send(new GeneralPayload('invalid', payload).toString());

        // Process data based on its type
        switch (payload.type)
        {
          case 'auth':
            // Set thisConnection to have type Connection, so that the authorize() function becomes available
            thisConnection = thisConnection as Connection;

            // Store the payload as an AuthPayload to get access to the token
            const authPayload: AuthPayload = payload as AuthPayload;

            // Try to upgrade the connection to an AuthorizedConnection
            thisConnection = await thisConnection.authorize(authPayload.token);

            // Check if the authorization was successful
            if (thisConnection instanceof AuthorizedConnection) return ws.send(new GeneralPayload('status', 'ok').toString());
            else return ws.send(new GeneralPayload('invalid', payload).toString());
          case 'message': case 'delete': case 'system': case 'typing':
            return ws.send('Not yet implemented');
          default:
            return ws.send(new GeneralPayload('invalid', payload).toString());
        }
      }
    });
  }

  handleDisconnect(client: WebSocket): void
  {
    // Get the connection from the list and its index
    const connection: Connection | AuthorizedConnection = this.connections.find(c => c.ws === client);
    const connectionIndex: number = this.connections.indexOf(connection);

    // Remove the connection from the list
    this.connections.splice(connectionIndex, 1);

    console.log(`Client ${connection.id} disconnected, ${this.connections.length} remaining`);
  }
}
